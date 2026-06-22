package com.sdai.news.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.sdai.news.R
import com.sdai.news.SDAINewsApp
import com.sdai.news.data.Article
import com.sdai.news.ui.components.WellnessOverlay
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.util.Share
import com.sdai.news.util.TimeAgo
import com.sdai.news.viewmodel.FeedMode
import com.sdai.news.viewmodel.FeedRegion
import com.sdai.news.viewmodel.UnifiedFeedViewModel

// News-tab chip definitions — maps to VM region/topic filters.
private data class HomeChip(val label: String, val region: FeedRegion, val topic: String)

private val HOME_CHIPS = listOf(
    HomeChip("For You", FeedRegion.GLOBAL, "all"),
    HomeChip("Food Safety", FeedRegion.GLOBAL, "health"),
    HomeChip("Trending", FeedRegion.GLOBAL, "breaking"),
    HomeChip("Tech", FeedRegion.GLOBAL, "tech"),
    HomeChip("Science", FeedRegion.GLOBAL, "science"),
    HomeChip("Business", FeedRegion.GLOBAL, "business"),
    HomeChip("Anime", FeedRegion.GLOBAL, "anime"),
    HomeChip("Local", FeedRegion.LOCAL, "all"),
)

private fun isYouTubeArticle(a: Article) =
    a.isVideo || a.url.contains("youtube.com") || a.url.contains("youtu.be/")

// Derive a mock view count from the article id hash.
private fun mockViewCount(id: String): String {
    val n = (id.hashCode().toLong() and 0xFFFFFFFFL) % 9_000 + 1_000
    return if (n >= 1000) "${n / 1000}.${(n % 1000) / 100}K" else "$n"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedFeedScreen(
    vm: UnifiedFeedViewModel,
    onOpenArticle: (Article) -> Unit,
    onOpenMenu: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Home always shows TEXT articles; video row uses the separate always-on feedVideos.
    val articles by vm.feed.collectAsState()
    val videoArticles by vm.feedVideos.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val wellnessEnabled by SDAINewsApp.get().prefs.wellnessEnabled.collectAsState(initial = true)
    val ctx = LocalContext.current

    var selectedChip by remember { mutableIntStateOf(0) }

    val open: (Article) -> Unit = { a ->
        vm.onArticleOpened(a)
        if (isYouTubeArticle(a)) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(a.url))
            runCatching { ctx.startActivity(intent) }.onFailure { onOpenArticle(a) }
        } else {
            onOpenArticle(a)
        }
    }

    // Ensure home always operates in TEXT mode.
    LaunchedEffect(Unit) {
        vm.setMode(FeedMode.TEXT)
    }

    LaunchedEffect(selectedChip) {
        val chip = HOME_CHIPS[selectedChip]
        vm.setRegion(chip.region)
        vm.setTopic(chip.topic)
    }

    val displayArticles = articles

    val briefVideos = remember(videoArticles) { videoArticles.take(6) }
    val breakingArticle = remember(displayArticles) {
        displayArticles.firstOrNull { it.tier == "breaking" }
            ?: displayArticles.firstOrNull()
    }
    val textArticles = remember(displayArticles, breakingArticle) {
        displayArticles
            .filter { it.id != breakingArticle?.id }
            .take(20)
    }

    Column(modifier.fillMaxSize().background(Sdai.background)) {
        HomeTopBar(onRefresh = { vm.refreshAll() }, modifier = Modifier.fillMaxWidth())
        HomeCategoryChips(
            chips = HOME_CHIPS,
            selectedIndex = selectedChip,
            onChipSelect = { selectedChip = it },
        )
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { vm.refreshAll() },
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            // Trigger a refresh if both feeds are empty and we're not already loading.
            if (articles.isEmpty() && briefVideos.isEmpty() && !isRefreshing) {
                LaunchedEffect(Unit) { vm.refreshAll() }
            }

            // Show full-screen spinner only while initially loading (nothing at all yet).
            if (articles.isEmpty() && briefVideos.isEmpty() && isRefreshing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = Sdai.primary,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Fetching the latest news…", color = Sdai.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                // Render the feed regardless — brief row uses its own video fallback chain
                // so it always shows something even when local text articles are empty.
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    // Breaking / Hero card
                    breakingArticle?.let { hero ->
                        item(key = "hero_${hero.id}") {
                            HeroCard(article = hero, onClick = { open(hero) })
                        }
                    }

                    // AI Daily Brief section — always renders if videos available.
                    if (briefVideos.isNotEmpty()) {
                        item(key = "brief_header") {
                            SectionHeader(
                                title = "AI Daily Brief – 30s",
                                actionLabel = "See All",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                        item(key = "brief_row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(briefVideos, key = { it.id }) { video ->
                                    AiDailyBriefCard(article = video, onClick = { open(video) })
                                }
                            }
                        }
                    }

                    // Top Headlines — empty placeholder when no text articles (e.g. Local tab
                    // without location configured, or sparse regional content).
                    if (textArticles.isEmpty()) {
                        item(key = "no_headlines") {
                            Box(
                                Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "No local headlines yet\nPull down to refresh",
                                    color = Sdai.muted,
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        item(key = "headlines_header") {
                            SectionHeader(
                                title = "Top Headlines",
                                actionLabel = null,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                        items(textArticles, key = { it.id }) { article ->
                            TopHeadlineRow(
                                article = article,
                                onClick = { open(article) },
                                onShare = {
                                    vm.onArticleShared(article)
                                    Share.article(ctx, article.title, article.url, article.source, article.imageUrl)
                                },
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            WellnessOverlay(enabled = wellnessEnabled)
        }
    }
}

@Composable
private fun HomeTopBar(onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Sdai.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.awarely_logo),
            contentDescription = null,
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.weight(1f)) {
            Text("News", color = Sdai.ink, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = 0.sp)
        }
        IconButton(onClick = onRefresh, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = Sdai.ink, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun HomeCategoryChips(
    chips: List<HomeChip>,
    selectedIndex: Int,
    onChipSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEachIndexed { index, chip ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)))
                        else Brush.linearGradient(listOf(Sdai.cardInner, Sdai.cardInner)),
                    )
                    .then(
                        if (selected) Modifier.border(1.5.dp, Color(0xFF818CF8), RoundedCornerShape(50))
                        else Modifier
                    )
                    .clickable { onChipSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Text(
                    chip.label,
                    color = if (selected) Color.White else Sdai.muted,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Sdai.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (actionLabel != null) {
            Text(
                "$actionLabel ›",
                color = Sdai.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun HeroCard(article: Article, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Sdai.cardInner)
            .clickable(onClick = onClick),
    ) {
        // Hero image — fills the whole card
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(article.imageUrl)
                .crossfade(true)
                .build(),
        )
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
        )

        // Deep gradient scrim from middle to bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.2f),
                        1f to Color.Black.copy(alpha = 0.88f),
                    ),
                ),
        )

        // BREAKING badge — always visible for top hero
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFEF4444))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text("BREAKING", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
        }

        // Title + metadata row (with play button on the right)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        ) {
            Text(
                article.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${mockViewCount(article.id)} views",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                )
                Text("  •  ", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                Text(
                    TimeAgo.format(article.publishedAtMillis),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.weight(1f))
                // Play button — always shown (AI audio / video)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AiDailyBriefCard(article: Article, onClick: () -> Unit) {
    val mockDuration = remember(article.id) {
        val sec = (article.id.hashCode().toLong() and 0xFF) % 50 + 15
        "0:${sec.toString().padStart(2, '0')}"
    }
    val mockLikes = remember(article.id) {
        val n = (article.id.hashCode().toLong() and 0xFFF) % 3000 + 500
        if (n >= 1000) "${n / 1000}.${(n % 1000) / 100}K" else "$n"
    }

    Box(
        modifier = Modifier
            .width(130.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Sdai.cardInner)
            .clickable(onClick = onClick),
    ) {
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(article.imageUrl)
                .crossfade(true)
                .build(),
        )
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Dark scrim
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))),
            ),
        )
        // Duration badge
        Box(
            modifier = Modifier
                .padding(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        ) {
            Text(mockDuration, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
        // Title + likes
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
        ) {
            Text(
                article.title,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text("❤ $mockLikes", color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp)
        }
    }
}

@Composable
private fun TopHeadlineRow(
    article: Article,
    onClick: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var bookmarked by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Sdai.cardInner)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(article.imageUrl)
                .crossfade(true)
                .build(),
        )
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Sdai.background),
        ) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                article.title,
                color = Sdai.ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Category pill
                article.category?.takeIf { it.isNotBlank() }?.let { cat ->
                    Text(
                        cat.replaceFirstChar { it.uppercase() },
                        color = Sdai.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Sdai.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                    Text("  •  ", color = Sdai.mutedDeep, fontSize = 11.sp)
                }
                Text(TimeAgo.format(article.publishedAtMillis), color = Sdai.muted, fontSize = 11.sp)
            }
        }
        IconButton(
            onClick = { bookmarked = !bookmarked },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = "Bookmark",
                tint = if (bookmarked) Sdai.primary else Sdai.muted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
