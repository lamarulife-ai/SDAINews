package com.sdai.news.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
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
    onOpen: (() -> Unit)? = null,
    onBlock: (() -> Unit)? = null,
) {
    val accent = accentForTier(article.tier)
    val letter = article.source.firstLetterUpper()
    val domainCaps = domainFromUrl(article.url).uppercase()

    // Quality-gated hero: load the image (capped at 1080px) and inspect its
    // real decoded size. A small og:image / social thumbnail would look
    // blurry blown up to the full-bleed hero, so anything under the
    // threshold (or a failed load) falls back to the editorial text card.
    // Video news (YouTube) — flagged on the article, or a youtube URL.
    val isVideo = article.isVideo ||
        article.url.contains("youtube.com") || article.url.contains("youtu.be/")

    // Video items must always show a thumbnail + play affordance. If the RSS
    // feed didn't carry one, derive it from the YouTube watch URL so the card
    // never falls back to the plain text variant.
    val imageUrl = article.imageUrl?.takeIf { it.isNotBlank() }
        ?: if (isVideo) youTubeThumb(article.url) else null
    val ctx = LocalContext.current
    val painter = imageUrl?.let {
        rememberAsyncImagePainter(
            ImageRequest.Builder(ctx).data(it).size(1080).crossfade(true).build()
        )
    }
    val painterState = painter?.state
    val imageTooSmall = (painterState as? AsyncImagePainter.State.Success)?.let { s ->
        val d = s.result.drawable
        d.intrinsicWidth < MIN_HERO_W || d.intrinsicHeight < MIN_HERO_H
    } ?: false
    val imageFailed = painterState is AsyncImagePainter.State.Error
    // Video cards always show the thumbnail+play affordance (YouTube
    // thumbnails are 480×360, below the photo gate, but the play button
    // makes intent clear); other cards gate on real resolution.
    val hasImage = painter != null && (isVideo || (!imageTooSmall && !imageFailed))

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
                        painter = painter!!,
                        accent = accent,
                        isVideo = isVideo,
                        onPlay = onOpen,
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
                    // Title case (not all-caps) — far faster to read over
                    // multiple lines, per the UX review.
                    Text(
                        text = article.title,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
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
                    onBlock = onBlock,
                    sourceName = article.source,
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
private fun ImageHero(
    painter: AsyncImagePainter,
    accent: Color,
    isVideo: Boolean = false,
    onPlay: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.45f)
            .clip(RoundedCornerShape(18.dp))
            // Black behind video so the fitted 16:9 frame letterboxes cleanly;
            // tinted shimmer behind photos while they load.
            .background(if (isVideo) Color.Black else accent.copy(alpha = 0.08f))
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            // Video → Fit (show the whole thumbnail, no side cropping).
            // Photo → Crop (fill the hero edge-to-edge).
            contentScale = if (isVideo) ContentScale.Fit else ContentScale.Crop,
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
        // Video → centred play button. Tapping it opens the video.
        if (isVideo) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(50))
                    .let { if (onPlay != null) it.clickable(onClick = onPlay) else it },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
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
    onBlock: (() -> Unit)?,
    sourceName: String,
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
            // Overflow → Block this source / Report.
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = Sdai.muted)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (onBlock != null) {
                        DropdownMenuItem(
                            text = { Text("Block $sourceName") },
                            onClick = { menuOpen = false; onBlock() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Report content") },
                        onClick = { menuOpen = false; onReport() },
                    )
                }
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
/** Minimum decoded pixel size to use a full-bleed hero. Smaller images
 *  (typical og:image / social thumbnails) look blurry blown up, so they
 *  fall back to the editorial text card instead. */
private const val MIN_HERO_W = 600
private const val MIN_HERO_H = 360

private fun accentForTier(tier: String?): Color = when (tier?.lowercase()) {
    "breaking" -> Color(0xFF5BE7B7)
    "industry" -> Color(0xFF6CB7FF)
    "research" -> Color(0xFFFFC861)
    "community", null -> Color(0xFF9B6BFF)
    else -> Color(0xFF9B6BFF)
}

private fun String.firstLetterUpper(): String =
    firstOrNull { it.isLetter() }?.uppercase() ?: "?"

/** Pull the YouTube video id out of a watch / short URL and build its
 *  thumbnail URL. Returns null for non-YouTube links. */
private fun youTubeThumb(url: String): String? {
    val id = Regex("[?&]v=([\\w-]{11})").find(url)?.groupValues?.get(1)
        ?: Regex("youtu\\.be/([\\w-]{11})").find(url)?.groupValues?.get(1)
        ?: Regex("/(?:embed|shorts)/([\\w-]{11})").find(url)?.groupValues?.get(1)
    return id?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
}

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
