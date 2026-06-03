package com.sdai.news.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sdai.news.R
import com.sdai.news.data.Article
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.util.ReadingTime
import com.sdai.news.util.TimeAgo

/**
 * Full-screen swipe card with a unified vertical rhythm — image and
 * text variants share the same outer Box (background gradient + tier
 * glow), the same metadata header, the same tier pill + divider, and
 * the same bottom action bar. Only the **middle "hero" slot** differs:
 *
 *  - With image → 45 %-of-viewport cropped photo with a bottom scrim.
 *  - Without image → low-opacity publisher-letter watermark + a
 *    quote-styled all-caps headline ("editorial poster").
 *
 * Keeping the anchored shell identical means swiping between an image
 * card and a text card feels like a single continuous feed — none of
 * the header / footer / chrome elements move under the user's eye.
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
    val accent = accentForTier(article.tier)
    val letter = article.source.firstLetterUpper()
    val domainCaps = domainFromUrl(article.url).uppercase()
    val hasImage = !article.imageUrl.isNullOrBlank()
    val description = article.summary?.takeIf { it.isNotBlank() }
        ?: article.description.takeIf { it.isNotBlank() }
    val mins = ReadingTime.minutes(description ?: article.title)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Sdai.background,
                    0.5f to Sdai.background,
                    1f to Color.Black,
                )
            )
    ) {
        // ── Tier glow (always) — soft radial in the lower-right.
        //   On image cards we run it dimmer so it doesn't compete
        //   with the hero photo's colors.
        val glowAlpha = if (hasImage) 0.14f else 0.28f
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = glowAlpha),
                            accent.copy(alpha = glowAlpha * 0.25f),
                            Color.Transparent,
                        ),
                        center = Offset(maxWidth.value * 2.6f, maxHeight.value * 2.8f),
                        radius = maxWidth.value * 2.4f,
                    )
                )
        )

        // ── Watermark — text-only cards. The huge low-opacity letter
        //   sits behind the foreground content in the bottom-right.
        if (!hasImage) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 140.dp)
                    .offset(x = 30.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Text(
                    text = letter,
                    fontSize = 360.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 320.sp,
                    color = accent.copy(alpha = 0.12f),
                )
            }
        }

        // ── Foreground content. One column for the entire card so the
        //   header / hero / body / footer stack on the same horizontal
        //   grid regardless of variant.
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 56.dp, bottom = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                EditorialHeader(
                    letter = letter,
                    domainCaps = domainCaps,
                    sourceName = article.source,
                    publishedAtMillis = article.publishedAtMillis,
                    accent = accent,
                )

                Spacer(Modifier.height(18.dp))

                // ── Hero slot ────────────────────────────────────────
                if (hasImage) {
                    ImageHero(
                        imageUrl = article.imageUrl!!,
                        accent = accent,
                    )
                    Spacer(Modifier.height(20.dp))
                    // Image variant: regular-case headline so the photo
                    // carries the visual weight, not the type.
                    Text(
                        text = article.title,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Sdai.ink,
                    )
                } else {
                    // Text variant: big editorial quote + all-caps title.
                    Text(
                        text = "“",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 36.sp,
                        color = accent,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = article.title.uppercase(),
                        fontSize = 38.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Black,
                        color = Sdai.ink,
                    )
                }

                Spacer(Modifier.height(14.dp))
                AccentRule(accent = accent)
                Spacer(Modifier.height(16.dp))

                if (description != null) {
                    // Image variant gets a shorter excerpt — the photo
                    // is the dominant element. Text variant gets the
                    // full body to fill the space the missing image
                    // would have occupied.
                    val maxChars = if (hasImage) 160 else 280
                    Text(
                        text = description.take(maxChars),
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        color = Sdai.inkSubtle,
                    )
                }
            }

            // ── Bottom cluster — identical across both variants ──────
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                article.tier?.takeIf { it.isNotBlank() }?.let {
                    TierPill(
                        label = it.replaceFirstChar { c -> c.uppercase() },
                        accent = accent,
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Sdai.border)
                )
                EditorialFooter(
                    minutes = mins,
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

/* ──────────────────────────────────────────────────────────────────────
 *  Hero slot — image variant only
 * ────────────────────────────────────────────────────────────────────── */

/**
 * Image hero capped to the user's "45 % viewport rule" so the
 * typography below always has room to breathe. The bottom 20 % of
 * the image fades into the card background so the headline reads
 * cleanly against the photo's edge regardless of its brightness.
 *
 * While the image is in flight Coil shows a tier-tinted placeholder
 * (neutral, slightly lighter than the background) — never a stark
 * white box.
 */
@Composable
private fun ImageHero(imageUrl: String, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.45f)
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.08f))   // shimmer-ish fallback while loading
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // Bottom scrim — guarantees title contrast on bright images.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.78f to Color.Transparent,
                        1f to Sdai.background.copy(alpha = 0.85f),
                    )
                )
        )
    }
}

/* ──────────────────────────────────────────────────────────────────────
 *  Shared chrome — header, accent rule, tier pill, footer
 * ────────────────────────────────────────────────────────────────────── */

@Composable
private fun EditorialHeader(
    letter: String,
    domainCaps: String,
    sourceName: String,
    publishedAtMillis: Long,
    accent: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.18f))
                .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = letter,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = domainCaps.ifBlank { sourceName.uppercase() },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Sdai.ink,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.width(6.dp))
                VerifiedDot(accent = accent)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$sourceName · ${TimeAgo.format(publishedAtMillis)}",
                fontSize = 12.sp,
                color = Sdai.muted,
            )
        }
    }
}

@Composable
private fun VerifiedDot(accent: Color) {
    Box(
        Modifier
            .size(14.dp)
            .clip(RoundedCornerShape(50))
            .background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "✓",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
        )
    }
}

@Composable
private fun AccentRule(accent: Color) {
    Box(
        Modifier
            .fillMaxWidth(0.55f)
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        accent,
                        Color(0xFFFF6BAA),
                        Color.Transparent,
                    )
                )
            )
    )
}

@Composable
private fun TierPill(label: String, accent: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
    }
}

@Composable
private fun EditorialFooter(
    minutes: Int,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = Sdai.muted,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$minutes min read",
                fontSize = 13.sp,
                color = Sdai.muted,
            )
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
                    Icons.Outlined.MoreVert,
                    contentDescription = "Report content",
                    tint = Sdai.muted,
                )
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────────
 *  Plain helpers
 * ────────────────────────────────────────────────────────────────────── */

/**
 * Per-tier accent colour for the editorial layout. Picked so each
 * tier feels distinct at a glance while staying within a calm,
 * editorial dark-mode palette.
 *  - breaking: emerald (lab announcements feel "fresh")
 *  - industry: cyan-blue (press / business feel)
 *  - community: violet (Reddit / HN — matches the mockup)
 *  - research: amber (papers / arXiv — warm "library" feel)
 */
private fun accentForTier(tier: String?): Color = when (tier?.lowercase()) {
    "breaking" -> Color(0xFF5BE7B7)
    "industry" -> Color(0xFF6CB7FF)
    "research" -> Color(0xFFFFC861)
    "community", null -> Color(0xFF9B6BFF)
    else -> Color(0xFF9B6BFF)
}

private fun String.firstLetterUpper(): String =
    firstOrNull { it.isLetter() }?.uppercase() ?: "?"

/**
 * Pull a `domain.tld` style label out of a full article URL, sans
 * `www.` prefix. Used for the bold caps header label
 * (e.g. `DEEPMIND.GOOGLE`). Falls back to empty string when the URL
 * is malformed; the header substitutes the source name in that case.
 */
private fun domainFromUrl(url: String): String {
    val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return ""
    return host.removePrefix("www.")
}
