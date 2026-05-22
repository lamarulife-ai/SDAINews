package com.sdai.news.util

/** Rough reading-time estimate at ~220 wpm. Always returns a minimum of 1 min. */
object ReadingTime {
    private const val WORDS_PER_MINUTE = 220.0

    fun minutes(text: String): Int {
        val words = text.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
        if (words == 0) return 1
        return maxOf(1, Math.round(words / WORDS_PER_MINUTE).toInt())
    }
}
