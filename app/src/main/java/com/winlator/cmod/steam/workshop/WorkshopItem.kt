package com.winlator.cmod.steam.workshop

data class WorkshopItem(
    val publishedFileId: Long,
    val appId: Int,
    val title: String,
    val fileSizeBytes: Long,
    val manifestId: Long,
    val timeUpdated: Long,
    val fileUrl: String = "",
    val fileName: String = "",
    val previewUrl: String = "",
)

data class WorkshopFetchResult(
    val items: List<WorkshopItem>,
    val succeeded: Boolean,
    val isComplete: Boolean = false,
)

data class WorkshopSyncResult(
    val syncedCount: Int = 0,
    val skippedCount: Int = 0,
    val removedCount: Int = 0,
    val unsupportedCount: Int = 0,
    val failedCount: Int = 0,
) {
    val succeeded: Boolean
        get() = failedCount == 0
}
