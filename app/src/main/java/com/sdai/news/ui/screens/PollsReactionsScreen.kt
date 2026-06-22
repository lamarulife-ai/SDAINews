package com.sdai.news.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdai.news.ui.theme.Sdai

data class Poll(
    val id: String,
    val question: String,
    val options: List<PollOption>,
    val totalVotes: Int,
    val hoursLeft: Int,
    val comments: Int,
    val likes: Int,
    val category: String? = null,
)

data class PollOption(
    val id: String,
    val label: String,
    val votes: Int,
    val color: Color,
)

data class Reaction(
    val emoji: String,
    val label: String,
    val count: Int,
    val userReacted: Boolean,
)

// Option colors for visual variety in poll bars
private val OPTION_COLORS = listOf(
    Color(0xFF6366F1),
    Color(0xFF06B6D4),
    Color(0xFF10B981),
    Color(0xFFF59E0B),
)

private val SAMPLE_POLLS = listOf(
    Poll(
        id = "poll1",
        question = "Who will win ICC World Cup 2025?",
        options = listOf(
            PollOption("o1", "India 🇮🇳", 61, OPTION_COLORS[0]),
            PollOption("o2", "Australia 🇦🇺", 21, OPTION_COLORS[1]),
            PollOption("o3", "England 🏴󠁧󠁢󠁥󠁮󠁧󠁿", 11, OPTION_COLORS[2]),
            PollOption("o4", "Other 🌍", 7, OPTION_COLORS[3]),
        ),
        totalVotes = 23500,
        hoursLeft = 2,
        comments = 210,
        likes = 1300,
        category = "Cricket",
    ),
    Poll(
        id = "poll2",
        question = "Will AI replace most coding jobs by 2030?",
        options = listOf(
            PollOption("o5", "Yes, mostly", 42, OPTION_COLORS[0]),
            PollOption("o6", "No, just augment", 38, OPTION_COLORS[1]),
            PollOption("o7", "Too early to tell", 20, OPTION_COLORS[2]),
        ),
        totalVotes = 8400,
        hoursLeft = 18,
        comments = 89,
        likes = 670,
        category = "Technology",
    ),
    Poll(
        id = "poll3",
        question = "Which AI model is most impressive right now?",
        options = listOf(
            PollOption("o8", "GPT-5", 35, OPTION_COLORS[0]),
            PollOption("o9", "Claude", 30, OPTION_COLORS[1]),
            PollOption("o10", "Gemini", 25, OPTION_COLORS[2]),
            PollOption("o11", "Llama", 10, OPTION_COLORS[3]),
        ),
        totalVotes = 12100,
        hoursLeft = 36,
        comments = 145,
        likes = 890,
        category = "AI",
    ),
)

private val SAMPLE_REACTIONS = listOf(
    Reaction("🔥", "Fire", 24, false),
    Reaction("🧠", "Insightful", 18, false),
    Reaction("😱", "Shocking", 12, false),
    Reaction("💡", "Smart", 15, true),
    Reaction("😂", "Funny", 8, false),
)

@Composable
fun PollsReactionsScreen(modifier: Modifier = Modifier) {
    val votedPolls = remember { mutableStateMapOf<String, String>() }
    val reactedItems = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier
            .fillMaxSize()
            .background(Sdai.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Community", color = Sdai.ink, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text("Vote on what matters", color = Sdai.muted, fontSize = 13.sp)
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Sdai.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.BarChart, null, tint = Sdai.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Live Polls", color = Sdai.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(SAMPLE_POLLS, key = { it.id }) { poll ->
                PollCard(
                    poll = poll,
                    votedOption = votedPolls[poll.id],
                    onVote = { optionId -> votedPolls[poll.id] = optionId },
                )
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("News Reactions", color = Sdai.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
            }
            item {
                ReactionBar(
                    reactions = SAMPLE_REACTIONS,
                    reactedIds = reactedItems,
                    onReact = { key -> reactedItems[key] = !(reactedItems[key] ?: false) },
                )
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "How are you feeling about today's news?",
                    color = Sdai.muted,
                    fontSize = 13.sp,
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun PollCard(poll: Poll, votedOption: String?, onVote: (String) -> Unit) {
    val hasVoted = votedOption != null
    val extraVotes = if (hasVoted) 1 else 0
    val totalDisplayVotes = poll.options.sumOf { it.votes } + extraVotes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Sdai.cardInner)
            .border(1.dp, Sdai.border, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        // Category + timer row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            poll.category?.let { cat ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Sdai.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(cat, color = Sdai.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            } ?: Spacer(Modifier.size(0.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Timer, null, tint = Sdai.muted, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    "${poll.hoursLeft}h left",
                    color = if (poll.hoursLeft <= 3) Sdai.danger else Sdai.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // Poll question
        Text(
            "POLL",
            color = Sdai.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(poll.question, color = Sdai.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(14.dp))

        // Options
        poll.options.forEach { option ->
            val isSelected = votedOption == option.id
            val displayVotes = option.votes + if (isSelected) 1 else 0
            val progress = if (totalDisplayVotes > 0) displayVotes.toFloat() / totalDisplayVotes else 0f
            val animatedProgress by animateFloatAsState(
                targetValue = if (hasVoted) progress else 0f,
                animationSpec = tween(600),
                label = "bar_${option.id}",
            )

            PollOptionRow(
                option = option,
                isSelected = isSelected,
                showResult = hasVoted,
                votes = displayVotes,
                progress = animatedProgress,
                rawProgress = progress,
                enabled = !hasVoted,
                onClick = { onVote(option.id) },
            )
            Spacer(Modifier.height(8.dp))
        }

        // Vote count + divider
        Text(
            "${formatVoteCount(totalDisplayVotes)} votes",
            color = Sdai.muted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))

        // Bottom row — comments + likes + vote button
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Sdai.muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(poll.comments.toString(), color = Sdai.muted, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.FavoriteBorder, null, tint = Sdai.muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(formatVoteCount(poll.likes), color = Sdai.muted, fontSize = 12.sp)
                }
            }
            if (!hasVoted) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Sdai.primary)
                        .clickable { /* vote handled per-option */ }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                ) {
                    Text("Vote Now", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                Text("Voted ✓", color = Sdai.success, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PollOptionRow(
    option: PollOption,
    isSelected: Boolean,
    showResult: Boolean,
    votes: Int,
    progress: Float,
    rawProgress: Float,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) option.color.copy(alpha = 0.15f) else Sdai.background)
            .border(
                1.dp,
                if (isSelected) option.color else Sdai.border,
                RoundedCornerShape(10.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        // Animated fill bar behind text
        if (showResult) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(10.dp))
                    .background(option.color.copy(alpha = 0.15f)),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                option.label,
                color = if (isSelected) option.color else Sdai.ink,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp,
            )
            if (showResult) {
                Text(
                    "${(rawProgress * 100).toInt()}%",
                    color = option.color,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ReactionBar(
    reactions: List<Reaction>,
    reactedIds: Map<String, Boolean>,
    onReact: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        reactions.forEach { reaction ->
            val reacted = reactedIds[reaction.emoji] ?: reaction.userReacted
            val displayCount = reaction.count + if (reacted && !reaction.userReacted) 1 else 0

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (reacted) Sdai.primary.copy(alpha = 0.15f) else Sdai.cardInner)
                    .border(1.dp, if (reacted) Sdai.primary else Sdai.border, RoundedCornerShape(12.dp))
                    .clickable { onReact(reaction.emoji) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(reaction.emoji, fontSize = 22.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "$displayCount",
                    color = if (reacted) Sdai.primary else Sdai.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun formatVoteCount(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
    n >= 1_000 -> "${n / 1_000}.${(n % 1_000) / 100}K"
    else -> "$n"
}
