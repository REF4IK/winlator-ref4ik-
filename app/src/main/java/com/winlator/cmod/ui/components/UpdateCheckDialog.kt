package com.winlator.cmod.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R
import com.winlator.cmod.core.UpdateManager
import java.io.File

/**
 * Диалог проверки обновлений: проверка, скачивание, установка, пропуск версии.
 * Используется из настроек (кнопка «Проверить обновление») и из корневого роута
 * (авто-проверка при запуске, если включено уведомление).
 */
@Composable
fun UpdateCheckDialog(
    show: Boolean,
    checking: Boolean,
    error: Boolean,
    info: UpdateManager.UpdateInfo?,
    downloading: Boolean,
    progress: Int,
    file: File?,
    onDismiss: () -> Unit,
    setDownloading: (Boolean) -> Unit,
    setProgress: (Int) -> Unit,
    setFile: (File?) -> Unit,
) {
    if (!show) return

    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun toast(resId: Int) = Toast.makeText(ctx, ctx.getString(resId), Toast.LENGTH_SHORT).show()

    AlertDialog(
        onDismissRequest = {
            if (!downloading) onDismiss()
        },
        title = { Text(stringResource(R.string.update_check), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    downloading -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            Text(stringResource(R.string.update_downloading, progress), style = MaterialTheme.typography.bodyMedium)
                        }
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    error -> {
                        Text(stringResource(R.string.update_error), color = MaterialTheme.colorScheme.error)
                    }
                    info != null && !info.isNewer -> {
                        Text(stringResource(R.string.update_not_available))
                    }
                    info != null -> {
                        if (UpdateManager.skippedVersion(ctx) == info.tagName) {
                            Text(stringResource(R.string.update_not_available))
                        } else {
                            Text(stringResource(R.string.update_available, info.tagName), fontWeight = FontWeight.SemiBold)
                            if (info.notes.isNotEmpty()) {
                                Text(stringResource(R.string.update_notes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    info.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 10,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 200.dp),
                                )
                            }
                            if (file != null) {
                                Text(stringResource(R.string.update_downloaded))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                downloading || checking -> {}
                file != null -> {
                    TextButton(onClick = {
                        val ok = UpdateManager.installApk(ctx, file)
                        if (!ok) toast(R.string.update_install_failed)
                    }) { Text(stringResource(R.string.update_install)) }
                }
                info != null && info.isNewer && UpdateManager.skippedVersion(ctx) != info.tagName -> {
                    if (info.apkUrl != null) {
                        TextButton(onClick = {
                            setDownloading(true)
                            setProgress(0)
                            UpdateManager.downloadApk(ctx, info.apkUrl, { p ->
                                (ctx as? android.app.Activity)?.runOnUiThread {
                                    setProgress((p * 100).toInt().coerceIn(0, 100))
                                }
                            }, { downloaded ->
                                (ctx as? android.app.Activity)?.runOnUiThread {
                                    setDownloading(false)
                                    if (downloaded != null) {
                                        setFile(downloaded)
                                    } else {
                                        toast(R.string.update_download_failed)
                                    }
                                }
                            })
                        }) { Text(stringResource(R.string.update_download)) }
                    } else {
                        TextButton(onClick = {
                            val url = "https://github.com/${UpdateManager.UPDATE_REPO}/releases/latest"
                            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            try { ctx.startActivity(i) } catch (_: Exception) { toast(R.string.update_download_failed) }
                        }) { Text(stringResource(R.string.update_download)) }
                    }
                }
            }
        },
        dismissButton = {
            Row {
                if (downloading) {
                    TextButton(onClick = {}) { Text("") }
                } else {
                    if (info != null && info.isNewer && file == null &&
                        UpdateManager.skippedVersion(ctx) != info.tagName) {
                        TextButton(onClick = {
                            UpdateManager.skipVersion(ctx, info.tagName)
                            onDismiss()
                        }) { Text(stringResource(R.string.update_skip)) }
                    }
                    TextButton(onClick = { onDismiss() }) {
                        Text(if (file != null) stringResource(R.string.cancel) else stringResource(R.string.close))
                    }
                }
            }
        },
    )
}
