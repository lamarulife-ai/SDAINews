package com.sdai.news.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sdai.news.data.ArticleRepository
import java.util.concurrent.TimeUnit

/**
 * Periodic background pull of cs.AI / cs.LG / cs.CL papers from arXiv.
 *
 * Why a dedicated worker rather than folding arXiv into the main
 * pull-to-refresh: arXiv enforces a strict client-spacing policy
 * (3 + seconds between requests; IP bans for sustained abuse). If
 * every user swipe triggered an arXiv hit, our shared IP space would
 * eventually get blocked. WorkManager gives us a free 6-hour throttle.
 *
 * The worker shares the same OkHttp 304-cache as the rest of the app,
 * so an unchanged feed costs us ~one HTTP HEAD's worth of headers.
 *
 * Schedule chosen as 6 h: arXiv typically publishes once a day in a
 * single batch, so 4×/day is more than enough to surface new papers
 * within hours of submission without being rude to the server.
 */
class ArxivRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        ArticleRepository(applicationContext).refreshArxivOnly()
        Result.success()
    } catch (t: Throwable) {
        Result.retry()
    }

    companion object {
        const val WORK_NAME = "sdai_arxiv_refresh_worker"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<ArxivRefreshWorker>(
                repeatInterval = 6, repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = 1, flexTimeIntervalUnit = TimeUnit.HOURS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req,
            )
        }
    }
}
