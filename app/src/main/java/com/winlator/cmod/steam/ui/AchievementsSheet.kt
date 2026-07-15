package com.winlator.cmod.steam.ui

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.R
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.steam.statsgen.Achievement
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
                .background(Color(0xCC0F1016)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(top = 40.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.steam_library_achievements),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                        if (!isLoading) {
                            val unlocked = achievements.count { it.unlocked == true }
                            Text(
                                text = stringResource(R.string.steam_library_achievements_count, unlocked, achievements.size),
                                color = Color(0xFFAAAAAA),
                                fontSize = 14.sp,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF2A2D37)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFF2A2D37))

                // Content
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF66C0F4))
                    }
                } else if (achievements.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No achievements data available for this game.",
                            color = Color(0xFF888888),
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(achievements, key = { it.name }) { achievement ->
                            AchievementRow(achievement = achievement)
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = if (unlocked) Color(0x801A2A1A) else Color(0xFF1A1D27)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(if (unlocked) Color(0xFF2A4A2A) else Color(0xFF2A2D37)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (unlocked) Color(0xFFFFD700) else Color(0xFF555555),
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = achievement.displayName?.get("english") ?: achievement.name,
                    color = if (unlocked) Color.White else Color(0xFFCCCCCC),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!achievement.description.isNullOrEmpty()) {
                    Text(
                        text = achievement.description["english"] ?: "",
                        color = Color(0xFF999999),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (unlocked && achievement.unlockTimestamp != null && achievement.unlockTimestamp!! > 0) {
                Text(
                    text = formatTimestamp(achievement.unlockTimestamp!!),
                    color = Color(0xFF888888),
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
