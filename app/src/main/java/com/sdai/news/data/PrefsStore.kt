package com.sdai.news.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    private val locCountryCodeKey = stringPreferencesKey("location_country_code")
    private val regionalLangCodeKey = stringPreferencesKey("regional_lang_code")
    private val regionalLangNameKey = stringPreferencesKey("regional_lang_name")
    private val positiveOnlyKey = booleanPreferencesKey("positive_only")

    // Blocked sources (channel block list)
    private val blockedSourcesKey = stringSetPreferencesKey("blocked_sources")

    // Language toggles per section
    private val worldEnglishKey = booleanPreferencesKey("world_english")
    private val worldRegionalKey = booleanPreferencesKey("world_regional")
    private val nationalEnglishKey = booleanPreferencesKey("national_english")
    private val nationalRegionalKey = booleanPreferencesKey("national_regional")
    private val regionalEnglishKey = booleanPreferencesKey("regional_english")
    private val regionalRegionalKey = booleanPreferencesKey("regional_regional")

    // Preferred content topics (up to 3)
    private val preferredTopicsKey = stringSetPreferencesKey("preferred_topics")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[themeKey] ?: ThemeMode.MINIMAL.name) }
            .getOrDefault(ThemeMode.MINIMAL)
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

    val locationCountryCode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[locCountryCodeKey] ?: ""
    }

    val regionalLanguageCode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[regionalLangCodeKey] ?: ""
    }

    val regionalLanguageName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[regionalLangNameKey] ?: ""
    }

    val positiveOnly: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[positiveOnlyKey] ?: false
    }

    suspend fun setPositiveOnly(v: Boolean) {
        context.dataStore.edit { it[positiveOnlyKey] = v }
    }

    // Blocked sources
    val blockedSources: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[blockedSourcesKey]?.toSet() ?: emptySet()
    }

    suspend fun setBlockedSources(sources: Set<String>) {
        context.dataStore.edit { it[blockedSourcesKey] = sources }
    }

    suspend fun addBlockedSource(source: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[blockedSourcesKey]?.toMutableSet() ?: mutableSetOf()
            current.add(source)
            prefs[blockedSourcesKey] = current
        }
    }

    suspend fun removeBlockedSource(source: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[blockedSourcesKey]?.toMutableSet() ?: mutableSetOf()
            current.remove(source)
            prefs[blockedSourcesKey] = current
        }
    }

    // Language toggles (defaults: World=English only, National=both, Regional=regional only)
    val worldEnglish: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[worldEnglishKey] ?: true }
    val worldRegional: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[worldRegionalKey] ?: false }
    val nationalEnglish: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[nationalEnglishKey] ?: true }
    val nationalRegional: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[nationalRegionalKey] ?: true }
    val regionalEnglish: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[regionalEnglishKey] ?: true }
    val regionalRegional: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[regionalRegionalKey] ?: true }

    suspend fun setWorldEnglish(v: Boolean) { context.dataStore.edit { it[worldEnglishKey] = v } }
    suspend fun setWorldRegional(v: Boolean) { context.dataStore.edit { it[worldRegionalKey] = v } }
    suspend fun setNationalEnglish(v: Boolean) { context.dataStore.edit { it[nationalEnglishKey] = v } }
    suspend fun setNationalRegional(v: Boolean) { context.dataStore.edit { it[nationalRegionalKey] = v } }
    suspend fun setRegionalEnglish(v: Boolean) { context.dataStore.edit { it[regionalEnglishKey] = v } }
    suspend fun setRegionalRegional(v: Boolean) { context.dataStore.edit { it[regionalRegionalKey] = v } }

    // Preferred topics
    val preferredTopics: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[preferredTopicsKey]?.toSet() ?: emptySet()
    }

    suspend fun setPreferredTopics(topics: Set<String>) {
        context.dataStore.edit { it[preferredTopicsKey] = topics }
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
        // Always persist a country (code + name) so National news can be pulled.
        // Manual entry often omits it → fall back to the device region (e.g. IN).
        val cc = resolveCountryCode(loc.countryCode, loc.country)
        val cname = loc.country.ifBlank { countryNameOf(cc) }
        context.dataStore.edit {
            it[locLatKey] = loc.latitude
            it[locLonKey] = loc.longitude
            it[locCityKey] = loc.city
            it[locRegionKey] = loc.region
            it[locCountryKey] = cname
            it[locLabelKey] = loc.label
            it[locCountryCodeKey] = cc
        }
    }

    suspend fun setRegionalLanguage(code: String, name: String) {
        context.dataStore.edit {
            it[regionalLangCodeKey] = code
            it[regionalLangNameKey] = name
        }
    }

    /** Save the location AND resolve+persist its most-spoken language (from the
     *  internet) so regional news/video can use it. */
    suspend fun saveLocationWithLanguage(loc: ResolvedLocation) {
        setLocation(loc)
        // Use the backfilled country code so language resolution works even when
        // the caller passed a blank country.
        val cc = resolveCountryCode(loc.countryCode, loc.country)
        val lang = LanguageResolver.mostSpoken(cc, loc.region, loc.country.ifBlank { countryNameOf(cc) })
        setRegionalLanguage(lang.code, lang.name)
    }

    suspend fun setManualLocation(city: String, region: String, country: String) {
        val cc = resolveCountryCode("", country)
        context.dataStore.edit {
            it[locCityKey] = city
            it[locRegionKey] = region
            it[locCountryKey] = country.ifBlank { countryNameOf(cc) }
            it[locCountryCodeKey] = cc
            it[locLabelKey] = listOfNotNull(
                city.takeIf { it.isNotBlank() },
                region.takeIf { it.isNotBlank() },
            ).joinToString(", ").ifEmpty { "$city, $region" }
        }
    }

    /** Best-effort country code: the supplied code, else derive from the country
     *  name, else the device's region (so National always has a country). */
    private fun resolveCountryCode(code: String, countryName: String): String {
        if (code.isNotBlank()) return code.uppercase()
        if (countryName.isNotBlank()) {
            val match = java.util.Locale.getISOCountries().firstOrNull {
                java.util.Locale("", it).displayCountry.equals(countryName.trim(), ignoreCase = true)
            }
            if (match != null) return match
        }
        return java.util.Locale.getDefault().country.uppercase()
    }

    private fun countryNameOf(code: String): String =
        if (code.isNotBlank()) java.util.Locale("", code).displayCountry else ""

    // ── Interest affinity (on-device personalization) ──────────────────
    // Serialized as "key=value;key=value" where keys are "cat:<category>",
    // "src:<source>", "tier:<tier>". Values are capped affinity scores.

    val affinity: Flow<Map<String, Float>> = context.dataStore.data.map { prefs ->
        parseAffinity(prefs[affinityKey] ?: "")
    }

    /** Add [points] to the affinity of each given key, capped at [AFFINITY_CAP]. */
    suspend fun addAffinity(keys: List<String>, points: Float) {
        if (keys.isEmpty()) return
        context.dataStore.edit { prefs ->
            val map = parseAffinity(prefs[affinityKey] ?: "").toMutableMap()
            for (k in keys) {
                map[k] = ((map[k] ?: 0f) + points).coerceAtMost(AFFINITY_CAP)
            }
            prefs[affinityKey] = serializeAffinity(map)
        }
    }

    // ── Reading streak + daily goal (habit loop) ────────────────────────

    val streakCurrent: Flow<Int> = context.dataStore.data.map { it[streakCurKey] ?: 0 }

    val todayReadCount: Flow<Int> = context.dataStore.data.map { prefs ->
        if ((prefs[todayDayKey] ?: -1L) == epochDayNow()) prefs[todayCountKey] ?: 0 else 0
    }

    /** Record that the reader engaged with a story today; updates streak + count. */
    suspend fun recordRead() {
        val today = epochDayNow()
        context.dataStore.edit { prefs ->
            val tDay = prefs[todayDayKey] ?: -1L
            if (tDay != today) {
                val last = prefs[lastReadDayKey] ?: -1L
                val cur = prefs[streakCurKey] ?: 0
                val newStreak = if (last == today - 1) cur + 1 else 1
                prefs[streakCurKey] = newStreak
                prefs[streakLongKey] = maxOf(newStreak, prefs[streakLongKey] ?: 0)
                prefs[lastReadDayKey] = today
                prefs[todayDayKey] = today
                prefs[todayCountKey] = 1
            } else {
                prefs[todayCountKey] = (prefs[todayCountKey] ?: 0) + 1
            }
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

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    private val affinityKey = stringPreferencesKey("interest_affinity")
    private val streakCurKey = intPreferencesKey("streak_current")
    private val streakLongKey = intPreferencesKey("streak_longest")
    private val lastReadDayKey = longPreferencesKey("streak_last_read_day")
    private val todayCountKey = intPreferencesKey("today_read_count")
    private val todayDayKey = longPreferencesKey("today_day")

    companion object {
        const val DAILY_GOAL = 5
        private const val AFFINITY_CAP = 60f
        private val KEY_LAST_FULL_REFRESH = longPreferencesKey("last_full_refresh_ms")

        /** UTC epoch-day index — cheap, timezone-stable enough for a streak. */
        private fun epochDayNow(): Long = System.currentTimeMillis() / 86_400_000L

        private fun parseAffinity(blob: String): Map<String, Float> {
            if (blob.isBlank()) return emptyMap()
            return blob.split(';').mapNotNull { pair ->
                val i = pair.lastIndexOf('=')
                if (i <= 0) return@mapNotNull null
                val k = pair.substring(0, i)
                val v = pair.substring(i + 1).toFloatOrNull() ?: return@mapNotNull null
                k to v
            }.toMap()
        }

        private fun serializeAffinity(map: Map<String, Float>): String =
            map.entries.joinToString(";") { "${it.key}=${it.value}" }
    }
}
