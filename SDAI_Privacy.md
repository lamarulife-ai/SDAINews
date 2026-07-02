---
title: Privacy Policy — Awarely
---

# Privacy Policy — Awarely

**Effective date:** 2 July 2026
**Last updated:** 2 July 2026
**Applies to:** Awarely for Android (package `com.sdai.news`).

Published alongside the [Terms of Use](./SDAI_Terms) and [Publisher / Contact](./SDAI_Contact).

---

## 1. Who we are

Awarely is a free, non-commercial Android app with two parts: a **news feed**
(World / national / regional headlines + video news) and **Scan to Know**, a
barcode scanner that rates a product's safety. No ads, no accounts, no monetisation.

| | |
|---|---|
| **Publisher** | Sudhir D — independent individual developer, India |
| **App package** | `com.sdai.news` |
| **Privacy contact** | lamarulife@gmail.com |
| **Project home** | https://github.com/lamarulife-ai |

---

## 2. The short version

- **The news feed is device-only.** No analytics, no accounts, no advertising IDs; we run no servers that receive your data.
- **Your location and reading history stay on your device** (used only on-device to localise and order the feed).
- **Scan to Know does send data out — but only product data, never you.** When you scan a barcode, the **camera image stays on your device** (the barcode is decoded on-device); the decoded barcode and the product's public details are sent to **Open Food Facts** and to **Google Gemini** to produce the rating. No personal identity is attached.
- **You stay anonymous** — we never know who you are.

---

## 3. What Awarely does **not** collect or transmit

- Your name, email, phone number, or any account identifier.
- Your device identifier, advertising ID (AAID), or installation ID.
- **Camera images / video** — the scanner decodes the barcode on-device; the picture itself is never uploaded or stored.
- **Your location** — resolved and used only on your device (see §6).
- **Your reading behaviour** — stays on the device (§7).
- Your contacts, calendar, files, or microphone.
- Crash logs, performance metrics, screen views, or any analytics signal.

There are **no analytics or tracking SDKs** (no Firebase Analytics, Google Analytics, Crashlytics, Adjust, AppsFlyer, Facebook SDK). The Scan feature uses Google's **ML Kit** (on-device barcode decoding) and the **Gemini API** (cloud, for ratings) — these are functional, not analytics (see §5, §10).

---

## 4. What stays on your device

Stored **locally** (Room + DataStore), never transmitted to us:

| Data | Why | How |
|---|---|---|
| Bookmarked / saved articles | Revisit later | Room SQLite |
| **Scan history** (barcode, product name, rating) | Review past scans | Room SQLite |
| Location (city / region / country / language), if shared | Localise national + regional news | DataStore |
| Read-state, interest signals, reading streak | Order your feed | Room / DataStore |
| Preferences (theme, positive-only, language, blocked sources) | Persist choices | DataStore |
| Cache of recent headlines + images | Instant feed, brief offline use | Room + image cache |

Delete anytime via **Settings → Apps → Awarely → Storage → Clear data**, or by uninstalling.

---

## 5. What the app fetches from the network

Standard HTTPS requests to public endpoints, carrying no personal identifier:

**News feed**
- **Google News RSS** — World edition, plus your country and regional-language editions if you share a location.
- **Publisher RSS feeds** (BBC, The Guardian, etc.) and positive-news sources.
- **YouTube channel RSS** — video-news headlines + thumbnails from official broadcaster channels (no account/key).
- **REST Countries** — a country-code → language lookup (no coordinates, no personal data).

**Scan to Know**
- **Open Food Facts** (`world.openfoodfacts.org` and sibling databases) — looks up the scanned **barcode** (or product name) to fetch public product details (name, brand, ingredients).
- **Google Gemini API** (`generativelanguage.googleapis.com`) — the product details are sent to Google's Gemini model to generate the **rating, safety label and breakdown**. This is a Google cloud service governed by **Google's privacy policy and API terms**. Only product information is sent — no identity, location, or reading data.

---

## 6. Location (optional)

Awarely can localise the news to where you are — **entirely optional**. A location is converted to city / region / country **on your device** (Android `Geocoder`); coordinates are not sent to us or stored after resolution. Country → national edition; region → local-language edition. **If you don't share a location, the app shows World news.**

---

## 7. On-device personalisation

The feed is ordered from what you read (seen-state, lightweight interest signals, a reading streak) — computed and stored **only on your device**, never uploaded, no server-side profile.

---

## 8. Content filtering

On-device keyword filters remove graphic / abusive / sexual / vulgar headlines (English + common regional languages) to keep the feed suitable for a general and 16+ audience. Heuristic — reduces but cannot guarantee removal of all objectionable content.

---

## 9. Opening articles and videos

- **Articles** open in your browser / an in-app tab, under the publisher's own privacy policy.
- **Videos** open in the **YouTube app** (or browser); from there you are in YouTube/Google's environment (comments, recommendations, sign-in, tracking) under Google's policy. We share nothing with YouTube beyond the public video link you open.

---

## 10. Scan to Know — product scanning

When you use the Scan tab:

1. The **camera** shows a live viewfinder. The barcode is decoded **on-device** by Google **ML Kit** — the **camera image is never uploaded or saved**.
2. The decoded **barcode** (or a product name) is sent to **Open Food Facts** to fetch public product details.
3. Those product details are sent to **Google Gemini** (cloud AI) to generate the **safety rating, label, and breakdown**.
4. The result (barcode, product name, rating) is saved to **local scan history** on your device.

Only **product data** leaves your device — never your identity, location, or reading history. Ratings are **AI-generated and informational** (see the [Terms](./SDAI_Terms)); they are not professional, medical, or safety advice.

---

## 11. Permissions

| Permission | Purpose | Optional? |
|---|---|---|
| `INTERNET` | Fetch news, product lookups, ratings | Required |
| `ACCESS_NETWORK_STATE` | Detect connectivity before refreshing | Required |
| `CAMERA` | Live viewfinder for barcode scanning — image decoded on-device, never uploaded (§10) | Used only in Scan |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | One-time fix to localise national/regional news — resolved on-device, never transmitted (§6) | **Optional** |
| `POST_NOTIFICATIONS` (Android 13+) | Optional daily digest | **Optional** |

No microphone, contacts, calendar, SMS, call logs, or arbitrary file access.

---

## 12. Age suitability & children

Awarely presents real-world news and is intended for a **general audience aged 16 and above** (rated Teen), with on-device content filtering (§8). It is **not directed at children under 13** and collects no personal information from anyone, so no parental-consent mechanism is required.

---

## 13. Retention, your rights, security

- No server-side data is collected, so there is nothing for us to retain. Local data stays on your device until you clear it; cached news is auto-pruned (~1 day).
- **Access / delete / export:** all local data is on your device — inspect or clear it via Android's app settings; uninstalling removes everything.
- Network requests use **HTTPS**. Local data lives in the app's private sandbox.
- For DPDP (India) / GDPR requests, email **lamarulife@gmail.com** — we reply within 7 days.

---

## 14. Changes & contact

We may update this policy (e.g. a new source). Material changes are surfaced in-app on first launch after the update.

**Email:** lamarulife@gmail.com · **Project home:** https://github.com/lamarulife-ai
