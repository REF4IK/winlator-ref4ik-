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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
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
import androidx.compose.runtime.mutableStateOf
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.winlator.cmod.steam.store.SteamStoreViewModel
import com.winlator.cmod.steam.store.StoreDetail
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Страница игры магазина в стиле десктоп-Steam: медиа, описание,
// цена/издания, отзывы, языки, требования.
@Composable
fun StoreDetailScreen(
    appId: Int,
    onBack: () -> Unit,
    onFindInLibrary: ((Int) -> Unit)? = null,
    viewModel: SteamStoreViewModel = viewModel(),
) {
    val state by viewModel.ui.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(appId) { viewModel.loadDetail(appId) }
    val detail = state.detail?.takeIf { it.appId == appId }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.clearDetail(); onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(detail?.name ?: "Магазин", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (detail != null) {
                val wished = state.wishlist.contains(appId)
                IconButton(onClick = { viewModel.toggleWishlist(appId) }) {
                    Icon(if (wished) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null, tint = if (wished) Color(0xFFE35D7C) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        when {
            state.detailLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            detail == null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(state.detailError ?: "Не удалось загрузить", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> StoreDetailBody(
                detail = detail,
                onOpenStore = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(detail.storeUrl)))
                },
                onFindInLibrary = { onFindInLibrary?.invoke(appId) },
            )
        }
    }
}

@Composable
private fun StoreDetailBody(detail: StoreDetail, onOpenStore: () -> Unit, onFindInLibrary: () -> Unit) {
    var galleryIndex by remember(detail.appId) { mutableStateOf(0) }
    val galleryCount = detail.screenshots.size + detail.movies.size
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Медиа-галерея
        if (galleryCount > 0) {
            item(key = "media") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
                        val movie = detail.movies.getOrNull(galleryIndex)
                        val shot = if (movie == null) detail.screenshots.getOrNull(galleryIndex - detail.movies.size) else null
                        AsyncImage(
                            model = movie?.thumbnail ?: shot?.full ?: detail.headerImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                            contentScale = ContentScale.Crop,
                        )
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)))))
                    }
                    if (galleryCount > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(galleryCount) { i ->
                                val m = detail.movies.getOrNull(i)
                                val s = if (m == null) detail.screenshots.getOrNull(i - detail.movies.size) else null
                                AsyncImage(
                                    model = m?.thumbnail ?: s?.thumbnail ?: detail.headerImage,
                                    contentDescription = null,
                                    modifier = Modifier.width(96.dp).aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { galleryIndex = i }
                                        .then(if (i == galleryIndex) Modifier else Modifier),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }
        }
        // Заголовок + отзывы
        item(key = "head") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(detail.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (detail.shortDescription.isNotBlank()) {
                    Text(detail.shortDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (detail.reviewSummary.totalReviews > 0) {
                        ReviewBadge(scoreDesc = detail.reviewSummary.scoreDesc, percent = detail.reviewSummary.positivePercent, total = detail.reviewSummary.totalReviews)
                    }
                    if (detail.releaseDate.isNotBlank()) {
                        Text(detail.releaseDate, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (detail.metacriticScore > 0) {
                        Text("MC ${detail.metacriticScore}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF66CC33))
                    }
                }
                if (detail.developers.isNotEmpty() || detail.publishers.isNotEmpty()) {
                    Text(
                        buildString {
                            if (detail.developers.isNotEmpty()) append("Разработчик: ${detail.developers.joinToString(", ")}")
                            if (detail.publishers.isNotEmpty()) {
                                if (isNotEmpty()) append("  •  ")
                                append("Издатель: ${detail.publishers.joinToString(", ")}")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (detail.genres.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(detail.genres) { g ->
                            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                                Text(g, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        // Цена / издания
        item(key = "buy") {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (detail.comingSoon) "Скоро выйдет" else "Купить ${detail.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        StorePriceTag(price = detail.price)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (onFindInLibrary != null) {
                                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onFindInLibrary() }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text("В библиотеке", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary).clickable { onOpenStore() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Text("В Steam", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    detail.editions.take(4).forEach { ed ->
                        if (ed.title.isNotBlank()) {
                            Text("• ${ed.title}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (detail.hasDemo) {
                        Text("Есть демоверсия", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        // Фичи
        if (detail.categories.isNotEmpty() || detail.fullController || detail.hasCloud || detail.hasAchievements) {
            item(key = "feat") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Особенности", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    (detail.categories.take(8) + listOfNotNull(
                        "Полная поддержка контроллера".takeIf { detail.fullController },
                        "Steam Cloud".takeIf { detail.hasCloud },
                        "Достижения Steam".takeIf { detail.hasAchievements },
                    )).distinct().take(10).forEach { f ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(f, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        // Об игре
        if (detail.aboutText.isNotBlank()) {
            item(key = "about") {
                var expanded by remember(detail.appId) { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Об игре", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        detail.aboutText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(vertical = 4.dp)) {
                        Text(if (expanded) "Свернуть" else "Читать дальше", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        // Отзывы
        if (detail.reviews.isNotEmpty()) {
            item(key = "revtitle") {
                Text("Обзоры", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            detail.reviews.take(6).forEachIndexed { i, r ->
                item(key = "rev_$i") { StoreReviewCard(text = r.text, votedUp = r.votedUp, hours = r.playtimeHours, ts = r.timestamp) }
            }
        }
        // Языки
        if (detail.supportedLanguages.isNotBlank()) {
            item(key = "lang") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Языки", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(detail.supportedLanguages.take(600), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Требования
        if (detail.minRequirements.isNotBlank() || detail.recRequirements.isNotBlank()) {
            item(key = "req") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Системные требования", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (detail.minRequirements.isNotBlank()) {
                        Text("Минимальные:\n${detail.minRequirements.take(900)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (detail.recRequirements.isNotBlank()) {
                        Text("Рекомендуемые:\n${detail.recRequirements.take(900)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item(key = "pad") { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ReviewBadge(scoreDesc: String, percent: Int, total: Int) {
    val color = when {
        percent >= 80 -> Color(0xFF66CC33)
        percent >= 60 -> Color(0xFFA3C53A)
        percent >= 40 -> Color(0xFFC5A53A)
        else -> Color(0xFFCC5533)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(scoreDesc.ifBlank { "$percent%" }, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text("($total)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StoreReviewCard(text: String, votedUp: Boolean, hours: Float, ts: Long) {
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (votedUp) Icons.Filled.ThumbUp else Icons.Filled.ThumbDown, contentDescription = null, tint = if (votedUp) Color(0xFF66CC33) else Color(0xFFCC5533), modifier = Modifier.width(18.dp))
                Text(if (votedUp) "Рекомендую" else "Не рекомендую", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (hours > 0) Text("%.1f ч.".format(hours), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (ts > 0) {
                    val d = remember(ts) {
                        runCatching { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ts * 1000)) }.getOrDefault("")
                    }
                    if (d.isNotBlank()) Text(d, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(text.take(800), style = MaterialTheme.typography.bodySmall, maxLines = 10, overflow = TextOverflow.Ellipsis)
        }
    }
}
