package com.sdai.news.data

import android.content.Context
import com.sdai.news.data.db.ArticleEntity
import com.sdai.news.data.db.BookmarkEntity
import com.sdai.news.data.db.SDAIDatabase
import com.sdai.news.data.remote.HackerNewsClient
import com.sdai.news.data.remote.HuggingFaceClient
import com.sdai.news.data.remote.OgImageFetcher
import com.sdai.news.data.remote.OksurfClient
import com.sdai.news.data.remote.RedditClient
import com.sdai.news.data.remote.RssClient
import com.sdai.news.data.remote.WordPressClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Zero-maintenance, zero-cost news pipeline.
 *
 * Five independent source layers run in parallel — each wrapped in its
 * own `runCatching` so a dead provider can't take the feed down. The
 * dedupe step picks the best variant per article (preferring records
 * that already ship an image). og:image scraping backfills only what's
 * left, and image-less articles never appear in the feed (DAO filter).
 *
 * | Layer | Source | Has images | Notes |
 * |-------|--------|------------|-------|
 * | 1 | WordPress JSON | yes | MarkTechPost, AI News, Unite.AI |
 * | 2 | Reddit `.json` | yes | r/ArtificialInteligence + 4 more |
 * | 3 | OKSURF JSON   | sometimes | Google News aggregator |
 * | 4 | Google News RSS | no (og:image fills in) | rotating 4-of-12 |
 * | 5 | Direct RSS    | yes (media:content) | TechCrunch, Verge, … |
 */
class ArticleRepository(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val db = SDAIDatabase.get(context)
    private val articleDao = db.articleDao()
    private val bookmarkDao = db.bookmarkDao()
    private val prefs = PrefsStore(context.applicationContext)

    private val bgScope = CoroutineScope(SupervisorJob() + io)
    private val queryRotation = AtomicInteger(0)

    // Brand-name queries only. Broad terms like "artificial intelligence"
    // pulled tons of low-quality vendor PR; pivot to specific AI labs +
    // product names so every result is unambiguously on-topic and
    // worth surfacing.
    private val googleNewsQueryPool = listOf(
        "OpenAI",
        "Anthropic",
        "\"Google DeepMind\"",
        "\"Meta AI\"",
        "\"Mistral AI\"",
        "Perplexity",
        "Claude",
        "Gemini",
        "\"GPT-5\" OR \"GPT-4\"",
        "Llama OR \"Llama 3\"",
        "Sora",
        "\"AI startup funding\"",
    )

    private val directRssFeeds = listOf(
        // Tier 0 — established tech publishers
        "https://techcrunch.com/category/artificial-intelligence/feed/",
        "https://www.theverge.com/rss/ai-artificial-intelligence/index.xml",
        "https://venturebeat.com/category/ai/feed/",
        "https://www.wired.com/feed/tag/ai/latest/rss",
        "https://feeds.arstechnica.com/arstechnica/index",

        // Tier 1 — first-party AI lab blogs (source-of-truth for launches)
        "https://www.anthropic.com/news/rss.xml",
        "https://www.anthropic.com/research/rss.xml",
        "https://openai.com/blog/rss.xml",
        "https://deepmind.google/discover/blog/rss.xml",
        "https://blog.research.google/feeds/posts/default",
        "https://ai.meta.com/blog/rss/",
        "https://huggingface.co/blog/feed.xml",
        "https://developer.nvidia.com/blog/feed",
        "https://aws.amazon.com/blogs/machine-learning/feed/",
        "https://blogs.microsoft.com/ai/feed/",

        // GitHub release Atom — open-source AI infra. New Ollama / vLLM
        // / PyTorch versions are major news for the local-LLM crowd and
        // these endpoints are completely unauthenticated.
        "https://github.com/ollama/ollama/releases.atom",
        "https://github.com/vllm-project/vllm/releases.atom",
        "https://github.com/pytorch/pytorch/releases.atom",
    )

    fun observeArticles(): Flow<List<Article>> =
        articleDao.observeRecent().map { rows -> rows.map { it.toArticle() } }

    /** Tier-scoped variant: pass null for the "All" chip. */
    fun observeArticlesByTier(tier: String?): Flow<List<Article>> =
        if (tier == null) observeArticles()
        else articleDao.observeByTier(tier).map { rows -> rows.map { it.toArticle() } }

    fun observeBookmarks(): Flow<List<BookmarkEntity>> = bookmarkDao.observeAll()
    fun observeBookmarkIds(): Flow<List<String>> = bookmarkDao.observeIds()

    /**
     * Fan out to every source layer and merge results into Room.
     *
     * Three speed gates:
     *  1. **Cache freshness short-circuit.** If the last full refresh
     *     completed less than [FRESH_WINDOW_MS] ago AND there's
     *     cached data, return 0 without hitting the network. Makes
     *     warm app launches instant.
     *  2. **Streaming upsert.** Each layer's results are upserted
     *     into Room as soon as that layer completes — the Room flow
     *     emits and the UI updates. The user sees first-party blog
     *     posts in <1 s instead of waiting for the slowest layer
     *     (Hacker News's ~20 fan-out queries).
     *  3. **Per-layer throttle.** Each layer skips if its own
     *     [lastFetchedMs] is within its [MIN_INTERVAL_MS]. Rapid
     *     pull-to-refresh costs nothing for layers that already ran
     *     recently.
     *
     * Returns the total fresh-row count (sum across layers that
     * actually ran). Returns 0 when fully throttled — that's not
     * an error; the cache is already current.
     */
    suspend fun refresh(): Int = withContext(io) {
        val now = System.currentTimeMillis()

        // Gate 1 — full-cache freshness short-circuit. If we ran a
        // full refresh recently and there's something in Room, skip
        // entirely. Cold installs (cache empty) always proceed.
        val lastFull = prefs.lastFullRefreshMs()
        if (now - lastFull < FRESH_WINDOW_MS && articleDao.recent(limit = 1).isNotEmpty()) {
            return@withContext 0
        }

        val googleQueries = pickGoogleQueries(count = 4)
        var totalNew = 0
        val anyLayerRan = java.util.concurrent.atomic.AtomicBoolean(false)

        coroutineScope {
            val jobs = buildList {
                add(launch { totalNew += runLayer("wordpress", MIN_INTERVAL_WP_MS, anyLayerRan) {
                    WordPressClient.fetchAll().map { it.toAggregated() }
                } })
                add(launch { totalNew += runLayer("reddit", MIN_INTERVAL_REDDIT_MS, anyLayerRan) {
                    RedditClient.fetchAll().map { it.toAggregated() }
                } })
                add(launch { totalNew += runLayer("oksurf", MIN_INTERVAL_OKSURF_MS, anyLayerRan) {
                    OksurfClient.fetchTechnology().map { it.toAggregated() }
                } })
                add(launch { totalNew += runLayer("hn", MIN_INTERVAL_HN_MS, anyLayerRan) {
                    HackerNewsClient.fetchAll().map { it.toAggregated() }
                } })
                add(launch { totalNew += runLayer("hf", MIN_INTERVAL_HF_MS, anyLayerRan) {
                    HuggingFaceClient.fetchDailyPapers().map { it.toAggregated() }
                } })
                add(launch { totalNew += runLayer("rss", MIN_INTERVAL_RSS_MS, anyLayerRan) {
                    directRssFeeds.flatMap { rss(it, isGoogleNews = false) }
                } })
                // Google News queries: one layer per query so they
                // throttle independently and stream as they finish.
                googleQueries.forEach { q ->
                    add(launch { totalNew += runLayer("gn:$q", MIN_INTERVAL_GN_MS, anyLayerRan) {
                        rss(googleNewsUrl(q), isGoogleNews = true)
                    } })
                }
            }
            jobs.forEach { it.join() }
        }

        // Only mark the full refresh as complete if at least one
        // layer actually fetched. Otherwise the throttle would skip
        // forever if it ever got stuck.
        if (anyLayerRan.get()) {
            prefs.setLastFullRefreshMs(now)
            articleDao.deleteOlderThan(now - 24L * 60 * 60 * 1000)
        }
        // Only surface a "network down" error on cold install — i.e.
        // we attempted to fetch and got literally nothing, AND there's
        // also nothing in the cache to fall back on. If we have any
        // cached rows, swallow the error and let the user see the
        // cache (the refresh icon goes back to its idle state).
        if (totalNew == 0 && anyLayerRan.get() && articleDao.recent(limit = 1).isEmpty()) {
            throw IllegalStateException(
                "Could not reach any news source. Check your internet connection and try again."
            )
        }
        totalNew
    }

    /**
     * Single-layer wrapper: throttle check → fetch → upsert. Each
     * layer's results are pushed to Room independently so the UI
     * updates incrementally instead of waiting for the slowest
     * layer to complete.
     */
    private suspend fun runLayer(
        name: String,
        minIntervalMs: Long,
        anyLayerRan: java.util.concurrent.atomic.AtomicBoolean,
        fetcher: suspend () -> List<Aggregated>,
    ): Int {
        val now = System.currentTimeMillis()
        val last = prefs.lastFetchedMs(name)
        if (now - last < minIntervalMs) return 0  // throttled — too soon

        val items = runCatching { fetcher() }.getOrDefault(emptyList())
        anyLayerRan.set(true)
        if (items.isEmpty()) {
            // Mark fetched anyway so we don't hammer a momentarily-
            // down endpoint on every refresh.
            prefs.setLastFetchedMs(name, now)
            return 0
        }
        val count = processAndUpsert(items, now, enrichImagesAfter = true)
        prefs.setLastFetchedMs(name, now)
        return count
    }

    /**
     * Atom-only refresh from arXiv. Triggered exclusively by
     * [com.sdai.news.notify.ArxivRefreshWorker] (≤ 1×/6 h) — arXiv
     * rate-limits hard if pull-to-refresh hits it on every swipe.
     * Returns the number of new rows upserted.
     */
    suspend fun refreshArxivOnly(): Int = withContext(io) {
        val now = System.currentTimeMillis()
        val collected = runCatching {
            RssClient.fetch(ARXIV_AI_FEED_URL).map { it.toAggregated(isGoogleNews = false) }
        }.getOrDefault(emptyList())
        if (collected.isEmpty()) return@withContext 0
        processAndUpsert(collected, now, enrichImagesAfter = false)
    }

    /**
     * Shared post-fetch pipeline: English filter → exact-match dedup →
     * token-overlap dedup → upsert. Both `refresh()` and
     * `refreshArxivOnly()` route through this so the rules stay in
     * one place.
     */
    private suspend fun processAndUpsert(
        collected: List<Aggregated>,
        now: Long,
        enrichImagesAfter: Boolean,
    ): Int {
        // Gate 1: spam / content-farm patterns. Drop before doing
        // anything expensive so we never even dedup junk headlines.
        val nonSpam = collected.filter { !isSpam(it.title) }

        // Gate 2: non-English headlines. Reddit/WordPress/syndicated
        // RSS occasionally surface translated copies even though the
        // Google query is pinned to en-US.
        val englishOnly = nonSpam.filter { isLikelyEnglish(it.title) }

        // Gate 3: exact-match dedup by normalised title. Prefer the
        // variant that has both an image AND a description; otherwise
        // the freshest record.
        val exactDeduped = englishOnly
            .groupBy { normalise(it.title) }
            .map { (_, group) ->
                group.sortedWith(
                    compareByDescending<Aggregated> { it.imageUrl != null }
                        .thenByDescending { it.description.isNotBlank() }
                        .thenByDescending { it.publishedAtMillis ?: 0L }
                ).first()
            }

        // Gate 4: token-overlap dedup. Catches headline variants the
        // exact match misses ("OpenAI launches GPT-5" vs "OpenAI
        // launches GPT-5 today"). Two articles are duplicates if their
        // word-token sets overlap by ≥ JACCARD_THRESHOLD AND they were
        // published within 6 hours. O(n²) over the already-shrunk set.
        val deduped = collapseByTokenOverlap(exactDeduped)

        // Gate 5: source diversity cap. No single source may occupy
        // more than DIVERSITY_CAP of the visible feed. Keeps Google
        // News + one prolific Reddit sub from drowning out everything
        // else. Within each source, the weight-sorted top picks win.
        val diverse = applyDiversityCap(deduped)

        val rows = diverse.map { it.toEntity(now) }
        articleDao.upsertAll(rows)

        if (enrichImagesAfter) {
            val needsImage = diverse.filter { it.imageUrl == null }
            if (needsImage.isNotEmpty()) {
                bgScope.launch { enrichImages(needsImage) }
            }
        }
        return rows.size
    }

    // ── Per-source quality weighting + tier classification ──────────

    /**
     * Quality weight 0-10 for a source string. Tuned so a fresh
     * OpenAI post (weight 10) always ranks above an aged Reddit hot
     * post (weight 4) regardless of recency — see [ArticleDao.observeRecent].
     */
    private fun weightForSource(source: String): Int {
        val s = source.lowercase()
        return when {
            // First-party AI lab blogs — the source of truth
            "anthropic" in s -> 10
            "openai" in s -> 10
            "deepmind" in s -> 10
            "ai.meta" in s || "meta ai" in s -> 10
            "nvidia" in s -> 9
            "huggingface" in s || "hf papers" in s -> 9
            "mistral" in s -> 9
            "google research" in s || "research.google" in s -> 9
            "aws" in s || "amazon" in s -> 8
            "microsoft" in s -> 8
            // Established AI publications
            "techcrunch" in s -> 8
            "venturebeat" in s -> 8
            "the verge" in s || "theverge" in s -> 8
            "wired" in s -> 7
            "ars technica" in s || "arstechnica" in s -> 7
            "marktechpost" in s -> 7
            "unite.ai" in s || "unite ai" in s -> 7
            "ai news" in s -> 7
            // GitHub releases
            "github.com" in s || "ollama" in s || "vllm" in s || "pytorch" in s -> 7
            // Curated community
            "hn" in s || "hacker news" in s -> 7
            // Reddit + Google News — high volume, lower signal
            s.startsWith("r/") || "reddit" in s -> 4
            "google news" in s -> 3
            else -> 5
        }
    }

    /** Tier classification — drives the filter chip strip in FeedScreen. */
    private fun tierForSource(source: String): String {
        val w = weightForSource(source)
        val s = source.lowercase()
        return when {
            // Research papers and benchmarks
            "huggingface" in s || "hf papers" in s || "arxiv" in s -> "research"
            // Community discussion
            s.startsWith("r/") || "reddit" in s || "hn" in s || "hacker news" in s -> "community"
            // First-party labs
            w >= 9 -> "breaking"
            // Everyone else = industry press
            else -> "industry"
        }
    }

    // ── Spam / content-farm filter ──────────────────────────────────

    private fun isSpam(title: String): Boolean =
        SPAM_PATTERNS.any { it.containsMatchIn(title) }

    // ── Source diversity cap ────────────────────────────────────────

    /**
     * Limit any one source to at most DIVERSITY_CAP of the feed.
     * Within each source's quota we keep the highest-weighted, then
     * freshest entries — the same ranking the DAO will eventually use.
     */
    private fun applyDiversityCap(items: List<Aggregated>): List<Aggregated> {
        if (items.size < MIN_FOR_CAP) return items
        val maxPerSource = maxOf(MIN_CAP_PER_SOURCE, (items.size * DIVERSITY_CAP).toInt())
        val grouped = items.groupBy { it.source }
        return grouped.flatMap { (_, group) ->
            group.sortedWith(
                compareByDescending<Aggregated> { weightForSource(it.source) }
                    .thenByDescending { it.publishedAtMillis ?: 0L }
            ).take(maxPerSource)
        }
    }

    private fun pickGoogleQueries(count: Int): List<String> {
        val pool = googleNewsQueryPool
        val start = queryRotation.getAndAdd(count) % pool.size
        return (0 until count).map { i -> pool[(start + i) % pool.size] }
    }

    private fun googleNewsUrl(query: String): String =
        "https://news.google.com/rss/search?q=" +
            java.net.URLEncoder.encode(query, "UTF-8") +
            "&hl=en-US&gl=US&ceid=US:en"

    private fun rss(url: String, isGoogleNews: Boolean): List<Aggregated> {
        return RssClient.fetch(url).map { it.toAggregated(isGoogleNews) }
    }

    /**
     * Background og:image sweeper.
     *
     * Replaces the previous 20 s-budgeted batch scraper. The old
     * model meant ~30 articles got their image (6 semaphore slots ×
     * 5 s per request × 20 s budget); the rest stayed image-less
     * forever. The new model:
     *
     *  - **No overall timeout.** Per-request timeout stays at 6 s via
     *    [HttpClient.fast]; that's plenty per article. The sweeper
     *    runs as long as it takes.
     *  - **Idempotent.** An in-flight set of article IDs prevents
     *    a rapid second refresh from re-queueing items already being
     *    scraped by the first.
     *  - **10-permit semaphore** (was 6). More throughput per second
     *    without hammering any one host (most articles come from
     *    distinct domains).
     *
     * Articles whose link resolves to a real og:image get their row
     * updated via [ArticleDao.setImageUrl]; Coil sees the URL change
     * on the Flow and swaps the placeholder for the real image
     * without a UI refresh.
     */
    private suspend fun enrichImages(items: List<Aggregated>) {
        val sem = Semaphore(10)
        coroutineScope {
            items.forEach { item ->
                val id = normalise(item.title).hashCode().toString()
                if (!enrichInFlight.add(id)) return@forEach   // already being scraped
                launch {
                    try {
                        sem.withPermit {
                            val img = OgImageFetcher.fetch(item.link) ?: return@withPermit
                            articleDao.setImageUrl(id, img)
                        }
                    } finally {
                        enrichInFlight.remove(id)
                    }
                }
            }
        }
    }

    /** IDs currently being scraped by [enrichImages] — prevents a
     *  rapid second refresh from duplicating work in flight. */
    private val enrichInFlight: java.util.concurrent.ConcurrentHashMap.KeySetView<String, Boolean> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()

    suspend fun bookmark(article: Article) = withContext(io) {
        bookmarkDao.insert(
            BookmarkEntity(
                id = article.id,
                title = article.title,
                url = article.url,
                imageUrl = article.imageUrl,
                source = article.source,
                savedAtMillis = System.currentTimeMillis(),
            )
        )
    }

    suspend fun removeBookmark(id: String) = withContext(io) {
        bookmarkDao.deleteById(id)
    }

    // ── Source-agnostic shape ───────────────────────────────────────

    private data class Aggregated(
        val title: String,
        val link: String,
        val source: String,
        val imageUrl: String?,
        val description: String,
        val publishedAtMillis: Long?,
    )

    private fun WordPressClient.WpItem.toAggregated(): Aggregated = Aggregated(
        title = title,
        link = link,
        source = source,
        imageUrl = imageUrl,
        description = description,
        publishedAtMillis = pubDateMillis,
    )

    private fun RedditClient.RedditItem.toAggregated(): Aggregated = Aggregated(
        title = title,
        link = link,
        source = source,
        imageUrl = imageUrl,
        description = "",       // Reddit titles are usually self-explanatory
        publishedAtMillis = pubDateMillis,
    )

    private fun OksurfClient.OksurfItem.toAggregated(): Aggregated = Aggregated(
        title = title,
        link = link,
        source = source ?: "Google News",
        imageUrl = imageUrl,
        description = "",
        publishedAtMillis = null,
    )

    private fun HackerNewsClient.HnItem.toAggregated(): Aggregated = Aggregated(
        title = title,
        link = link,
        source = source,
        imageUrl = null,                  // og:image enrichment fills it in
        description = "",
        publishedAtMillis = pubDateMillis,
    )

    private fun HuggingFaceClient.HfPaper.toAggregated(): Aggregated = Aggregated(
        title = title,
        link = link,
        source = "HF Papers",
        imageUrl = imageUrl,
        description = summary,
        publishedAtMillis = publishedAtMillis,
    )

    private fun RssClient.RssItem.toAggregated(isGoogleNews: Boolean): Aggregated {
        val (cleanTitle, inferredSource) =
            if (isGoogleNews) stripTrailingSource(title) else title to null
        return Aggregated(
            title = cleanTitle,
            link = link,
            source = source?.takeIf { it.isNotBlank() } ?: inferredSource ?: "News",
            imageUrl = imageUrl,
            description = "",
            publishedAtMillis = pubDateMillis,
        )
    }

    private fun stripTrailingSource(title: String): Pair<String, String?> {
        val idx = title.lastIndexOf(" - ")
        if (idx <= 0) return title to null
        return title.substring(0, idx).trim() to title.substring(idx + 3).trim()
    }

    private fun Aggregated.toEntity(now: Long): ArticleEntity = ArticleEntity(
        id = normalise(title).hashCode().toString(),
        title = title,
        description = description,
        summary = null,
        url = link,
        imageUrl = imageUrl,
        source = source,
        category = null,
        publishedAtMillis = publishedAtMillis ?: now,
        fetchedAtMillis = now,
        weight = weightForSource(source),
        tier = tierForSource(source),
    )

    private fun normalise(title: String): String =
        title.lowercase().filter { it.isLetterOrDigit() }.take(80)

    /**
     * Second-pass dedup using Jaccard similarity over tokenised
     * titles. Two records are merged when:
     *   - they share ≥ [JACCARD_THRESHOLD] of their unique word tokens
     *     (after lowercasing, removing stopwords + tokens <3 chars), AND
     *   - their publishedAt timestamps are within
     *     [TIME_WINDOW_MS] of each other.
     *
     * Within a merged cluster we keep the record with the better
     * payload (image > description > recency) — same ordering as
     * pass-1 dedup.
     *
     * O(n²) over the deduped set. After the first pass `items` is
     * already small (~200 entries on a healthy day), so 40k cheap
     * set-intersections is fine.
     */
    private fun collapseByTokenOverlap(items: List<Aggregated>): List<Aggregated> {
        if (items.size < 2) return items
        val tokens: List<Set<String>> = items.map { tokenise(it.title) }
        val assigned = IntArray(items.size) { -1 }
        var nextCluster = 0
        for (i in items.indices) {
            if (assigned[i] != -1) continue
            assigned[i] = nextCluster
            for (j in (i + 1) until items.size) {
                if (assigned[j] != -1) continue
                if (!withinTimeWindow(items[i], items[j])) continue
                if (jaccard(tokens[i], tokens[j]) >= JACCARD_THRESHOLD) {
                    assigned[j] = nextCluster
                }
            }
            nextCluster++
        }
        return items.withIndex()
            .groupBy { assigned[it.index] }
            .map { (_, group) ->
                group.map { it.value }.sortedWith(
                    compareByDescending<Aggregated> { it.imageUrl != null }
                        .thenByDescending { it.description.isNotBlank() }
                        .thenByDescending { it.publishedAtMillis ?: 0L }
                ).first()
            }
    }

    private fun tokenise(title: String): Set<String> {
        val raw = title.lowercase().split(NON_WORD)
        return raw.filter { it.length >= 3 && it !in ENGLISH_STOPWORDS }.toSet()
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.count { it in b }
        val union = a.size + b.size - intersection
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }

    private fun withinTimeWindow(a: Aggregated, b: Aggregated): Boolean {
        val pa = a.publishedAtMillis ?: return true   // unknown date → don't gate on time
        val pb = b.publishedAtMillis ?: return true
        return kotlin.math.abs(pa - pb) <= TIME_WINDOW_MS
    }

    /**
     * Heuristic English-language filter. Three gates, in order:
     *
     *  1. **Latin block check.** ≥85 % of letters must fall in
     *     U+0000..U+024F (Basic Latin + Latin-1 Supplement + Latin
     *     Extended-A). Rejects Devanagari, CJK, Arabic, Cyrillic, Tamil,
     *     Thai, Hebrew, Greek, Bengali at this gate.
     *  2. **Foreign-tell veto.** If any common Spanish/French/German/
     *     Italian/Portuguese function word appears as a whole word,
     *     reject. These short words don't appear in real English
     *     headlines (`que`, `und`, `der`, `del`, `pour`, `dans`).
     *  3. **English-stopword sentinel.** For longer titles (≥ 6 words),
     *     require at least one common English word. Short headlines
     *     (≤ 5 words like "OpenAI releases o4") bypass this gate
     *     because they often legitimately don't contain any stopword.
     *
     * Zero dependencies; runs in microseconds per title.
     */
    private fun isLikelyEnglish(title: String): Boolean {
        val letters = title.filter { it.isLetter() }
        if (letters.length < MIN_LETTERS) return false
        val latin = letters.count { it.code <= 0x024F }
        if (latin.toDouble() / letters.length < LATIN_RATIO_THRESHOLD) return false

        val words = title.lowercase().split(NON_WORD).filter { it.isNotBlank() }
        if (words.any { it in FOREIGN_TELLS }) return false
        if (words.size <= SHORT_TITLE_WORDS) return true
        return words.any { it in ENGLISH_STOPWORDS }
    }

    private companion object {
        const val LATIN_RATIO_THRESHOLD = 0.85
        const val MIN_LETTERS = 4
        const val SHORT_TITLE_WORDS = 5
        const val JACCARD_THRESHOLD = 0.80
        const val TIME_WINDOW_MS = 6L * 60L * 60L * 1000L  // 6 hours

        // ── Refresh timing — controls cache freshness + per-source
        //    throttling. All in milliseconds.

        /** Skip the full refresh entirely if the cache is this fresh.
         *  5 min = "the user just opened the app — don't re-fetch
         *  everything just because they backgrounded for a minute." */
        const val FRESH_WINDOW_MS = 5L * 60L * 1000L

        /** First-party lab blogs publish rarely — 30 min throttle. */
        const val MIN_INTERVAL_RSS_MS = 30L * 60L * 1000L

        /** Google News, OKSURF — relatively fresh, 5–10 min. */
        const val MIN_INTERVAL_GN_MS = 5L * 60L * 1000L
        const val MIN_INTERVAL_OKSURF_MS = 10L * 60L * 1000L

        /** Reddit / Hacker News — community velocity, 10 min. */
        const val MIN_INTERVAL_REDDIT_MS = 10L * 60L * 1000L
        const val MIN_INTERVAL_HN_MS = 10L * 60L * 1000L

        /** HuggingFace daily papers refreshes once/24h upstream — no
         *  point hitting it more than hourly. */
        const val MIN_INTERVAL_HF_MS = 60L * 60L * 1000L

        /** WordPress publishers post a few times a day — 15 min. */
        const val MIN_INTERVAL_WP_MS = 15L * 60L * 1000L

        // Diversity cap — no single source > 20 % of the visible feed.
        // [MIN_FOR_CAP] avoids capping when the batch is tiny (early
        // app launch / sparse refresh).
        const val DIVERSITY_CAP = 0.20
        const val MIN_FOR_CAP = 20
        const val MIN_CAP_PER_SOURCE = 3

        val NON_WORD = Regex("\\W+")

        /**
         * Content-farm patterns. If any one of these matches the
         * title, the article is rejected before dedup. Tuned from
         * the noise we were seeing in production: listicles,
         * "ultimate guides", clickbait shockers, vendor PR lead-ins.
         *
         * All matches are case-insensitive.
         */
        val SPAM_PATTERNS: List<Regex> = listOf(
            Regex("""\b(sponsored|promoted|advertorial|webinar|sign\s*up|register\s*now)\b""", RegexOption.IGNORE_CASE),
            // Listicle bait — "Top 10 AI Tools", "Best AI tools", etc.
            Regex("""\b(top|best|greatest)\s+\d+\s+(ai|llm|chatbot|ml)\s+(tools?|apps?|websites?|hacks?|tips?|tricks?|prompts?)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(top|best)\s+(ai|llm)\s+(tools?|websites?|apps?)\s+(you|to)\b""", RegexOption.IGNORE_CASE),
            // Ultimate-guide bait
            Regex("""\b(ultimate|complete|definitive)\s+guide\s+to\b""", RegexOption.IGNORE_CASE),
            // Clickbait shockers
            Regex("""\b(you\s+won['']?t\s+believe|shocking|revolutionary\s+secret|mind[\s-]*blowing)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(must[\s-]*(know|read|see|try)|life[\s-]*changing)\b""", RegexOption.IGNORE_CASE),
            // Vendor press-release lead-ins ("Acme today announced …")
            Regex("""\b\w+(\s+\w+){0,4}\s+today\s+announced\b""", RegexOption.IGNORE_CASE),
            // Generic "X tips to Y" listicles
            Regex("""\b\d+\s+(simple|easy|quick|powerful)\s+(ways|tips|tricks|hacks)\b""", RegexOption.IGNORE_CASE),
        )

        /**
         * arXiv Atom feed for cs.AI / cs.LG / cs.CL, sorted by submission
         * date. We pull only the 30 newest entries per call. arXiv asks
         * clients to space requests 3+ seconds apart — we comply via the
         * 6-hour worker interval.
         */
        const val ARXIV_AI_FEED_URL =
            "https://export.arxiv.org/api/query?" +
                "search_query=cat:cs.AI+OR+cat:cs.LG+OR+cat:cs.CL" +
                "&sortBy=submittedDate&sortOrder=descending&max_results=30"

        /**
         * Common English function/content words. Broad enough that any
         * real English headline with ≥ 6 words contains at least one.
         */
        val ENGLISH_STOPWORDS = setOf(
            "the", "a", "an", "and", "or", "of", "to", "in", "on", "at",
            "is", "are", "was", "were", "be", "been", "being",
            "for", "with", "by", "from", "as", "into", "about",
            "it", "its", "this", "that", "these", "those",
            "has", "have", "had", "will", "would", "can", "could",
            "new", "now", "says", "said", "launches", "releases",
            "after", "before", "but", "not", "no", "yes",
        )

        /**
         * Function words that effectively never appear in an English
         * headline. Spotting any of these is a strong veto.
         *
         * Languages targeted: Spanish, French, German, Italian,
         * Portuguese. These five cover ~95 % of false positives we'd
         * otherwise let through (Latin-script European languages
         * are what Tier 4/5 RSS sometimes surfaces).
         */
        val FOREIGN_TELLS = setOf(
            // Spanish
            "que", "los", "las", "del", "con", "para", "una", "uno",
            "por", "más", "esta", "este", "como", "pero", "todo",
            // French
            "que", "dans", "pour", "avec", "sur", "sous", "leur",
            "leurs", "cette", "cet", "ces", "nous", "vous", "ils",
            "elles", "ainsi", "vers",
            // German
            "und", "der", "die", "das", "den", "dem", "des", "ein",
            "eine", "einen", "einem", "einer", "eines", "ist", "sind",
            "wird", "wurde", "wurden", "nicht", "auch", "auf", "mit",
            "für", "von", "zum", "zur", "über",
            // Italian
            "che", "della", "delle", "degli", "nella", "nelle", "negli",
            "sono", "anche", "molto", "tutti", "tutto",
            // Portuguese
            "uma", "para", "pelo", "pela", "como", "mas", "mais",
            "também", "esta", "este", "isso",
        )
    }
}
