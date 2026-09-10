package com.winlator.cmod.steam.cloud

import android.app.Dialog as AndroidDialog
import android.view.ViewGroup
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.winlator.cmod.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Данные конфликта local/remote для диалога. */
data class CloudConflictData(
    val localTimestamp: Long,
    val remoteTimestamp: Long,
    val localBytes: Long,
    val remoteBytes: Long,
)

/** Выбор пользователя в диалоге конфликта. */
enum class CloudConflictChoice {
    USE_LOCAL,
    USE_REMOTE,
    BACKUP_BOTH,
}

private fun formatConflictDate(timestampMs: Long, unknown: String): String {
    if (timestampMs <= 0L) return unknown
    return runCatching {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestampMs))
    }.getOrDefault(unknown)
}

private fun formatConflictSize(bytes: Long): String {
    if (bytes < 0L) return "—"
    if (bytes < 1024L) return "$bytes Б"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f КБ", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f МБ", mb)
    return String.format(Locale.US, "%.2f ГБ", mb / 1024.0)
}

/**
 * Compose-диалог конфликта сохранений: local/remote timestamp+size,
 * кнопки Local / Remote / Backup оба.
 */
@Composable
fun SteamCloudConflictDialog(
    data: CloudConflictData,
    onChoice: (CloudConflictChoice) -> Unit,
    onDismissRequest: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.steam_cloud_conflict_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.steam_cloud_conflict_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val unknown = stringResource(R.string.steam_cloud_conflict_unknown)
                    ConflictVersionLine(
                        label = stringResource(
                            R.string.steam_cloud_conflict_local,
                            formatConflictDate(data.localTimestamp, unknown),
                            formatConflictSize(data.localBytes),
                        ),
                    )
                    ConflictVersionLine(
                        label = stringResource(
                            R.string.steam_cloud_conflict_remote,
                            formatConflictDate(data.remoteTimestamp, unknown),
                            formatConflictSize(data.remoteBytes),
                        ),
                    )

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = { onChoice(CloudConflictChoice.USE_LOCAL) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.steam_cloud_conflict_use_local))
                    }
                    Button(
                        onClick = { onChoice(CloudConflictChoice.USE_REMOTE) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.steam_cloud_conflict_use_remote))
                    }
                    OutlinedButton(
                        onClick = { onChoice(CloudConflictChoice.BACKUP_BOTH) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.steam_cloud_conflict_backup_both))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictVersionLine(label: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

object SteamCloudConflictDialogHost {
    /**
     * Показать диалог из Activity без Compose-инфраструктуры.
     * Не отменяемый: колбэк всегда вызывается ровно один раз.
     */
    fun show(
        activity: ComponentActivity,
        data: CloudConflictData,
        onChoice: (CloudConflictChoice) -> Unit,
    ) {
        var delivered = false
        fun deliver(choice: CloudConflictChoice) {
            if (!delivered) {
                delivered = true
                onChoice(choice)
            }
        }

        val dialog = AndroidDialog(activity, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
        }

        val composeView = ComposeView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent {
                SteamCloudConflictDialog(
                    data = data,
                    onChoice = {
                        deliver(it)
                        dialog.dismiss()
                    },
                )
            }
        }

        dialog.setContentView(composeView)
        dialog.setOnDismissListener { deliver(CloudConflictChoice.USE_LOCAL) }
        dialog.show()
    }
}

/**
 * Suspend-обёртка над диалогом: ждёт выбор пользователя.
 * Таймаут 10 минут — fallback на USE_LOCAL.
 */
suspend fun ComponentActivity.awaitCloudConflictChoice(data: CloudConflictData): CloudConflictChoice {
    val chosen = withTimeoutOrNull(10L * 60L * 1000L) {
        suspendCancellableCoroutine { cont ->
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    if (cont.isActive) cont.resume(CloudConflictChoice.USE_LOCAL)
                    return@runOnUiThread
                }
                SteamCloudConflictDialogHost.show(this@awaitCloudConflictChoice, data) { choice ->
                    if (cont.isActive) cont.resume(choice)
                }
            }
        }
    }
    return chosen ?: CloudConflictChoice.USE_LOCAL
}
