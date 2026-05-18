# Privacy Policy — SD AI News

**Effective date:** 18 May 2026
**Last updated:** 18 May 2026
**Applies to:** SD AI News for Android (package `com.sdai.news`), all versions.

---

## 1. Who we are

SD AI News is a free, open, non-commercial Android application developed and maintained as a society-benefit project. The app has no business model, no advertising, and no monetisation. It is provided "as is" so that anyone can read curated artificial-intelligence news without surrendering personal data.

For any privacy-related question, contact: **lamarulife@gmail.com**

---

## 2. The short version

- **We collect nothing.** No analytics. No crash reporting. No advertising IDs. No user accounts.
- **Nothing leaves your device** other than ordinary HTTP requests to publicly available news endpoints — and those requests carry no personal identifier.
- **You stay anonymous.** We never know who you are, what you read, what you bookmark, or how often you open the app.

The rest of this document explains, in detail, why that is true.

---

## 3. What information SD AI News does **not** collect

We do not collect or transmit any of the following:

- Your name, email, phone number, or any account identifier.
- Your device identifier, advertising ID (AAID), or installation ID.
- Your IP address (beyond what the news publishers naturally see when their servers respond to a request).
- Your precise or coarse location.
- Your contacts, calendar, photos, files, microphone, or camera.
- Crash logs, performance metrics, screen views, click events, or any other analytics signal.
- The list of articles you read, bookmark, share, or open.

There are **no third-party SDKs** of any kind in the app: no Firebase, no Google Analytics, no Crashlytics, no Adjust, no AppsFlyer, no Sentry, no Facebook SDK. The release build of the app contains only the libraries necessary to display news.

---

## 4. What stays on your device

The following data is created and stored **locally** on your device using Android's Room database and DataStore preferences. It is never transmitted to us or any third party.

| Data | Why it's stored | How it's stored |
|------|-----------------|------------------|
| Bookmarked articles | So you can revisit saved articles | Room SQLite database |
| Theme preference (AMOLED / Cyber / Minimal) | So your chosen theme persists across launches | DataStore preferences |
| Eye-break reminder toggle | So your wellness preference persists | DataStore preferences |
| Local cache of recent article headlines + images | So the feed appears instantly on launch and works briefly offline | Room SQLite database + Coil image cache |

This data lives only inside the app's private sandbox directory. You can delete it at any time by clearing the app's storage from system settings (**Settings → Apps → SD AI News → Storage → Clear data**) or by uninstalling the app — in both cases everything is removed immediately.

---

## 5. What the app fetches from the network

SD AI News retrieves news articles by sending standard HTTPS `GET` requests to public endpoints. These requests carry no personal identifier and no user-specific token. The endpoints are:

- **WordPress JSON feeds** — MarkTechPost, Artificial Intelligence News, Unite.AI
- **Reddit public JSON** — r/ArtificialInteligence, r/MachineLearning, r/singularity, r/OpenAI, r/LocalLLaMA
- **OKSURF** — `ok.surf` Google News aggregator
- **Google News RSS** — `news.google.com/rss/search`
- **Direct publisher RSS** — TechCrunch, The Verge, VentureBeat, Wired, Ars Technica

When an article's hero image is not included directly in a feed, the app fetches the article's HTML page (`GET` request) and reads the `<meta property="og:image">` tag to find an image URL. The fetched HTML is parsed in memory and discarded.

These endpoints are owned by their respective publishers. Their privacy policies — not ours — govern how they handle the HTTP request data they receive (such as your IP address). We do not share, supplement, or correlate that data in any way.

---

## 6. Opening full articles

When you tap "Read full article" or swipe a card to the left, SD AI News opens the article URL in **Chrome Custom Tabs**, which uses your installed Android browser (Chrome, Brave, Samsung Internet, Firefox, etc.).

The publisher's website is then loaded by your browser, not by us. That site may:
- Set its own cookies in your browser.
- Run its own analytics, advertising, or tracking scripts.
- Apply its own privacy policy.

These activities are outside SD AI News's control and outside the scope of this privacy policy. We recommend reviewing the privacy practices of the publishers you choose to read.

---

## 7. Notifications

If you grant the `POST_NOTIFICATIONS` permission, SD AI News posts **one** notification per day — the "Morning AI Digest" containing the top three article headlines.

- The notification is generated **entirely on your device** by Android's WorkManager.
- We do **not** use any push-notification service (no Firebase Cloud Messaging, no OneSignal, no proprietary push server).
- No personal information is required to deliver it.
- You can revoke the permission at any time via **Settings → Apps → SD AI News → Notifications**.

If you never grant the permission, the digest is silently skipped — no other functionality is affected.

---

## 8. Home-screen widget

The optional home-screen widget displays the most recent cached headline. It reads the same local Room database described in section 4 and does not initiate any additional network requests of its own.

---

## 9. Permissions and why they exist

| Permission | Purpose |
|-----------|---------|
| `INTERNET` | Fetch news article metadata from public endpoints |
| `ACCESS_NETWORK_STATE` | Detect whether the device is online before attempting a refresh |
| `POST_NOTIFICATIONS` (Android 13+) | Deliver the optional daily Morning AI Digest |

SD AI News requests **no other permissions**. It cannot access your location, microphone, camera, contacts, calendar, SMS, call logs, photos, or arbitrary files.

---

## 10. Children

SD AI News is not directed at children under 13. We do not knowingly collect any information from children under 13 (or, in any jurisdiction with a higher minimum age, from anyone below that age). Because the app collects no personal information from anyone, no special parental-consent mechanism is needed.

---

## 11. Data retention

Because no data is collected, there is no data retention period. The local data described in section 4 stays on your device until you remove it.

---

## 12. Your rights

You have full and immediate control over every piece of data the app stores:

- **Access** — all data is on your device; you can inspect it via the app or Android's app-info screen.
- **Deletion** — *Settings → Apps → SD AI News → Storage → Clear data* removes everything instantly. Uninstalling the app has the same effect.
- **Portability** — none of the local data is in a proprietary format that prevents export.

Because we never receive your data, we cannot send it to you, correct it, or delete it on your behalf — there is nothing on our side to act on.

---

## 13. Security

All network requests are made over HTTPS (`android:usesCleartextTraffic="false"` is enforced at the manifest level — the app refuses to connect to any plain-text HTTP endpoint). Local data is stored inside the app's private sandbox directory, which Android isolates from other apps by default.

---

## 14. Third-party services we do not use

For transparency, here is an explicit list of third-party services SD AI News does **not** integrate with:

- Google Analytics, Firebase Analytics, Google Tag Manager, AdMob, Google Ads
- Meta / Facebook SDK, Facebook Audience Network
- Adjust, AppsFlyer, Branch, Singular, Kochava, Tenjin
- Mixpanel, Amplitude, Heap, Segment, PostHog
- Sentry, Crashlytics, Bugsnag, Instabug
- OneSignal, Airship, Iterable
- Hotjar, FullStory, LogRocket

If a future version adds any third-party service that collects user data, this privacy policy will be updated **before** that version is released, and the change will be highlighted at the top of this page.

---

## 15. Changes to this policy

We may update this policy from time to time — for example, to reflect a new news source or to clarify existing language. When we do, we will:

- Update the "Last updated" date at the top.
- Note the substantive change in section 16 below.

Continued use of the app after a change constitutes acceptance of the revised policy. If a future change would materially affect your privacy, we will surface it inside the app on first launch after the update.

---

## 16. Revision history

| Date | Change |
|------|--------|
| 2026-05-18 | Initial release (v1.0.0). |

---

## 17. Contact

Questions, suggestions, or concerns about this privacy policy or about SD AI News in general:

**Email:** lamarulife@gmail.com
**Project home:** https://github.com/lamarulife-ai

We try to respond to privacy questions within 7 days.
