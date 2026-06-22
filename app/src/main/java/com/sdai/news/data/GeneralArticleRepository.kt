package com.sdai.news.data

import android.content.Context
import com.sdai.news.data.db.ArticleEntity
import com.sdai.news.data.db.SDAIDatabase
import com.sdai.news.data.remote.RssClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class GeneralArticleRepository(context: Context) {

    private val db = SDAIDatabase.get(context)
    private val articleDao = db.articleDao()
    private val seenDao = db.seenDao()

    /** Live set of article ids the reader has already seen. */
    fun observeSeenIds(): Flow<Set<String>> =
        seenDao.observeIds().map { it.toSet() }

    /** Mark an article read (dwelled on). Idempotent. */
    suspend fun markSeen(id: String) = withContext(Dispatchers.IO) {
        seenDao.insert(com.sdai.news.data.db.SeenEntity(id, System.currentTimeMillis()))
        // Keep the seen table bounded — read-state older than 14 days is
        // irrelevant once the article has long fallen out of the cache.
        seenDao.deleteOlderThan(System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000)
    }

    fun observeBySection(section: String?): Flow<List<Article>> =
        if (section == null) articleDao.observeAll().map { rows -> rows.map { it.toArticle() } }
        else articleDao.observeBySection(section).map { rows -> rows.map { it.toArticle() } }

    fun observeByCategory(category: String): Flow<List<Article>> =
        articleDao.observeByCategory(category).map { rows -> rows.map { it.toArticle() } }

    /** Text/image news only (excludes video). */
    fun observeTextNews(): Flow<List<Article>> =
        articleDao.observeTextNews().map { rows -> rows.map { it.toArticle() } }

    /** Video news — all regions ([section] null) or one region (world/national/regional). */
    fun observeVideo(section: String?): Flow<List<Article>> =
        (if (section == null) articleDao.observeAllVideo()
        else articleDao.observeVideoBySection(section))
            .map { rows -> rows.map { it.toArticle() } }

    /** Unified Mode × Region × Topic feed. [section]/[category] null = unfiltered. */
    fun observeFeed(isVideo: Boolean, section: String?, category: String?): Flow<List<Article>> =
        articleDao.observeFeed(isVideo, section, category).map { rows -> rows.map { it.toArticle() } }

    data class CategorizedFeed(
        val url: String,
        val source: String,
        val section: String,
        val category: String,
        /** "en" or a regional ISO code (e.g. "te"). */
        val lang: String = "en",
    )

    /** Global feeds (World + topics + World video) — same for everyone. */
    suspend fun refreshGeneral(): Int = fetchAndUpsert(ALL_FEEDS)

    /** National news for the user's country (Google News country edition;
     *  curated Indian sources + India video when the country is India). */
    suspend fun refreshNational(countryCode: String, countryName: String): Int {
        val cc = countryCode.trim().uppercase()
        if (cc.isBlank()) return 0
        val name = countryName.ifBlank { "National" }
        val feeds = mutableListOf(
            CategorizedFeed("https://news.google.com/rss?hl=en-$cc&gl=$cc&ceid=$cc:en", "$name News", "national", "top"),
        )
        if (cc == "IN") {
            feeds += CategorizedFeed("https://www.thehindu.com/news/national/feeder/default.rss", "The Hindu", "national", "top")
            feeds += CategorizedFeed("https://timesofindia.indiatimes.com/rssfeeds/-2128936835.cms", "TOI", "national", "top")
            feeds += CategorizedFeed("https://www.indiatoday.in/rss/home", "India Today", "national", "top")
            // National (India) video — English channels only. Hindi/regional
            // language video belongs under the Regional section.
            listOf(
                "NDTV" to "UCZFMm1mMw0F81Z37aaEzTUA",
                "India Today" to "UCYPvAwZP8pZhSMW8qs7cVCw",
                "WION" to "UCWEIPvoxRwn6llPOIn555rQ",
            ).forEach { (src, id) ->
                feeds += CategorizedFeed("https://www.youtube.com/feeds/videos.xml?channel_id=$id", src, "national", "video")
            }
        }
        // Curated national sources for other mapped countries (US, …). Countries
        // not in the registry still get the Google News country edition above, so
        // National text works ANYWHERE; this just adds publisher + video depth.
        COUNTRY_SOURCES[cc]?.let { src ->
            src.publishers.forEach { (n, url) -> feeds += CategorizedFeed(url, n, "national", "top") }
            src.videoChannels.forEach { (n, id) ->
                feeds += CategorizedFeed("https://www.youtube.com/feeds/videos.xml?channel_id=$id", n, "national", "video")
            }
        }
        return fetchAndUpsert(feeds)
    }

    /** World news in the user's regional [langCode] (Google News World topic).
     *  Tagged section "world", lang=langCode so the World section can show it
     *  when the World→Regional toggle is on. */
    suspend fun refreshWorldRegional(langCode: String, countryCode: String): Int {
        if (langCode.isBlank()) return 0
        val gl = countryCode.ifBlank { "US" }.uppercase()
        return fetchAndUpsert(
            listOf(
                CategorizedFeed(
                    "https://news.google.com/rss/headlines/section/topic/WORLD?hl=$langCode&gl=$gl&ceid=$gl:$langCode",
                    "World", "world", "world", langCode,
                ),
            )
        )
    }

    /** National news in the user's regional [langCode] (e.g. India news in
     *  Telugu). Tagged section "national", lang=langCode. */
    suspend fun refreshNationalRegional(countryCode: String, langCode: String, langName: String): Int {
        val cc = countryCode.trim().uppercase()
        if (cc.isBlank() || langCode.isBlank()) return 0
        val name = langName.ifBlank { "National" }
        return fetchAndUpsert(
            listOf(
                CategorizedFeed(
                    "https://news.google.com/rss?hl=$langCode&gl=$cc&ceid=$cc:$langCode",
                    "$name News", "national", "top", langCode,
                ),
            )
        )
    }

    /**
     * Local + regional-language feed for the user's place: city news (English)
     * when [includeEnglish] + top news in the resolved [langName] ([langCode])
     * and that language's YouTube video when [includeRegional]. So a user in
     * Visakhapatnam (Andhra Pradesh) gets Telugu news + video.
     */
    suspend fun refreshRegional(
        city: String,
        region: String,
        countryCode: String,
        langCode: String,
        langName: String,
        includeEnglish: Boolean = true,
        includeRegional: Boolean = true,
    ): Int {
        val feeds = mutableListOf<CategorizedFeed>()
        val isIndia = countryCode.trim().uppercase() == "IN"

        // ── Local (city) — always fetched when a city is known ──
        if (city.isNotBlank()) {
            val encoded = URLEncoder.encode(city, "UTF-8")
            feeds += CategorizedFeed("https://news.google.com/rss/search?q=$encoded&hl=en", "$city News", "local", "top", "en")
            // The Hindu keeps named city editions (e.g. /cities/Visakhapatnam) —
            // a deterministic, high-quality local source, no keyword guessing.
            if (isIndia) {
                val citySlug = URLEncoder.encode(city.trim(), "UTF-8")
                feeds += CategorizedFeed("https://www.thehindu.com/news/cities/$citySlug/feeder/default.rss", "The Hindu", "local", "top", "en")
            }
        }

        // ── Regional (state language + English state edition) ──
        if (includeRegional && langCode.isNotBlank()) {
            val gl = countryCode.ifBlank { "IN" }.uppercase()
            feeds += CategorizedFeed(
                "https://news.google.com/rss?hl=$langCode&gl=$gl&ceid=$gl:$langCode",
                "$langName News", "regional", "top", langCode,
            )
            LanguageResolver.videoChannels(langCode).forEach { (src, id) ->
                feeds += CategorizedFeed("https://www.youtube.com/feeds/videos.xml?channel_id=$id", src, "regional", "video", langCode)
            }
        }
        // The Hindu state edition (English) — generic across Indian states via a
        // slug (e.g. "Andhra Pradesh" -> andhra-pradesh). Gated by the English
        // regional toggle. Unknown states just return an empty (ignored) feed.
        if (isIndia && includeEnglish && region.isNotBlank()) {
            val stateSlug = region.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            if (stateSlug.isNotBlank()) {
                feeds += CategorizedFeed(
                    "https://www.thehindu.com/news/national/$stateSlug/feeder/default.rss",
                    "The Hindu", "regional", "top", "en",
                )
            }
        }
        return fetchAndUpsert(feeds)
    }

    /** Fetch all [feeds] concurrently, then dedupe + upsert. */
    private suspend fun fetchAndUpsert(feeds: List<CategorizedFeed>): Int = withContext(Dispatchers.IO) {
        if (feeds.isEmpty()) return@withContext 0
        val items = coroutineScope {
            feeds.map { feed ->
                async {
                    runCatching {
                        RssClient.fetch(feed.url).map { item ->
                            CategorizedItem(
                                title = item.title,
                                link = item.link,
                                description = "",
                                imageUrl = item.imageUrl,
                                source = item.source ?: feed.source,
                                pubDateMillis = item.pubDateMillis,
                                section = feed.section,
                                category = feed.category,
                                lang = feed.lang,
                            )
                        }
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
        val total = if (items.isNotEmpty()) processAndUpsert(items) else 0
        if (total > 0) {
            articleDao.deleteOlderThan(System.currentTimeMillis() - 24L * 60 * 60 * 1000)
        }
        total
    }

    private suspend fun processAndUpsert(items: List<CategorizedItem>, isRegional: Boolean = false): Int {
        val deduped = items.groupBy { normaliseTitle(it.title) }
            .map { (_, group) -> group.first() }
            // Drop graphic / abuse / self-harm stories (the "Good News" section
            // is exempt — it never matches these anyway).
            .filterNot { it.section != "good" && isDisturbing(it.title) }
        val now = System.currentTimeMillis()
        val rows = deduped.map { item ->
            val isBreaking = item.section == "breaking" ||
                item.title.contains("BREAKING", ignoreCase = true)
            // "video" → classify topic by title; "video:<topic>" → forced topic
            // (e.g., anime channels); anything else is text news.
            val video = item.category == "video" || item.category.startsWith("video:")
            val category = when {
                item.category.startsWith("video:") -> item.category.substringAfter("video:")
                video -> classifyTopic(item.title)
                // Generic placeholders (national/regional/local/world) carry no
                // real topic — classify by title so the universal topic filter
                // works across every region, not just World.
                item.category in GENERIC_CATEGORIES -> classifyTopic(item.title)
                else -> item.category
            }
            ArticleEntity(
                id = normaliseTitle(item.title).hashCode().toString(),
                title = item.title,
                description = item.description,
                summary = null,
                url = item.link,
                imageUrl = item.imageUrl,
                source = item.source,
                category = category,
                publishedAtMillis = item.pubDateMillis ?: now,
                fetchedAtMillis = now,
                weight = if (isBreaking) 10 else 5,
                tier = null,
                section = item.section,
                isVideo = video,
                lang = item.lang,
            )
        }
        articleDao.upsertAll(rows)
        return rows.size
    }

    private data class CategorizedItem(
        val title: String,
        val link: String,
        val description: String,
        val imageUrl: String?,
        val source: String,
        val pubDateMillis: Long?,
        val section: String,
        val category: String,
        val lang: String = "en",
    )

    private fun normaliseTitle(title: String): String =
        title.lowercase().filter { it.isLetterOrDigit() }.take(80)

    /** Heuristic blocklist for graphic / abuse / self-harm headlines.
     *  English word-boundaried regex misses regional content, so we also check
     *  native-script terms (substring — Indic scripts have no ASCII word
     *  boundary) and common Latin transliterations ("Hinglish"/"Tenglish"). */
    private fun isDisturbing(title: String): Boolean {
        if (DISTURBING_REGEX.containsMatchIn(title) || VULGAR_REGEX.containsMatchIn(title)) return true
        if (REGIONAL_NATIVE.any { title.contains(it) }) return true
        return REGIONAL_TRANSLIT.containsMatchIn(title)
    }

    /** Coarse topic for a video title so it appears under the matching
     *  category filter. Falls back to "top" (general). */
    private fun classifyTopic(title: String): String {
        val t = title.lowercase()
        fun any(vararg k: String) = k.any { it in t }
        return when {
            any("election", "parliament", "minister", "president", "govern", "policy",
                "vote", "congress", "senate", "modi", "biden", "trump", "geopolit") -> "politics"
            any("cricket", "football", "soccer", "match", "tournament", "olympic", "fifa",
                "ipl", "nba", "tennis", "world cup", " vs ") -> "sports"
            any("iphone", "android", "app ", "software", "google", "apple", "microsoft",
                "gadget", "robot", "chip", " ai ", "artificial intelligence", "tech", "startup") -> "tech"
            any("market", "stock", "economy", "business", "gdp", "inflation", "ipo",
                "rupee", "dollar", "trade", "profit", "earnings") -> "business"
            any("space", "nasa", "isro", "research", "scientist", "discovery", "climate", "study") -> "science"
            any("covid", "virus", "vaccine", "hospital", "disease", "medical", "health", "mental") -> "health"
            any("film", "movie", "actor", "music", "celebrity", "box office", "song",
                "trailer", "bollywood", "hollywood", "ott") -> "entertainment"
            any("anime", "manga") -> "anime"
            else -> "top"
        }
    }

    /** Curated national sources for a country (text RSS + YouTube video channels).
     *  India keeps its own dedicated block in [refreshNational] (unchanged); this
     *  registry adds depth for OTHER countries. Anything not listed still gets the
     *  Google News country edition, so the app works from anywhere. */
    data class CountrySources(
        val publishers: List<Pair<String, String>>,    // display name -> RSS url
        val videoChannels: List<Pair<String, String>>, // display name -> YouTube channelId
    )

    companion object {
        // Generic section placeholders that should be re-classified into a real
        // topic by title (so the universal topic filter works everywhere).
        private val GENERIC_CATEGORIES = setOf("top", "world", "local")

        /** Per-country curated National sources — text RSS verified live, key-free.
         *  India is intentionally absent — it's handled by the existing IN block.
         *  Countries not listed still get the Google News country edition, so the
         *  app works from anywhere; this just adds publisher + video depth. */
        val COUNTRY_SOURCES: Map<String, CountrySources> = mapOf(
            "US" to CountrySources(
                publishers = listOf(
                    "CNN" to "https://rss.cnn.com/rss/cnn_topstories.rss",
                    "The New York Times" to "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml",
                    "NPR" to "https://feeds.npr.org/1001/rss.xml",
                    "Wall Street Journal" to "https://feeds.a.dj.com/rss/RSSWorldNews.xml",
                ),
                videoChannels = listOf(
                    "ABC News" to "UCBi2mrWuNuyYy4gbM6fU18Q",
                    "PBS NewsHour" to "UC6ZFN9Tx6xh-skXCuRHCDpQ",
                ),
            ),
            "GB" to CountrySources(
                listOf(
                    "The Independent" to "https://www.independent.co.uk/rss",
                    "The Guardian" to "https://www.theguardian.com/world/rss",
                ),
                emptyList(),
            ),
            "CA" to CountrySources(
                listOf(
                    "Global News" to "https://globalnews.ca/feed/",
                    "The Globe and Mail" to "https://www.theglobeandmail.com/arc/outboundfeeds/rss/category/world/",
                ),
                emptyList(),
            ),
            "AU" to CountrySources(
                listOf(
                    "ABC News" to "https://www.abc.net.au/news/feed/51120/rss.xml",
                    "SBS News" to "https://www.sbs.com.au/news/feed",
                    "The Sydney Morning Herald" to "https://www.smh.com.au/rss/feed.xml",
                    "The Age" to "https://www.theage.com.au/rss/feed.xml",
                ),
                emptyList(),
            ),
            "FR" to CountrySources(
                listOf(
                    "France 24" to "https://www.france24.com/en/rss",
                    "Le Monde" to "https://www.lemonde.fr/en/rss/une.xml",
                    "Liberation" to "https://www.liberation.fr/arc/outboundfeeds/rss/",
                    "Mediapart" to "https://www.mediapart.fr/articles/feed",
                ),
                emptyList(),
            ),
            "DE" to CountrySources(
                listOf(
                    "Der Spiegel" to "https://www.spiegel.de/international/index.rss",
                    "Tagesschau" to "https://www.tagesschau.de/infoservices/alle-meldungen-100~rss2.xml",
                    "Die Zeit" to "https://newsfeed.zeit.de/index",
                    "Suddeutsche Zeitung" to "https://rss.sueddeutsche.de/rss/Topthemen",
                ),
                emptyList(),
            ),
            "IE" to CountrySources(
                listOf("TheJournal.ie" to "https://www.thejournal.ie/feed/"),
                emptyList(),
            ),
            "NZ" to CountrySources(
                listOf(
                    "RNZ News" to "https://www.rnz.co.nz/rss/news.xml",
                    "Stuff.co.nz" to "https://www.stuff.co.nz/rss",
                ),
                emptyList(),
            ),
            "SG" to CountrySources(
                listOf(
                    "The Straits Times" to "https://www.straitstimes.com/news/world/rss.xml",
                    "The Business Times" to "https://www.businesstimes.com.sg/rss/top-stories",
                    "Mothership" to "https://mothership.sg/feed/",
                ),
                emptyList(),
            ),
            "ZA" to CountrySources(
                listOf(
                    "SABC News" to "https://www.sabcnews.com/sabcnews/feed/",
                    "The Citizen" to "https://www.citizen.co.za/feed/",
                ),
                emptyList(),
            ),
        )

        // Graphic / abuse / self-harm headline filter. Word-boundaried so it
        // won't trip on innocents ("grape", "therapy", …).
        private val DISTURBING_REGEX = Regex(
            "\\b(rape|raped|molest\\w*|sexual assault|sexually assaulted|sexual abuse|" +
                "child abuse|domestic abuse|p[ae]edophile|suicide|self[- ]?harm|" +
                "beheaded|decapitat\\w*|mutilat\\w*|dismember\\w*|massacre|gruesome)\\b",
            RegexOption.IGNORE_CASE,
        )

        // Regional native-script terms (Hindi + Telugu) — rape / nude / obscene /
        // prostitute and the harshest slurs. Substring match (no ASCII \b in
        // Devanagari/Telugu). Kept short + unambiguous to avoid false positives.
        private val REGIONAL_NATIVE = listOf(
            // Hindi
            "बलात्कार", "गैंगरेप", "अश्लील", "नग्न", "वेश्या", "मादरचोद", "भोसड़ी",
            // Telugu
            "అత్యాచారం", "నగ్న", "అశ్లీల", "వేశ్య", "మానభంగం",
        )

        // Latin transliteration of regional profanity ("Hinglish"/"Tenglish").
        // Word-boundaried; deliberately excludes short/colliding tokens (e.g.
        // "rand" = SA currency, "lund" = Lund, Sweden) to protect the world feed.
        private val REGIONAL_TRANSLIT = Regex(
            "\\b(balatkar|gangrape|ashleel|nanga|nangi|randi|veshya|atyachaaram|" +
                "atyachar|maanbhang|madarchod|behenchod|bhenchod|chutiya|" +
                "chutiye|bhosdi|bhosda|bhosdike|gandu|chutia|chinaal|chhinaal)\\b",
            RegexOption.IGNORE_CASE,
        )

        // Vulgarity / sexual content / body-shaming filter.
        private val VULGAR_REGEX = Regex(
            "\\b(sex|sexual\\w*|porn\\w*|nude|nudes|naked|topless|cleavage|lingerie|" +
                "bikini|xxx|onlyfans|escort|brothel|orgy|incest|vulgar|obscene|nsfw|" +
                "body[- ]?sham\\w*|fat[- ]?sham\\w*|weight[- ]?sham\\w*|slut[- ]?sham\\w*|" +
                "flaunt\\w*)\\b",
            RegexOption.IGNORE_CASE,
        )

        val ALL_FEEDS: List<CategorizedFeed> = listOf(
            // ── Breaking News ──────────────────────────────────────
            CategorizedFeed("https://feeds.bbci.co.uk/news/rss.xml", "BBC News", "breaking", "top"),
            CategorizedFeed("https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en", "Google News", "breaking", "top"),
            CategorizedFeed("https://www.theguardian.com/uk/rss", "The Guardian", "breaking", "top"),
            CategorizedFeed("https://feeds.skynews.com/feeds/rss/home.xml", "Sky News", "breaking", "top"),

            // ── World News ─────────────────────────────────────────
            CategorizedFeed("https://feeds.bbci.co.uk/news/world/rss.xml", "BBC World", "world", "world"),
            CategorizedFeed("https://www.theguardian.com/world/rss", "The Guardian", "world", "world"),
            CategorizedFeed("https://rss.nytimes.com/services/xml/rss/nyt/World.xml", "NYT", "world", "world"),
            CategorizedFeed("https://www.aljazeera.com/xml/rss/all.xml", "Al Jazeera", "world", "world"),
            CategorizedFeed("https://feeds.skynews.com/feeds/rss/world.xml", "Sky World", "world", "world"),

            // ── Sports ─────────────────────────────────────────────
            CategorizedFeed("https://feeds.bbci.co.uk/sport/rss.xml", "BBC Sport", "world", "sports"),
            CategorizedFeed("https://www.theguardian.com/uk/sport/rss", "Guardian Sport", "world", "sports"),
            CategorizedFeed("https://www.espn.com/espn/rss/news", "ESPN", "world", "sports"),
            // ── Politics ───────────────────────────────────────────
            CategorizedFeed("https://feeds.bbci.co.uk/news/politics/rss.xml", "BBC Politics", "world", "politics"),
            CategorizedFeed("https://www.theguardian.com/politics/rss", "Guardian Politics", "world", "politics"),

            // ── Technology ─────────────────────────────────────────
            CategorizedFeed("https://feeds.bbci.co.uk/news/technology/rss.xml", "BBC Tech", "world", "tech"),
            CategorizedFeed("https://www.theverge.com/rss/index.xml", "The Verge", "world", "tech"),
            CategorizedFeed("https://arstechnica.com/feed/", "Ars Technica", "world", "tech"),
            CategorizedFeed("https://www.wired.com/feed/rss", "Wired", "world", "tech"),

            // ── Science & Space ────────────────────────────────────
            CategorizedFeed("https://feeds.bbci.co.uk/news/science_and_environment/rss.xml", "BBC Science", "world", "science"),
            CategorizedFeed("https://www.theguardian.com/science/rss", "Guardian Science", "world", "science"),
            CategorizedFeed("https://www.nationalgeographic.com/rss", "NatGeo", "world", "science"),

            // ── Health ─────────────────────────────────────────────
            CategorizedFeed("https://feeds.bbci.co.uk/news/health/rss.xml", "BBC Health", "world", "health"),
            CategorizedFeed("https://www.theguardian.com/lifeandstyle/rss", "Guardian Health", "world", "health"),

            // ── Weather & Environment ──────────────────────────────
            CategorizedFeed("https://www.theguardian.com/environment/rss", "Guardian Env", "world", "climate"),
            CategorizedFeed("https://news.google.com/rss/search?q=climate+change+weather&hl=en-US&gl=US&ceid=US:en", "Google News", "world", "climate"),

            // ── Business ───────────────────────────────────────────
            CategorizedFeed("https://feeds.bbci.co.uk/news/business/rss.xml", "BBC Business", "world", "business"),
            CategorizedFeed("https://www.theguardian.com/uk/business/rss", "Guardian Business", "world", "business"),

            // ── Entertainment ──────────────────────────────────────
            CategorizedFeed("https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml", "BBC Ent", "world", "entertainment"),
            CategorizedFeed("https://www.theguardian.com/uk/culture/rss", "Guardian Culture", "world", "entertainment"),

            // ── Anime (text + video) ───────────────────────────────
            CategorizedFeed("https://www.animenewsnetwork.com/all/rss.xml", "Anime News Network", "world", "anime"),
            CategorizedFeed("https://news.google.com/rss/search?q=anime+OR+manga&hl=en-US&gl=US&ceid=US:en", "Google News", "world", "anime"),
            CategorizedFeed("https://www.youtube.com/feeds/videos.xml?channel_id=UC6pGDc4bFGD1_36IKv3FnYg", "Crunchyroll", "world", "video:anime"),
            CategorizedFeed("https://www.youtube.com/feeds/videos.xml?channel_id=UCZYShIzcvrEBXqqjqYfMAsA", "Anime News Network", "world", "video:anime"),
            CategorizedFeed("https://www.youtube.com/feeds/videos.xml?channel_id=UCGbshtvS9t-8CW11W7TooQg", "Muse Asia", "world", "video:anime"),

            // ── Inspiration (youth / achievement / uplifting) ──────
            CategorizedFeed("https://ideas.ted.com/feed/", "TED Ideas", "good", "inspiration"),
            CategorizedFeed("https://news.google.com/rss/search?q=inspiring+story+OR+young+achiever+OR+student+success+OR+overcoming+odds&hl=en-US&gl=US&ceid=US:en", "Google News", "good", "inspiration"),
            CategorizedFeed("https://news.google.com/rss/search?q=act+of+kindness+OR+community+hero+OR+scholarship+OR+youth+innovation&hl=en-IN&gl=IN&ceid=IN:en", "Google News", "good", "inspiration"),

            // ── Good News (uplifting / positive) ───────────────────
            CategorizedFeed("https://www.goodnewsnetwork.org/feed/", "Good News Network", "good", "good"),
            CategorizedFeed("https://www.positive.news/feed/", "Positive News", "good", "good"),
            CategorizedFeed("https://news.google.com/rss/search?q=uplifting+OR+heartwarming+OR+inspiring+story&hl=en-US&gl=US&ceid=US:en", "Google News", "good", "good"),

            // National news is location-driven now — see refreshNational().

            // ── Video news (YouTube channel Atom feeds; no API key) ─────
            // Each is youtube.com/feeds/videos.xml?channel_id=… — the feed
            // carries the watch link + i.ytimg thumbnail, parsed unchanged by
            // RssClient. category="video"; section groups them World/India/
            // Regional so the menu can show them per region. Channel ids
            // verified to return entries; a stale one just yields an empty
            // (error-isolated) feed.
            // World
            *yt("BBC News", "UC16niRr50-MSBwiO3YDb3RA", "world"),
            *yt("Reuters", "UChqUTb7kYRX8-EiaN3XFrSQ", "world"),
            *yt("Al Jazeera", "UCNye-wNBqNL5ZzHSJj3l8Bg", "world"),
            *yt("DW News", "UCknLrEdhRCp1aegoMqRaCZg", "world"),
            *yt("CNBC", "UCvJJ_dzjViJCoLf5uKUTwoA", "world"),
            *yt("Sky News", "UCoMdktPbSTixAyNGwb-UYkQ", "world"),
            // National + regional video are added dynamically by location —
            // see refreshNational() / refreshRegional().
        )

        /** One video feed for a YouTube channel, tagged with its region section. */
        private fun yt(source: String, channelId: String, section: String): Array<CategorizedFeed> = arrayOf(
            CategorizedFeed(
                "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId",
                source, section, "video",
            )
        )

        private val REGIONAL_FEEDS = listOf(
            "https://news.google.com/rss/search?q={city}+news+today&hl=en-IN&gl=IN&ceid=IN:en",
            "https://news.google.com/rss/search?q={city}+local&hl=en-IN&gl=IN&ceid=IN:en",
        )
    }
}
