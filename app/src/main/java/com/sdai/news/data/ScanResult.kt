package com.sdai.news.data

data class ScanResult(
    val barcode: String,
    val name: String,
    val brand: String,
    val description: String,
    val overallRating: Float,
    /** "Safe" | "Moderate" | "Low" | "Unknown" */
    val safetyLabel: String,
    val safetyReason: String,
    /** Ordered map — e.g. "Ingredients Safety" → 4.0 */
    val ratingBreakdown: Map<String, Float>,
    val category: String,
    val keyFacts: List<String>,
    val relatedAlerts: List<String>,
)
