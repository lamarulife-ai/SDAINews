# SD AI News

**Smart News. Real Insight.** — a free, open, society-benefit Android app that surfaces today's most important AI news in a swipe-feed format.

No accounts. No API keys. No tracking. No ads. No monetisation. Built once to run forever.

---

## Features

- **Reels-style vertical swipe feed.** Quick flicks advance — no full-screen drags needed.
- **Image-rich cards** with source name, headline, AI-relevant description, category-time chips.
- **Refresh button** with auto-scroll to top and rotating query angles so the feed stays fresh.
- **Auto load-more** when you swipe within 5 cards of the end — the feed never dead-ends.
- **Bookmarks** (Room) — tap the bookmark icon on any card.
- **Swipe left** on a card to open the full article in **Chrome Custom Tabs** — your installed browser engine (cookies, ad-blockers, autofill, reader mode) with an SD AI News-themed toolbar.
- **Share card image** — tap share on any card to send a branded 1080×1350 PNG via WhatsApp, Gmail, Telegram, X, etc.
- **Daily 8 AM push** with the top three AI headlines. Tap → opens the feed.
- **Home-screen widget** (4×1) showing today's top headline.
- **Three theme modes:** AMOLED Black (default), Cyber Blue, Minimal White.
- **Wellness overlay** — soft eye-break reminder after 30 min of continuous reading.
- **Offline cache** — articles persist across launches via Room.

## Zero-maintenance news pipeline

Five independent source layers run in parallel on every refresh. Each layer is wrapped in its own error-isolated `async`; one provider going down can't take the feed offline.

| # | Layer | Endpoints | Has images? |
|---|---|---|---|
| 1 | **WordPress JSON** | MarkTechPost · Artificial Intelligence News · Unite.AI (`/wp-json/wp/v2/posts?_embed=true`) | yes (featured media) |
| 2 | **Reddit `.json`** | r/ArtificialInteligence · r/MachineLearning · r/singularity · r/OpenAI · r/LocalLLaMA | yes (`preview.images`) |
| 3 | **OKSURF** | `ok.surf/api/v1/technology` — Google News aggregator JSON | sometimes |
| 4 | **Google News RSS** | rotating 4-of-12 AI query subset on every refresh | filled in by og:image scraper |
| 5 | **Direct publisher RSS** | TechCrunch AI · The Verge AI · VentureBeat AI · Wired AI · Ars Technica | yes (`media:content`) |

After fetch, articles are **deduped by normalised title** — same article from multiple sources collapses to a single row, preferring the variant with both image and description.

Articles without images stay hidden until the **og:image scraper** (parallel, 6-way concurrent, 20 s budget) fills them in — the DAO query filters on `imageUrl IS NOT NULL`, so the feed always looks visual.

## Architecture

```
com.sdai.news
├── data/
│   ├── Article.kt                       — UI model
│   ├── ArticleRepository.kt             — Source fan-out + dedup + image enrichment
│   ├── PrefsStore.kt                    — DataStore (theme, wellness toggle)
│   ├── db/                              — Room: SDAIDatabase + Article/Bookmark entities + DAOs
│   └── remote/                          — 5 source clients + OgImageFetcher
│       ├── WordPressClient.kt
│       ├── RedditClient.kt
│       ├── OksurfClient.kt
│       ├── RssClient.kt                 — generic RSS 2.0 / Atom reader
│       └── OgImageFetcher.kt            — meta-tag scraper for images
├── viewmodel/
│   └── FeedViewModel.kt                 — status / isRefreshing / scrollToTopRequests
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
│   └── DailyDigestWorker.kt             — WorkManager daily 8 AM push
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

V1.0.4 (version code 5) — initial public release.

## Why this exists

Built as a society-benefit project — every dependency is open-source, every endpoint is public, every byte of data stays on the user's device. The goal is an app that quietly does its job for years without anyone paying a bill or refreshing an API key.
