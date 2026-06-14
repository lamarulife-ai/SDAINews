package com.sdai.news.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.provider.Settings
import com.sdai.news.BuildConfig
import com.sdai.news.R
import com.sdai.news.SDAINewsApp
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.ui.theme.ThemeMode
import com.sdai.news.util.ArticleViewer
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenContact: () -> Unit,
    onOpenDisclaimer: () -> Unit,
    onOpenLocationPicker: () -> Unit,
) {
    val prefs = SDAINewsApp.get().prefs
    val themeMode by prefs.themeMode.collectAsState(initial = ThemeMode.AMOLED)
    val wellness by prefs.wellnessEnabled.collectAsState(initial = true)
    val locationLabel by prefs.locationLabel.collectAsState(initial = "")
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Sdai.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Sdai.ink)
            }
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Sdai.ink,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(16.dp))

        // ── Location ────────────────────────────────────────────
        SectionLabel(text = "Location")
        Spacer(Modifier.height(8.dp))
        NavRow(
            label = locationLabel.ifEmpty { "Set your location" },
            subtitle = "Change location for local news",
            onClick = onOpenLocationPicker,
        )
        Spacer(Modifier.height(24.dp))

        SectionLabel(text = stringResource(R.string.settings_theme))
        Spacer(Modifier.height(8.dp))
        ThemeOption(
            label = stringResource(R.string.theme_amoled),
            selected = themeMode == ThemeMode.AMOLED,
            onClick = { scope.launch { prefs.setThemeMode(ThemeMode.AMOLED) } },
        )
        ThemeOption(
            label = stringResource(R.string.theme_cyber),
            selected = themeMode == ThemeMode.CYBER,
            onClick = { scope.launch { prefs.setThemeMode(ThemeMode.CYBER) } },
        )
        ThemeOption(
            label = stringResource(R.string.theme_minimal),
            selected = themeMode == ThemeMode.MINIMAL,
            onClick = { scope.launch { prefs.setThemeMode(ThemeMode.MINIMAL) } },
        )

        Spacer(Modifier.height(24.dp))
        SectionLabel(text = "Wellness")
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Sdai.cardInner)
                .border(1.dp, Sdai.border, RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_wellness),
                    style = MaterialTheme.typography.titleMedium,
                    color = Sdai.ink,
                )
                Text(
                    stringResource(R.string.settings_wellness_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Sdai.muted,
                )
            }
            Switch(
                checked = wellness,
                onCheckedChange = { scope.launch { prefs.setWellnessEnabled(it) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Sdai.onPrimary,
                    checkedTrackColor = Sdai.primary,
                ),
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel(text = stringResource(R.string.settings_notifications))
        Spacer(Modifier.height(8.dp))
        BatteryHintCard(onOpen = {
            // ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS opens the
            // system battery-optimisation list without requiring the
            // REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission, which
            // Play Store policy restricts.
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                )
            }
        })

        // ── Reading ────────────────────────────────────────────────
        Spacer(Modifier.height(24.dp))
        SectionLabel(text = stringResource(R.string.settings_reading_section))
        Spacer(Modifier.height(8.dp))
        NavRow(label = stringResource(R.string.settings_bookmarks), onClick = onOpenBookmarks)

        // ── Legal & About — single hub for every policy / info link.
        //   Play News policy expects all of these to be one tap away
        //   from the user's hand, not nested inside other screens.
        Spacer(Modifier.height(24.dp))
        SectionLabel(text = stringResource(R.string.settings_legal_section))
        Spacer(Modifier.height(8.dp))
        NavRow(label = stringResource(R.string.settings_disclaimer), onClick = onOpenDisclaimer)
        Spacer(Modifier.height(8.dp))
        NavRow(
            label = stringResource(R.string.settings_privacy),
            onClick = {
                ArticleViewer.open(ctx, ctx.getString(R.string.contact_privacy_url))
            },
        )
        Spacer(Modifier.height(8.dp))
        NavRow(
            label = stringResource(R.string.settings_terms),
            onClick = {
                ArticleViewer.open(ctx, ctx.getString(R.string.contact_terms_url))
            },
        )
        Spacer(Modifier.height(8.dp))
        NavRow(label = stringResource(R.string.settings_contact), onClick = onOpenContact)
        Spacer(Modifier.height(8.dp))
        NavRow(
            label = stringResource(R.string.settings_github),
            onClick = {
                ArticleViewer.open(ctx, ctx.getString(R.string.contact_website_url))
            },
        )

        Spacer(Modifier.height(24.dp))
        SectionLabel(text = stringResource(R.string.settings_about))
        Spacer(Modifier.height(8.dp))
        AboutCard()
    }
}

/**
 * Read-only About card. Pulls version info from the generated
 * `BuildConfig` so it always matches what was built — there's nothing
 * to keep in sync by hand.
 */
@Composable
private fun AboutCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Sdai.cardInner)
            .border(1.dp, Sdai.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AboutRow(label = "App", value = stringResource(R.string.app_name))
        AboutRow(label = "Version", value = "V${BuildConfig.VERSION_NAME}")
        AboutRow(label = "Version code", value = BuildConfig.VERSION_CODE.toString())
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Sdai.muted)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = Sdai.ink,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BatteryHintCard(onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Sdai.cardInner)
            .border(1.dp, Sdai.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.settings_battery),
            style = MaterialTheme.typography.titleMedium,
            color = Sdai.ink,
        )
        Text(
            stringResource(R.string.settings_battery_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = Sdai.muted,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_battery_action),
            style = MaterialTheme.typography.labelSmall,
            color = Sdai.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Sdai.muted,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Sdai.primary.copy(alpha = 0.12f) else Sdai.cardInner)
            .border(
                width = 1.dp,
                color = if (selected) Sdai.primary else Sdai.border,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = Sdai.ink,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (selected) {
            Text("●", color = Sdai.primary)
        }
    }
}

@Composable
private fun NavRow(label: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Sdai.cardInner)
            .border(1.dp, Sdai.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = Sdai.ink)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Sdai.muted)
            }
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Sdai.muted)
    }
}
