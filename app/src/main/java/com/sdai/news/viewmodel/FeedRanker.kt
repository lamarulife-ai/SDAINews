package com.sdai.news.viewmodel

import com.sdai.news.data.Article

/**
 * On-device feed ranking. Pure function, no I/O — fed the current article
 * list plus the reader's seen-set, interest affinity, blocked sources,
 * and preferred topics.
 *
 * Ordering:
 *  1. **Unseen first.** Already-read stories sink to the bottom so the feed
 *     never re-shows what you've dwelled on, but they're still reachable
 *     (the feed never dead-ends).
 *  2. Within each group, by a blended score of source quality (`weight`),
 *     recency, and how much the reader has engaged with that story's
 *     category / source / tier.
 *  3. **Blocked sources** are filtered out entirely.
 *  4. **Preferred topics** get a ranking *boost* — they are NEVER a filter, so
 *     every other topic still loads, just lower in the feed.
 */
object FeedRanker {

    fun rank(
        list: List<Article>,
        seen: Set<String>,
        affinity: Map<String, Float>,
        blockedSources: Set<String> = emptySet(),
        preferredTopics: Set<String> = emptySet(),
    ): List<Article> {
        val now = System.currentTimeMillis()
        return list
            .filter { it.source !in blockedSources }
            .map { it.copy(seen = it.id in seen) }
            .sortedWith(
                compareBy<Article> { it.seen }
                    .thenByDescending { score(it, affinity, now, preferredTopics) }
            )
    }

    private val POSITIVE_REGEX = Regex(
        "\\b(inspir\\w*|uplift\\w*|heartwarming|hero\\w*|rescu\\w*|kindness|" +
            "achiev\\w*|triumph\\w*|overcom\\w*|breakthrough|success\\w*|" +
            "donat\\w*|volunteer\\w*|scholarship|prodigy|youngest|record-break\\w*|" +
            "wholesome|good news|feel-good)\\b",
        RegexOption.IGNORE_CASE,
    )

    private fun score(a: Article, affinity: Map<String, Float>, now: Long, preferredTopics: Set<String>): Double {
        val hours = (now - a.publishedAtMillis) / 3_600_000.0
        // 0..6 — full boost for fresh, fading to 0 over ~48h.
        val recency = ((48.0 - hours).coerceIn(0.0, 48.0)) / 48.0 * 6.0
        val aff = affinityFor(a, affinity)
        // Content priority: inspiration first (positivity for youth), then
        // video, then good-news; everything else neutral.
        val priorityBoost = when {
            a.category == "inspiration" -> 12.0
            a.isVideo -> 8.0
            a.category == "good" -> 7.0
            else -> 0.0
        }
        // Uplifting headlines rise even within ordinary news.
        val positivity = if (POSITIVE_REGEX.containsMatchIn(a.title)) 4.0 else 0.0
        // Preferred topics get a moderate nudge — enough to lead, small enough
        // that fresh / high-weight stories from OTHER topics still interleave
        // (they are loaded, never filtered out).
        val topicBoost = if (a.category != null && a.category!! in preferredTopics) 5.0 else 0.0
        return a.weight.toDouble() + recency + aff + priorityBoost + positivity + topicBoost
    }

    /** Capped so a heavily-engaged topic can lead, but never dominates. */
    private fun affinityFor(a: Article, affinity: Map<String, Float>): Double {
        fun get(key: String?) = key?.let { affinity[it] }?.toDouble() ?: 0.0
        val raw = get("cat:${a.category}") + get("src:${a.source}") + get("tier:${a.tier}")
        return (raw * 0.3).coerceAtMost(12.0)
    }
}
