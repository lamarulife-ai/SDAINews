package com.sdai.news.data

import com.google.mlkit.vision.text.Text
import java.util.regex.Pattern

object ProductTextHeuristicParser {

    private val NOISE_PATTERNS = listOf(
        // Weights and volumes
        Pattern.compile("\\d+\\s*(g|mg|kg|oz|ml|fl\\.?\\s*oz|lb|l|cl)\\b", Pattern.CASE_INSENSITIVE),
        // Nutrition / ingredient headers
        Pattern.compile("nutrition\\s*facts", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bingredients?\\s*:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bserving\\s+size\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bcalories\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(total\\s+fat|sodium|protein|carbohydrate)\\b", Pattern.CASE_INSENSITIVE),
        // Barcodes and pure numbers
        Pattern.compile("^\\d{6,}$"),
        // Common label noise
        Pattern.compile("\\bbest before\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bmfg\\b|\\bmfd\\b|\\bexp\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bwww\\.", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\be[-\\s]?\\d{3}\\b", Pattern.CASE_INSENSITIVE),
    )

    fun extractProductName(visionText: Text, imageHeight: Int): String? {
        data class Candidate(val text: String, val fontHeight: Int, val topY: Int)

        val candidates = mutableListOf<Candidate>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val text = line.text.trim()
                if (text.length < 4) continue
                val box = line.boundingBox ?: continue

                if (isNoise(text)) continue

                // Must be mostly letters — filters wall textures, numbers, symbols
                val letterCount = text.count { it.isLetter() }
                if (letterCount < text.length * 0.5f || letterCount < 3) continue

                // Skip lines with too many words — ingredient lists / legal copy
                if (text.split(Regex("\\s+")).size > 6) continue

                // Skip content in the bottom 15% of the image
                val lineCenter = box.top + box.height() / 2
                if (lineCenter > imageHeight * 0.85) continue

                candidates.add(Candidate(text, box.height(), box.top))
            }
        }

        if (candidates.isEmpty()) return null

        // Primary sort: largest font (tallest bounding box); secondary: highest on label
        return candidates
            .sortedWith(compareByDescending<Candidate> { it.fontHeight }.thenBy { it.topY })
            .firstOrNull()?.text
    }

    private fun isNoise(text: String): Boolean =
        NOISE_PATTERNS.any { it.matcher(text).find() }
}
