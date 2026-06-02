package com.sdai.news.data.remote

import android.content.Context
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Process-wide [OkHttpClient] singleton.
 *
 * Created once in [com.sdai.news.SDAINewsApp.onCreate] via [init], then
 * shared by every remote client (RSS, Reddit, WordPress, OKSURF, og:image
 * scrape, Hacker News, HuggingFace, arXiv).
 *
 * Why a shared client matters:
 *
 *  1. **HTTP cache.** A 10 MiB on-disk cache handles `ETag` /
 *     `If-None-Match` / `Last-Modified` / `304 Not Modified` natively.
 *     Refreshing a feed that hasn't changed costs ~one byte of headers,
 *     not the full XML payload. Every RSS/JSON layer benefits without
 *     writing any per-source caching logic.
 *  2. **Connection pool.** OkHttp keeps a small pool of keep-alive
 *     connections per host. Hitting `huggingface.co` for daily papers
 *     re-uses the connection opened for the blog feed.
 *  3. **Single bot identity.** One realistic browser-shaped User-Agent
 *     keeps us out of Cloudflare's bot-protection rules. Default
 *     OkHttp UAs (`okhttp/4.x`) get 403'd on Cloudflare/Imperva sites.
 *  4. **Cheap variants.** [fast] derives an aggressive-timeout client
 *     for og:image scraping while sharing the same cache + pool.
 */
object HttpClient {

    @Volatile private var _instance: OkHttpClient? = null

    /** The shared client. Throws if [init] hasn't run yet. */
    val instance: OkHttpClient
        get() = _instance ?: error("HttpClient.init(context) was not called")

    /**
     * Variant used by [OgImageFetcher] — tight timeouts so a slow
     * Cloudflare-protected page doesn't stall the enrichment pool, but
     * shares the underlying connection pool and disk cache via
     * `newBuilder()`.
     */
    val fast: OkHttpClient by lazy {
        instance.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun init(context: Context) {
        if (_instance != null) return
        val dir = File(context.applicationContext.cacheDir, "http_cache")
        val cache = Cache(dir, CACHE_SIZE_BYTES)
        _instance = OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(DefaultUserAgentInterceptor)
            .build()
    }

    /**
     * Adds [BROWSER_UA] when the request didn't already specify a
     * User-Agent. Reddit and any other client that sets its own UA per
     * request is left untouched.
     */
    private object DefaultUserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            if (req.header("User-Agent") != null) return chain.proceed(req)
            val withUa = req.newBuilder()
                .header("User-Agent", BROWSER_UA)
                .build()
            return chain.proceed(withUa)
        }
    }

    private const val CACHE_SIZE_BYTES: Long = 10L * 1024 * 1024

    /**
     * Realistic mobile browser UA + an explicit bot identifier so a
     * sysadmin reading their logs can recognise us. Without a realistic
     * UA, Cloudflare / Imperva / Akamai aggressively 403 us.
     */
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 " +
            "SDAINewsBot/1.0 (+https://sdai.news/bot)"
}
