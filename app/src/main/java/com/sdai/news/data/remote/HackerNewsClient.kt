package com.sdai.news.data.remote

import com.sdai.news.util.HtmlText
import okhttp3.Request
import org.json.JSONObject

/**
 * Hacker News via the unauthenticated Algolia search API.
 *
 *   https://hn.algolia.com/api/v1/search_by_date
 *
 * The naive query (`?query=AI`) is incredibly noisy — any comment
 * mentioning "AI" bubbles a story up. Instead we issue narrow,
 * **structured** queries per pull:
 *
 *  1. Domain-restricted searches against known first-party AI sources
 *     (anthropic.com, openai.com, deepmind.google, …). HN front-page
 *     posts pointing at these domains are virtually always AI news.
 *  2. Term-specific searches with a hard `points >= 50` filter. 50
 *     points is roughly the "below-the-fold-but-rising" threshold —
 *     enough signal to suggest the community thinks it matters, not
 *     so high that we miss new stories.
 *
 * No auth, no key. Algolia rate limit is generous (10k req/h by IP);
 * we issue ~10 requests per refresh which is well within budget.
 *
 * Image URLs are not returned by Algolia — we rely on the og:image
 * background enrichment that already runs on image-less records.
 */
object HackerNewsClient {

    data class HnItem(
        val title: String,
        val link: String,
        val source: String,
        val pubDateMillis: Long?,
        val points: Int,
    )

    /** Known first-party AI publishers — front-page hits are pure gold. */
    private val firstPartyDomains = listOf(
        "anthropic.com",
        "openai.com",
        "deepmind.google",
        "deepmind.com",
        "ai.meta.com",
        "huggingface.co",
        "arxiv.org",
        "blog.research.google",
        "research.google",
        "mistral.ai",
        "stability.ai",
        "midjourney.com",
        "perplexity.ai",
        "cohere.com",
    )

    /** Specific search terms that map cleanly to AI stories. */
    private val termQueries = listOf(
        "\"large language model\"",
        "\"GPT-5\" OR \"GPT-4\"",
        "Claude OR Anthropic",
        "Gemini OR DeepMind",
        "\"open source LLM\"",
        "\"AI agent\" OR \"AI agents\"",
    )

    private val http get() = HttpClient.instance

    /** Top-level entry — fans out into ~20 parallel queries and merges. */
    fun fetchAll(): List<HnItem> {
        val byDomain = firstPartyDomains.flatMap { domain ->
            runCatching { searchByDomain(domain) }.getOrDefault(emptyList())
        }
        val byTerm = termQueries.flatMap { q ->
            runCatching { searchByTerm(q, minPoints = MIN_POINTS) }.getOrDefault(emptyList())
        }
        return (byDomain + byTerm)
            .distinctBy { it.link }
            .filter { it.title.isNotBlank() && it.link.startsWith("http") }
    }

    private fun searchByDomain(domain: String): List<HnItem> {
        val url = "https://hn.algolia.com/api/v1/search_by_date?" +
            "tags=story&restrictSearchableAttributes=url&query=" +
            java.net.URLEncoder.encode(domain, "UTF-8") +
            "&hitsPerPage=$HITS_PER_PAGE"
        return fetchHits(url)
    }

    private fun searchByTerm(term: String, minPoints: Int): List<HnItem> {
        val url = "https://hn.algolia.com/api/v1/search_by_date?" +
            "tags=story&query=" +
            java.net.URLEncoder.encode(term, "UTF-8") +
            "&numericFilters=points%3E%3D$minPoints" +
            "&hitsPerPage=$HITS_PER_PAGE"
        return fetchHits(url)
    }

    private fun fetchHits(url: String): List<HnItem> {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
        return http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use emptyList()
            parse(resp.body?.string().orEmpty())
        }
    }

    private fun parse(body: String): List<HnItem> {
        return runCatching {
            val root = JSONObject(body)
            val hits = root.optJSONArray("hits") ?: return@runCatching emptyList<HnItem>()
            val out = ArrayList<HnItem>(hits.length())
            for (i in 0 until hits.length()) {
                val h = hits.optJSONObject(i) ?: continue
                val title = h.optString("title").takeIf { it.isNotBlank() }
                    ?: h.optString("story_title").takeIf { it.isNotBlank() }
                    ?: continue
                val link = h.optString("url").takeIf { it.isNotBlank() } ?: continue
                val createdMs = h.optLong("created_at_i", 0L) * 1000L
                val points = h.optInt("points", 0)
                val sourceHost = runCatching { java.net.URI(link).host?.removePrefix("www.") }
                    .getOrNull()
                    ?.let { "$it · HN" }
                    ?: "Hacker News"
                out.add(
                    HnItem(
                        title = HtmlText.decode(title),
                        link = link,
                        source = sourceHost,
                        pubDateMillis = createdMs.takeIf { it > 0 },
                        points = points,
                    )
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    private const val HITS_PER_PAGE = 10
    private const val MIN_POINTS = 50
}
