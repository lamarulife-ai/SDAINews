package com.sdai.news.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sdai.news.data.Article
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.viewmodel.FeedViewModel
import com.sdai.news.viewmodel.UnifiedFeedViewModel

private data class Tab(val label: String, val icon: ImageVector, val section: String?)

private val TABS = listOf(
    Tab("All", Icons.Outlined.Home, null),
    Tab("Breaking", Icons.Outlined.Notifications, "breaking"),
    Tab("AI", Icons.Outlined.Memory, null),
    Tab("National", Icons.Outlined.Flag, "national"),
    Tab("Regional", Icons.Outlined.Place, "regional"),
)

@Composable
fun MainScreen(
    onOpenArticle: (Article) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val unifiedVm: UnifiedFeedViewModel = viewModel()
    val aiVm: FeedViewModel = viewModel()
    val isRefreshing by unifiedVm.isRefreshing.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Sdai.background,
                contentColor = Sdai.ink,
                tonalElevation = 0.dp,
            ) {
                TABS.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(tab.icon, contentDescription = tab.label)
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Sdai.primary,
                            selectedTextColor = Sdai.primary,
                            unselectedIconColor = Sdai.muted,
                            unselectedTextColor = Sdai.muted,
                            indicatorColor = Sdai.primary.copy(alpha = 0.12f),
                        ),
                    )
                }
            }
        },
        containerColor = Sdai.background,
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                0 -> UnifiedFeedScreen(
                    vm = unifiedVm,
                    section = null,
                    onOpenArticle = onOpenArticle,
                    onOpenBookmarks = onOpenBookmarks,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.fillMaxSize(),
                )
                1 -> UnifiedFeedScreen(
                    vm = unifiedVm,
                    section = "breaking",
                    onOpenArticle = onOpenArticle,
                    onOpenBookmarks = onOpenBookmarks,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.fillMaxSize(),
                )
                2 -> FeedScreen(
                    onOpenArticle = onOpenArticle,
                    onOpenBookmarks = onOpenBookmarks,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.fillMaxSize(),
                )
                3 -> UnifiedFeedScreen(
                    vm = unifiedVm,
                    section = "national",
                    onOpenArticle = onOpenArticle,
                    onOpenBookmarks = onOpenBookmarks,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.fillMaxSize(),
                )
                4 -> UnifiedFeedScreen(
                    vm = unifiedVm,
                    section = "regional",
                    onOpenArticle = onOpenArticle,
                    onOpenBookmarks = onOpenBookmarks,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
