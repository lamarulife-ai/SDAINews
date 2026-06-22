package com.sdai.news.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.sdai.news.BuildConfig
import com.sdai.news.R
import com.sdai.news.SDAINewsApp
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.ui.theme.ThemeMode
import com.sdai.news.util.ArticleViewer
import kotlinx.coroutines.launch

private enum class SettingsSection(val title: String) {
    GENERAL("General"),
    CONTENT("Content & Feed"),
    LANGUAGE("Languages"),
    NOTIFICATIONS("Notifications"),
    ABOUT("About & Legal"),
}

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
    val positiveOnly by prefs.positiveOnly.collectAsState(initial = false)
    val locationLabel by prefs.locationLabel.collectAsState(initial = "")
    val blockedSources by prefs.blockedSources.collectAsState(initial = emptySet())
    val worldEnglish by prefs.worldEnglish.collectAsState(initial = true)
    val worldRegional by prefs.worldRegional.collectAsState(initial = false)
    val nationalEnglish by prefs.nationalEnglish.collectAsState(initial = true)
    val nationalRegional by prefs.nationalRegional.collectAsState(initial = true)
    val regionalEnglish by prefs.regionalEnglish.collectAsState(initial = false)
    val regionalRegional by prefs.regionalRegional.collectAsState(initial = true)
    val preferredTopics by prefs.preferredTopics.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // Drill-down: null = the categorised index; non-null = one section's page.
    var section by remember { mutableStateOf<SettingsSection?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Sdai.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Back goes up one level: section → index → out of Settings.
            IconButton(onClick = { if (section == null) onBack() else section = null }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Sdai.ink)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                section?.title ?: stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Sdai.ink,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(16.dp))

        when (section) {
            null -> {
                SettingsIndexRow("General", "Location · Theme · Wellness") { section = SettingsSection.GENERAL }
                Spacer(Modifier.height(8.dp))
                SettingsIndexRow("Content & Feed", "Positive only · Preferred topics · Blocked sources") { section = SettingsSection.CONTENT }
                Spacer(Modifier.height(8.dp))
                SettingsIndexRow("Languages", "English / regional per section") { section = SettingsSection.LANGUAGE }
                Spacer(Modifier.height(8.dp))
                SettingsIndexRow("Notifications", "Daily digest & battery") { section = SettingsSection.NOTIFICATIONS }
                Spacer(Modifier.height(8.dp))
                SettingsIndexRow("Saved articles", "Your bookmarks", onClick = onOpenBookmarks)
                Spacer(Modifier.height(8.dp))
                SettingsIndexRow("About & Legal", "Rate · Share · Privacy · Terms · About") { section = SettingsSection.ABOUT }
            }

            SettingsSection.GENERAL -> {
                SectionLabel("Location")
                Spacer(Modifier.height(8.dp))
                NavRow(
                    label = locationLabel.ifEmpty { "Set your location" },
                    subtitle = "Change location for local news",
                    onClick = onOpenLocationPicker,
                )
                Spacer(Modifier.height(24.dp))
                SectionLabel(stringResource(R.string.settings_theme))
                Spacer(Modifier.height(8.dp))
                ThemeOption(stringResource(R.string.theme_amoled), themeMode == ThemeMode.AMOLED) { scope.launch { prefs.setThemeMode(ThemeMode.AMOLED) } }
                ThemeOption(stringResource(R.string.theme_cyber), themeMode == ThemeMode.CYBER) { scope.launch { prefs.setThemeMode(ThemeMode.CYBER) } }
                ThemeOption(stringResource(R.string.theme_minimal), themeMode == ThemeMode.MINIMAL) { scope.launch { prefs.setThemeMode(ThemeMode.MINIMAL) } }
                Spacer(Modifier.height(24.dp))
                SectionLabel("Wellness")
                Spacer(Modifier.height(8.dp))
                SwitchCard(
                    title = stringResource(R.string.settings_wellness),
                    subtitle = stringResource(R.string.settings_wellness_sub),
                    checked = wellness,
                ) { scope.launch { prefs.setWellnessEnabled(it) } }
            }

            SettingsSection.CONTENT -> {
                SwitchCard(
                    title = "Positive news only",
                    subtitle = "Show only Good News & Inspiration — hides everything else.",
                    checked = positiveOnly,
                ) { scope.launch { prefs.setPositiveOnly(it) } }
                Spacer(Modifier.height(24.dp))
                SectionLabel("Preferred Topics (max 3)")
                Spacer(Modifier.height(8.dp))
                PreferredTopicsSection(preferredTopics, scope, prefs)
                Spacer(Modifier.height(24.dp))
                SectionLabel("Blocked Sources")
                Spacer(Modifier.height(8.dp))
                BlockChannelSection(blockedSources, scope, prefs)
            }

            SettingsSection.LANGUAGE -> {
                SectionLabel("Language per Section")
                Spacer(Modifier.height(8.dp))
                LanguageTogglesSection(
                    worldEnglish, worldRegional,
                    nationalEnglish, nationalRegional,
                    regionalEnglish, regionalRegional,
                    scope, prefs,
                )
            }

            SettingsSection.NOTIFICATIONS -> {
                BatteryHintCard(onOpen = {
                    runCatching {
                        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                })
            }

            SettingsSection.ABOUT -> {
                val playLink = "https://play.google.com/store/apps/details?id=${ctx.packageName}"
                NavRow(label = "⭐  Rate Awarely") {
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ctx.packageName}")))
                    }.onFailure { ArticleViewer.open(ctx, playLink) }
                }
                Spacer(Modifier.height(8.dp))
                NavRow(label = "📤  Share Awarely") {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Check out Awarely — scan products, read AI news, make smarter choices. $playLink")
                    }
                    runCatching { ctx.startActivity(Intent.createChooser(send, "Share Awarely")) }
                }
                Spacer(Modifier.height(8.dp))
                NavRow(label = stringResource(R.string.settings_disclaimer), onClick = onOpenDisclaimer)
                Spacer(Modifier.height(8.dp))
                NavRow(label = stringResource(R.string.settings_privacy)) { ArticleViewer.open(ctx, ctx.getString(R.string.contact_privacy_url)) }
                Spacer(Modifier.height(8.dp))
                NavRow(label = stringResource(R.string.settings_terms)) { ArticleViewer.open(ctx, ctx.getString(R.string.contact_terms_url)) }
                Spacer(Modifier.height(8.dp))
                NavRow(label = stringResource(R.string.settings_contact), onClick = onOpenContact)
                Spacer(Modifier.height(8.dp))
                NavRow(label = stringResource(R.string.settings_github)) { ArticleViewer.open(ctx, ctx.getString(R.string.contact_website_url)) }
                Spacer(Modifier.height(24.dp))
                AboutCard()
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** A category row on the Settings index — label + subtitle + chevron. */
@Composable
private fun SettingsIndexRow(title: String, subtitle: String, onClick: () -> Unit) {
    NavRow(label = title, subtitle = subtitle, onClick = onClick)
}

/** Standard switch-in-a-card row used by several settings. */
@Composable
private fun SwitchCard(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
            Text(title, style = MaterialTheme.typography.titleMedium, color = Sdai.ink)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Sdai.muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Sdai.onPrimary,
                checkedTrackColor = Sdai.primary,
            ),
        )
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

@Composable
private fun BlockChannelSection(
    blockedSources: Set<String>,
    scope: kotlinx.coroutines.CoroutineScope,
    prefs: com.sdai.news.data.PrefsStore,
) {
    val inputText = remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Sdai.cardInner)
            .border(1.dp, Sdai.border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Blocked sources won't appear in your feed", style = MaterialTheme.typography.bodySmall, color = Sdai.muted)
        
        // Add new blocked source
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = inputText.value,
                onValueChange = { inputText.value = it },
                modifier = Modifier.weight(1f),
                label = { Text("Source name (e.g., BBC News)", style = MaterialTheme.typography.bodyMedium, color = Sdai.muted) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Sdai.background,
                    unfocusedContainerColor = Sdai.background,
                    focusedIndicatorColor = Sdai.primary,
                    unfocusedIndicatorColor = Sdai.border,
                ),
            )
            IconButton(onClick = {
                val text = inputText.value.trim()
                if (text.isNotBlank()) {
                    scope.launch { prefs.addBlockedSource(text) }
                    inputText.value = ""
                }
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add blocked source", tint = Sdai.primary)
            }
        }
        
        // List blocked sources
        if (blockedSources.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                blockedSources.toList().sorted().forEach { source ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Sdai.background)
                            .clip(RoundedCornerShape(8.dp)),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(source, style = MaterialTheme.typography.bodyMedium, color = Sdai.ink, modifier = Modifier.padding(12.dp).weight(1f))
                        IconButton(onClick = {
                            scope.launch { prefs.removeBlockedSource(source) }
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Unblock $source", tint = Sdai.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageTogglesSection(
    worldEnglish: Boolean, worldRegional: Boolean,
    nationalEnglish: Boolean, nationalRegional: Boolean,
    regionalEnglish: Boolean, regionalRegional: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    prefs: com.sdai.news.data.PrefsStore,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Sdai.cardInner)
            .border(1.dp, Sdai.border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Choose languages for each section. Regional = your state's language (e.g., Telugu for Andhra Pradesh).", 
            style = MaterialTheme.typography.bodySmall, color = Sdai.muted)
        
        LanguageToggleRow("World", "English", worldEnglish, { scope.launch { prefs.setWorldEnglish(it) } })
        LanguageToggleRow("World", "Regional", worldRegional, { scope.launch { prefs.setWorldRegional(it) } })

        HorizontalDivider(color = Sdai.border, thickness = 1.dp)

        LanguageToggleRow("National", "English", nationalEnglish, { scope.launch { prefs.setNationalEnglish(it) } })
        LanguageToggleRow("National", "Regional", nationalRegional, { scope.launch { prefs.setNationalRegional(it) } })

        HorizontalDivider(color = Sdai.border, thickness = 1.dp)

        LanguageToggleRow("Regional", "English", regionalEnglish, { scope.launch { prefs.setRegionalEnglish(it) } })
        LanguageToggleRow("Regional", "Regional", regionalRegional, { scope.launch { prefs.setRegionalRegional(it) } })
    }
}

@Composable
private fun LanguageToggleRow(section: String, language: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text("$section — $language", style = MaterialTheme.typography.titleMedium, color = Sdai.ink)
            Text(if (language == "Regional") "Your state's main language" else "English", style = MaterialTheme.typography.bodySmall, color = Sdai.muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Sdai.onPrimary,
                checkedTrackColor = Sdai.primary,
            ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreferredTopicsSection(
    preferredTopics: Set<String>,
    scope: kotlinx.coroutines.CoroutineScope,
    prefs: com.sdai.news.data.PrefsStore,
) {
    val availableTopics = listOf("top", "politics", "sports", "tech", "science", "health", "business", "entertainment", "anime", "climate", "inspiration", "good")

    fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Sdai.cardInner)
            .border(1.dp, Sdai.border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Select up to 3 topics to boost in your feed", style = MaterialTheme.typography.bodySmall, color = Sdai.muted)

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            availableTopics.forEach { topic ->
                val isSelected = topic in preferredTopics
                val isMaxReached = preferredTopics.size >= 3 && !isSelected

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) {
                            scope.launch { prefs.setPreferredTopics(preferredTopics - topic) }
                        } else if (!isMaxReached) {
                            scope.launch { prefs.setPreferredTopics(preferredTopics + topic) }
                        }
                    },
                    label = { Text(cap(topic), style = MaterialTheme.typography.labelLarge) },
                    enabled = !isMaxReached,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Sdai.cardInner,
                        labelColor = Sdai.ink,
                        selectedContainerColor = Sdai.primary.copy(alpha = 0.2f),
                        selectedLabelColor = Sdai.primary,
                    ),
                    border = BorderStroke(1.dp, if (isSelected) Sdai.primary else Sdai.border),
                )
            }
        }

        if (preferredTopics.isNotEmpty()) {
            Text(
                "Selected: ${preferredTopics.toList().sorted().joinToString(", ") { cap(it) }}",
                style = MaterialTheme.typography.bodySmall, color = Sdai.muted,
            )
        }
    }
}
