package com.sdai.news.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sdai.news.MainActivity
import com.sdai.news.SDAINewsApp
import com.sdai.news.data.db.SDAIDatabase
import kotlinx.coroutines.flow.first

/**
 * Home-screen widget — today's top AI headline.
 *
 * Reads the freshest article from Room on every `provideGlance` call
 * (the system pumps the widget on its `updatePeriodMillis` schedule
 * and on Glance.update() invocations). Tapping anywhere opens the
 * app at the feed.
 */
class HeadlineWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Gate: until the user has accepted the in-app disclaimer the
        // widget shows no news — only a prompt to open the app.
        val accepted = SDAINewsApp.get().prefs.disclaimerAccepted.first()
        val top = if (accepted) {
            SDAIDatabase.get(context).articleDao().recent(limit = 1).firstOrNull()
        } else null

        val headline = when {
            !accepted -> "Tap to accept the disclaimer and start reading"
            top != null -> top.title
            else -> "Open SD AI News for the latest"
        }
        val source = when {
            !accepted -> "SD AI News"
            top != null -> top.source
            else -> "SD AI News"
        }

        // Explicit Intent — older Glance variants don't have the
        // inline reified `actionStartActivity<T>()` overload.
        val launchAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A0F))
                    .padding(14.dp)
                    .clickable(actionStartActivity(launchAppIntent)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Column {
                    Text(
                        text = "TODAY IN AI · ${source.uppercase()}",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF6366F1)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        text = headline,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFF5F5FA)),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

class HeadlineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = HeadlineWidget()
}
