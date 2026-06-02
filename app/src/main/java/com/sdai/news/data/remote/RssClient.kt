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
 * find (`<media:content>`, `<media:thumbnail>`, `<enclosure>`).
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
                .header("User-Agent", "Mozilla/5.0 (Android) SDAINews/1.0")
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
                            // <media:content url="..."> / <media:thumbnail url="...">
                            "media:content", "media:thumbnail" -> if (image == null) {
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
                XmlPullParser.TEXT -> {
                    if (inItem) {
                        val text = parser.text ?: ""
                        when (tag) {
                            "title" -> title.append(text)
                            "link" -> link.append(text)
                            "pubDate", "published", "updated" -> pubDate.append(text)
                            "source", "dc:creator" -> source.append(text)
                            // Atom: <link href="..."/> already handled via attribute
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" || parser.name == "entry") {
                        val t = HtmlText.decode(title.toString())
                        val l = link.toString().trim()
                        if (t.isNotEmpty() && l.isNotEmpty()) {
                            out += RssItem(
                                title = t,
                                link = l,
                                pubDateMillis = parseDate(pubDate.toString()),
                                source = HtmlText.decode(source.toString())
                                    .takeIf { it.isNotEmpty() },
                                imageUrl = image?.takeIf { it.isNotBlank() },
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
}
