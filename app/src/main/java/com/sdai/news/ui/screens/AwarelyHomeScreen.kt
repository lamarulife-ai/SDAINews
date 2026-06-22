package com.sdai.news.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.sdai.news.R
import com.sdai.news.data.Article
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.util.TimeAgo
import com.sdai.news.viewmodel.FeedMode
import com.sdai.news.viewmodel.FeedRegion
import com.sdai.news.viewmodel.UnifiedFeedViewModel
import java.util.Calendar

private data class HealthCategory(val label: String, val emoji: String, val keys: Set<String>)

private val HEALTH_CATEGORIES = listOf(
    HealthCategory("For You", "✨", emptySet()),
    HealthCategory("Health", "❤️", setOf("health", "nutrition", "food", "diet", "medical", "wellness")),
    HealthCategory("Kids", "🍼", setOf("kids", "children", "child", "baby", "pediatric", "school")),
    HealthCategory("Safety", "🛡️", setOf("safety", "recall", "warning", "alert", "contamination", "ban")),
    HealthCategory("Science", "🔬", setOf("science", "research", "study", "clinical", "trial", "discovery")),
    HealthCategory("Eco", "🌿", setOf("climate", "environment", "eco", "green", "sustainable", "organic")),
    HealthCategory("Tech", "💻", setOf("tech", "technology", "ai", "software", "digital", "cyber")),
    HealthCategory("Business", "💼", setOf("business", "economy", "finance", "market", "company", "trade")),
)

@Composable
fun AwarelyHomeScreen(
    vm: UnifiedFeedViewModel,
    onOpenArticle: (Article) -> Unit,
    onOpenScanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allArticles by vm.feed.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    var selectedCat by remember { mutableIntStateOf(0) }

    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val currentHour = remember(nowMs) { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(currentHour) {
        when {
            currentHour < 12 -> "Good Morning"
            currentHour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(60_000); nowMs = System.currentTimeMillis() }
    }

    LaunchedEffect(Unit) {
        vm.setMode(FeedMode.TEXT)
        vm.setRegion(FeedRegion.GLOBAL)
        vm.setTopic("all")
    }

    val displayArticles = remember(allArticles, selectedCat) {
        val cat = HEALTH_CATEGORIES[selectedCat]
        if (cat.keys.isEmpty()) {
            allArticles.sortedByDescending { it.weight }
        } else {
            val haystack = { a: Article ->
                "${a.category} ${a.title} ${a.source} ${a.description}".lowercase()
            }
            val priority = allArticles.filter { a -> cat.keys.any { k -> haystack(a).contains(k) } }
            val rest = allArticles.filter { a -> cat.keys.none { k -> haystack(a).contains(k) } }
            (priority + rest).distinctBy { it.id }
        }.take(25)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Sdai.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.awarely_logo),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Awarely", color = Sdai.ink, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text(greeting, color = Sdai.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            IconButton(onClick = { vm.refreshAll() }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = Sdai.ink, modifier = Modifier.size(22.dp))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item(key = "scan_cta") {
                ScanCtaCard(onOpenScanner = onOpenScanner)
            }

            item(key = "category_chips") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HEALTH_CATEGORIES.forEachIndexed { idx, cat ->
                        val sel = idx == selectedCat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (sel) Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF1D4ED8)))
                                    else Brush.linearGradient(listOf(Sdai.cardInner, Sdai.cardInner)),
                                )
                                .then(
                                    if (sel) Modifier.border(1.5.dp, Color(0xFF34D399).copy(alpha = 0.5f), RoundedCornerShape(50))
                                    else Modifier
                                )
                                .clickable { selectedCat = idx }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text(
                                "${cat.emoji} ${cat.label}",
                                color = if (sel) Color.White else Sdai.muted,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            if (displayArticles.isEmpty()) {
                item(key = "empty") {
                    Box(
                        Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(color = Color(0xFF059669), modifier = Modifier.size(32.dp))
                        } else {
                            Text("Pull down to refresh", color = Sdai.muted, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                item(key = "section_header") {
                    Text(
                        "Top Stories",
                        color = Sdai.ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                items(displayArticles, key = { "home_${it.id}" }) { article ->
                    HomeArticleRow(
                        article = article,
                        onClick = { onOpenArticle(article) },
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanCtaCard(onOpenScanner: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF1D4ED8))))
            .clickable(onClick = onOpenScanner)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Scan to Know", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Spacer(Modifier.height(3.dp))
            Text(
                "Instantly know ingredients, additives & safety score of any food product.",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.18f))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("Scan Now  →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun HomeArticleRow(
    article: Article,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Sdai.cardInner)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!article.imageUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Sdai.background),
            ) {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(article.imageUrl)
                            .crossfade(true)
                            .build(),
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(10.dp))
        }
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
                article.category?.takeIf { it.isNotBlank() }?.let { cat ->
                    Text(
                        cat.replaceFirstChar { it.uppercase() },
                        color = Color(0xFF059669),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF059669).copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                    Text("  •  ", color = Sdai.mutedDeep, fontSize = 11.sp)
                }
                Text(TimeAgo.format(article.publishedAtMillis), color = Sdai.muted, fontSize = 11.sp)
            }
        }
    }
}
