package com.winlator.cmod.steam.store.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.winlator.cmod.steam.store.NewsItem
import com.winlator.cmod.steam.ui.SteamLibraryGameUi

// Дом библиотеки в стиле десктоп-Steam: Недавние, Во что сыграть.
@Composable
fun LibraryHomeSections(
    games: List<SteamLibraryGameUi>,
    news: List<NewsItem>,
    onOpenGame: (Int) -> Unit,
    onOpenStoreDetail: (Int) -> Unit,
) {
    if (games.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Недавние игры — установленные первыми, затем остальные из библиотеки
        val installedFirst = games.filter { it.installed }
        val rest = games.filter { !it.installed }.sortedBy { it.name.lowercase() }
        val recent = (installedFirst + rest).distinctBy { it.appId }.take(10)
        if (recent.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Недавние игры", action = null, onAction = {})
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(recent, key = { it.appId }) { g ->
                        RecentGameCard(game = g, onClick = { onOpenGame(g.appId) })
                    }
                }
            }
        }
        // Во что сыграть — неустановленные из библиотеки, без мусора-инструментов
        val backlog = games
            .filter { !it.installed && !it.isDownloading }
            .filter { g ->
                val n = g.name.lowercase()
                !n.contains("saxxy") && !n.contains("dedicated server") &&
                    !n.contains("sdk") && !n.contains("redistributable")
            }
            .distinctBy { it.appId }.take(10)
        if (backlog.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Во что сыграть?", action = null, onAction = {})
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(backlog, key = { it.appId }) { g ->
                        RecentGameCard(game = g, onClick = { onOpenGame(g.appId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String?, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onAction).padding(4.dp),
            )
        }
    }
}

@Composable
private fun NewsPreviewCard(item: NewsItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(280.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 7f).background(
                    Brush.linearGradient(listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.surfaceVariant,
                    )),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.gameName.takeIf { it.isNotBlank() } ?: item.feedLabel.ifBlank { "Steam" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.gameName.isNotBlank()) {
                    Text(item.gameName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun RecentGameCard(game: SteamLibraryGameUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(172.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = game.capsuleUrl,
                    contentDescription = game.name,
                    modifier = Modifier.fillMaxWidth().aspectRatio(460f / 215f),
                    contentScale = ContentScale.Crop,
                )
                if (game.installed) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1A73E8).copy(alpha = 0.9f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text("Вы играли", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                if (game.isDownloading) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text("${(game.downloadProgress * 100f).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                game.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}
