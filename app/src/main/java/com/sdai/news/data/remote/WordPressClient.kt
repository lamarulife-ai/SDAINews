package com.sdai.news.data.remote

import com.sdai.news.util.HtmlText
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Pulls articles from WordPress-powered AI publishers via their public
 * `wp-json/wp/v2/posts?_embed=true` endpoint. No auth, no key, no quota
 * documented — and the embed payload includes the featured image URL
 * so we never need a separate og:image scrape.
 *
 * Each publisher is fetched independently; one being offline simply
 * yields an empty list for that source. The aggregator stays alive.
 */
object WordPressClient {

    data class WpItem(
        val title: String,
        val link: String,
        val description: String,
        val imageUrl: String?,
        val source: String,
        val pubDateMillis: Long?,
    )

    // ── Public WordPress AI news sites ───────────────────────────────
    private data class Site(val baseUrl: String, val displayName: String)

    private val sites = listOf(
        Site("https://www.marktechpost.com", "MarkTechPost"),
        Site("https://artificialintelligence-news.com", "AI News"),
        Site("https://www.unite.ai", "Unite.AI"),
    )

    // ── HTTP + Moshi infrastructure ──────────────────────────────────
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val listAdapter = moshi.adapter<List<WpPost>>(
        Types.newParameterizedType(List::class.java, WpPost::class.java)
    )

    // ── Wire model — only the fields we care about ───────────────────
    private data class WpPost(
        val link: String?,
        val date: String?,
        val title: WpRendered?,
        val excerpt: WpRendered?,
        @param:Json(name = "_embedded") val embedded: WpEmbedded?,
    )

    private data class WpRendered(val rendered: String?)

    private data class WpEmbedded(
        @param:Json(name = "wp:featuredmedia") val featuredMedia: List<WpMedia>?,
    )

    private data class WpMedia(
        @param:Json(name = "source_url") val sourceUrl: String?,
    )

    // ── Public API ───────────────────────────────────────────────────
    fun fetchAll(): List<WpItem> = sites.flatMap { site ->
        runCatching { fetchSite(site) }.getOrDefault(emptyList())
    }

    private fun fetchSite(site: Site): List<WpItem> {
        val url = "${site.baseUrl}/wp-json/wp/v2/posts?_embed=true&per_page=15"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) SDAINewsApp/1.0")
            .header("Accept", "application/json")
            .build()
        return http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use emptyList()
            val body = resp.body?.string().orEmpty()
            val posts = runCatching { listAdapter.fromJson(body) }.getOrNull()
                ?: return@use emptyList()
            posts.mapNotNull { it.toItem(site.displayName) }
        }
    }

    private fun WpPost.toItem(sourceName: String): WpItem? {
        val cleanTitle = HtmlText.decode(title?.rendered).takeIf { it.isNotBlank() } ?: return null
        val articleLink = link?.takeIf { it.isNotBlank() } ?: return null
        val description = HtmlText.decode(excerpt?.rendered).take(320)

        val image = embedded?.featuredMedia
            ?.firstOrNull()
            ?.sourceUrl
            ?.takeIf { it.startsWith("http") }

        return WpItem(
            title = cleanTitle,
            link = articleLink,
            description = description,
            imageUrl = image,
            source = sourceName,
            pubDateMillis = parseIsoDate(date),
        )
    }

    // WordPress dates: "2026-05-17T14:30:00" (UTC, no zone).
    private fun parseIsoDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(value)?.time
        }.getOrNull()
    }
}
