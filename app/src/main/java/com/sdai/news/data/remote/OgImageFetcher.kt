package com.sdai.news.data.remote

import okhttp3.Request
import java.net.URI

/**
 * Fetches the Open Graph image URL for a given article URL by GETting
 * the page and parsing the `<meta property="og:image">` tag from the
 * HTML head. Falls back to `twitter:image` if og:image isn't present.
 *
 * Implementation notes:
 *  - Follows redirects so Google News redirect URLs resolve to the
 *    publisher's actual page.
 *  - Reads only the first ~64 KB and stops at `</head>` to avoid
 *    downloading the full article HTML.
 *  - Returns null for any failure path so the caller can fall through
 *    to the source-letter avatar.
 */
object OgImageFetcher {

    // Aggressive timeouts. Cloudflare / DDoS-protected sites return a
    // CAPTCHA HTML page rather than 4xx — we'd waste seconds parsing it
    // for an og:image that isn't there. Fail fast, drop, move on.
    // Uses [HttpClient.fast] so we share the same cache + connection
    // pool as the rest of the app, just with tighter timeouts.
    private val http get() = HttpClient.fast

    // Two regex variants — content attribute can appear before OR after
    // the property attribute, depending on the publisher's CMS.
    private val PROPERTY_FIRST = Regex(
        """<meta\s+[^>]*?(?:property|name)\s*=\s*["'](?:og:image|twitter:image)["'][^>]*?content\s*=\s*["']([^"']+)["']""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val CONTENT_FIRST = Regex(
        """<meta\s+[^>]*?content\s*=\s*["']([^"']+)["'][^>]*?(?:property|name)\s*=\s*["'](?:og:image|twitter:image)["']""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun fetch(articleUrl: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url(articleUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Mobile",
                )
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val finalUrl = resp.request.url.toString()
                val head = readHead(resp.body?.byteStream()) ?: return null
                extractOgImage(head, finalUrl)
            }
        }.getOrNull()
    }

    /** Reads up to 64 KB and stops once `</head>` is seen. */
    private fun readHead(stream: java.io.InputStream?): String? {
        if (stream == null) return null
        val reader = stream.bufferedReader(Charsets.UTF_8)
        val buf = CharArray(4096)
        val sb = StringBuilder()
        var total = 0
        while (total < 65_536) {
            val read = reader.read(buf)
            if (read < 0) break
            sb.append(buf, 0, read)
            total += read
            if (sb.contains("</head>", ignoreCase = true)) break
        }
        return sb.toString()
    }

    private fun extractOgImage(html: String, baseUrl: String): String? {
        val raw = PROPERTY_FIRST.find(html)?.groupValues?.get(1)
            ?: CONTENT_FIRST.find(html)?.groupValues?.get(1)
            ?: return null
        return resolveUrl(baseUrl, raw.trim())
    }

    /** Resolve a possibly-relative og:image against the final page URL. */
    private fun resolveUrl(baseUrl: String, ref: String): String? {
        if (ref.isBlank()) return null
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        return runCatching { URI(baseUrl).resolve(ref).toString() }.getOrNull()
    }
}
