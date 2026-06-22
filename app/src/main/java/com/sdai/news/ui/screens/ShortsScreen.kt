package com.sdai.news.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.sdai.news.data.Article
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.util.Share
import com.sdai.news.viewmodel.UnifiedFeedViewModel

private fun isYouTubeUrl(a: Article) =
    a.isVideo || a.url.contains("youtube.com") || a.url.contains("youtu.be/")

private val SHORTS_TABS = listOf("For You", "Trending")

@Composable
fun ShortsScreen(
    onOpenArticle: (Article) -> Unit,
    vm: UnifiedFeedViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val allArticles by vm.feedVideos.collectAsState()
    val ctx = LocalContext.current

    // Trending = same pool sorted by weight descending.
    val allVideos = allArticles
    val trendingVideos = allVideos.sortedByDescending { it.weight }

    var selectedTab by remember { mutableIntStateOf(0) }
    val videos = if (selectedTab == 0) allVideos else trendingVideos

    if (videos.isEmpty()) {
        Box(
            modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("No shorts yet", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("Videos will appear here as they're fetched", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { videos.size })
    val likedIds = remember { mutableStateMapOf<String, Boolean>() }
    val savedIds = remember { mutableStateMapOf<String, Boolean>() }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            val article = videos[index]
            ShortVideoCard(
                article = article,
                isLiked = likedIds[article.id] == true,
                isSaved = savedIds[article.id] == true,
                onOpen = {
                    vm.onArticleOpened(article)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(article.url))
                    runCatching { ctx.startActivity(intent) }.onFailure { onOpenArticle(article) }
                },
                onLike = { likedIds[article.id] = !(likedIds[article.id] ?: false) },
                onSave = { savedIds[article.id] = !(savedIds[article.id] ?: false) },
                onShare = {
                    vm.onArticleShared(article)
                    Share.article(ctx, article.title, article.url, article.source, article.imageUrl)
                },
            )
        }

        // Top bar — tabs + search
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            SHORTS_TABS.forEachIndexed { i, label ->
                val sel = i == selectedTab
                Text(
                    label,
                    color = if (sel) Color.White else Color.White.copy(alpha = 0.5f),
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clickable { selectedTab = i }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
                if (i == 0) {
                    // Simple underline indicator
                    Spacer(Modifier.width(4.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Outlined.Search,
                contentDescription = "Search",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ShortVideoCard(
    article: Article,
    isLiked: Boolean,
    isSaved: Boolean,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    val mockLikes = remember(article.id) {
        val n = (article.id.hashCode().toLong() and 0x1FFF) % 8000 + 200
        if (n >= 1000) "${n / 1000}.${(n % 1000) / 100}K" else "$n"
    }
    val mockComments = remember(article.id) {
        ((article.id.hashCode().toLong() and 0xFF) % 500 + 10).toString()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Thumbnail / background
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

        // Full-screen dark scrim
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                    startY = 400f,
                ),
            ),
        )

        // Center play button
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }

        // Right sidebar — engagement buttons
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SidebarAction(
                icon = { Icon(if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null, tint = if (isLiked) Color.Red else Color.White, modifier = Modifier.size(28.dp)) },
                label = if (isLiked) "${(mockLikes.replace("K", "").toFloatOrNull()?.plus(0.1f) ?: 1f)}K" else mockLikes,
                onClick = onLike,
            )
            SidebarAction(
                icon = { Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Color.White, modifier = Modifier.size(26.dp)) },
                label = mockComments,
                onClick = {},
            )
            SidebarAction(
                icon = { Icon(Icons.Outlined.Share, null, tint = Color.White, modifier = Modifier.size(26.dp)) },
                label = "Share",
                onClick = onShare,
            )
            SidebarAction(
                icon = {
                    Icon(
                        if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        null,
                        tint = if (isSaved) Sdai.primary else Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                },
                label = "Save",
                onClick = onSave,
            )
        }

        // Bottom info bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 72.dp, bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            // Source avatar + name row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Sdai.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        article.source.firstOrNull()?.uppercaseChar()?.toString() ?: "N",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(article.source, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                article.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (article.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    article.description,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Sdai.primary)
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text("Watch on YouTube", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SidebarAction(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        icon()
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
