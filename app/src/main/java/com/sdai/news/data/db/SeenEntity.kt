package com.sdai.news.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Read-state for an article, keyed by the article's stable id (URL hash).
 *
 * Kept in its OWN table — like bookmarks — so it survives both the 24 h
 * cache sweep and the REPLACE-on-refresh upsert that rewrites the
 * `articles` rows. If `seen` lived on [ArticleEntity] it would be reset
 * to 0 every time the same article was re-fetched.
 */
@Entity(tableName = "seen")
data class SeenEntity(
    @PrimaryKey val id: String,
    val seenAtMillis: Long,
)
