package com.sdai.news.ui.screens

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.sdai.news.R
import com.sdai.news.data.Article
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.viewmodel.UnifiedFeedViewModel
import java.util.Calendar

private enum class TimeSlot(val label: String, val emoji: String) {
    MORNING("Morning", "☀"),
    AFTERNOON("Afternoon", "🌤"),
    EVENING("Evening", "🌙"),
}

private val SUMMARY_LANGUAGES = listOf("English", "Telugu", "हिंदी")

@Composable
fun BriefingScreen(
    onOpenArticle: (Article) -> Unit,
    vm: UnifiedFeedViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val articles by vm.feed.collectAsState()
    val top10 = remember(articles) { articles.sortedByDescending { it.weight }.take(10) }

    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { nowMs = System.currentTimeMillis(); kotlinx.coroutines.delay(60_000) }
    }
    val currentHour = remember(nowMs) { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(nowMs) {
        when {
            currentHour < 12 -> "Good Morning, Person1"
            currentHour < 17 -> "Good Afternoon, Person1"
            else -> "Good Evening, Person1"
        }
    }
    val activeSlot = remember(currentHour) {
        when {
            currentHour < 12 -> TimeSlot.MORNING
            currentHour < 18 -> TimeSlot.AFTERNOON
            else -> TimeSlot.EVENING
        }
    }
    var selectedSlot by remember(activeSlot) { mutableStateOf(activeSlot) }
    var summaryArticle by remember { mutableStateOf<Article?>(null) }

    // TTS
    val context = LocalContext.current
    var ttsReady by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }
    LaunchedEffect(tts) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { isPlaying = false }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { isPlaying = false }
        })
    }
    DisposableEffect(Unit) {
        onDispose { tts.stop(); tts.shutdown() }
    }
    val briefingText = remember(greeting, top10) {
        if (top10.isEmpty()) "$greeting. No stories loaded yet."
        else buildString {
            append("$greeting. ")
            append("Your daily brief: top ${top10.size} must-know stories. ")
            top10.forEachIndexed { i, a -> append("Story ${i + 1}: ${a.title}. ") }
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().background(Sdai.background).statusBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Image(
                        painter = painterResource(R.drawable.awarely_logo),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Daily Briefing", color = Sdai.ink, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        Text(greeting, color = Sdai.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Sdai.primary, Sdai.accent))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    DailyBriefCard(
                        topCount = top10.size,
                        isPlaying = isPlaying,
                        ttsReady = ttsReady,
                        onPlayToggle = {
                            if (isPlaying) {
                                tts.stop(); isPlaying = false
                            } else if (ttsReady) {
                                tts.speak(briefingText, TextToSpeech.QUEUE_FLUSH, null, "daily_brief")
                                isPlaying = true
                            }
                        },
                    )
                }
                item { TimeSlotRow(selected = selectedSlot, onSelect = { selectedSlot = it }) }

                if (top10.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.AutoAwesome, null, tint = Sdai.muted, modifier = Modifier.size(40.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Your briefing will appear here", color = Sdai.muted, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                } else {
                    item { Text("Top ${top10.size} Stories Today", color = Sdai.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                    items(top10.size) { idx ->
                        val article = top10[idx]
                        BriefingArticleRow(rank = idx + 1, article = article,
                            onOpen = { onOpenArticle(article) }, onSummary = { summaryArticle = article })
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }

        AnimatedVisibility(visible = summaryArticle != null, enter = fadeIn(), exit = fadeOut()) {
            summaryArticle?.let { art ->
                AiSummaryOverlay(article = art, onOpenArticle = { onOpenArticle(art) }, onDismiss = { summaryArticle = null })
            }
        }
    }
}

@Composable
private fun DailyBriefCard(topCount: Int, isPlaying: Boolean, ttsReady: Boolean, onPlayToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF16213E))))
            .border(1.dp, Sdai.border, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Your Daily Brief", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text("Top $topCount must-know stories today", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (ttsReady) Sdai.primary else Sdai.mutedDeep)
                    .clickable(enabled = ttsReady, onClick = onPlayToggle)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        null, tint = Color.White, modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isPlaying) "Stop" else if (!ttsReady) "Loading…" else "Play Brief",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    )
                }
            }
        }
        Box(
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))
                .background(Sdai.cardInner.copy(alpha = 0.6f))
                .border(1.dp, Sdai.border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(painter = painterResource(R.drawable.awarely_logo), contentDescription = null, modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
private fun TimeSlotRow(selected: TimeSlot, onSelect: (TimeSlot) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TimeSlot.entries.forEach { slot ->
            val isSelected = slot == selected
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Sdai.primary.copy(alpha = 0.15f) else Sdai.cardInner)
                    .border(1.dp, if (isSelected) Sdai.primary else Sdai.border, RoundedCornerShape(12.dp))
                    .clickable { onSelect(slot) }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(slot.emoji, fontSize = 20.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(slot.label, color = if (isSelected) Sdai.primary else Sdai.ink,
                        fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun BriefingArticleRow(rank: Int, article: Article, onOpen: () -> Unit, onSummary: () -> Unit) {
    val rankColor = when (rank) { 1 -> Color(0xFFFFC107); 2 -> Color(0xFFC0C0C0); 3 -> Color(0xFFCD7F32); else -> Sdai.mutedDeep }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Sdai.cardInner).clickable(onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(34.dp).clip(CircleShape)
            .background(rankColor.copy(alpha = if (rank <= 3) 0.20f else 0.10f)),
            contentAlignment = Alignment.Center) {
            Text("$rank", color = rankColor, fontWeight = FontWeight.ExtraBold, fontSize = if (rank <= 3) 15.sp else 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(article.title, color = Sdai.ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2)
            Spacer(Modifier.height(3.dp))
            Text(article.source, color = Sdai.muted, fontSize = 11.sp)
        }
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Sdai.primary.copy(alpha = 0.12f))
            .clickable(onClick = onSummary).padding(horizontal = 8.dp, vertical = 5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Sdai.primary, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text("AI", color = Sdai.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AiSummaryOverlay(article: Article, onOpenArticle: () -> Unit, onDismiss: () -> Unit) {
    var selectedLang by remember { mutableIntStateOf(0) }
    var feedbackGiven by remember { mutableStateOf<Boolean?>(null) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onDismiss)) {
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Sdai.surface).clickable {}.padding(20.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Sdai.border))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Sdai.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("AI Summary", color = Sdai.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.Language, null, tint = Sdai.muted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SUMMARY_LANGUAGES.forEachIndexed { i, lang ->
                    val sel = i == selectedLang
                    Box(modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (sel) Sdai.primary else Sdai.cardInner)
                        .border(1.dp, if (sel) Sdai.primary else Sdai.border, RoundedCornerShape(50))
                        .clickable { selectedLang = i }.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Text(lang, color = if (sel) Color.White else Sdai.ink, fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    val summaryText = when (selectedLang) {
                        0 -> article.summary?.takeIf { it.isNotBlank() } ?: article.description.take(200).trimEnd()
                        1 -> "AI సారాంశం వచ్చే వరకు వేచి ఉండండి…"
                        else -> "AI सारांश आ रहा है…"
                    }
                    Text(summaryText, color = Sdai.ink, fontSize = 14.sp, lineHeight = 20.sp)
                }
                article.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(Sdai.cardInner)) {
                        Image(
                            painter = rememberAsyncImagePainter(ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build()),
                            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryActionButton("Listen", Icons.Outlined.Headphones, modifier = Modifier.weight(1f)) {}
                SummaryActionButton("Full Article", Icons.Outlined.OpenInBrowser, modifier = Modifier.weight(1f), onClick = onOpenArticle)
                SummaryActionButton("Share", Icons.Outlined.Share, modifier = Modifier.weight(1f)) {}
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Was this helpful?", color = Sdai.muted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.ThumbUp, "Helpful", tint = if (feedbackGiven == true) Sdai.success else Sdai.muted,
                        modifier = Modifier.size(22.dp).clickable { feedbackGiven = true })
                    Icon(Icons.Outlined.ThumbDown, "Not helpful", tint = if (feedbackGiven == false) Sdai.danger else Sdai.muted,
                        modifier = Modifier.size(22.dp).clickable { feedbackGiven = false })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SummaryActionButton(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(Sdai.cardInner)
            .border(1.dp, Sdai.border, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Sdai.ink, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = Sdai.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
