package com.sdai.news.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sdai.news.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    // ── Refresh timing — used by ArticleRepository to avoid re-fetching
    //    a fresh cache and to throttle per-source fetches. Stored as
    //    epoch millis. Zero = never fetched.

    suspend fun lastFullRefreshMs(): Long =
        context.dataStore.data.first()[KEY_LAST_FULL_REFRESH] ?: 0L

    suspend fun setLastFullRefreshMs(ms: Long) {
        context.dataStore.edit { it[KEY_LAST_FULL_REFRESH] = ms }
    }

    suspend fun lastFetchedMs(layer: String): Long =
        context.dataStore.data.first()[longPreferencesKey("fetched_$layer")] ?: 0L

    suspend fun setLastFetchedMs(layer: String, ms: Long) {
        context.dataStore.edit { it[longPreferencesKey("fetched_$layer")] = ms }
    }

    companion object {
        private val KEY_LAST_FULL_REFRESH = longPreferencesKey("last_full_refresh_ms")
    }
}
