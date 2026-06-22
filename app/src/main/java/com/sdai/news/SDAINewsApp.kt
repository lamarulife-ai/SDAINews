package com.sdai.news

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.sdai.news.data.PrefsStore
import com.sdai.news.data.remote.HttpClient
import com.sdai.news.notify.DailyDigestWorker

/**
 * Process-wide singleton + global Coil ImageLoader.
 *
 * The ImageLoader is tuned for a swipe feed of full-screen images:
 *  - `RGB_565` halves the per-bitmap RAM cost vs ARGB_8888 with no
 *    visible quality loss for photo-style hero images.
 *  - Memory cache capped at 25% of free heap so 50+ swipes don't OOM.
 *  - Disk cache at 100 MB persists across launches; the feed appears
 *    instantly on warm starts.
 */
class SDAINewsApp : Application(), ImageLoaderFactory {
    lateinit var prefs: PrefsStore
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsStore(this)
        instance = this
        HttpClient.init(this)
        DailyDigestWorker.schedule(this)
        com.sdai.news.notify.ArxivRefreshWorker.schedule(this)
        com.sdai.news.notify.EveningDigestWorker.schedule(this)
        com.sdai.news.analytics.AnalyticsManager.get(this).startSession()
        com.sdai.news.analytics.AnalyticsManager.get(this).pruneOldData()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .bitmapConfig(Bitmap.Config.RGB_565)
        .crossfade(true)
        .respectCacheHeaders(false)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(100L * 1024 * 1024)
                .build()
        }
        .build()

    companion object {
        @Volatile private var instance: SDAINewsApp? = null
        fun get(): SDAINewsApp =
            checkNotNull(instance) { "SDAINewsApp not initialised yet" }
    }
}
