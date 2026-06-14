package com.sdai.news.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sdai.news.data.Article

/**
 * Cached article row. Lives at most a few hours — see
 * [ArticleDao.deleteOlderThan]. Bookmarks have their own table because
 * we don't want the cache eviction sweep to remove a saved article.
 */
@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val summary: String?,
    val url: String,
    val imageUrl: String?,
    val source: String,
    val category: String?,
    val publishedAtMillis: Long,
    val fetchedAtMillis: Long,
    /**
     * Quality weight 0-10 (higher = more prominent in the feed). Set
     * once at upsert time by [com.sdai.news.data.ArticleRepository].
     * Used by [ArticleDao.observeRecent] as the primary sort key
     * before recency.
     */
    val weight: Int = 0,
    /**
     * Coarse category for filter chips ("breaking", "industry",
     * "community", "research"). Derived from the source string at
     * upsert time. Stored so the chip filter can use a simple
     * SQL WHERE rather than computing it per row in Kotlin.
     */
    val tier: String? = null,
    val section: String? = null,
) {
    fun toArticle() = Article(
        id = id,
        title = title,
        description = description,
        summary = summary,
        url = url,
        imageUrl = imageUrl,
        source = source,
        category = category,
        publishedAtMillis = publishedAtMillis,
        weight = weight,
        tier = tier,
    )

    companion object {
        fun fromArticle(a: Article, fetchedAtMillis: Long) = ArticleEntity(
            id = a.id,
            title = a.title,
            description = a.description,
            summary = a.summary,
            url = a.url,
            imageUrl = a.imageUrl,
            source = a.source,
            category = a.category,
            publishedAtMillis = a.publishedAtMillis,
            fetchedAtMillis = fetchedAtMillis,
            weight = a.weight,
            tier = a.tier,
        )
    }
}

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val imageUrl: String?,
    val source: String,
    val savedAtMillis: Long,
)
