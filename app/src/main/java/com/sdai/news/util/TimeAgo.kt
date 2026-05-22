package com.sdai.news.util

import java.util.concurrent.TimeUnit

/** Lightweight "5m ago" / "2h ago" formatter — no Android Context needed. */
object TimeAgo {
    fun format(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val deltaMs = (nowMillis - epochMillis).coerceAtLeast(0)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(deltaMs)
        val hours = TimeUnit.MILLISECONDS.toHours(deltaMs)
        val days = TimeUnit.MILLISECONDS.toDays(deltaMs)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> "${days / 7}w ago"
        }
    }
}
