package com.sdai.news.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sdai.news.R
import com.sdai.news.data.Article
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.util.ReadingTime
import com.sdai.news.util.TimeAgo

/**
 * One full-screen swipe card — edge-to-edge hero on top, text cluster
 * + footer pinned to the bottom by an `Arrangement.SpaceBetween` on
 * the content column. The card now occupies the full pager page on
 * any aspect ratio.
 */
@Composable
fun ArticleCard(
    article: Article,
    isBookmarked: Boolean,
    onShare: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Sdai.background),
    ) {

        // ── Hero — edge-to-edge, rounded only at the bottom so it
        // flows under the status bar like Inshorts / Reels.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(Sdai.cardInner)
        ) {
            if (!article.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // Bottom fade so the source row reads cleanly against
                // the image edge.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Sdai.background.copy(alpha = 0.85f),
                                ),
                                startY = 220f,
                            )
                        )
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = article.source.take(1).uppercase(),
                        style = MaterialTheme.typography.displayLarge,
                        color = Sdai.primary,
                    )
                }
            }
        }

        // ── Content column — takes the remaining height; top cluster
        // and bottom cluster anchored to opposite edges.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top cluster
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SourceRow(article)
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Sdai.ink,
                    fontWeight = FontWeight.SemiBold,
                )
                SummaryBlock(article)
            }

            // Bottom cluster — footer + hint pinned to bottom edge
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FooterRow(
                    article = article,
                    isBookmarked = isBookmarked,
                    onBookmarkToggle = onBookmarkToggle,
                    onShare = onShare,
                    onReport = onReport,
                )
                Text(
                    text = stringResource(R.string.hint_swipe),
                    style = MaterialTheme.typography.labelSmall,
                    color = Sdai.mutedDeep,
                )
            }
        }
    }
}

@Composable
private fun SourceRow(article: Article) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(28.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Sdai.cardInner),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                article.source.take(1).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Sdai.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = article.source,
            style = MaterialTheme.typography.titleMedium,
            color = Sdai.ink,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "·  " + TimeAgo.format(article.publishedAtMillis),
            style = MaterialTheme.typography.bodyMedium,
            color = Sdai.muted,
        )
    }
}

@Composable
private fun SummaryBlock(article: Article) {
    val summaryText = article.summary?.takeIf { it.isNotBlank() } ?: article.description
    val summaryReady = !article.summary.isNullOrBlank()
    if (summaryText.isBlank()) return

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Sdai.cardInner)
            .border(1.dp, Sdai.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(
                if (summaryReady) R.string.badge_ai_summary
                else R.string.badge_quick_read
            ).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (summaryReady) Sdai.primary else Sdai.muted,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = summaryText,
            style = MaterialTheme.typography.bodyLarge,
            color = Sdai.inkSubtle,
        )
    }
}

@Composable
private fun FooterRow(
    article: Article,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
) {
    val summary = article.summary?.takeIf { it.isNotBlank() } ?: article.description
    val mins = ReadingTime.minutes(summary.ifBlank { article.title })
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            article.category?.takeIf { it.isNotBlank() }?.let { cat ->
                Chip(text = cat.replaceFirstChar { it.uppercase() })
                Spacer(Modifier.width(8.dp))
            }
            Chip(text = "$mins min read", muted = true)
        }
        Row {
            IconButton(onClick = onBookmarkToggle) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Outlined.Bookmark
                                  else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Bookmark",
                    tint = if (isBookmarked) Sdai.primary else Sdai.ink,
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = "Share", tint = Sdai.ink)
            }
            IconButton(onClick = onReport) {
                Icon(
                    Icons.Outlined.Flag,
                    contentDescription = "Report content",
                    tint = Sdai.muted,
                )
            }
        }
    }
}

@Composable
private fun Chip(text: String, muted: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Sdai.cardInner)
            .border(1.dp, Sdai.border, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (muted) Sdai.muted else Sdai.inkSubtle,
            fontWeight = FontWeight.Medium,
        )
    }
}
