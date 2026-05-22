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

    // The feed only surfaces articles that have a hero image — items
    // without one are still persisted (the og:image scraper may patch
    // them later) but stay hidden until then.
    @Query(
        "SELECT * FROM articles " +
            "WHERE imageUrl IS NOT NULL AND imageUrl != '' " +
            "ORDER BY publishedAtMillis DESC LIMIT :limit"
    )
    fun observeRecent(limit: Int = 200): Flow<List<ArticleEntity>>

    @Query(
        "SELECT * FROM articles " +
            "WHERE imageUrl IS NOT NULL AND imageUrl != '' " +
            "ORDER BY publishedAtMillis DESC LIMIT :limit"
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
