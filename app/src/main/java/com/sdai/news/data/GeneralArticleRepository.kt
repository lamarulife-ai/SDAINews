package com.sdai.news.data

import android.content.Context
import com.sdai.news.data.db.ArticleEntity
import com.sdai.news.data.db.SDAIDatabase
import com.sdai.news.data.remote.RssClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class GeneralArticleRepository(context: Context) {

    private val db = SDAIDatabase.get(context)
    private val articleDao = db.articleDao()

    fun observeBySection(section: String?): Flow<List<Article>> =
        if (section == null) articleDao.observeAll().map { rows -> rows.map { it.toArticle() } }
        else articleDao.observeBySection(section).map { rows -> rows.map { it.toArticle() } }

    fun observeByCategory(category: String): Flow<List<Article>> =
        articleDao.observeByCategory(category).map { rows -> rows.map { it.toArticle() } }

    data class CategorizedFeed(
        val url: String,
        val source: String,
        val section: String,
        val category: String,
    )

    suspend fun refreshGeneral(): Int = withContext(Dispatchers.IO) {
        var total = 0
        for (feed in ALL_FEEDS) {
            val items = runCatching {
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
                    )
                }
            }.getOrDefault(emptyList())
            if (items.isNotEmpty()) {
                total += processAndUpsert(items)
            }
        }
        if (total > 0) {
            articleDao.deleteOlderThan(System.currentTimeMillis() - 24L * 60 * 60 * 1000)
        }
        total
    }

    suspend fun refreshRegional(city: String): Int = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(city, "UTF-8")
        var total = 0
        for (template in REGIONAL_FEEDS) {
            val url = template.replace("{city}", encoded)
            val items = runCatching {
                RssClient.fetch(url).map { item ->
                    CategorizedItem(
                        title = item.title,
                        link = item.link,
                        description = "",
                        imageUrl = item.imageUrl,
                        source = item.source ?: "Google News",
                        pubDateMillis = item.pubDateMillis,
                        section = "regional",
                        category = "local",
                    )
                }
            }.getOrDefault(emptyList())
            if (items.isNotEmpty()) {
                total += processAndUpsert(items, isRegional = true)
            }
        }
        if (total > 0) {
            articleDao.deleteOlderThan(System.currentTimeMillis() - 24L * 60 * 60 * 1000)
        }
        total
    }

    private suspend fun processAndUpsert(items: List<CategorizedItem>, isRegional: Boolean = false): Int {
        val deduped = items.groupBy { normaliseTitle(it.title) }
            .map { (_, group) -> group.first() }
        val now = System.currentTimeMillis()
        val rows = deduped.map { item ->
            val isBreaking = item.section == "breaking" ||
                item.title.contains("BREAKING", ignoreCase = true)
            ArticleEntity(
                id = normaliseTitle(item.title).hashCode().toString(),
                title = item.title,
                description = item.description,
                summary = null,
                url = item.link,
                imageUrl = item.imageUrl,
                source = item.source,
                category = item.category,
                publishedAtMillis = item.pubDateMillis ?: now,
                fetchedAtMillis = now,
                weight = if (isBreaking) 10 else 5,
                tier = null,
                section = item.section,
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
    )

    private fun normaliseTitle(title: String): String =
        title.lowercase().filter { it.isLetterOrDigit() }.take(80)

    companion object {
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

            // ── India National ─────────────────────────────────────
            CategorizedFeed("https://www.thehindu.com/news/national/feed/", "The Hindu", "national", "top"),
            CategorizedFeed("https://timesofindia.indiatimes.com/rssfeeds/-2128936835.cms", "TOI", "national", "top"),
            CategorizedFeed("https://www.indiatoday.in/rss/home", "India Today", "national", "top"),
            CategorizedFeed("https://www.deccanherald.com/feed", "Deccan Herald", "national", "top"),
            CategorizedFeed("https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en", "Google News IN", "national", "top"),

            // ── India Sports ───────────────────────────────────────
            CategorizedFeed("https://timesofindia.indiatimes.com/rssfeeds/4719148.cms", "TOI Sports", "national", "sports"),
            CategorizedFeed("https://www.thehindu.com/sport/feed/", "Hindu Sport", "national", "sports"),

            // ── India Business ─────────────────────────────────────
            CategorizedFeed("https://timesofindia.indiatimes.com/rssfeeds/1898055.cms", "TOI Business", "national", "business"),

            // ── India Tech ─────────────────────────────────────────
            CategorizedFeed("https://timesofindia.indiatimes.com/rssfeeds/66949542.cms", "TOI Tech", "national", "tech"),
        )

        private val REGIONAL_FEEDS = listOf(
            "https://news.google.com/rss/search?q={city}+news+today&hl=en-IN&gl=IN&ceid=IN:en",
            "https://news.google.com/rss/search?q={city}+local&hl=en-IN&gl=IN&ceid=IN:en",
        )
    }
}
