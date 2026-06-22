package com.sdai.news.ai

import android.content.Context
import com.sdai.news.data.db.ArticleDao
import com.sdai.news.data.db.SDAIDatabase
import com.sdai.news.data.remote.GeminiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SummaryGenerator(context: Context) {

    private val gemini = GeminiClient.get(context)
    private val dao: ArticleDao = SDAIDatabase.get(context).articleDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun generateSummariesForNewArticles(lang: String = "te") {
        scope.launch {
            try {
                val articles = dao.recent(limit = 50)
                val needSummary = articles.filter {
                    it.summary.isNullOrBlank() && it.title.isNotBlank() && it.description.isNotBlank()
                }.take(10)

                for (entity in needSummary) {
                    val summary = gemini.generateSummary(entity.title, entity.description, lang)
                    if (summary != null) {
                        dao.setSummary(entity.id, summary)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    companion object {
        @Volatile
        private var instance: SummaryGenerator? = null

        fun get(context: Context): SummaryGenerator =
            instance ?: synchronized(this) {
                instance ?: SummaryGenerator(context.applicationContext).also { instance = it }
            }
    }
}
