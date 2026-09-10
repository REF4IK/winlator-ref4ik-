package com.winlator.cmod.steam.cloud

import android.content.Context
import com.winlator.cmod.steam.enums.PathType
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Снапшот локальных сейвов перед заливкой в Steam Cloud.
 *
 * Пакует userdata/{accountId}/{appId} в
 * filesDir/cloud_snapshots/{appId}_{timestamp}.zip, держит последние 5.
 * Всё best-effort: неуспех не должен ломать синк.
 */
object SteamSaveSnapshotManager {
    const val SNAPSHOT_DIR_NAME = "cloud_snapshots"
    const val MAX_SNAPSHOTS = 5

    private fun snapshotsDir(context: Context): File =
        File(context.filesDir, SNAPSHOT_DIR_NAME)

    private fun snapshotName(appId: Int, timestampMs: Long): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestampMs))
        return "${appId}_$stamp.zip"
    }

    /**
     * Zip-бэкап userdata/{accountId}/{appId} перед заливкой.
     * @return файл снапшота или null (нечего бэкапить / ошибка).
     */
    suspend fun snapshotBeforeUpload(
        context: Context,
        appId: Int,
        prefixToPath: (String) -> String,
    ): File? = withContext(Dispatchers.IO) {
        try {
            val remotePath = runCatching { prefixToPath(PathType.SteamUserData.name) }.getOrNull()
                ?: return@withContext null
            // SteamUserData указывает на .../userdata/{accountId}/{appId}/remote
            val userdataAppDir = File(remotePath).parentFile ?: return@withContext null
            if (!userdataAppDir.isDirectory) {
                Timber.i("Cloud snapshot: no userdata dir for appId=$appId")
                return@withContext null
            }
            if (userdataAppDir.listFiles()?.isNotEmpty() != true) {
                Timber.i("Cloud snapshot: userdata dir empty for appId=$appId")
                return@withContext null
            }

            val outDir = snapshotsDir(context).apply { mkdirs() }
            val outFile = File(outDir, snapshotName(appId, System.currentTimeMillis()))
            zipDirectory(outFile, userdataAppDir)
            Timber.i("Cloud snapshot: wrote ${outFile.name} for appId=$appId")
            pruneSnapshots(context, appId)
            outFile
        } catch (e: Exception) {
            Timber.w(e, "Cloud snapshot failed for appId=$appId")
            null
        }
    }

    /** Снапшоты appId, новые первые. */
    fun listSnapshots(context: Context, appId: Int): List<File> {
        val dir = snapshotsDir(context)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { file -> file.isFile && file.name.startsWith("${appId}_") && file.name.endsWith(".zip") }
            ?.sortedByDescending { it.name }
            ?: emptyList()
    }

    /** Ротация: держать последние [MAX_SNAPSHOTS]. */
    fun pruneSnapshots(context: Context, appId: Int) {
        listSnapshots(context, appId).drop(MAX_SNAPSHOTS).forEach { file ->
            if (!file.delete()) {
                Timber.w("Cloud snapshot: failed to prune ${file.name}")
            }
        }
    }

    private fun zipDirectory(outFile: File, sourceDir: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zip ->
            val basePath = sourceDir.parentFile?.absolutePath ?: sourceDir.absolutePath
            sourceDir.walkTopDown().forEach { file ->
                val relative = file.absolutePath.removePrefix("$basePath/").replace(File.separatorChar, '/')
                if (relative.isEmpty()) return@forEach
                if (file.isDirectory) {
                    zip.putNextEntry(ZipEntry("$relative/"))
                    zip.closeEntry()
                } else {
                    zip.putNextEntry(ZipEntry(relative))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }
}
