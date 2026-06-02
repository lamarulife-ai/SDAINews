package com.sdai.news.data.remote

import com.sdai.news.util.HtmlText
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * HuggingFace daily-papers feed.
 *
 *   https://huggingface.co/api/daily_papers
 *
 * Returns the HF community's curated trending arXiv papers for the
 * day with thumbnails, summaries, and an upvote count. No auth, no
 * key. Schema isn't versioned so we tolerate field renames silently —
 * any record we can't parse is skipped, never thrown.
 *
 * Output links point at `huggingface.co/papers/<id>` rather than
 * arXiv directly — the HF page has the AI summary the community is
 * actually discussing, which is the value-add over raw arXiv.
 */
object HuggingFaceClient {

    data class HfPaper(
        val title: String,
        val link: String,
        val imageUrl: String?,
        val summary: String,
        val publishedAtMillis: Long?,
        val upvotes: Int,
    )

    private val http get() = HttpClient.instance

    fun fetchDailyPapers(): List<HfPaper> {
        val req = Request.Builder()
            .url("https://huggingface.co/api/daily_papers")
            .header("Accept", "application/json")
            .build()
        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                parse(resp.body?.string().orEmpty())
            }
        }.getOrDefault(emptyList())
    }

    private fun parse(body: String): List<HfPaper> {
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        val out = ArrayList<HfPaper>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val paper = obj.optJSONObject("paper") ?: obj
            val id = paper.optString("id").takeIf { it.isNotBlank() }
                ?: obj.optString("id").takeIf { it.isNotBlank() }
                ?: continue
            val title = paper.optString("title").takeIf { it.isNotBlank() }
                ?: continue
            val summary = paper.optString("summary").let { HtmlText.decode(it) }
            val upvotes = paper.optInt("upvotes", obj.optInt("upvotes", 0))
            val publishedAtMillis = parseTimestamp(
                obj.optString("publishedAt").takeIf { it.isNotBlank() }
                    ?: paper.optString("publishedAt")
            )
            val thumbnail = obj.optString("thumbnail").takeIf { it.isNotBlank() }
                ?: paper.optString("thumbnail").takeIf { it.isNotBlank() }
                ?: "https://huggingface.co/papers/$id/thumbnail.png"
            out.add(
                HfPaper(
                    title = HtmlText.decode(title).trim(),
                    link = "https://huggingface.co/papers/$id",
                    imageUrl = thumbnail,
                    summary = summary.trim(),
                    publishedAtMillis = publishedAtMillis,
                    upvotes = upvotes,
                )
            )
        }
        return out
    }

    private fun parseTimestamp(raw: String): Long? {
        if (raw.isBlank()) return null
        // Format is ISO-8601: "2026-05-31T08:13:09.000Z"
        return runCatching {
            java.time.Instant.parse(raw).toEpochMilli()
        }.getOrNull()
    }
}
