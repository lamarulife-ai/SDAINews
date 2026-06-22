# SD News

**Smart News. Real Insight.** — a free, ad-free, privacy-first Android news app in a reels-style swipe feed. World news, your country, and your region in your local language, plus video, Good News and Inspiration — tuned to bring positivity.

- **Version:** 1.6.0 (versionCode 13)
- **Package:** `com.sdai.news`
- No accounts. No API keys. No trackers. No ads. Offline-first; online content uses only public RSS, Google News, REST Countries and YouTube channel feeds — no keys.

---

## Features

- **Reels-style vertical swipe feed** — pull-to-refresh **and** an explicit refresh button. Auto load-more near the end; "you're all caught up" card.
- **Hamburger side menu** (≡): Home · **News** (collapsible) · **Video News** (collapsible) · Saved · Profile, with SD-yellow active highlight. **Category dropdown** beside the logo (All, Inspiration, Sports, Politics, Tech, Science, Health, Business, Entertainment, Anime). **Live date-time** in the bar.
- **Location-aware, global:** *National* follows your country (ISO code → Google News country edition; curated India sources when applicable). *Regional* follows your state → its **most-spoken language**, auto-detected from the internet (India state map + REST Countries) and saved — e.g., Andhra Pradesh → **Telugu** news + Telugu video. No location shared → World only.
- **Video News** from reputable YouTube channels (World; your country in English; regional language) via key-free channel RSS. Opens in the **YouTube app** (system picker → web fallback). Thumbnails **fit** (no side-cropping).
- **Positivity-first:** **Inspiration** (TED + youth-achievement/kindness searches) is boosted to the **top** of the feed; a **Good News** section; uplifting headlines get a ranking boost everywhere.
- **Content safety filter** — graphic/abuse/self-harm and vulgar/sexual/body-shaming headlines are removed (word-boundaried; Good News exempt).
- **On-device personalization** (no backend): read-state (read items sink), interest **affinity** re-rank, **reading streak** + daily goal.
- **Smart hero** — low-resolution images fall back to a clean editorial text card instead of a blurry photo.
- **Bookmarks** (Room), branded **share-card** image, daily 8 AM digest, home-screen widget, three themes, wellness overlay, offline cache.

## What's new in 1.6.0 — general news + positivity

The app evolved from AI-only into a global SD News platform. Major additions:

- **Sectioned, location-driven feed.** World (global) is always on; National = your country; Regional = your city + your state's language. Drawer shows World / ‹Country› / ‹City› dynamically; nothing location-based appears without a shared location.
- **Auto language detection.** On location resolve, the region's main language is pulled from the internet (REST Countries for the country; an India state→language map for sub-national precision) and persisted (`PrefsStore.regionalLanguage*`). `refreshAll` self-heals it if missing.
- **Video News** (`category = "video"`) from key-free YouTube channel Atom feeds, fetched concurrently with all other feeds (`fetchAndUpsert`), grouped World / India (English) / Regional (language). National video is English-only; regional is bilingual.
- **Inspiration + Good News** sections; `FeedRanker` boosts inspiration (top) > video > good news, plus a positive-keyword boost so uplifting stories rise.
- **Content safety filter** in `processAndUpsert` (graphic/abuse/self-harm + vulgar/sexual/body-shaming), Good News exempt.
- **Engagement:** read-state (`seen` table), affinity re-rank against a stable snapshot (no mid-scroll reordering), streak + daily goal.
- **Nav redesign:** hamburger ModalNavigationDrawer (Home/News/Video/Saved/Profile) replaced the bottom bar; category dropdown + live date-time in the brand bar; new **SD News** logo everywhere.
- Anime category; pull-to-refresh; smart hero (resolution-gated images).

> The AI-news pipeline below is the project's origin and still present as a secondary source layer; the primary feed is now the general/location pipeline in `GeneralArticleRepository`.

## Zero-maintenance news pipeline

Seven independent source layers run in parallel on every refresh, plus a periodic background worker for arXiv. Each layer is wrapped in its own error-isolated `async`; one provider going down can't take the feed offline. All HTTP traffic goes through a shared OkHttp client with a 10 MiB on-disk cache (handles `304 Not Modified` natively) and a browser-shaped User-Agent (so Cloudflare-protected sources stop returning 403s).

| # | Layer | Endpoints | Has images? |
|---|---|---|---|
| 1 | **WordPress JSON** | MarkTechPost · Artificial Intelligence News · Unite.AI (`/wp-json/wp/v2/posts?_embed=true`) | yes (featured media) |
| 2 | **Reddit `.json`** (quality-gated) | r/ArtificialInteligence · r/MachineLearning · r/singularity · r/OpenAI · r/LocalLLaMA · r/StableDiffusion | yes (`preview.images`) |
| 3 | **OKSURF** | `ok.surf/api/v1/technology` — Google News aggregator JSON | sometimes |
| 4 | **Google News RSS** | 12 brand-name queries (OpenAI, Anthropic, DeepMind, Meta AI, Mistral, Claude, Gemini, GPT-4/5, Llama, Sora, Perplexity); rotates 4 per refresh | og:image backfill |
| 5 | **Direct RSS + GitHub Atom** | 5 tech publishers + 10 first-party AI lab blogs (Anthropic / OpenAI / DeepMind / Meta AI / NVIDIA / HuggingFace / Microsoft / AWS / Google Research) + 3 GitHub release feeds (Ollama / vLLM / PyTorch) | yes (`media:content`) |
| 6 | **Hacker News (Algolia)** | 14 domain-restricted + 6 term queries with `points ≥ 50` filter | og:image backfill |
| 7 | **HuggingFace daily papers** | `huggingface.co/api/daily_papers` — trending arXiv papers with summaries | yes (thumbnails) |
| Worker | **arXiv Atom** | cs.AI / cs.LG / cs.CL — refreshed every 6 h via WorkManager (arXiv rate-limits hard) | placeholder |

**Quality pipeline.** After fetch, articles flow through six gates: spam-pattern regex → English-only filter → exact-match dedup → Jaccard token-overlap dedup → source diversity cap (no source > 20 % of feed) → upsert with per-source quality weight (Anthropic / OpenAI / DeepMind = 10; NVIDIA / HuggingFace = 9; TechCrunch = 8; Hacker News = 7; Reddit = 4; Google News = 3). Reddit posts must hit `score ≥ 100 AND comments ≥ 20`, or contain a flagship AI keyword, to make it through.

**Image-first display.** Articles appear in the feed immediately with a source-letter placeholder; the og:image scraper (parallel, 6-way concurrent, 20 s budget) updates rows reactively as images arrive.

For the deeper write-up — full feed lists, healthy-refresh metrics, contributor recipes, pre-release checklist — see [`TECHNICAL_DOCS.md`](TECHNICAL_DOCS.md).

## Architecture

```
com.sdai.news
├── data/
│   ├── Article.kt                       — UI model
│   ├── ArticleRepository.kt             — Source fan-out + dedup + image enrichment
│   ├── PrefsStore.kt                    — DataStore (theme, wellness toggle)
│   ├── db/                              — Room: SDAIDatabase + Article/Bookmark entities + DAOs
│   └── remote/                          — shared HTTP + 7 source clients + OgImageFetcher
│       ├── HttpClient.kt                — shared OkHttp + 10 MiB cache + browser UA
│       ├── WordPressClient.kt
│       ├── RedditClient.kt              — quality-gated (score / comments / flagship keyword)
│       ├── OksurfClient.kt
│       ├── RssClient.kt                 — RSS 2.0 / Atom 1.0 reader
│       ├── HackerNewsClient.kt          — Algolia, domain + term queries, points ≥ 50
│       ├── HuggingFaceClient.kt         — daily_papers trending paper feed
│       └── OgImageFetcher.kt            — meta-tag scraper for images
├── viewmodel/
│   └── FeedViewModel.kt                 — status / isRefreshing / selectedTier / scrollToTopRequests
├── ui/
│   ├── theme/                           — 3-palette theme system
│   ├── components/                      — ArticleCard, WellnessOverlay
│   ├── screens/                         — Splash, Feed, ArticleWeb, Settings, Bookmarks
│   └── nav/NavGraph.kt
├── util/
│   ├── Share.kt                         — system share sheet + card-image PNG
│   ├── ShareCardRenderer.kt             — Canvas-rendered branded PNG
│   ├── TimeAgo.kt
│   └── ReadingTime.kt
├── notify/
│   ├── DailyDigestWorker.kt             — WorkManager daily 8 AM push
│   └── ArxivRefreshWorker.kt            — WorkManager periodic arXiv pull (6 h)
├── widget/
│   └── HeadlineWidget.kt                — Glance home-screen widget
├── SDAINewsApp.kt                       — Application class (schedules daily worker)
└── MainActivity.kt                      — single activity, hosts NavGraph
```

## Getting started

1. Open the project in Android Studio (Hedgehog or newer).
2. Copy `local.properties.example` to `local.properties` and let Android Studio populate `sdk.dir` on first sync. **No API keys are required.**
3. Sync Gradle. The app needs `minSdk 26` (Android 8.0) / `targetSdk 35`.
4. Run on a device or emulator.

## Permissions

- `INTERNET` + `ACCESS_NETWORK_STATE` — fetch news.
- `POST_NOTIFICATIONS` — daily 8 AM digest. Prompted at runtime on Android 13+.

No location, camera, contacts, calendar, microphone, or storage access is requested.

## Privacy

- Zero tracking, zero analytics, zero ad SDKs.
- Bookmarks live in local Room only — they never leave the device.
- Every news source is fetched directly from the publisher's public endpoint.
- The only outbound traffic is `GET` requests to news sources + the article URL you tap to read.

> **Note:** While the app itself does no tracking, reading full articles opens them in your installed browser via Chrome Custom Tabs. Those third-party sites may set their own cookies and run their own analytics — that's on them, outside our control.

## Version

**v1.2.0 (version code 8)** — GPS location replaces pincode setup.

### v1.2.0 — GPS location replaces pincode setup

**Breaking change (setup flow)**

- **Removed pincode-based setup.** The old `PincodeService` relied on India-only
  postal API (`api.postalpincode.in`) and didn't work outside India. Users in
  other countries had no way to complete onboarding.
- **New one-time GPS location flow.** Setup now requests `ACCESS_FINE_LOCATION`
  + `ACCESS_COARSE_LOCATION` at runtime, gets a fresh GPS fix via
  `FusedLocationProviderClient`, reverse-geocodes it to city/region/country
  via Android `Geocoder`, and shows the resolved location for confirmation.
  If GPS is off or permission is denied, the user can enter city/region/country
  manually.
- **Settings → Location.** A new "Location" section shows the current city
  and offers "Re-detect Location" (GPS re-fetch) or manual entry. This replaces
  the previous one-time pincode that was unchangeable after setup.
- **DataStore keys migrated.** Old `pincode_*` keys replaced with `location_*`
  keys: lat/lon/city/region/country/label. The `PincodeService.kt` file has
  been removed.

**Dependencies**
- Added `com.google.android.gms:play-services-location:21.3.0`.

**Permissions**
- Added `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` to manifest.
  Only requested once during setup; the app never polls location in the
  background.

**Version bump** — 1.1.1 → 1.2.0, versionCode 7 → 8.

### v1.1.0 — release notes

**Content sources (expanded from 5 layers to 7 + 1 worker)**
- Added 10 first-party AI lab RSS feeds: Anthropic news + research, OpenAI, DeepMind, Google Research, Meta AI, HuggingFace, NVIDIA Developer, AWS ML, Microsoft AI.
- Added 3 GitHub release Atom feeds: Ollama, vLLM, PyTorch — open-source AI infra updates land in the feed.
- New Hacker News client (Algolia, points-thresholded, 14 domain-restricted + 6 term queries).
- New HuggingFace daily-papers client (trending community-curated arXiv papers with summaries).
- New arXiv worker: pulls cs.AI / cs.LG / cs.CL every 6 hours via WorkManager (arXiv rate-limits hard, so kept off the pull-to-refresh path).
- Added r/StableDiffusion to the Reddit sub list.

**Quality pipeline (six gates now, was three)**
- Smarter English filter: Latin-script ratio + foreign-tell veto (Spanish / French / German / Italian / Portuguese function words) + English stopword sentinel for longer titles.
- Token-overlap Jaccard dedup as a second pass — collapses headline variants the exact-match dedup misses.
- Spam regex: drops listicle bait ("Top 10 AI Tools"), ultimate-guide bait, clickbait shockers, vendor PR lead-ins ("Acme today announced…").
- Source diversity cap: no single source may occupy more than 20 % of the visible feed.
- Per-source quality weighting (0-10): first-party labs rank above tech press, which ranks above Reddit / Google News.
- Reddit quality gate: posts require `score ≥ 100 AND comments ≥ 20`, OR a flagship AI keyword in the title. Cuts ~70 % of Reddit volume.
- Google News query pool pivoted from broad terms to brand names (OpenAI / Anthropic / Claude / Gemini / etc.) — much higher signal.

**UX**
- **Image-first display.** Articles appear instantly with a source-letter placeholder; images stream in as the og:image scraper finds them. Previously the DAO hid image-less rows entirely, making the feed feel slow.
- **Tier filter chips** — top-bar pill row with `All / Breaking / Industry / Community / Research`. Tap to focus on lab announcements, news pubs, social, or research papers.

**Infrastructure**
- Shared OkHttp client with 10 MiB on-disk cache (`304 Not Modified` for free across all RSS layers — second consecutive refresh is now < 1 s).
- Browser-shaped User-Agent (Cloudflare / Imperva / Akamai stop 403-ing the og:image scraper).
- Fixed an Atom `<link href>` parsing bug in `RssClient` — previously dropped links from arXiv, Google Research, and any other Atom feed.

**Schema**
- Articles table v1 → v2: added `weight` and `tier` columns. Explicit migration preserves existing bookmarks.

### v1.0.4 (version code 5) — initial public release

## Why this exists

Built as a society-benefit project — every dependency is open-source, every endpoint is public, every byte of data stays on the user's device. The goal is an app that quietly does its job for years without anyone paying a bill or refreshing an API key.
