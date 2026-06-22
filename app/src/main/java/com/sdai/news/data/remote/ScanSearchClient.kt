package com.sdai.news.data.remote

import com.sdai.news.data.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface ScanResolveResult {
    data class Found(val result: ScanResult) : ScanResolveResult
    data object NotFound : ScanResolveResult
    data class Error(val message: String) : ScanResolveResult
}

object ScanSearchClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val FIELDS =
        "product_name,product_name_en,brands,ingredients_text,ingredients_text_en," +
        "nutriscore_grade,nova_group,additives_tags,allergens,nutriments,categories"

    // All three Open*Facts databases share the same API structure.
    // Cascade: food → beauty/personal-care → everything else.
    private val BARCODE_HOSTS = listOf(
        "world.openfoodfacts.org",
        "world.openbeautyfacts.org",
        "world.openproductsfacts.org",
    )
    private val SEARCH_HOSTS = listOf(
        "world.openfoodfacts.org",
        "world.openbeautyfacts.org",
        "world.openproductsfacts.org",
    )

    // Search by product name across all databases.
    suspend fun resolveByName(name: String): ScanResolveResult = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(name.trim(), "UTF-8")
        for (host in SEARCH_HOSTS) {
            try {
                val request = Request.Builder()
                    .url(
                        "https://$host/cgi/search.pl" +
                        "?search_terms=$encoded&search_simple=1&action=process" +
                        "&json=1&page_size=5&lc=en&fields=$FIELDS"
                    )
                    .header("User-Agent", "Awarely/1.0")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: continue
                if (!response.isSuccessful) continue
                val products = JSONObject(body).optJSONArray("products") ?: continue
                // First significant word (≥4 chars) from the query must appear
                // in the result name or brand — prevents returning unrelated
                // products when the brand isn't in the database.
                val firstKeyword = name.lowercase()
                    .split(Regex("\\s+"))
                    .firstOrNull { it.length >= 4 }
                for (i in 0 until products.length()) {
                    val p = products.getJSONObject(i)
                    val pName = p.optString("product_name_en", "").ifBlank {
                        p.optString("product_name", "")
                    }
                    val pBrand = p.optString("brands", "")
                    if (pName.isBlank()) continue
                    if (firstKeyword != null &&
                        !("$pName $pBrand").lowercase().contains(firstKeyword)) continue
                    val wrapped = JSONObject().put("status", 1).put("product", p).toString()
                    val result = parseOFDResponse(wrapped, name)
                    if (result is ScanResolveResult.Found) return@withContext result
                }
            } catch (_: Exception) { continue }
        }
        ScanResolveResult.NotFound
    }

    // Exact barcode lookup — tries food, then beauty, then general products.
    suspend fun resolveBarcode(barcode: String): ScanResolveResult = withContext(Dispatchers.IO) {
        for (host in BARCODE_HOSTS) {
            try {
                val request = Request.Builder()
                    .url("https://$host/api/v2/product/$barcode.json?lc=en&fields=$FIELDS")
                    .header("User-Agent", "Awarely/1.0")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: continue
                if (!response.isSuccessful) continue
                val result = parseOFDResponse(body, barcode)
                if (result is ScanResolveResult.Found) return@withContext result
            } catch (_: Exception) { continue }
        }
        ScanResolveResult.NotFound
    }

    private fun parseOFDResponse(body: String, inputKey: String): ScanResolveResult {
        return try {
            val root = JSONObject(body)
            if (root.optInt("status", 0) == 0) return ScanResolveResult.NotFound
            val p = root.optJSONObject("product") ?: return ScanResolveResult.NotFound

            // Prefer English name fields; fall back to localised name
            val name = p.optString("product_name_en", "").ifBlank {
                p.optString("product_name", "").ifBlank { "" }
            }
            val ingredients = p.optString("ingredients_text_en", "").ifBlank {
                p.optString("ingredients_text", "")
            }
            if (name.isBlank() && ingredients.isBlank()) return ScanResolveResult.NotFound

            val displayName = name.ifBlank { "Unknown Product" }
            val brand = p.optString("brands", "")
            val nutriscoreGrade = p.optString("nutriscore_grade", "")
            val novaGroup = p.optInt("nova_group", 0)
            val additiveTags = p.optJSONArray("additives_tags")
            val allergens = p.optString("allergens", "")
            val nutriments = p.optJSONObject("nutriments")

            val nutriscoreRating = when (nutriscoreGrade.lowercase()) {
                "a" -> 4.8f; "b" -> 4.0f; "c" -> 3.0f; "d" -> 2.0f; "e" -> 1.2f; else -> 3.0f
            }
            val novaRating = when (novaGroup) {
                1 -> 4.5f; 2 -> 3.8f; 3 -> 2.8f; 4 -> 1.5f; else -> 3.0f
            }
            val additiveCount = additiveTags?.length() ?: 0
            val additiveRating = when {
                additiveCount == 0 -> 4.5f; additiveCount <= 2 -> 3.8f
                additiveCount <= 5 -> 3.0f; else -> 1.8f
            }
            val overall = ((nutriscoreRating + novaRating + additiveRating) / 3f).coerceIn(1f, 5f)
            val safetyLabel = when {
                overall >= 3.5f -> "Safe"; overall >= 2.5f -> "Moderate"; else -> "Low"
            }

            val nutritionScore = (nutriments?.let {
                val sugar = it.optDouble("sugars_100g", -1.0)
                val sodium = it.optDouble("sodium_100g", -1.0)
                when {
                    sugar < 0 && sodium < 0 -> null   // not food — omit this dimension
                    sugar > 20 || sodium > 1.5 -> 1.8f
                    sugar > 10 || sodium > 0.8 -> 2.8f
                    else -> 4.2f
                }
            })
            val breakdown = linkedMapOf<String, Float>().apply {
                put("Ingredients Safety", nutriscoreRating.coerceIn(1f, 5f))
                put("Additives & Chemicals", additiveRating.coerceIn(1f, 5f))
                if (nutritionScore != null) put("Nutritional Value", nutritionScore.coerceIn(1f, 5f))
                if (novaGroup > 0) put("Processing Level", novaRating.coerceIn(1f, 5f))
            }

            val keyFacts = mutableListOf<String>()
            if (nutriscoreGrade.isNotBlank()) keyFacts.add("Nutri-Score: ${nutriscoreGrade.uppercase()}")
            if (novaGroup > 0) keyFacts.add("NOVA Group $novaGroup: ${
                when (novaGroup) {
                    1 -> "Unprocessed"; 2 -> "Processed ingredients"
                    3 -> "Processed food"; else -> "Ultra-processed"
                }
            }")
            if (additiveCount > 0) keyFacts.add("$additiveCount additive${if (additiveCount > 1) "s" else ""} detected")
            if (allergens.isNotBlank()) {
                val cleaned = allergens.replace(Regex("en:|fr:|de:|es:"), "").replace(",", " ·").trim()
                if (cleaned.isNotBlank()) keyFacts.add("Contains: $cleaned")
            }

            val description = buildString {
                if (ingredients.isNotBlank()) append(ingredients.take(150).trimEnd(',', ' '))
                if (nutriscoreGrade.isNotBlank()) append(". Nutri-Score ${nutriscoreGrade.uppercase()}")
                if (novaGroup == 4) append(". Ultra-processed product")
            }.ifBlank { "Product data from Open Food Facts." }

            ScanResolveResult.Found(
                ScanResult(
                    barcode = inputKey.take(200),
                    name = displayName,
                    brand = brand,
                    description = description,
                    overallRating = overall,
                    safetyLabel = safetyLabel,
                    safetyReason = "Nutri-Score, NOVA group and additives",
                    ratingBreakdown = breakdown,
                    category = p.optString("categories", "").split(",").firstOrNull()?.trim() ?: "",
                    keyFacts = keyFacts,
                    relatedAlerts = emptyList(),
                )
            )
        } catch (e: Exception) {
            ScanResolveResult.Error("Parse error: ${e.message}")
        }
    }
}
