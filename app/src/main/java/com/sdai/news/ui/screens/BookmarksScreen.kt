package com.sdai.news.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.util.Share as ShareUtil
import com.sdai.news.util.TimeAgo
import com.sdai.news.viewmodel.FeedViewModel
import kotlinx.coroutines.launch

@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    onOpenUrl: (url: String, title: String) -> Unit,
) {
    val vm: FeedViewModel = viewModel()
    val bookmarks by vm.repo.observeBookmarks().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Sdai.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Sdai.ink)
            }
            Text(
                "Saved articles",
                style = MaterialTheme.typography.headlineMedium,
                color = Sdai.ink,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (bookmarks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No saved articles yet.\nDouble-tap a card to save it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Sdai.muted,
                )
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items = bookmarks, key = { it.id }) { bm ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Sdai.cardInner)
                            .border(1.dp, Sdai.border, RoundedCornerShape(14.dp))
                            .clickable { onOpenUrl(bm.url, bm.title) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Sdai.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!bm.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = bm.imageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(
                                    bm.source.take(1).uppercase(),
                                    color = Sdai.primary,
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                            }
                        }
                        Spacer(Modifier.padding(4.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(
                                bm.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Sdai.ink,
                                maxLines = 3,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${bm.source} · ${TimeAgo.format(bm.savedAtMillis)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Sdai.muted,
                            )
                        }
                        IconButton(onClick = {
                            ShareUtil.article(
                                context = ctx,
                                title = bm.title,
                                url = bm.url,
                                source = bm.source,
                                imageUrl = bm.imageUrl,
                            )
                        }) {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = null,
                                tint = Sdai.muted,
                            )
                        }
                        IconButton(onClick = {
                            scope.launch { vm.repo.removeBookmark(bm.id) }
                        }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = Sdai.muted,
                            )
                        }
                    }
                }
            }
        }
    }
}
