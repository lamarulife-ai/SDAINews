package com.sdai.news.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sdai.news.R
import com.sdai.news.SDAINewsApp
import com.sdai.news.data.Article
import com.sdai.news.ui.components.ArticleCard
import com.sdai.news.ui.components.WellnessOverlay
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.util.Share
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.sdai.news.viewmodel.FeedTier
import com.sdai.news.viewmodel.FeedUiState
import com.sdai.news.viewmodel.FeedViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    onOpenArticle: (Article) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: FeedViewModel = viewModel()
    val status by vm.status.collectAsState()
    val bookmarkIds by vm.bookmarkIds.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val selectedTier by vm.selectedTier.collectAsState()
    val ctx = LocalContext.current

    val wellnessEnabled by SDAINewsApp.get().prefs.wellnessEnabled
        .collectAsState(initial = true)

    Box(Modifier.fillMaxSize().background(Sdai.background)) {
        when (val s = status) {
            FeedUiState.Loading -> LoadingState()
            is FeedUiState.Error -> ErrorState(message = s.message, onRetry = { vm.refresh() })
            is FeedUiState.Ready -> {
                val articles = s.articles
                val pagerState = rememberPagerState(pageCount = { articles.size })

                // Trigger another batch when the user is within 5 cards
                // of the end so the feed never feels exhausted. The VM
                // guards against overlapping refreshes.
                LaunchedEffect(pagerState, articles.size) {
                    snapshotFlow { pagerState.currentPage }.collect { page ->
                        if (articles.size - page <= 5) vm.loadMore()
                    }
                }

                // Scroll-to-top is decoupled from the VM via a one-shot
                // events flow, so the top-bar button can drive pager
                // state from outside this branch.
                LaunchedEffect(pagerState) {
                    vm.scrollToTopRequests.collect {
                        pagerState.animateScrollToPage(0)
                    }
                }

                // A low positional threshold turns the pager into a
                // Reels-style flick — a short upward swipe (15% of the
                // page) is enough to commit to the next article; the
                // velocity-aware decay handles fast flicks naturally.
                val flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(1),
                    snapPositionalThreshold = 0.15f,
                )

                VerticalPager(
                    state = pagerState,
                    flingBehavior = flingBehavior,
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    val article = articles[index]
                    ArticleCard(
                        article = article,
                        isBookmarked = bookmarkIds.contains(article.id),
                        onShare = {
                            Share.article(
                                context = ctx,
                                title = article.title,
                                url = article.url,
                                source = article.source,
                                imageUrl = article.imageUrl,
                            )
                        },
                        onBookmarkToggle = { vm.toggleBookmark(article) },
                        onReport = {
                            // Pre-filled email so publishers / users can
                            // flag inaccurate, offensive, or rights-
                            // infringing content. Body includes the
                            // article URL for traceability.
                            val mail = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse(
                                    "mailto:" + ctx.getString(R.string.contact_email_value)
                                )
                                putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    ctx.getString(R.string.report_email_subject),
                                )
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    ctx.getString(
                                        R.string.report_email_body,
                                        article.title,
                                        article.source,
                                        article.url,
                                    ),
                                )
                            }
                            runCatching { ctx.startActivity(mail) }
                        },
                        // Detect a left-swipe gesture on the card itself
                        // — VerticalPager only intercepts vertical drags.
                        modifier = Modifier.pointerInput(article.id) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (dragAmount < -25) onOpenArticle(article)
                            }
                        },
                    )
                }
            }
        }

        // Top app-bar — chip filter strip on the left, action icons
        // on the right, sharing a single row that overlays the top
        // of the pager. Pager touches outside this row still work.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TierChipRow(
                selected = selectedTier,
                onSelect = vm::selectTier,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { vm.refresh(scrollToTop = true) },
                enabled = !isRefreshing,
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Sdai.primary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = Sdai.ink)
                }
            }
            IconButton(onClick = onOpenBookmarks) {
                Icon(Icons.Outlined.Bookmarks, contentDescription = null, tint = Sdai.ink)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = Sdai.ink)
            }
        }

        WellnessOverlay(enabled = wellnessEnabled)
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Sdai.primary, strokeWidth = 2.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.feed_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = Sdai.inkSubtle,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.feed_error),
                style = MaterialTheme.typography.titleMedium,
                color = Sdai.danger,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = Sdai.inkSubtle,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onRetry) {
                Text("Retry", color = Sdai.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Horizontal scrolling row of FeedTier pills. The selected pill is
 * filled with the brand primary; others are translucent so they
 * read cleanly over hero images without a hard scrim.
 */
@Composable
private fun TierChipRow(
    selected: FeedTier,
    onSelect: (FeedTier) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(FeedTier.values()) { tier ->
            val isSelected = tier == selected
            val bg = if (isSelected) Sdai.primary else Color.Black.copy(alpha = 0.55f)
            val fg = if (isSelected) Color.Black else Sdai.ink
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable { onSelect(tier) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = tier.label,
                    color = fg,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

