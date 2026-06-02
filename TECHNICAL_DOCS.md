# SDAINews — Content Pipeline Technical Notes

Author: assistant
Last updated: 2026-06-02 (post-quality-pass — image-first display, per-source weighting, source diversity cap, Reddit quality gates, expanded spam filter, brand-name Google queries, tier filter chips)

This document describes how the news pipeline works, the trade-offs
behind each piece, and what's still open. Read this before changing
anything in `ArticleRepository` or the `data/remote/` clients.

The non-negotiables:

- **Zero credentials.** No API keys, no signed requests, no OAuth.
- **Zero tracking.** No analytics, no third-party SDKs that phone home.
- **Lean install.** No heavy NLP/LLM deps (see `MEMORY.md` —
  on-device LLM is permanently out of scope).
- **Graceful degradation.** Every source layer is wrapped in
  `runCatching`; one dead provider must never take the feed down.

---

## 1. Architecture overview

```
                              ┌──────────────────────────────────────┐
   pull-to-refresh ────────►  │      ArticleRepository.refresh()     │
                              │                                       │
                              │  Layers 1-7 run in parallel:          │
                              │   1 WordPressClient                   │
                              │   2 RedditClient                      │
                              │   3 OksurfClient                      │
                              │   4 RssClient (Google News × N)       │
                              │   5 RssClient (direct + GitHub atom)  │
                              │   6 HackerNewsClient (Algolia)        │
                              │   7 HuggingFaceClient (daily papers)  │
                              │                                       │
                              │       ▼                               │
                              │   processAndUpsert():                 │
                              │     • English filter                  │
                              │     • exact-match dedup               │
                              │     • Jaccard token-overlap dedup     │
                              │     • upsert to Room                  │
                              │     • async og:image enrichment       │
                              │     • deleteOlderThan(now − 24h)      │
                              └──────────────────────────────────────┘

   every 6h (WorkManager) ──► ArxivRefreshWorker.doWork()
                              └─► refreshArxivOnly()
                                  └─► processAndUpsert()  (no enrichment)

   daily 08:00 (WorkManager) ─► DailyDigestWorker.doWork()
                              └─► refresh() + notification + widget update
```

All HTTP traffic goes through one shared `HttpClient.instance` — see
§2.

---

## 2. Shared HTTP layer (`HttpClient.kt`)

Single `OkHttpClient` initialised once in `SDAINewsApp.onCreate()`,
referenced by every remote client. Wins:

| Concern | How it's handled |
|---|---|
| HTTP cache | 10 MiB `okhttp3.Cache` on disk. Handles `ETag` / `If-None-Match` / `Last-Modified` / `304 Not Modified` natively. An unchanged feed costs ~one byte of headers. |
| Connection pool | OkHttp's default keep-alive pool. Cross-source connection reuse to the same host. |
| User-Agent | `DefaultUserAgentInterceptor` adds a realistic mobile browser UA + `SDAINewsBot/1.0` to every request that doesn't already set one. Required to avoid Cloudflare/Imperva 403s. |
| og:image variant | `HttpClient.fast` derives a tight-timeout client (3s connect / 4s read / 6s call) via `newBuilder()`, sharing the cache + pool. Used by `OgImageFetcher`. |
| Reddit's specific UA | `RedditClient` still sets `android:com.sdai.news:v1.0.0 (by /u/sdainews)` per-request — the interceptor leaves any request that already has a UA untouched. |

If you add a new client, just reference `HttpClient.instance`. Don't
spin up a fresh `OkHttpClient.Builder()` — you'd lose the cache, pool,
and bot identity.

---

## 3. Source layers

| # | Layer | Transport | Image quality | Notes |
|---|-------|-----------|---------------|-------|
| 1 | WordPress JSON | `wp-json/wp/v2/posts` | High (featured media) | MarkTechPost, AI News, Unite.AI |
| 2 | Reddit JSON   | `r/<sub>/hot.json`     | High (post thumbnails) | 6 subs: ArtificialInteligence, MachineLearning, singularity, OpenAI, LocalLLaMA, StableDiffusion |
| 3 | OKSURF JSON   | `api.oksurf.io`        | Mixed | Google News aggregator with images |
| 4 | Google News RSS | `news.google.com/rss/search` | None — og:image backfill | Rotates 4-of-12 queries per refresh |
| 5 | Direct RSS + GitHub Atom | RSS 2.0 / Atom | High (`media:content`) | 5 tech publishers + 10 first-party AI labs + 3 GitHub release feeds (Ollama, vLLM, PyTorch) — see §3.1 |
| 6 | Hacker News Algolia | `hn.algolia.com/api/v1/search_by_date` | None — og:image backfill | Domain-restricted searches against known AI publishers + term queries thresholded at points ≥ 50 |
| 7 | HuggingFace daily papers | `huggingface.co/api/daily_papers` | High (thumbnail) | Community-curated trending arXiv papers, with summary |
| Worker | arXiv Atom | `export.arxiv.org/api/query` | None | 6-hourly background pull — see §4 |

### 3.1 Direct feeds (Layer 5) — full list

Defined in `ArticleRepository.directRssFeeds` (18 entries):

**Tech publishers (5)**

```
techcrunch.com/category/artificial-intelligence/feed/
theverge.com/rss/ai-artificial-intelligence/index.xml
venturebeat.com/category/ai/feed/
wired.com/feed/tag/ai/latest/rss
feeds.arstechnica.com/arstechnica/index
```

**First-party AI labs (10)** — source-of-truth for every model launch:

```
anthropic.com/news/rss.xml
anthropic.com/research/rss.xml
openai.com/blog/rss.xml
deepmind.google/discover/blog/rss.xml
blog.research.google/feeds/posts/default      (Atom)
ai.meta.com/blog/rss/
huggingface.co/blog/feed.xml
developer.nvidia.com/blog/feed
aws.amazon.com/blogs/machine-learning/feed/
blogs.microsoft.com/ai/feed/
```

**GitHub release Atom (3)** — see §3.2.

### 3.2 GitHub release Atom (Layer 5)

```
github.com/ollama/ollama/releases.atom
github.com/vllm-project/vllm/releases.atom
github.com/pytorch/pytorch/releases.atom
```

Major version cuts in these projects are real news for the local-LLM
crowd. Add more as needed — any GitHub repo exposes
`/<owner>/<repo>/releases.atom` with no auth.

### 3.3 Hacker News query strategy (Layer 6)

The naive `?query=AI` is incredibly noisy (any comment mentioning AI
floats up). Instead `HackerNewsClient` fires ~20 narrow queries per
refresh and unions the results, deduped by URL.

**Domain allowlist (14)** — front-page posts pointing here are
virtually always AI news, so no points threshold needed:

```
anthropic.com           openai.com           deepmind.google
deepmind.com            ai.meta.com          huggingface.co
arxiv.org               blog.research.google research.google
mistral.ai              stability.ai         midjourney.com
perplexity.ai           cohere.com
```

**Term queries (6)** — applied with `numericFilters=points>=50`:

```
"large language model"
"GPT-5" OR "GPT-4"
Claude OR Anthropic
Gemini OR DeepMind
"open source LLM"
"AI agent" OR "AI agents"
```

**Why 50 points?** Below-the-fold-but-rising — strong enough signal
that the HN community thinks it matters, low enough that we still
catch new stories before they hit the front page.

To extend, edit `HackerNewsClient.firstPartyDomains` (no threshold)
or `HackerNewsClient.termQueries` (threshold applies).

### 3.4 Google News query pool (Layer 4)

12 queries in `ArticleRepository.googleNewsQueryPool`. Each refresh
picks 4 in round-robin via an `AtomicInteger` cursor — over three
refreshes the full pool gets cycled:

```
"artificial intelligence"
"generative AI"
"OpenAI OR Anthropic OR \"Google DeepMind\""
"ChatGPT OR Claude OR Gemini"
"large language model OR LLM"
"AI startup funding"
"machine learning research"
"AI regulation OR policy"
"AI agent OR autonomous"
"computer vision OR robotics AI"
"AI ethics OR AI safety"
"neural network OR deep learning"
```

URLs are pinned to `hl=en-US&gl=US&ceid=US:en`. Non-English bleed-
through still happens (Google does soft-match adjacent languages)
which is why the §4.1 filter is the final gate.

### 3.5 arXiv (Worker, not Layer)

`ArxivRefreshWorker` runs every 6h via WorkManager and calls
`ArticleRepository.refreshArxivOnly()`. The reason it's NOT in the
pull-to-refresh path: arXiv rate-limits hard. They ask clients to
space requests ≥ 3 seconds apart, and aggressive pulls earn temporary
IP bans. The 6-hour worker schedule keeps us well under that ceiling.

URL: `export.arxiv.org/api/query?search_query=cat:cs.AI+OR+cat:cs.LG+OR+cat:cs.CL&sortBy=submittedDate&sortOrder=descending&max_results=30`.

arXiv uses Atom; `RssClient` parses it correctly (the Atom `link
href="..."` attribute is captured in the `START_TAG` branch).

---

## 4. The processAndUpsert pipeline

Six gates, in order. All in `ArticleRepository.kt`:

```
collected
  → §4.0 spam regex
  → §4.1 English filter
  → §4.2 exact-match dedup
  → §4.3 Jaccard token-overlap dedup
  → §4.4 source diversity cap (≤20% per source)
  → upsert with weight + tier columns
  → §4.5 background og:image enrichment
  → §4.6 24h sweep
```

### 4.0 Spam / content-farm regex

Configured in `ArticleRepository.SPAM_PATTERNS`. Drops listicles
("Top 10 AI Tools"), ultimate-guide bait, clickbait shockers
("you won't believe"), vendor PR lead-ins ("Acme today announced…"),
and explicit `Sponsored / Promoted / Webinar` flags. Title must
match zero patterns to pass.

### 4.1 English filter (`isLikelyEnglish`)

Three sub-gates:

1. **Latin block check** — ≥85% of letters in U+0000..U+024F (Basic
   Latin + Latin-1 Supplement + Latin Extended-A). Rejects
   Devanagari, CJK, Arabic, Cyrillic, Tamil, Thai, Hebrew, Greek,
   Bengali.
2. **Foreign-tell veto** — if any common Spanish/French/German/
   Italian/Portuguese function word (`que`, `und`, `der`, `del`,
   `pour`, `dans`, …) appears as a whole word, reject.
3. **English-stopword sentinel** — for titles with ≥6 words, require
   at least one common English word (`the`, `and`, `is`, `for`,
   `launches`, `says`, …). Short headlines (≤5 words like "OpenAI
   releases o4") bypass — they often legitimately have no stopword.

Zero deps; microseconds per title. Tuned to be **conservative**: we'd
rather drop a borderline English headline than let a Spanish one
through.

### 4.2 Exact-match dedup

Group by `normalise(title)` = lowercased, alphanumerics only, capped
at 80 chars. Within each group, pick the variant by
`hasImage` → `hasDescription` → freshest `publishedAt`.

### 4.3 Token-overlap dedup (Jaccard, second pass)

Catches headline variants the exact match misses:
`"OpenAI launches GPT-5"` vs `"OpenAI launches GPT-5 today"`.

Two records merge when:

- their unique word-token sets (lowercased, stopwords + tokens
  <3 chars removed) overlap by ≥ 0.80 Jaccard similarity, AND
- their `publishedAt` timestamps are within 6 hours.

O(n²) over the already-shrunk set (~200 records on a healthy day = ~40k
cheap set-intersections; runs in ms).

### 4.4 Source diversity cap

Defined in `ArticleRepository.applyDiversityCap()`. After dedup, no
single source may occupy more than `DIVERSITY_CAP` (20 %) of the
batch. Floor of 3 entries per source so a low-volume but
high-quality source (e.g. Anthropic research) isn't capped to one.
Within each source's quota, weight-then-recency wins — same ordering
the DAO uses.

### 4.5 Upsert + og:image enrichment (image-first)

The capped batch goes to Room via `upsertAll`, populating the new
`weight` and `tier` columns. Records missing `imageUrl` get queued
onto a 6-permit Semaphore for background og:image scraping. The DAO
**no longer filters by image presence** — articles appear in the feed
immediately with a source-letter placeholder (see `ArticleCard.kt:96-100`).
When the scraper finds an og:image, `setImageUrl()` updates the row
reactively and the UI swaps in the real image without a refresh.

This is a material UX shift from the prior "wait-for-image" model.
The trade-off: occasionally users see a placeholder for 2-3 seconds
before the image arrives. Worth it — the feed feels instant.

### 4.6 24-hour sweep

`articleDao.deleteOlderThan(now − 24h)` trims anything we haven't
seen in the last refresh window. Keeps the table bounded.

### 4.7 Per-source weighting + tier classification

`weightForSource(source)` returns a 0-10 quality score (Anthropic /
OpenAI / DeepMind / Meta AI = 10; NVIDIA / HuggingFace / Mistral = 9;
TechCrunch / VentureBeat = 8; Hacker News = 7; Reddit = 4; Google
News = 3). `tierForSource(source)` buckets each row into
`breaking` / `industry` / `community` / `research` for the FeedScreen
chip filter. Both are computed once at upsert time and persisted on
`ArticleEntity`. The DAO orders `WEIGHT DESC, publishedAtMillis DESC`
— inside a 24h window, weight-first is appropriate (the sweep keeps
nothing stale enough for "fresh garbage beats old gold" to apply).

---

---

## 5. Common changes — recipes

### 5.1 Add a new RSS / Atom feed

If the source ships standard RSS 2.0 or Atom 1.0, this is a one-line
change. Append the URL to `ArticleRepository.directRssFeeds`:

```kotlin
private val directRssFeeds = listOf(
    // ... existing entries
    "https://your-new-source.example/feed.xml",
)
```

That's it. The shared OkHttp client (cache + UA), the parser, the
English filter, both dedup passes, the og:image backfill, and the
24-hour sweep all kick in for free.

Pre-merge sanity check: `curl -A "Mozilla/5.0" "https://..."` should
return XML with `<item>` (RSS) or `<entry>` (Atom) elements. If it
returns HTML or JSON, you need a dedicated client instead.

### 5.2 Add a new subreddit

Append to `RedditClient.subreddits`:

```kotlin
private val subreddits = listOf(
    // ... existing entries
    "YourNewSub",
)
```

Reddit enforces a non-default `User-Agent`; `RedditClient` already
sets one per-request. Don't change it.

### 5.3 Add a new HN watched domain / term

Edit `HackerNewsClient.firstPartyDomains` (domain-restricted, no
points threshold) or `HackerNewsClient.termQueries` (term search,
applies the `points>=50` filter). The `fetchAll()` orchestrator
picks both lists up automatically.

### 5.4 Add a new structured JSON source

Pattern: clone `HuggingFaceClient.kt`. Three obligations:

1. Use `HttpClient.instance` (never new up `OkHttpClient.Builder`).
2. Wrap network calls in `runCatching` — silent empty list on failure.
3. Define a data class with the same shape as the other clients
   (`title`, `link`, `imageUrl`, `publishedAtMillis`, …) and add a
   `toAggregated()` mapper in `ArticleRepository`.

Then add it as a new layer in `refresh()` inside the `coroutineScope`.

---

## 6. Healthy-refresh metrics

What "working" looks like on a normal day, **post-quality-pass**.
Use these as guideposts when triaging "is the feed broken?" reports.

| Layer | Typical fresh rows per refresh | Notes |
|-------|-------------------------------|-------|
| WordPress | 20–40 | Three publishers × ~10 latest posts |
| Reddit | 15–40 | Quality gate (score ≥100, comments ≥20, or flagship keyword) cuts ~70 % of raw volume |
| OKSURF | 10–25 | Single category, occasionally empty |
| Google News RSS (×4) | 20–45 | Brand-name queries return less but cleaner than the old broad terms |
| Direct RSS + GitHub | 50–100 | 18 feeds; lab blogs publish rarely |
| Hacker News | 20–50 | ~20 narrow queries, points-thresholded |
| HuggingFace | 5–15 | Daily papers list, refreshed once/24h upstream |
| **Pre-dedup total** | **~140–315** | Lower than v1 because the quality gates run earlier |
| After spam regex | ~92 % of input | Mostly drops listicle bait from Reddit + Google News |
| After English filter | ~90 % | |
| After exact-match dedup | ~55 % | Google News duplicates collapse heaviest |
| After token-overlap dedup | ~85 % of exact-deduped | |
| After diversity cap (20 %) | ~70-90 % of above | Caps prolific sources (Reddit, Google News) |
| **Visible in feed** | **~80–180 rows** | Now includes image-less rows with source-letter placeholders |

Any single layer returning **zero** for two consecutive refreshes
warrants investigation — the layer's URL has probably moved.

Refresh wall time on Wi-Fi / mid-range device: **2–5 s cold**,
**<1 s warm** (304s from the HTTP cache). First few cards visible
within ~500 ms of refresh thanks to image-first display.

### Tier breakdown (typical post-pipeline)

| Tier | Approx. share of feed |
|------|------------------------|
| Breaking (first-party labs) | 10-25 % — varies wildly by news day |
| Industry (TechCrunch / Verge / VentureBeat / Wired / Ars / etc.) | 35-50 % |
| Community (Reddit + Hacker News) | 20-30 % |
| Research (HuggingFace + arXiv) | 5-15 % |

---

## 7. Recommended next steps (not yet applied)

These are listed in priority order. None block release.

### 7.1 Per-source weighting ✓ APPLIED

See §4.7. Listed here for historical context.

Add a `weight` field on `Aggregated`. Suggested defaults:

| Tier | Weight |
|------|--------|
| First-party AI lab | 1.5 |
| WordPress | 1.2 |
| Hacker News (≥100 pts) | 1.2 |
| HuggingFace daily papers | 1.1 |
| Direct publisher RSS | 1.0 |
| Reddit | 0.9 |
| Google News RSS | 0.8 |
| arXiv | 0.7 (informational, not "news") |

Score = `weight × time_decay(publishedAt)` where `time_decay(t) =
1 / (1 + hoursOld/12)`. Plug into the DAO ordering.

### 7.2 Source diversity cap ✓ APPLIED

See §4.4. Listed here for historical context.

After dedup, group by source and cap any single source at ~20% of
the visible feed. Stops Google News (rotating queries) and HuggingFace
papers from flooding on slow news days.

### 7.3 Quality / spam regexes ✓ APPLIED

See §4.0. The regex list lives in `ArticleRepository.SPAM_PATTERNS`
— edit it there to tune.

Heuristic title patterns to drop in `processAndUpsert` before dedup:

```kotlin
val spamPatterns = listOf(
    Regex("""\b(sponsored|promoted|webinar|sign up|register now)\b""", RegexOption.IGNORE_CASE),
    Regex("""\b(best \d+|top \d+) (ai|tools)\b""", RegexOption.IGNORE_CASE),
    Regex("""^.+ today announced """, RegexOption.IGNORE_CASE),  // vendor PR
)
```

### 7.4 Topic chips (medium ROI, UX)

Keyword-based classification — no model needed. Persist as a string
column on `ArticleEntity`. UI: chip on the card, optional filter
strip above the feed.

```kotlin
val tagRules = listOf(
    "Funding"  to listOf("raises", "Series A", "Series B", "seed round", "valuation"),
    "Research" to listOf("paper", "arxiv", "benchmark", "outperforms", "fine-tuned"),
    "Policy"   to listOf("regulation", "EU AI", "executive order", "ban", "lawsuit"),
    "Product"  to listOf("launches", "released", "announces", "now available", "rolls out"),
    "Tutorial" to listOf("how to", "guide", "tutorial", "walkthrough"),
)
```

### 7.5 Image fallback chain (low ROI)

Currently: explicit imageUrl → og:image scrape → drop. Could add
`<img>` extraction from `description` HTML and favicon fallback. Or
keep the strict policy (the rhythm of the feed depends on a
consistent visual treatment). Trade-off call.

---

## 8. Deliberately NOT recommended

Documenting these so future-you doesn't relitigate:

| Source | Why skipped |
|--------|-------------|
| Twitter / X | No auth-free access since 2023. Read-only scraping breaks routinely. |
| LinkedIn | No public API; ToS forbids scraping. |
| Discord guilds | No public API for non-bot users; per-server auth required. |
| YouTube channels | Requires API key with quota. Channel RSS at `feeds/videos.xml?channel_id=…` is zero-auth but title-only and low signal. |
| TikTok | No public API. |
| On-device LLM summarization | Permanently rejected — ~1.5 GB install bloat. Cloud Gemini is the only summarization path (per `MEMORY.md`). |
| Translation of non-English sources | Same reason — would need a model or an API key. The English-only filter is the right call. |
| Manual ETag caching in DataStore | Superseded by OkHttp's native HTTP cache (§2). |

---

## 9. File map (post-hardening)

```
app/src/main/java/com/sdai/news/
├── data/
│   ├── ArticleRepository.kt           ← orchestration + processAndUpsert pipeline
│   ├── remote/
│   │   ├── HttpClient.kt              ← shared OkHttp + cache + UA  [new]
│   │   ├── HackerNewsClient.kt        ← Algolia, domain + term + points filter  [new]
│   │   ├── HuggingFaceClient.kt       ← daily_papers JSON  [new]
│   │   ├── RssClient.kt               ← RSS 2.0 + Atom (link-href bug fix)
│   │   ├── RedditClient.kt
│   │   ├── WordPressClient.kt
│   │   ├── OksurfClient.kt
│   │   └── OgImageFetcher.kt
│   └── db/
│       └── (Room schema — unchanged)
└── notify/
    ├── DailyDigestWorker.kt           ← 08:00 daily refresh + notification
    └── ArxivRefreshWorker.kt          ← 6-hourly arXiv pull  [new]
```

---

## 10. Source-pruning recommendation (review before applying)

If your feed still feels noisy after the quality-pass changes (weight
ordering, source diversity cap, Reddit gates), the next lever is
removing low-signal sources entirely. **None of these are applied
automatically** — review and decide.

### Candidates to drop

| Source | Why | Risk if removed |
|---|---|---|
| **OKSURF (Layer 3)** | Redundant with Google News RSS (Layer 4). Same upstream signal, less control over query targeting, no image guarantee. | Low. Layer 4 covers the same Google News surface. |
| **Google News RSS — broad-term queries** | Even after the brand-name pivot, `"AI startup funding"` is borderline. Consider keeping only OpenAI/Anthropic/DeepMind/Meta AI/Mistral/Perplexity. | Low. Brand queries already dominate. |
| **r/singularity** | High volume of speculation + meme posts. Reddit quality gates drop most but not all. | Low. Drop if Reddit is still flooding. |
| **r/StableDiffusion** | Image-gen focus — only intermittently AI-news. | Low-medium. Lose model-release news from that community. |
| **AWS ML blog** | Mostly product tutorials, not news. | Low. Useful for ML engineers, less so for general AI news. |
| **Microsoft AI blog** | Similar — heavy on Copilot product PR. | Low. |

### Candidates to keep (recommended core set)

These contribute the highest signal-to-noise ratio:

- Anthropic news + research, OpenAI, DeepMind, Meta AI (Tier "breaking")
- NVIDIA Developer, HuggingFace blog, HF daily papers (research signal)
- TechCrunch AI, VentureBeat AI, The Verge AI (industry framing)
- Hacker News (curated by points threshold)
- r/LocalLLaMA, r/MachineLearning, r/OpenAI (highest-signal subs)
- arXiv worker (research pipeline)
- GitHub release Atom for Ollama / vLLM (local-LLM news)

### How to apply pruning

1. Delete the URL / sub from `directRssFeeds` / `RedditClient.subreddits`.
2. (Optional) Delete the broad Google News queries from
   `googleNewsQueryPool`.
3. Re-run the healthy-refresh metrics check (§6) — pre-dedup totals
   should drop ~30-40 % which is the point.

---

## 11. Pre-release verification checklist

Run these once before cutting the release build. The pipeline is
defensive (every layer in `runCatching`) so failures here won't
crash the app — but silent zero-result layers mean wasted RTTs.

1. **Logcat watch:** trigger one pull-to-refresh, grep for each
   client name. Every client should log ≥1 successful fetch on a
   healthy network. A client returning 0 hits indicates either:
   - The endpoint moved (404 / 410) → update URL.
   - The response shape changed → update parser.
   - Cloudflare 403 → check that the UA interceptor ran.
2. **Atom verification:** the Google Research feed and arXiv feed
   should both populate articles. If they don't, the link-href
   fix in `RssClient.kt:65-78` isn't running (e.g. namespace
   feature flipped).
3. **Cache verification:** the second consecutive refresh in
   <2 minutes should be visibly faster on cold devices. If not,
   `okhttp3.Cache` may have failed to mount the cache dir.
4. **arXiv worker:** `adb shell dumpsys jobscheduler | grep sdai`
   should show the `sdai_arxiv_refresh_worker` periodic job
   scheduled. Force-run via Android Studio's Background Task
   Inspector and confirm rows arrive in Room.
5. **English filter spot-check:** intentionally tail
   `r/MachineLearning` for non-English titles; verify they're
   dropped. The veto list in `ArticleRepository.FOREIGN_TELLS`
   is conservative — if too many English headlines get dropped,
   tighten `LATIN_RATIO_THRESHOLD` instead.
