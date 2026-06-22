package com.sdai.news.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sdai.news.data.Article
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.viewmodel.UnifiedFeedViewModel

data class CategoryDef(
    val label: String,
    val topic: String,
    val icon: ImageVector,
    val color: Color,
)

private val CATEGORIES = listOf(
    CategoryDef("Technology", "tech", Icons.Outlined.Computer, Color(0xFF6366F1)),
    CategoryDef("Sports", "sports", Icons.Outlined.SportsEsports, Color(0xFF22C55E)),
    CategoryDef("Politics", "politics", Icons.Outlined.Policy, Color(0xFFEF4444)),
    CategoryDef("Science", "science", Icons.Outlined.Science, Color(0xFF06B6D4)),
    CategoryDef("Health", "health", Icons.Outlined.LocalHospital, Color(0xFFF59E0B)),
    CategoryDef("Business", "business", Icons.AutoMirrored.Outlined.TrendingUp, Color(0xFF8B5CF6)),
    CategoryDef("Anime", "anime", Icons.Outlined.Category, Color(0xFFEC4899)),
    CategoryDef("Breaking", "breaking", Icons.Outlined.Poll, Color(0xFF10B981)),
)

@Composable
fun CategoriesScreen(
    onOpenArticle: (Article) -> Unit,
    vm: UnifiedFeedViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val articles by vm.feed.collectAsState()
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .background(Sdai.background)
            .statusBarsPadding(),
    ) {
        Text(
            "Categories",
            color = Sdai.ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        if (selectedCategory == null) {
            Text(
                "Browse news by topic",
                color = Sdai.muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(CATEGORIES) { cat ->
                    val count = articles.count { it.category.equals(cat.topic, ignoreCase = true) }
                    CategoryCard(
                        category = cat,
                        articleCount = count,
                        onClick = {
                            vm.setTopic(cat.topic)
                            selectedCategory = cat.topic
                        },
                    )
                }
            }
        } else {
            val label = CATEGORIES.firstOrNull { it.topic == selectedCategory }?.label ?: selectedCategory
            val filtered = articles.filter {
                it.category.equals(selectedCategory, ignoreCase = true)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "← Back",
                    color = Sdai.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable {
                        vm.setTopic("all")
                        selectedCategory = null
                    },
                )
                Spacer(Modifier.weight(1f))
                Text(label ?: "", color = Sdai.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No stories in this category yet", color = Sdai.muted)
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                ) {
                    items(filtered.size) { idx ->
                        val article = filtered[idx]
                        CategoryArticleRow(article = article, onClick = { onOpenArticle(article) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(category: CategoryDef, articleCount: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Sdai.cardInner)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(category.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(category.icon, contentDescription = null, tint = category.color, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(category.label, color = Sdai.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            "$articleCount ${if (articleCount == 1) "story" else "stories"}",
            color = Sdai.muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun CategoryArticleRow(article: Article, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Sdai.cardInner)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                article.title,
                color = Sdai.ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            Text(article.source, color = Sdai.muted, fontSize = 12.sp)
        }
    }
}
