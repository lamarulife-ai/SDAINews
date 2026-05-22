package com.sdai.news.util

import androidx.core.text.HtmlCompat

/**
 * Centralised HTML-entity decoder. RSS and WordPress feeds routinely
 * ship raw entities (`AI &amp; ML`, `OpenAI&#8217;s`) and the occasional
 * `<i>` / `<em>` tag in titles. Cards must show clean prose.
 *
 * `HtmlCompat.fromHtml` handles all standard entities + named refs
 * across every API level the app supports.
 */
object HtmlText {
    fun decode(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return HtmlCompat
            .fromHtml(raw, HtmlCompat.FROM_HTML_MODE_COMPACT)
            .toString()
            .trim()
    }
}
