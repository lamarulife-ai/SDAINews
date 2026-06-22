package com.sdai.news.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sdai.news.R
import com.sdai.news.SDAINewsApp
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.viewmodel.UnifiedFeedViewModel

@Composable
fun ProfileScreen(
    vm: UnifiedFeedViewModel = viewModel(),
    onOpenSettings: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenContact: () -> Unit,
    onOpenLocationPicker: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onClearAllData: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val streak by vm.streakCurrent.collectAsState()
    val todayCount by vm.todayReadCount.collectAsState()
    val preferredTopics by SDAINewsApp.get().prefs.preferredTopics.collectAsState(initial = emptySet())

    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Data", color = Sdai.ink, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will delete all scan history, preferences, and reset the app to the initial setup. This cannot be undone.",
                    color = Sdai.muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = { showClearDialog = false; onClearAllData() },
                    colors = ButtonDefaults.buttonColors(containerColor = Sdai.danger),
                ) {
                    Text("Clear Everything", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = Sdai.muted)
                }
            },
            containerColor = Sdai.surface,
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Sdai.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.awarely_logo),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Profile", color = Sdai.ink, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Sdai.primary, Sdai.accent)))
                            .border(3.dp, Sdai.border, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("P", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Person1", color = Sdai.ink, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("Awarely Reader", color = Sdai.muted, fontSize = 13.sp)
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ProfileStatCard(emoji = "🔥", value = "$streak", label = "Day Streak", modifier = Modifier.weight(1f))
                    ProfileStatCard(emoji = "📰", value = "$todayCount", label = "Read Today", modifier = Modifier.weight(1f))
                    ProfileStatCard(emoji = "🎯", value = "${vm.dailyGoal}", label = "Daily Goal", modifier = Modifier.weight(1f))
                }
            }

            if (preferredTopics.isNotEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Sdai.cardInner).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Sdai.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Your Interests", color = Sdai.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            preferredTopics.take(6).forEach { topic ->
                                Box(
                                    Modifier.clip(RoundedCornerShape(50))
                                        .background(Sdai.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    Text(topic.replaceFirstChar { it.uppercase() }, color = Sdai.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Text("AI learns your preferences and shows what YOU care about", color = Sdai.muted, fontSize = 12.sp)
                    }
                }
            }

            item {
                Text("Quick Access", color = Sdai.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp))
            }
            item { ProfileActionRow("Scan History", Icons.Outlined.History, onOpenHistory) }
            item { ProfileActionRow("Bookmarks", Icons.Outlined.Bookmark, onOpenBookmarks) }
            item { ProfileActionRow("Location", Icons.Outlined.LocationOn, onOpenLocationPicker) }
            item { ProfileActionRow("Settings", Icons.Outlined.Settings, onOpenSettings) }
            item { ProfileActionRow("About", Icons.Outlined.Info, onOpenContact) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Sdai.danger.copy(alpha = 0.08f))
                        .border(1.dp, Sdai.danger.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .clickable { showClearDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.DeleteForever, null, tint = Sdai.danger, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Clear All Data", color = Sdai.danger, fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("›", color = Sdai.danger.copy(alpha = 0.5f), fontSize = 20.sp)
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun ProfileStatCard(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Sdai.cardInner).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Sdai.ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Sdai.muted, fontSize = 11.sp)
    }
}

@Composable
private fun ProfileActionRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Sdai.cardInner).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Sdai.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = Sdai.ink, fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("›", color = Sdai.muted, fontSize = 20.sp)
    }
}
