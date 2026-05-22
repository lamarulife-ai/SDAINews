package com.sdai.news.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sdai.news.R
import com.sdai.news.ui.theme.Sdai
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Soft overlay that surfaces after a configurable continuous-reading
 * window (default 30 min). "Continue 10 min" snoozes by 10 min;
 * "Take Break" hides until the user re-opens the app.
 *
 * Implemented as a fade-in card pinned to the bottom of the screen so
 * the underlying feed stays interactive — this is a reminder, not a
 * blocker.
 */
@Composable
fun WellnessOverlay(
    enabled: Boolean,
    triggerAfterMillis: Long = 30L * 60 * 1000,
    snoozeMillis: Long = 10L * 60 * 1000,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return

    var showing by remember { mutableStateOf(false) }
    var dismissedUntilMillis by remember { mutableStateOf(0L) }

    LaunchedEffect(enabled) {
        val sessionStart = System.currentTimeMillis()
        while (isActive) {
            delay(15_000)  // coarse tick — we don't need second precision
            val now = System.currentTimeMillis()
            if (now < dismissedUntilMillis) continue
            if (!showing && now - sessionStart >= triggerAfterMillis) {
                showing = true
            }
        }
    }

    AnimatedVisibility(
        visible = showing,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Sdai.surface)
                    .border(1.dp, Sdai.border, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.wellness_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Sdai.ink,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.wellness_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Sdai.inkSubtle,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        showing = false
                        // "Take Break" — hide until next launch
                        dismissedUntilMillis = Long.MAX_VALUE
                    }) {
                        Text(
                            stringResource(R.string.wellness_action_break),
                            color = Sdai.muted,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        showing = false
                        dismissedUntilMillis = System.currentTimeMillis() + snoozeMillis
                    }) {
                        Text(
                            stringResource(R.string.wellness_action_continue),
                            color = Sdai.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

}
