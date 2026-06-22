package com.sdai.news.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sdai.news.data.Article
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.viewmodel.UnifiedFeedViewModel

@Composable
fun MainScreen(
    onOpenArticle: (Article) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenContact: () -> Unit,
    onOpenDisclaimer: () -> Unit,
    onOpenLocationPicker: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenScanner: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onClearAllData: () -> Unit = {},
) {
    val vm: UnifiedFeedViewModel = viewModel()
    // Tabs: 0=Home, 1=News, 2=Alerts, 3=Profile  (Scan is center CTA, not a tab)
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val open: (Article) -> Unit = { article ->
        vm.onArticleOpened(article)
        if (article.isVideo || article.url.contains("youtube.com") || article.url.contains("youtu.be/")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(article.url))
            runCatching { context.startActivity(intent) }.onFailure { onOpenArticle(article) }
        } else {
            onOpenArticle(article)
        }
    }

    Scaffold(
        containerColor = Sdai.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AwarelyBottomBar(
                selectedTab = selectedTab,
                onTabSelect = { selectedTab = it },
                onOpenScanner = onOpenScanner,
            )
        },
    ) { innerPadding ->
        when (selectedTab) {
            0 -> AwarelyHomeScreen(
                vm = vm,
                onOpenArticle = open,
                onOpenScanner = onOpenScanner,
                modifier = Modifier.padding(innerPadding),
            )
            1 -> BriefingScreen(
                vm = vm,
                onOpenArticle = open,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            2 -> AlertsScreen(
                modifier = Modifier.padding(innerPadding),
            )
            3 -> ProfileScreen(
                vm = vm,
                onOpenSettings = onOpenSettings,
                onOpenBookmarks = onOpenBookmarks,
                onOpenContact = onOpenContact,
                onOpenLocationPicker = onOpenLocationPicker,
                onOpenHistory = onOpenHistory,
                onClearAllData = onClearAllData,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun AwarelyBottomBar(
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    onOpenScanner: () -> Unit,
) {
    NavigationBar(
        containerColor = Sdai.surface,
        contentColor = Sdai.ink,
    ) {
        NavigationBarItem(
            icon = { Icon(if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home, null, modifier = Modifier.size(22.dp)) },
            label = { Text("Home", fontSize = 10.sp) },
            selected = selectedTab == 0,
            onClick = { onTabSelect(0) },
            colors = navItemColors(),
        )
        NavigationBarItem(
            icon = { Icon(if (selectedTab == 1) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(22.dp)) },
            label = { Text("Briefing", fontSize = 10.sp) },
            selected = selectedTab == 1,
            onClick = { onTabSelect(1) },
            colors = navItemColors(),
        )
        // Centre Scan CTA — gradient circle, navigates to ScanScreen (not a tab)
        NavigationBarItem(
            icon = {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF1D4ED8)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.QrCodeScanner, "Scan to Know", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            },
            label = { Text("Scan", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = onOpenScanner,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                unselectedIconColor = Color.White,
                selectedTextColor = Color(0xFF059669),
                unselectedTextColor = Sdai.muted,
                indicatorColor = Color.Transparent,
            ),
        )
        NavigationBarItem(
            icon = { Icon(if (selectedTab == 2) Icons.Filled.Notifications else Icons.Outlined.Notifications, null, modifier = Modifier.size(22.dp)) },
            label = { Text("Alerts", fontSize = 10.sp) },
            selected = selectedTab == 2,
            onClick = { onTabSelect(2) },
            colors = navItemColors(),
        )
        NavigationBarItem(
            icon = { Icon(if (selectedTab == 3) Icons.Filled.Person else Icons.Outlined.Person, null, modifier = Modifier.size(22.dp)) },
            label = { Text("Profile", fontSize = 10.sp) },
            selected = selectedTab == 3,
            onClick = { onTabSelect(3) },
            colors = navItemColors(),
        )
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Sdai.primary,
    selectedTextColor = Sdai.primary,
    unselectedIconColor = Sdai.muted,
    unselectedTextColor = Sdai.muted,
    indicatorColor = Sdai.primary.copy(alpha = 0.12f),
)
