package com.winlator.cmod.steam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.winlator.cmod.R
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.steam.statsgen.Achievement
import com.winlator.cmod.steam.utils.SteamUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AchievementsSheet(
    appId: Int,
    onDismiss: () -> Unit,
) {
    var achievements by remember { mutableStateOf<List<Achievement>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(appId) {
        isLoading = true
        val result = withContext(Dispatchers.IO) {
            SteamService.fetchAchievementsForDisplay(appId)
        }
        achievements = result
        isLoading = false
    }

    val sorted = remember(achievements) {
        achievements.sortedWith(
            compareByDescending<Achievement> { it.unlocked == true }
                .thenBy { SteamUtils.resolveAchievementText(it.displayName, it.name) },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(top = 16.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.steam_library_achievements),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                        if (!isLoading) {
                            val unlocked = achievements.count { it.unlocked == true }
                            Text(
                                text = stringResource(R.string.steam_library_achievements_count, unlocked, achievements.size),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                // Content
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (sorted.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.steam_library_achievements_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(sorted, key = { it.name }) { achievement ->
                            AchievementRow(achievement = achievement)
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun AchievementRow(achievement: Achievement) {
    val unlocked = achievement.unlocked == true
    val title = SteamUtils.resolveAchievementText(achievement.displayName, achievement.name)
    val description = SteamUtils.resolveAchievementText(achievement.description, "")
    val iconUrl = if (unlocked) {
        achievement.icon?.takeIf { it.isNotBlank() }
            ?: achievement.iconGray?.takeIf { it.isNotBlank() }
    } else {
        achievement.iconGray?.takeIf { it.isNotBlank() }
            ?: achievement.icon?.takeIf { it.isNotBlank() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = if (unlocked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(if (unlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (!iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        alpha = if (unlocked) 1f else 0.7f,
                        colorFilter = if (unlocked) null else ColorFilter.tint(
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            BlendMode.Saturation,
                        ),
                    )
                } else {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (unlocked && achievement.unlockTimestamp != null && achievement.unlockTimestamp!! > 0) {
                Text(
                    text = formatTimestamp(achievement.unlockTimestamp!!),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Int): String {
    val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp * 1000L))
}
