package com.sdai.news.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sdai.news.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "sdai_prefs")

class PrefsStore(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val wellnessKey = booleanPreferencesKey("wellness_enabled")
    private val disclaimerKey = booleanPreferencesKey("disclaimer_accepted")
    private val setupDoneKey = booleanPreferencesKey("setup_completed")

    private val locLatKey = doublePreferencesKey("location_lat")
    private val locLonKey = doublePreferencesKey("location_lon")
    private val locCityKey = stringPreferencesKey("location_city")
    private val locRegionKey = stringPreferencesKey("location_region")
    private val locCountryKey = stringPreferencesKey("location_country")
    private val locLabelKey = stringPreferencesKey("location_label")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[themeKey] ?: ThemeMode.AMOLED.name) }
            .getOrDefault(ThemeMode.AMOLED)
    }

    val wellnessEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[wellnessKey] ?: true
    }

    val disclaimerAccepted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[disclaimerKey] ?: false
    }

    val setupCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[setupDoneKey] ?: false
    }

    val locationCity: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[locCityKey] ?: ""
    }

    val locationRegion: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[locRegionKey] ?: ""
    }

    val locationCountry: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[locCountryKey] ?: ""
    }

    val locationLabel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[locLabelKey] ?: ""
    }

    val locationHasFix: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[locLatKey] != null && prefs[locLonKey] != null
    }

    suspend fun locationLat(): Double =
        context.dataStore.data.first()[locLatKey] ?: 0.0

    suspend fun locationLon(): Double =
        context.dataStore.data.first()[locLonKey] ?: 0.0

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setWellnessEnabled(enabled: Boolean) {
        context.dataStore.edit { it[wellnessKey] = enabled }
    }

    suspend fun setDisclaimerAccepted(accepted: Boolean) {
        context.dataStore.edit { it[disclaimerKey] = accepted }
    }

    suspend fun setSetupCompleted(completed: Boolean) {
        context.dataStore.edit { it[setupDoneKey] = completed }
    }

    suspend fun setLocation(loc: ResolvedLocation) {
        context.dataStore.edit {
            it[locLatKey] = loc.latitude
            it[locLonKey] = loc.longitude
            it[locCityKey] = loc.city
            it[locRegionKey] = loc.region
            it[locCountryKey] = loc.country
            it[locLabelKey] = loc.label
        }
    }

    suspend fun setManualLocation(city: String, region: String, country: String) {
        context.dataStore.edit {
            it[locCityKey] = city
            it[locRegionKey] = region
            it[locCountryKey] = country
            it[locLabelKey] = listOfNotNull(
                city.takeIf { it.isNotBlank() },
                region.takeIf { it.isNotBlank() },
            ).joinToString(", ").ifEmpty { "$city, $region" }
        }
    }

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
