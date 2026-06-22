package com.sdai.news.data.remote

import android.content.Context
import com.sdai.news.SDAINewsApp
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private data class GeminiRequest(val contents: List<Content>)
    private data class Content(val parts: List<Part>)
    private data class Part(val text: String)
    private data class GeminiResponse(val candidates: List<Candidate>?)
    private data class Candidate(val content: Content?)

    private val requestAdapter = moshi.adapter(GeminiRequest::class.java)
    private val responseAdapter = moshi.adapter(GeminiResponse::class.java)

    suspend fun generateSummary(title: String, description: String, lang: String = "te"): String? {
        val apiKey = getApiKey() ?: return null
        if (title.isBlank()) return null

        val prompt = when (lang) {
            "te" -> "Summarize this AI news in 2-3 sentences in Telugu (తెలుగు). Keep it concise and factual:\n\nTitle: $title\n\nDescription: $description"
            "hi" -> "Summarize this AI news in 2-3 sentences in Hindi. Keep it concise and factual:\n\nTitle: $title\n\nDescription: $description"
            else -> "Summarize this AI news in 2-3 sentences in English. Keep it concise and factual:\n\nTitle: $title\n\nDescription: $description"
        }

        return withContext(Dispatchers.IO) {
            try {
                val requestBody = GeminiRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt))))
                )
                val json = requestAdapter.toJson(requestBody)

                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null

                if (!response.isSuccessful) return@withContext null

                val geminiResponse = responseAdapter.fromJson(body)
                geminiResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun generateBatchSummary(articles: List<Pair<String, String>>, lang: String = "te"): Map<String, String> {
        val results = mutableMapOf<String, String>()
        for ((title, desc) in articles) {
            val summary = generateSummary(title, desc, lang)
            if (summary != null) {
                results[title] = summary
            }
        }
        return results
    }

    private fun getApiKey(): String? {
        return try {
            val props = java.util.Properties()
            val localProps = context.assets?.open("../local.properties")
                ?: context.javaClass.getResourceAsStream("/local.properties")
            localProps?.use { props.load(it) }
            props.getProperty("gemini.apiKey")?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            try {
                val scanner = java.util.Scanner(
                    java.io.File(context.applicationInfo.dataDir, "../local.properties").takeIf { it.exists() }
                        ?: return null,
                    "UTF-8"
                )
                val content = scanner.useDelimiter("\\A").next()
                val match = Regex("gemini\\.apiKey=(.+)").find(content)
                match?.groupValues?.get(1)?.trim()
            } catch (e: Exception) {
                null
            }
        }
    }

    companion object {
        @Volatile
        private var instance: GeminiClient? = null

        fun get(context: Context): GeminiClient =
            instance ?: synchronized(this) {
                instance ?: GeminiClient(context.applicationContext).also { instance = it }
            }
    }
}
