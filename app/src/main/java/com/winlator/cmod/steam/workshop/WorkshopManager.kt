package com.winlator.cmod.steam.workshop

import android.content.Context
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.steam.utils.PrefManager
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetUserFiles_Request
import `in`.dragonbra.javasteam.rpc.service.PublishedFile
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.types.SteamID
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

object WorkshopManager {
    private const val TAG = "SteamWorkshopManager"
    private const val MAX_PAGES = 50
    private const val PAGE_SIZE = 100
    private const val COMPLETE_MARKER = ".workshop_complete"
    private val httpClient = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()

    suspend fun getSubscribedItems(
        appId: Int,
        steamClient: SteamClient,
        steamId: SteamID,
    ): WorkshopFetchResult {
        val unifiedMessages = steamClient.getHandler<SteamUnifiedMessages>() ?: return WorkshopFetchResult(
            items = emptyList(),
            succeeded = false,
        )
        val publishedFile = unifiedMessages.createService(PublishedFile::class.java)

        val allItems = mutableListOf<WorkshopItem>()
        var fetchedAtLeastOnePage = false
        var allPagesSucceeded = false
        var page = 1

        while (page <= MAX_PAGES) {
            val result = fetchSubscribedFilesViaRPC(
                publishedFile = publishedFile,
                appId = appId,
                steamId = steamId,
                page = page,
            ) ?: break

            fetchedAtLeastOnePage = true
            allItems.addAll(result.items.map { item ->
                if (item.appId == 0) item.copy(appId = appId) else item
            })

            if (result.items.isEmpty() || allItems.size >= result.totalResults) {
                allPagesSucceeded = true
                break
            }
            page++
        }

        return WorkshopFetchResult(
            items = allItems.sortedBy { it.title.lowercase() },
            succeeded = fetchedAtLeastOnePage,
            isComplete = allPagesSucceeded,
        )
    }

    fun parseEnabledIds(idsString: String?): Set<Long> =
        (idsString ?: "").split(",").mapNotNull { it.trim().toLongOrNull() }.filter { it > 0L }.toSet()

    fun getWorkshopContentDir(containerRootPath: String, appId: Int): File {
        return File(
            containerRootPath,
            ".wine/drive_c/Program Files (x86)/Steam/steamapps/workshop/content/$appId",
        )
    }

    fun cleanupUnsubscribedItems(
        subscribedItems: List<WorkshopItem>,
        workshopContentDir: File,
    ): Int {
        if (!workshopContentDir.exists()) return 0
        val subscribedIds = subscribedItems.map { it.publishedFileId }.toSet()
        val onDiskDirs = workshopContentDir.listFiles()
            ?.filter { it.isDirectory && it.name.toLongOrNull() != null }
            ?: return 0

        var removedCount = 0
        onDiskDirs.forEach { dir ->
            val id = dir.name.toLong()
            if (id !in subscribedIds && dir.deleteRecursively()) {
                removedCount++
            }
            File(workshopContentDir, "${dir.name}.partial").deleteRecursively()
        }
        return removedCount
    }

    fun getItemsNeedingSync(
        items: List<WorkshopItem>,
        workshopContentDir: File,
    ): List<WorkshopItem> {
        return items.filter { item ->
            if (item.fileUrl.isBlank()) {
                return@filter false
            }

            val itemDir = File(workshopContentDir, item.publishedFileId.toString())
            val markerFile = File(itemDir, COMPLETE_MARKER)
            val contentFiles = itemDir.listFiles()?.filter { !it.name.startsWith(".") }.orEmpty()
            if (!markerFile.exists() || contentFiles.isEmpty()) {
                return@filter true
            }

            val savedTimestamp = runCatching { markerFile.readText().trim().toLongOrNull() }.getOrNull()
            savedTimestamp == null || item.timeUpdated > savedTimestamp
        }
    }

    suspend fun syncEnabledItems(
        context: Context,
        appId: Int,
        containerId: Int?,
        enabledIds: Set<Long>,
        onStatus: (String) -> Unit = {},
    ): WorkshopSyncResult = withContext(Dispatchers.IO) {
        val containerManager = ContainerManager(context)
        val container = containerId?.let(containerManager::getContainerById)
            ?: PrefManager.preferredSteamContainerId.takeIf { it != 0 }?.let(containerManager::getContainerById)
            ?: containerManager.containers.firstOrNull()
            ?: return@withContext WorkshopSyncResult(failedCount = 1)

        val workshopContentDir = getWorkshopContentDir(container.rootDir.absolutePath, appId)
        workshopContentDir.mkdirs()

        if (enabledIds.isEmpty()) {
            val removed = cleanupAllWorkshopItems(workshopContentDir)
            return@withContext WorkshopSyncResult(removedCount = removed)
        }

        val steamClient = SteamService.instance?.steamClient ?: return@withContext WorkshopSyncResult(failedCount = 1)
        val steamId = SteamService.userSteamId ?: return@withContext WorkshopSyncResult(failedCount = 1)
        val fetchResult = getSubscribedItems(appId, steamClient, steamId)
        if (!fetchResult.succeeded) {
            return@withContext WorkshopSyncResult(failedCount = 1)
        }

        val enabledItems = fetchResult.items.filter { it.publishedFileId in enabledIds }
        val removed = if (fetchResult.isComplete) cleanupUnsubscribedItems(enabledItems, workshopContentDir) else 0
        val itemsToSync = getItemsNeedingSync(enabledItems, workshopContentDir)

        if (itemsToSync.isEmpty()) {
            updateMarkerTimestamps(enabledItems, workshopContentDir)
            return@withContext WorkshopSyncResult(
                skippedCount = enabledItems.size,
                removedCount = removed,
            )
        }

        var syncedCount = 0
        var unsupportedCount = 0
        var failedCount = 0

        itemsToSync.forEachIndexed { index, item ->
            try {
                ensureActiveStatus(index, itemsToSync.size, item.title, onStatus)
                if (item.fileUrl.isBlank()) {
                    unsupportedCount++
                } else {
                    downloadWorkshopItem(item, workshopContentDir)
                    syncedCount++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedCount++
                Timber.tag(TAG).w(e, "Failed to sync workshop item ${item.publishedFileId} for appId=$appId")
            }
        }

        updateMarkerTimestamps(enabledItems, workshopContentDir)

        WorkshopSyncResult(
            syncedCount = syncedCount,
            skippedCount = enabledItems.size - itemsToSync.size,
            removedCount = removed,
            unsupportedCount = unsupportedCount,
            failedCount = failedCount,
        )
    }

    private data class SubscribedFilesPage(
        val items: List<WorkshopItem>,
        val totalResults: Int,
    )

    private suspend fun fetchSubscribedFilesViaRPC(
        publishedFile: PublishedFile,
        appId: Int,
        steamId: SteamID,
        page: Int,
    ): SubscribedFilesPage? = withContext(Dispatchers.IO) {
        try {
            val request = CPublishedFile_GetUserFiles_Request.newBuilder().apply {
                steamid = steamId.convertToUInt64()
                appid = appId
                this.page = page
                numperpage = PAGE_SIZE
                type = "mysubscriptions"
                filetype = 0xFFFFFFFF.toInt()
            }.build()

            val response = withTimeoutOrNull(30_000L) {
                publishedFile.getUserFiles(request).toFuture().await()
            } ?: return@withContext null

            if (response.result != EResult.OK) {
                return@withContext null
            }

            val body = response.body.build()
            val items = body.publishedfiledetailsList.map { details ->
                WorkshopItem(
                    publishedFileId = details.publishedfileid,
                    appId = if (details.consumerAppid != 0) details.consumerAppid else appId,
                    title = details.title.ifEmpty { details.publishedfileid.toString() },
                    fileSizeBytes = details.fileSize,
                    manifestId = details.hcontentFile,
                    timeUpdated = details.timeUpdated.toLong(),
                    fileUrl = details.fileUrl ?: "",
                    fileName = details.filename ?: "",
                    previewUrl = details.previewUrl ?: "",
                )
            }

            SubscribedFilesPage(
                items = items,
                totalResults = body.total,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to fetch workshop subscriptions for appId=$appId page=$page")
            null
        }
    }

    private fun updateMarkerTimestamps(
        items: List<WorkshopItem>,
        workshopContentDir: File,
    ) {
        items.forEach { item ->
            val itemDir = File(workshopContentDir, item.publishedFileId.toString())
            if (!itemDir.isDirectory) return@forEach
            val markerFile = File(itemDir, COMPLETE_MARKER)
            markerFile.writeText(item.timeUpdated.toString())
        }
    }

    private fun cleanupAllWorkshopItems(workshopContentDir: File): Int {
        if (!workshopContentDir.exists()) return 0
        var removed = 0
        workshopContentDir.listFiles()?.forEach { entry ->
            if (entry.deleteRecursively()) {
                removed++
            }
        }
        return removed
    }

    private fun ensureActiveStatus(
        index: Int,
        total: Int,
        title: String,
        onStatus: (String) -> Unit,
    ) {
        onStatus("$title (${index + 1}/$total)")
    }

    private fun downloadWorkshopItem(item: WorkshopItem, workshopContentDir: File) {
        val itemDir = File(workshopContentDir, item.publishedFileId.toString())
        val partialDir = File(workshopContentDir, "${item.publishedFileId}.partial")
        itemDir.mkdirs()
        partialDir.deleteRecursively()

        val fileName = resolveOutputFileName(item)
        val destination = File(itemDir, fileName)
        val tempFile = File(itemDir, "$fileName.part")
        val request = Request.Builder().url(item.fileUrl).build()

        httpClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "HTTP ${response.code}" }
            val body = response.body ?: error("Empty body")
            tempFile.outputStream().use { output ->
                body.byteStream().copyTo(output)
            }
        }

        if (!tempFile.renameTo(destination)) {
            tempFile.copyTo(destination, overwrite = true)
            tempFile.delete()
        }

        itemDir.listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") && it.name != destination.name }
            ?.forEach { it.delete() }
    }

    private fun resolveOutputFileName(item: WorkshopItem): String {
        val fromMetadata = item.fileName.substringAfterLast('/').substringAfterLast('\\').trim()
        if (fromMetadata.isNotBlank()) {
            return fromMetadata
        }

        val urlName = item.fileUrl.substringAfterLast('/').substringBefore('?').trim()
        if (urlName.isNotBlank()) {
            return urlName
        }

        return item.publishedFileId.toString()
    }
}
