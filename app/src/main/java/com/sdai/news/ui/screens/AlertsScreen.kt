package com.sdai.news.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.sdai.news.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdai.news.ui.theme.Sdai

@Composable
fun AlertsScreen(modifier: Modifier = Modifier) {
    var foodSafety by remember { mutableStateOf(true) }
    var productRecalls by remember { mutableStateOf(true) }
    var kidsHealth by remember { mutableStateOf(false) }
    var govtAdvisories by remember { mutableStateOf(false) }
    var brandAlerts by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Sdai.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painter = painterResource(R.drawable.awarely_logo), contentDescription = null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Alerts", color = Sdai.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Stay informed about food safety", color = Sdai.muted, fontSize = 12.sp)
            }
        }

        HorizontalDivider(color = Sdai.border)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Text(
                    "SUBSCRIBE TO ALERTS",
                    color = Sdai.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item {
                AlertSubscribeRow(
                    emoji = "⚠️",
                    title = "Food Safety Alerts",
                    subtitle = "Contamination reports and food safety warnings",
                    checked = foodSafety,
                    onCheckedChange = { foodSafety = it },
                )
            }
            item {
                AlertSubscribeRow(
                    emoji = "🔔",
                    title = "Product Recalls",
                    subtitle = "Official recall announcements from FSSAI, FDA & regulators",
                    checked = productRecalls,
                    onCheckedChange = { productRecalls = it },
                )
            }
            item {
                AlertSubscribeRow(
                    emoji = "🍼",
                    title = "Kids Health Alerts",
                    subtitle = "Alerts related to children's food, snacks and products",
                    checked = kidsHealth,
                    onCheckedChange = { kidsHealth = it },
                )
            }
            item {
                AlertSubscribeRow(
                    emoji = "🏛️",
                    title = "Government Advisories",
                    subtitle = "Regulatory updates and consumer advisories",
                    checked = govtAdvisories,
                    onCheckedChange = { govtAdvisories = it },
                )
            }
            item {
                AlertSubscribeRow(
                    emoji = "🏷️",
                    title = "Brand-Specific Alerts",
                    subtitle = "Follow specific brands and get notified about their products",
                    checked = brandAlerts,
                    onCheckedChange = { brandAlerts = it },
                )
            }

            item { Spacer(Modifier.height(24.dp)) }

            item {
                Text(
                    "ACTIVE ALERTS",
                    color = Sdai.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Sdai.cardInner)
                        .border(1.dp, Sdai.border, RoundedCornerShape(16.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Sdai.primary.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.NotificationsNone,
                                contentDescription = null,
                                tint = Sdai.primary,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("No active alerts", color = Sdai.ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Subscribe above to receive food safety\nalerts and product recall notifications.",
                            color = Sdai.muted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            item {
                Text(
                    "Alert notifications require the app to be installed and notification permission granted. Push delivery depends on your device battery settings.",
                    color = Sdai.mutedDeep,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun AlertSubscribeRow(
    emoji: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Sdai.cardInner)
            .border(1.dp, if (checked) Sdai.primary.copy(alpha = 0.3f) else Sdai.border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Sdai.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Sdai.muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Sdai.onPrimary,
                checkedTrackColor = Sdai.primary,
            ),
        )
    }
}
