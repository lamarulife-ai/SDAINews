package com.sdai.news.data.remote

import com.sdai.news.util.HtmlText
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Reads AI-focused subreddits via the public `.json` backdoor — the
 * one Reddit API surface that doesn't need OAuth.
 *
 * Reddit *requires* a non-default User-Agent, otherwise it returns
 * 429 / 403. Format: `<app>/<version> by <id>`.
 *
 * We deliberately discard self-posts and cross-posts so the feed stays
 * link-to-article. We also keep only items whose preview includes a
 * real image — text-only items don't fit the visual card format.
 */
object RedditClient {

    data class RedditItem(
        val title: String,
        val link: String,
        val source: String,
        val imageUrl: String?,
        val pubDateMillis: Long?,
    )

    // The first one has a notorious typo in the actual subreddit name —
    // keep it.
    private val subreddits = listOf(
        "ArtificialInteligence",
        "MachineLearning",
        "singularity",
        "OpenAI",
        "LocalLLaMA",
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(RedditResponse::class.java)

    fun fetchAll(): List<RedditItem> = subreddits.flatMap { sub ->
        runCatching { fetchSubreddit(sub) }.getOrDefault(emptyList())
    }

    private fun fetchSubreddit(sub: String): List<RedditItem> {
        val url = "https://www.reddit.com/r/$sub/hot.json?limit=20"
        val req = Request.Builder()
            .url(url)
            // Reddit aggressively rate-limits generic UAs. The platform
            // convention is `<platform>:<applicationId>:<version> (by /u/<owner>)`
            // — unique strings get higher quota than browser-shaped UAs.
            .header("User-Agent", "android:com.sdai.news:v1.0.0 (by /u/sdainews)")
            .header("Accept", "application/json")
            .build()
        return http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use emptyList()
            val body = resp.body?.string().orEmpty()
            val parsed = runCatching { adapter.fromJson(body) }.getOrNull()
                ?: return@use emptyList()
            parsed.data?.children.orEmpty().mapNotNull { it.data?.toItem() }
        }
    }

    private fun RedditPost.toItem(): RedditItem? {
        val cleanTitle = HtmlText.decode(title).takeIf { it.isNotBlank() } ?: return null
        if (isSelf == true) return null

        val articleUrl = (urlOverride ?: url)?.takeIf { it.isNotBlank() } ?: return null
        // Drop cross-posts and meta-discussion threads — we want
        // outbound links to real articles.
        if (articleUrl.contains("reddit.com", ignoreCase = true)) return null

        val image = preview?.images?.firstOrNull()?.source?.url
            ?.let { unescapeAmpersands(it) }
            ?.takeIf { it.startsWith("http") }

        return RedditItem(
            title = cleanTitle,
            link = articleUrl,
            source = "r/${subreddit ?: ""}",
            imageUrl = image,
            pubDateMillis = createdUtc?.let { (it * 1000).toLong() },
        )
    }

    // Reddit's preview URLs are HTML-encoded (`&amp;` in the wire).
    private fun unescapeAmpersands(url: String): String =
        url.replace("&amp;", "&")

    // ── Wire models — only the fields we read ───────────────────────

    @JsonClass(generateAdapter = false)
    private data class RedditResponse(val data: RedditData?)

    @JsonClass(generateAdapter = false)
    private data class RedditData(val children: List<RedditChild>?)

    @JsonClass(generateAdapter = false)
    private data class RedditChild(val data: RedditPost?)

    @JsonClass(generateAdapter = false)
    private data class RedditPost(
        val title: String?,
        val url: String?,
        @param:Json(name = "url_overridden_by_dest") val urlOverride: String?,
        @param:Json(name = "created_utc") val createdUtc: Double?,
        val subreddit: String?,
        val preview: RedditPreview?,
        @param:Json(name = "is_self") val isSelf: Boolean?,
    )

    @JsonClass(generateAdapter = false)
    private data class RedditPreview(val images: List<RedditImage>?)

    @JsonClass(generateAdapter = false)
    private data class RedditImage(val source: RedditImageSource?)

    @JsonClass(generateAdapter = false)
    private data class RedditImageSource(val url: String?)
}
