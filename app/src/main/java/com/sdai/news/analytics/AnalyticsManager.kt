package com.sdai.news.analytics

import android.content.Context
import com.sdai.news.data.db.AnalyticsDao
import com.sdai.news.data.db.CategoryViewEntity
import com.sdai.news.data.db.SDAIDatabase
import com.sdai.news.data.db.SessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AnalyticsManager(context: Context) {

    private val dao: AnalyticsDao = SDAIDatabase.get(context).analyticsDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentSessionId: Long? = null
    private var sessionArticleCount = 0

    fun startSession() {
        scope.launch {
            val now = System.currentTimeMillis()
            val dayEpoch = now / TimeUnit.DAYS.toMillis(1)
            currentSessionId = dao.insertSession(
                SessionEntity(dayEpoch = dayEpoch, openedAtMillis = now)
            )
        }
    }

    fun trackArticleRead() {
        sessionArticleCount++
    }

    fun trackCategoryView(category: String) {
        if (category.isBlank()) return
        scope.launch {
            val dayEpoch = System.currentTimeMillis() / TimeUnit.DAYS.toMillis(1)
            val existing = dao.incrementCategoryView(dayEpoch, category)
            if (existing == 0) {
                dao.insertCategoryView(
                    CategoryViewEntity(dayEpoch = dayEpoch, category = category)
                )
            }
        }
    }

    fun endSession() {
        val sessionId = currentSessionId ?: return
        scope.launch {
            val now = System.currentTimeMillis()
            dao.closeSession(sessionId, now, sessionArticleCount)
            currentSessionId = null
            sessionArticleCount = 0
        }
    }

    suspend fun getDAU(): Int {
        val today = System.currentTimeMillis() / TimeUnit.DAYS.toMillis(1)
        return dao.distinctDaysActive(today - 30)
    }

    suspend fun getD1Retention(): Float {
        val today = System.currentTimeMillis() / TimeUnit.DAYS.toMillis(1)
        val yesterday = today - 1
        val activeYesterday = dao.wasActiveOnDay(yesterday)
        if (!activeYesterday) return 0f
        val activeToday = dao.wasActiveOnDay(today)
        return if (activeToday) 1f else 0f
    }

    suspend fun getD7Retention(): Float {
        val today = System.currentTimeMillis() / TimeUnit.DAYS.toMillis(1)
        val weekAgo = today - 7
        val activeWeekAgo = dao.wasActiveOnDay(weekAgo)
        if (!activeWeekAgo) return 0f
        val activeToday = dao.wasActiveOnDay(today)
        return if (activeToday) 1f else 0f
    }

    suspend fun getTopCategories(days: Int = 7): List<Pair<String, Int>> {
        val since = System.currentTimeMillis() / TimeUnit.DAYS.toMillis(1) - days
        return dao.topCategoriesSince(since).map { it.category to it.totalViews }
    }

    suspend fun getArticlesReadToday(): Int {
        val today = System.currentTimeMillis() / TimeUnit.DAYS.toMillis(1)
        return dao.articlesReadOnDay(today) ?: 0
    }

    fun pruneOldData() {
        scope.launch {
            val cutoff = System.currentTimeMillis() / TimeUnit.DAYS.toMillis(1) - 90
            dao.pruneSessionsOlderThan(cutoff)
            dao.pruneCategoryViewsOlderThan(cutoff)
        }
    }

    companion object {
        @Volatile
        private var instance: AnalyticsManager? = null

        fun get(context: Context): AnalyticsManager =
            instance ?: synchronized(this) {
                instance ?: AnalyticsManager(context.applicationContext).also { instance = it }
            }
    }
}
