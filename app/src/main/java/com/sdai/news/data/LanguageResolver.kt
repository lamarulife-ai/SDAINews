package com.sdai.news.data

import com.sdai.news.data.remote.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** A resolved regional language: ISO 639-1 [code] (for Google News `hl`) + display [name]. */
data class RegionLanguage(val code: String, val name: String)

/**
 * Determines the most-spoken language for the user's location, pulled from the
 * internet and then saved (see PrefsStore.regionalLanguage*).
 *
 *  - For multilingual countries we know at sub-national granularity (India),
 *    map the state → its main language (e.g., Andhra Pradesh → Telugu).
 *  - Otherwise look up the country's primary language live via the
 *    **REST Countries** API (no key) — so the app works worldwide.
 */
object LanguageResolver {

    suspend fun mostSpoken(countryCode: String, state: String, countryName: String): RegionLanguage {
        // Sub-national precision for India.
        indiaStateLanguage(state)?.let { return it }
        if (countryCode.equals("IN", true) || countryName.equals("India", true)) {
            return RegionLanguage("hi", "Hindi")
        }
        // Global: ask the internet for the country's primary language.
        return countryPrimaryLanguage(countryCode) ?: RegionLanguage("en", "English")
    }

    /** YouTube video channels for a language code (empty if we have none yet). */
    fun videoChannels(code: String): List<Pair<String, String>> = when (code) {
        "te" -> listOf("NTV Telugu" to "UCumtYpCY26F6Jr3satUgMvA", "Sakshi TV" to "UCZ9m4KOh8Ei60428xeGYDCQ")
        "ta" -> listOf("News18 Tamil" to "UCat88i6_rELqI_prwvjspRA")
        "ml" -> listOf("Asianet News" to "UCf8w5m0YsRa8MHQ5bwSGmbw")
        "kn" -> listOf("TV9 Kannada" to "UC8dnBi4WUErqYQHZ4PfsLTg")
        "mr" -> listOf("TV9 Marathi" to "UCdOSeEq9Cs2Pco7OCn2_i5w", "ABP Majha" to "UCH7nv1A9xIrAifZJNvt7cgA")
        "bn" -> listOf("News18 Bangla" to "UCbf0XHULBkTfv2hBjaaDw9Q")
        "pa" -> listOf("PTC News" to "UCQLEbraENUGWh6p1Rv664rQ")
        "hi" -> listOf("Aaj Tak" to "UCt4t-jeY85JegMlZ-E5UWtA", "ABP News" to "UCRWFSbif-RFENbBrSiez1DA")
        else -> emptyList()
    }

    private fun indiaStateLanguage(state: String): RegionLanguage? {
        val s = state.lowercase()
        return when {
            "andhra" in s || "telangana" in s -> RegionLanguage("te", "Telugu")
            "tamil" in s || "puducherry" in s -> RegionLanguage("ta", "Tamil")
            "kerala" in s -> RegionLanguage("ml", "Malayalam")
            "karnataka" in s -> RegionLanguage("kn", "Kannada")
            "maharashtra" in s -> RegionLanguage("mr", "Marathi")
            "bengal" in s -> RegionLanguage("bn", "Bengali")
            "punjab" in s -> RegionLanguage("pa", "Punjabi")
            "gujarat" in s -> RegionLanguage("gu", "Gujarati")
            listOf("pradesh", "bihar", "rajasthan", "delhi", "haryana", "jharkhand",
                "chhattisgarh", "uttarakhand", "himachal").any { it in s } -> RegionLanguage("hi", "Hindi")
            else -> null
        }
    }

    private suspend fun countryPrimaryLanguage(countryCode: String): RegionLanguage? =
        withContext(Dispatchers.IO) {
            if (countryCode.isBlank()) return@withContext null
            runCatching {
                val req = Request.Builder()
                    .url("https://restcountries.com/v3.1/alpha/$countryCode?fields=languages")
                    .header("User-Agent", "Awarely/1.0")
                    .build()
                HttpClient.instance.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string().orEmpty()
                    val langs = JSONObject(body).optJSONObject("languages") ?: return@use null
                    val firstName = langs.keys().asSequence().firstOrNull()?.let { langs.getString(it) }
                        ?: return@use null
                    RegionLanguage(nameToCode(firstName), firstName)
                }
            }.getOrNull()
        }

    /** Map a language display name → ISO 639-1 code for Google News `hl`. */
    private fun nameToCode(name: String): String = when (name.lowercase()) {
        "english" -> "en"; "hindi" -> "hi"; "french" -> "fr"; "spanish" -> "es"
        "german" -> "de"; "portuguese" -> "pt"; "arabic" -> "ar"; "russian" -> "ru"
        "japanese" -> "ja"; "chinese" -> "zh"; "italian" -> "it"; "korean" -> "ko"
        "dutch" -> "nl"; "turkish" -> "tr"; "thai" -> "th"; "vietnamese" -> "vi"
        "indonesian" -> "id"; "urdu" -> "ur"; "persian" -> "fa"; "polish" -> "pl"
        "telugu" -> "te"; "tamil" -> "ta"; "bengali" -> "bn"; "marathi" -> "mr"
        "malayalam" -> "ml"; "kannada" -> "kn"; "punjabi" -> "pa"; "gujarati" -> "gu"
        else -> "en"
    }
}
