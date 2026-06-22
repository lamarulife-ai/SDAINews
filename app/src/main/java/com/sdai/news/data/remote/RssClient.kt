package com.sdai.news.data.remote

import android.util.Xml
import com.sdai.news.util.HtmlText
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Generic RSS 2.0 / Atom feed reader. Works for Google News search
 * feeds, TechCrunch, The Verge, Ars Technica, and any other publisher
 * that ships a standard feed.
 *
 * Captures title, link, pubDate, source, and any inline image we can
 * find. Image extraction tries the following sources, in order, and
 * keeps the first match:
 *
 *   1. `<media:content url="...">` / `<media:thumbnail url="...">`
 *   2. `<enclosure url="..." type="image/...">`
 *   3. First `<img src="...">` inside `<content:encoded>` or
 *      `<description>` — many lab blogs (Anthropic, OpenAI, DeepMind,
 *      Google Research, etc.) ship their hero image only inside the
 *      description HTML rather than as a structured media element.
 *
 * Google-News-specific title cleanup is done in [ArticleRepository] —
 * this client stays unopinionated.
 */
object RssClient {

    data class RssItem(
        val title: String,
        val link: String,
        val pubDateMillis: Long?,
        val source: String?,
        val imageUrl: String?,
    )

    private val http get() = HttpClient.instance

    fun fetch(feedUrl: String): List<RssItem> {
        return runCatching {
            val req = Request.Builder()
                .url(feedUrl)
                .header("User-Agent", "Mozilla/5.0 (Android) Awarely/1.0")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                parse(resp.body?.string().orEmpty())
            }
        }.getOrDefault(emptyList())
    }

    private fun parse(xml: String): List<RssItem> {
        if (xml.isBlank()) return emptyList()
        val out = mutableListOf<RssItem>()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
        }

        var inItem = false
        var tag: String? = null
        var title = StringBuilder()
        var link = StringBuilder()
        var pubDate = StringBuilder()
        var source = StringBuilder()
        var descriptionHtml = StringBuilder()
        var image: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    when {
                        tag == "item" || tag == "entry" -> {
                            inItem = true
                            title = StringBuilder()
                            link = StringBuilder()
                            pubDate = StringBuilder()
                            source = StringBuilder()
                            descriptionHtml = StringBuilder()
                            image = null
                        }
                        inItem -> when (tag) {
                            // Atom: <link href="..." rel="alternate"/>
                            // RSS:  <link>text</link>  (handled in TEXT branch)
                            // We only grab Atom's href when no plain-text
                            // link has accumulated yet AND when the rel
                            // attribute is missing or "alternate".
                            "link" -> {
                                val href = parser.getAttributeValue(null, "href")
                                if (!href.isNullOrBlank() && link.isEmpty()) {
                                    val rel = parser.getAttributeValue(null, "rel")
                                    if (rel == null || rel.equals("alternate", true)) {
                                        link.append(href)
                                    }
                                }
                            }
                            // <media:content url="..."> — only if it's actually an
                            // image. YouTube feeds put a Flash <media:content>
                            // BEFORE <media:thumbnail>, so guard on the type.
                            "media:content" -> if (image == null) {
                                val type = parser.getAttributeValue(null, "type")
                                if (type == null || type.startsWith("image", ignoreCase = true)) {
                                    image = parser.getAttributeValue(null, "url")
                                }
                            }
                            "media:thumbnail" -> if (image == null) {
                                image = parser.getAttributeValue(null, "url")
                            }
                            // <enclosure url="..." type="image/jpeg">
                            "enclosure" -> if (image == null) {
                                val type = parser.getAttributeValue(null, "type")
                                if (type == null || type.startsWith("image", ignoreCase = true)) {
                                    image = parser.getAttributeValue(null, "url")
                                }
                            }
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    if (inItem) {
                        val text = parser.text ?: ""
                        when (tag) {
                            "title" -> title.append(text)
                            "link" -> link.append(text)
                            "pubDate", "published", "updated" -> pubDate.append(text)
                            "source", "dc:creator" -> source.append(text)
                            // Capture the raw HTML — used as a fallback image
                            // source. `content:encoded` is the richer field
                            // (full post HTML); `description` is the summary
                            // and often also contains the hero image.
                            "description", "content:encoded", "summary", "content" ->
                                descriptionHtml.append(text)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" || parser.name == "entry") {
                        val t = HtmlText.decode(title.toString())
                        val l = link.toString().trim()
                        // Fallback image: first <img src="..."> inside the
                        // description / content HTML. Many lab blog RSS feeds
                        // (Anthropic, OpenAI, DeepMind, Google Research) only
                        // ship the hero this way.
                        val finalImage = image?.takeIf { it.isNotBlank() }
                            ?: extractFirstImg(descriptionHtml.toString(), baseUrl = l)
                        if (t.isNotEmpty() && l.isNotEmpty()) {
                            out += RssItem(
                                title = t,
                                link = l,
                                pubDateMillis = parseDate(pubDate.toString()),
                                source = HtmlText.decode(source.toString())
                                    .takeIf { it.isNotEmpty() },
                                imageUrl = finalImage,
                            )
                        }
                        inItem = false
                    }
                    tag = null
                }
            }
            event = parser.next()
        }
        return out
    }

    // Most feeds use RFC-822 (Google News, TechCrunch); Atom uses ISO-8601.
    // Try the common formats; return null if none match.
    private val dateFormats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
    )

    private fun parseDate(value: String): Long? {
        val v = value.trim().ifBlank { return null }
        for (pattern in dateFormats) {
            runCatching {
                SimpleDateFormat(pattern, Locale.US).parse(v)?.time
            }.getOrNull()?.let { return it }
        }
        return null
    }

    /**
     * Find the first plausible hero image in the description / content
     * HTML. Skips obvious noise (1×1 tracking pixels, base-64 inline
     * blobs, vector spacers) and resolves protocol-relative or
     * relative URLs against the article's own link.
     */
    private fun extractFirstImg(html: String, baseUrl: String): String? {
        if (html.isBlank()) return null
        IMG_SRC_PATTERN.findAll(html).forEach { m ->
            val raw = m.groupValues[1].trim()
            val resolved = resolveImageUrl(raw, baseUrl) ?: return@forEach
            if (looksLikeRealImage(resolved)) return resolved
        }
        return null
    }

    private fun resolveImageUrl(raw: String, baseUrl: String): String? {
        if (raw.isBlank() || raw.startsWith("data:")) return null
        if (raw.startsWith("//")) return "https:$raw"
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        return runCatching { java.net.URI(baseUrl).resolve(raw).toString() }.getOrNull()
    }

    /** Reject obvious tracking pixels / spacers / vector icons. */
    private fun looksLikeRealImage(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.endsWith(".svg") || lower.endsWith(".gif")) return false
        if (lower.contains("/pixel.") || lower.contains("tracking") ||
            lower.contains("/spacer") || lower.contains("blank.")) return false
        if (lower.contains("1x1")) return false
        return true
    }

    /** Matches `<img ... src="..."/>` — quote style and attribute order
     *  agnostic. Case-insensitive. */
    private val IMG_SRC_PATTERN = Regex(
        """<img\s[^>]*?\bsrc\s*=\s*["']([^"']+)["']""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
}
