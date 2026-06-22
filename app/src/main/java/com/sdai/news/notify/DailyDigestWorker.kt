package com.sdai.news.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import com.sdai.news.MainActivity
import com.sdai.news.R
import com.sdai.news.SDAINewsApp
import com.sdai.news.data.ArticleRepository
import com.sdai.news.data.db.SDAIDatabase
import com.sdai.news.widget.HeadlineWidget
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Daily-at-8am background fetch + notification.
 *
 * Why CoroutineWorker over a one-shot AlarmManager: WorkManager handles
 * Doze, app standby, reboot persistence, and retries for free. The
 * `flexInterval` lets the system batch the wake-up with other jobs to
 * save battery; we still fire within an hour of 8am.
 */
class DailyDigestWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        return try {
            // Gate: never deliver a news notification before the user
            // has accepted the in-app disclaimer.
            val accepted = SDAINewsApp.get().prefs.disclaimerAccepted.first()
            if (!accepted) return Result.success()

            val repo = ArticleRepository(ctx)
            runCatching { repo.refresh() }  // best-effort — we'll send what's in cache regardless

            // Refresh every installed home-screen widget so the
            // morning headline matches the notification.
            runCatching { HeadlineWidget().updateAll(ctx) }

            val top = SDAIDatabase.get(ctx).articleDao().recent(limit = 3)
            if (top.isEmpty()) return Result.success()

            val headline = top.first().title
            val body = top.drop(1).take(2).joinToString(separator = "  ·  ") { it.title }
                .ifBlank { "Open Awarely for today's stories." }

            postNotification(ctx, headline, body)
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    private fun postNotification(ctx: Context, title: String, body: String) {
        // Android 13+ requires the runtime POST_NOTIFICATIONS grant.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel(ctx)

        val launch = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FROM_DIGEST, true)
        }
        val pi = PendingIntent.getActivity(
            ctx, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Framed as "Morning Digest" rather than promising a fixed
        // 8 AM delivery — OEM battery optimisations (Samsung, Xiaomi,
        // OnePlus) routinely defer WorkManager wake-ups by hours.
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Your Morning AI Digest")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n\n$body"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        NotificationManagerCompat.from(ctx).notify(NOTIF_ID, notif)
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, "Morning AI digest",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Top AI news once a day."
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "sdai_daily_digest"
        const val WORK_NAME = "sdai_daily_digest_worker"
        const val EXTRA_FROM_DIGEST = "from_digest"
        private const val NOTIF_ID = 1001

        /** Schedules (or reschedules) the daily 8am wake-up. Idempotent. */
        fun schedule(ctx: Context) {
            val initialDelay = computeDelayUntilNext8amMillis()
            val req = PeriodicWorkRequestBuilder<DailyDigestWorker>(
                repeatInterval = 1, repeatIntervalTimeUnit = TimeUnit.DAYS,
                flexTimeInterval = 1, flexTimeIntervalUnit = TimeUnit.HOURS,
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
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

        private fun computeDelayUntilNext8amMillis(): Long {
            val now = Calendar.getInstance()
            val next = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
            }
            return next.timeInMillis - now.timeInMillis
        }
    }
}
