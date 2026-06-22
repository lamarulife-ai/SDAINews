package com.sdai.news.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey val barcode: String,
    val name: String,
    val brand: String,
    val overallRating: Float,
    val safetyLabel: String,
    val category: String,
    val scannedAtMs: Long,
)
