package com.sdai.news.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sdai.news.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "sdai_prefs")

/**
 * Thin wrapper around DataStore. Holds the handful of user-tunable
 * preferences — theme mode and the eye-break reminder toggle.
 *
 * Bookmarks and the article cache live in Room, not here.
 */
class PrefsStore(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val wellnessKey = booleanPreferencesKey("wellness_enabled")
    private val disclaimerKey = booleanPreferencesKey("disclaimer_accepted")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[themeKey] ?: ThemeMode.AMOLED.name) }
            .getOrDefault(ThemeMode.AMOLED)
    }

    val wellnessEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[wellnessKey] ?: true
    }

    // Defaults to false so first launch always shows the disclaimer.
    val disclaimerAccepted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[disclaimerKey] ?: false
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setWellnessEnabled(enabled: Boolean) {
        context.dataStore.edit { it[wellnessKey] = enabled }
    }

    suspend fun setDisclaimerAccepted(accepted: Boolean) {
        context.dataStore.edit { it[disclaimerKey] = accepted }
    }
}
