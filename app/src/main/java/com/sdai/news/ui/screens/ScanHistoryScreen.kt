package com.sdai.news.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sdai.news.data.db.ScanHistoryEntity
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.viewmodel.ScanHistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScanHistoryScreen(
    modifier: Modifier = Modifier,
    onOpenScanner: () -> Unit = {},
) {
    val vm: ScanHistoryViewModel = viewModel()
    val history by vm.history.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Sdai.background),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Scan History",
                color = Sdai.ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (history.isNotEmpty()) {
                TextButton(onClick = { showClearDialog = true }) {
                    Text("Clear All", color = Sdai.danger, fontSize = 13.sp)
                }
            }
        }

        HorizontalDivider(color = Sdai.border)

        if (history.isEmpty()) {
            EmptyHistoryView(onOpenScanner = onOpenScanner)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(history, key = { it.barcode }) { item ->
                    ScanHistoryItem(
                        entity = item,
                        onDelete = { vm.delete(item) },
                    )
                    HorizontalDivider(
                        color = Sdai.border,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear History") },
            text = { Text("Remove all ${history.size} scanned items?") },
            confirmButton = {
                TextButton(onClick = { vm.clearAll(); showClearDialog = false }) {
                    Text("Clear All", color = Sdai.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
            containerColor = Sdai.surface,
            titleContentColor = Sdai.ink,
            textContentColor = Sdai.inkSubtle,
        )
    }
}

@Composable
private fun ScanHistoryItem(
    entity: ScanHistoryEntity,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Sdai.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Product icon placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Sdai.cardInner),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.QrCode,
                contentDescription = null,
                tint = Sdai.muted,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entity.name,
                color = Sdai.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entity.brand.isNotBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(entity.brand, color = Sdai.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatTimestamp(entity.scannedAtMs),
                color = Sdai.mutedDeep,
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            // Rating
            Text(
                text = entity.overallRating.let { r ->
                    val rounded = (Math.round(r * 10) / 10.0).toString()
                    rounded
                },
                color = safetyColor(entity.safetyLabel),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            // Safety badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(safetyColor(entity.safetyLabel).copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = entity.safetyLabel,
                    color = safetyColor(entity.safetyLabel),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.DeleteOutline, "Delete", tint = Sdai.muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EmptyHistoryView(onOpenScanner: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Sdai.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = Sdai.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "No scans yet",
                color = Sdai.ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Products you scan will appear here with their safety ratings.",
                color = Sdai.muted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.Button(
                onClick = onOpenScanner,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Sdai.primary,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.QrCode, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Scan a Product", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun safetyColor(label: String): Color = when (label) {
    "Safe" -> Color(0xFF22C55E)
    "Moderate" -> Color(0xFFF59E0B)
    "Low" -> Color(0xFFEF4444)
    else -> Color(0xFF8A8AA0)
}

private fun formatTimestamp(ms: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ms
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "Today, ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))}"
        diff < 172_800_000 -> "Yesterday, ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))}"
        else -> SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(ms))
    }
}
