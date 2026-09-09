package com.winlator.cmod.steam.store.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.winlator.cmod.R
import com.winlator.cmod.steam.store.NewsItem
import com.winlator.cmod.steam.store.SteamStoreViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Лента новостей по играм библиотеки.
@Composable
fun NewsFeedScreen(
    ownedAppIds: List<Int>,
    gameNames: Map<Int, String>,
    onOpenDetail: (Int) -> Unit,
    viewModel: SteamStoreViewModel = viewModel(),
) {
    val state by viewModel.ui.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(ownedAppIds) {
        if (ownedAppIds.isNotEmpty()) viewModel.refreshNews(ownedAppIds, gameNames)
    }
    if (state.newsLoading && state.news.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.news.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.store_news_empty), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.store_news_empty_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            IconButton(onClick = { viewModel.refreshNews(ownedAppIds, gameNames) }) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.news.distinctBy { it.gid.ifBlank { it.title + it.dateSeconds } }, key = { it.gid.ifBlank { it.title + it.dateSeconds } }) { n ->
            NewsCard(
                item = n,
                onOpen = {
                    if (n.url.isNotBlank()) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.url))) }
                    }
                },
                onOpenGame = if (n.appId > 0) ({ onOpenDetail(n.appId) }) else null,
            )
        }
    }
}

@Composable
private fun NewsCard(item: NewsItem, onOpen: () -> Unit, onOpenGame: (() -> Unit)?) {
    val date = rememberDate(item.dateSeconds)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.feedLabel.isNotBlank()) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(item.feedLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                if (item.gameName.isNotBlank()) {
                    Text(
                        item.gameName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).then(if (onOpenGame != null) Modifier.clickable { onOpenGame() } else Modifier),
                    )
                }
                if (date.isNotBlank()) {
                    Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (item.plainPreview.isNotBlank()) {
                Text(item.plainPreview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            if (item.author.isNotBlank()) {
                Text(item.author, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun rememberDate(seconds: Long): String {
    if (seconds <= 0) return ""
    return try {
        SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(seconds * 1000))
    } catch (_: Exception) { "" }
}
