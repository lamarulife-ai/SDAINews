package com.sdai.news.data.remote

import com.sdai.news.util.HtmlText
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * OKSURF — a public Google News aggregator that ships JSON.
 *
 *   https://ok.surf/api/v1/news-feed             (all categories)
 *   https://ok.surf/api/v1/{category}            (single category)
 *
 * No auth, no key, no quota documented. Treated as a best-effort
 * primary source — we keep a couple of standard RSS feeds wired in as
 * fallback so the app survives if OKSURF disappears or rate-limits.
 *
 * Implementation: the OKSURF response shape isn't versioned, so we
 * parse with the platform `org.json` library and accept any of the
 * common field names (title/Title, link/url/URL, image/img, source).
 * Anything we don't recognise falls through silently.
 */
object OksurfClient {

    data class OksurfItem(
        val title: String,
        val link: String,
        val source: String?,
        val imageUrl: String?,
    )

    private val http get() = HttpClient.instance

    /** Fetches the Technology category — the closest fit for AI news. */
    fun fetchTechnology(): List<OksurfItem> = fetch("https://ok.surf/api/v1/technology")

    /**
     * Fetches the combined news feed. The endpoint returns a map of
     * `{categoryName: [items]}`; we flatten and let downstream dedupe
     * by title.
     */
    fun fetchAll(): List<OksurfItem> = fetch("https://ok.surf/api/v1/news-feed")

    private fun fetch(url: String): List<OksurfItem> {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) Awarely/1.0")
                .header("Accept", "application/json")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val raw = resp.body?.string().orEmpty()
                if (raw.isBlank()) return emptyList()
                parse(raw).take(MAX_ITEMS)
            }
        }.getOrDefault(emptyList())
    }

    /** Bounds OKSURF's contribution. The endpoint returns a map of
     *  categories that can balloon to 80+ items; we only want the
     *  top slice anyway since downstream dedup collapses heavily. */
    private const val MAX_ITEMS = 25

    private fun parse(raw: String): List<OksurfItem> {
        val trimmed = raw.trim()
        val out = mutableListOf<OksurfItem>()
        when {
            trimmed.startsWith("[") -> {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) toItem(arr.optJSONObject(i))?.let(out::add)
            }
            trimmed.startsWith("{") -> {
                val obj = JSONObject(trimmed)
                // Either a flat object with an items array, or a map
                // of category -> [items]. Walk every JSONArray we can
                // find and pull items from it.
                obj.keys().forEach { key ->
                    val value = obj.opt(key)
                    if (value is JSONArray) {
                        for (i in 0 until value.length()) toItem(value.optJSONObject(i))?.let(out::add)
                    }
                }
            }
        }
        return out
    }

    /** Reads whichever variant of title/url/img/source is present. */
    private fun toItem(json: JSONObject?): OksurfItem? {
        if (json == null) return null
        val title = HtmlText.decode(json.optStringFlexible("title", "Title"))
            .takeIf { it.isNotBlank() } ?: return null
        val link = json.optStringFlexible("link", "url", "URL", "Link")?.trim()
            ?: return null
        val img = json.optStringFlexible("image", "img", "Image", "thumbnail", "Thumbnail")
            ?.takeIf { it.startsWith("http", ignoreCase = true) }
        val src = HtmlText.decode(json.optStringFlexible("source", "Source", "publisher", "Publisher"))
        return OksurfItem(title = title, link = link, source = src.takeIf { it.isNotBlank() }, imageUrl = img)
    }

    private fun JSONObject.optStringFlexible(vararg keys: String): String? {
        for (k in keys) {
            val v = opt(k)
            if (v is String && v.isNotBlank() && v != "null") return v
        }
        return null
    }
}
