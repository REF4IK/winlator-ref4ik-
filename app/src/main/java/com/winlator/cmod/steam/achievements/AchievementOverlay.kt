package com.winlator.cmod.steam.achievements

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.winlator.cmod.R
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber

data class AchievementNotification(
    val name: String,
    val description: String = "",
    val iconUrl: String? = null,
)

object AchievementNotificationManager {
    private val _notifications = Channel<AchievementNotification>(capacity = Channel.BUFFERED)
    val notifications = _notifications.receiveAsFlow()

    fun show(name: String, description: String = "", iconUrl: String? = null) {
        _notifications.trySend(AchievementNotification(name, description, iconUrl))
    }
}

/**
 * Звук анлока без бинарников: системный звук уведомлений + лёгкая вибрация.
 * res/raw/achievement_pop* в проекте нет — используется дефолт системы (best-effort).
 */
object AchievementSound {
    fun playUnlock(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (uri != null) {
                val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
                if (ringtone != null && !ringtone.isPlaying) ringtone.play()
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to play achievement unlock sound")
        }
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(80)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to vibrate on achievement unlock")
        }
    }
}

/** Тост-карточка анлока: иконка + название + описание, автоскрытие 4с. */
@Composable
fun BoxScope.AchievementOverlay() {
    var current by remember { mutableStateOf<AchievementNotification?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AchievementNotificationManager.notifications.collect { notification ->
            current = notification
            visible = true
            delay(4000)
            visible = false
            delay(500)
            current = null
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
    ) {
        current?.let { notification ->
            AchievementNotificationContent(notification)
        }
    }
}

@Composable
private fun AchievementNotificationContent(notification: AchievementNotification) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 6.dp,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!notification.iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = notification.iconUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column {
                Text(
                    text = stringResource(R.string.achievement_unlocked),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = notification.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (notification.description.isNotBlank()) {
                    Text(
                        text = notification.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Хост вотчера для экрана библиотеки: следит за выбранной игрой,
 * при анлоке показывает оверлей и играет звук. Навигацию не трогает.
 */
@Composable
fun AchievementWatcherHost(appId: Int?) {
    if (appId == null || appId <= 0) return
    val context = LocalContext.current
    LaunchedEffect(appId) {
        val watcher = AchievementWatcher(appId, context) { _, displayName, iconUrl ->
            AchievementNotificationManager.show(displayName, "", iconUrl)
            AchievementSound.playUnlock(context)
        }
        watcher.start()
        try {
            awaitCancellation()
        } finally {
            watcher.stop()
        }
    }
}
