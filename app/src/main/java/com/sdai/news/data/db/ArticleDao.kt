package com.sdai.news.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room 2.8.x note — every UPDATE / DELETE @Query method has an explicit
 * `Int` return (rows-affected). Without it, KSP fails with
 * "unexpected jvm signature V" because the compiler can't reconcile a
 * Unit return against the SQL operation it's generating.
 */
@Dao
interface ArticleDao {

    // Image-first display: items without a hero image are visible
    // immediately with a source-letter placeholder. When the og:image
    // backfill arrives, setImageUrl() updates the row reactively and
    // the UI swaps in the real image. Feels dramatically faster than
    // waiting for every article to have its image.
    //
    // Ranking: weight (per-source quality 0-10) primary, recency
    // secondary. The 24h cache sweep keeps everything fresh enough
    // that weight-first never pins old content.
    @Query(
        "SELECT * FROM articles " +
            "ORDER BY weight DESC, publishedAtMillis DESC LIMIT :limit"
    )
    fun observeRecent(limit: Int = 200): Flow<List<ArticleEntity>>

    /** Same as [observeRecent], filtered to a single tier chip. */
    @Query(
        "SELECT * FROM articles WHERE tier = :tier " +
            "ORDER BY weight DESC, publishedAtMillis DESC LIMIT :limit"
    )
    fun observeByTier(tier: String, limit: Int = 200): Flow<List<ArticleEntity>>

    @Query(
        "SELECT * FROM articles " +
            "ORDER BY weight DESC, publishedAtMillis DESC LIMIT :limit"
    )
    suspend fun recent(limit: Int = 200): List<ArticleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ArticleEntity>): List<Long>

    @Query("UPDATE articles SET imageUrl = :url WHERE id = :id")
    suspend fun setImageUrl(id: String, url: String): Int

    @Query("DELETE FROM articles WHERE fetchedAtMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks ORDER BY savedAtMillis DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT id FROM bookmarks")
    fun observeIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
