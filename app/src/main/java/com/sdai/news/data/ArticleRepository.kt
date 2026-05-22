package com.sdai.news.data

import android.content.Context
import com.sdai.news.data.db.ArticleEntity
import com.sdai.news.data.db.BookmarkEntity
import com.sdai.news.data.db.SDAIDatabase
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
import kotlinx.coroutines.withTimeoutOrNull
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

    private val bgScope = CoroutineScope(SupervisorJob() + io)
    private val queryRotation = AtomicInteger(0)

    private val googleNewsQueryPool = listOf(
        "artificial intelligence",
        "generative AI",
        "OpenAI OR Anthropic OR \"Google DeepMind\"",
        "ChatGPT OR Claude OR Gemini",
        "large language model OR LLM",
        "AI startup funding",
        "machine learning research",
        "AI regulation OR policy",
        "AI agent OR autonomous",
        "computer vision OR robotics AI",
        "AI ethics OR AI safety",
        "neural network OR deep learning",
    )

    private val directRssFeeds = listOf(
        "https://techcrunch.com/category/artificial-intelligence/feed/",
        "https://www.theverge.com/rss/ai-artificial-intelligence/index.xml",
        "https://venturebeat.com/category/ai/feed/",
        "https://www.wired.com/feed/tag/ai/latest/rss",
        "https://feeds.arstechnica.com/arstechnica/index",
    )

    fun observeArticles(): Flow<List<Article>> =
        articleDao.observeRecent().map { rows -> rows.map { it.toArticle() } }

    fun observeBookmarks(): Flow<List<BookmarkEntity>> = bookmarkDao.observeAll()
    fun observeBookmarkIds(): Flow<List<String>> = bookmarkDao.observeIds()

    suspend fun refresh(): Int = withContext(io) {
        val now = System.currentTimeMillis()
        val googleQueries = pickGoogleQueries(count = 4)

        val collected: List<Aggregated> = coroutineScope {
            buildList {
                // 1 — WordPress JSON (rich descriptions + featured images)
                add(async { WordPressClient.fetchAll().map { it.toAggregated() } })
                // 2 — Reddit hot AI subs (always image-backed posts only)
                add(async { RedditClient.fetchAll().map { it.toAggregated() } })
                // 3 — OKSURF Google News aggregator
                add(async { OksurfClient.fetchTechnology().map { it.toAggregated() } })
                // 4 — Google News RSS, rotating subset of the query pool
                googleQueries.forEach { q ->
                    add(async { rss(googleNewsUrl(q), isGoogleNews = true) })
                }
                // 5 — Direct publisher RSS feeds
                directRssFeeds.forEach { url ->
                    add(async { rss(url, isGoogleNews = false) })
                }
            }.awaitAll().flatten()
        }

        if (collected.isEmpty()) {
            throw IllegalStateException(
                "Could not reach any news source. Check your internet connection and try again."
            )
        }

        // Dedup by normalised title. Prefer the variant that has both
        // an image AND a description; otherwise the freshest record.
        val deduped = collected
            .groupBy { normalise(it.title) }
            .map { (_, group) ->
                group.sortedWith(
                    compareByDescending<Aggregated> { it.imageUrl != null }
                        .thenByDescending { it.description.isNotBlank() }
                        .thenByDescending { it.publishedAtMillis ?: 0L }
                ).first()
            }

        val rows = deduped.map { it.toEntity(now) }
        articleDao.upsertAll(rows)
        articleDao.deleteOlderThan(now - 24L * 60 * 60 * 1000)

        val needsImage = deduped.filter { it.imageUrl == null }
        if (needsImage.isNotEmpty()) {
            bgScope.launch { enrichImages(needsImage) }
        }

        rows.size
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

    private suspend fun enrichImages(items: List<Aggregated>) {
        val sem = Semaphore(6)
        withTimeoutOrNull(20_000) {
            coroutineScope {
                items.forEach { item ->
                    launch {
                        sem.withPermit {
                            val img = OgImageFetcher.fetch(item.link) ?: return@withPermit
                            val id = normalise(item.title).hashCode().toString()
                            articleDao.setImageUrl(id, img)
                        }
                    }
                }
            }
        }
    }

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
    )

    private fun normalise(title: String): String =
        title.lowercase().filter { it.isLetterOrDigit() }.take(80)
}
