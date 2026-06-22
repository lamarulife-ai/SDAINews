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
import com.sdai.news.MainActivity
import com.sdai.news.R
import com.sdai.news.SDAINewsApp
import com.sdai.news.data.ArticleRepository
import com.sdai.news.data.db.SDAIDatabase
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

class EveningDigestWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        return try {
            val prefs = SDAINewsApp.get().prefs
            val accepted = prefs.disclaimerAccepted.first()
            if (!accepted) return Result.success()

            val streak = prefs.streakCurrent.first()
            val todayCount = prefs.todayReadCount.first()

            val repo = ArticleRepository(ctx)
            runCatching { repo.refresh() }

            val top = SDAIDatabase.get(ctx).articleDao().recent(limit = 3)
            if (top.isEmpty()) return Result.success()

            val headline = top.first().title
            val body = top.drop(1).take(2).joinToString(separator = "  ·  ") { it.title }
                .ifBlank { "Open Awarely for tonight's stories." }

            postNotification(ctx, headline, body, streak, todayCount)
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    private fun postNotification(ctx: Context, title: String, body: String, streak: Int, todayCount: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel(ctx)

        val launch = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(DailyDigestWorker.EXTRA_FROM_DIGEST, true)
        }
        val pi = PendingIntent.getActivity(
            ctx, 1, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val streakText = when {
            streak > 1 -> "  🔥 $streak-day streak"
            todayCount > 0 -> "  📖 $todayCount stories read today"
            else -> ""
        }

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Evening Briefing$streakText")
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
                CHANNEL_ID, "Evening briefing",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Your evening news digest with streak tracking."
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "sdai_evening_digest"
        const val WORK_NAME = "sdai_evening_digest_worker"
        private const val NOTIF_ID = 1002

        fun schedule(ctx: Context) {
            val initialDelay = computeDelayUntilNext6pmMillis()
            val req = PeriodicWorkRequestBuilder<EveningDigestWorker>(
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

        private fun computeDelayUntilNext6pmMillis(): Long {
            val now = Calendar.getInstance()
            val next = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 18)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
            }
            return next.timeInMillis - now.timeInMillis
        }
    }
}
