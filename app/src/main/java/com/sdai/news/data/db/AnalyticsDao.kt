package com.sdai.news.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CategorySummary(
    val category: String,
    val totalViews: Int,
)

@Dao
interface AnalyticsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    @Query("UPDATE analytics_sessions SET closedAtMillis = :closedAt, articlesRead = :articlesRead WHERE id = :sessionId")
    suspend fun closeSession(sessionId: Long, closedAt: Long, articlesRead: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryView(view: CategoryViewEntity): Long

    @Query("UPDATE analytics_category_views SET viewCount = viewCount + 1 WHERE dayEpoch = :dayEpoch AND category = :category")
    suspend fun incrementCategoryView(dayEpoch: Long, category: String): Int

    @Query("SELECT COUNT(DISTINCT dayEpoch) FROM analytics_sessions WHERE dayEpoch >= :sinceEpoch")
    suspend fun distinctDaysActive(sinceEpoch: Long): Int

    @Query("SELECT COUNT(DISTINCT dayEpoch) FROM analytics_sessions WHERE dayEpoch = :epoch")
    suspend fun wasActiveOnDay(epoch: Long): Boolean

    @Query("SELECT SUM(articlesRead) FROM analytics_sessions WHERE dayEpoch = :epoch")
    suspend fun articlesReadOnDay(epoch: Long): Int?

    @Query("SELECT category, SUM(viewCount) as totalViews FROM analytics_category_views WHERE dayEpoch >= :sinceEpoch GROUP BY category ORDER BY totalViews DESC")
    suspend fun topCategoriesSince(sinceEpoch: Long): List<CategorySummary>

    @Query("DELETE FROM analytics_sessions WHERE dayEpoch < :cutoffEpoch")
    suspend fun pruneSessionsOlderThan(cutoffEpoch: Long): Int

    @Query("DELETE FROM analytics_category_views WHERE dayEpoch < :cutoffEpoch")
    suspend fun pruneCategoryViewsOlderThan(cutoffEpoch: Long): Int

    @Query("SELECT * FROM analytics_sessions ORDER BY dayEpoch DESC LIMIT 30")
    fun observeRecentSessions(): Flow<List<SessionEntity>>
}
