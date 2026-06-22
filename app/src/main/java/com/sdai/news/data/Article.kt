package com.sdai.news.data

/**
 * The UI-facing article model. Normalised from whichever news API
 * returned it so the feed code doesn't have to branch on provider.
 *
 * @property id stable identifier — usually the article URL hash. Used
 *           as the Room primary key and the LazyColumn / Pager key.
 * @property summary an optional 2-sentence summary. Currently always
 *           null — the AI summarisation path was removed when we
 *           switched to a pure RSS/aggregator pipeline. Field kept so
 *           the UI can show summaries again if we re-wire one later.
 */
data class Article(
    val id: String,
    val title: String,
    val description: String,
    val summary: String? = null,
    val url: String,
    val imageUrl: String?,
    val source: String,
    val category: String?,
    val publishedAtMillis: Long,
    val weight: Int = 0,
    /** "breaking" | "industry" | "community" | "research" | null */
    val tier: String? = null,
    /** Whether the reader has already dwelled on this card. Set by the
     *  re-rank step (from the `seen` table), not stored on the entity. */
    val seen: Boolean = false,
    /** YouTube video item. [category] carries its topic (tech/politics/…). */
    val isVideo: Boolean = false,
    /** Content language — "en" or a regional ISO code (e.g. "te"). Used to
     *  honour the per-section English / Regional / Both toggles. */
    val lang: String = "en",
)
