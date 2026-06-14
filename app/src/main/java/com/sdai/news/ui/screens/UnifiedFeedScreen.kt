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
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sdai.news.R
import com.sdai.news.SDAINewsApp
import com.sdai.news.data.Article
import com.sdai.news.ui.components.ArticleCard
import com.sdai.news.ui.components.WellnessOverlay
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.util.Share
import com.sdai.news.viewmodel.UnifiedFeedViewModel

private val CATEGORIES = listOf(
    "All" to null,
    "Sports" to "sports",
    "Politics" to "politics",
    "Tech" to "tech",
    "World" to "world",
    "Science" to "science",
    "Health" to "health",
    "Climate" to "climate",
    "Business" to "business",
    "Entertainment" to "entertainment",
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnifiedFeedScreen(
    vm: UnifiedFeedViewModel,
    section: String?,
    onOpenArticle: (Article) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allArticles by vm.filteredAll.collectAsState()
    val breakingArticles by vm.breakingArticles.collectAsState()
    val worldArticles by vm.worldArticles.collectAsState()
    val nationalArticles by vm.nationalArticles.collectAsState()
    val regionalArticles by vm.regionalArticles.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val categoryFilter by vm.categoryFilter.collectAsState()
    val ctx = LocalContext.current

    val currentArticles: List<Article> = when (section) {
        "breaking" -> breakingArticles
        "world" -> worldArticles
        "national" -> nationalArticles
        "regional" -> regionalArticles
        else -> allArticles
    }

    val wellnessEnabled by SDAINewsApp.get().prefs.wellnessEnabled
        .collectAsState(initial = true)

    Box(modifier.fillMaxSize().background(Sdai.background)) {
        if (currentArticles.isNotEmpty()) {
            val articles = currentArticles
            val pagerState = rememberPagerState(pageCount = { articles.size })

            LaunchedEffect(pagerState, articles.size) {
                snapshotFlow { pagerState.currentPage }.collect { page ->
                    if (articles.size - page <= 5) {
                        vm.refreshAll()
                    }
                }
            }

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
                    isBookmarked = false,
                    onShare = {
                        Share.article(
                            context = ctx,
                            title = article.title,
                            url = article.url,
                            source = article.source,
                            imageUrl = article.imageUrl,
                        )
                    },
                    onBookmarkToggle = { },
                    onReport = {
                        val mail = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:" + ctx.getString(R.string.contact_email_value))
                            putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.report_email_subject))
                            putExtra(
                                Intent.EXTRA_TEXT,
                                ctx.getString(R.string.report_email_body, article.title, article.source, article.url),
                            )
                        }
                        runCatching { ctx.startActivity(mail) }
                    },
                    modifier = Modifier.pointerInput(article.id) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (dragAmount < -25) onOpenArticle(article)
                        }
                    },
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isRefreshing) {
                        CircularProgressIndicator(color = Sdai.primary, strokeWidth = 2.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Loading news…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Sdai.inkSubtle,
                        )
                    } else {
                        Text(
                            "Pull down to load latest news",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Sdai.muted,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            if (section == null) {
                CategoryChipRow(
                    categories = CATEGORIES,
                    selectedCategory = categoryFilter,
                    onSelect = { vm.setCategoryFilter(it) },
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.refreshAll() }) {
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
        }

        WellnessOverlay(enabled = wellnessEnabled)
    }
}

@Composable
private fun CategoryChipRow(
    categories: List<Pair<String, String?>>,
    selectedCategory: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(categories.size) { index ->
            val (label, value) = categories[index]
            val isSelected = selectedCategory == value
            val bg = if (isSelected) Sdai.primary else Color.Black.copy(alpha = 0.55f)
            val fg = if (isSelected) Color.Black else Sdai.ink
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = label,
                    color = fg,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}
