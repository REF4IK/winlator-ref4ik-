package com.winlator.cmod.steam.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tencent.mmkv.MMKV
import timber.log.Timber
import java.security.SecureRandom

object PrefManager {
    private const val MMKV_ID = "PluviaPreferences"
    private const val KEY_CRYPT_KEY = "mmkv_crypt_key"
    private var mmkv: MMKV? = null

    fun init(context: Context) {
        val cryptKey = getOrCreateCryptKey()
        mmkv = MMKV.mmkvWithID(MMKV_ID, 0, cryptKey)

        migrateLegacyPrefsIfNeeded(context)
    }

    private fun getOrCreateCryptKey(): String {
        val root = MMKV.defaultMMKV()
        var key = root.decodeString(KEY_CRYPT_KEY)
        if (key.isNullOrBlank()) {
            val random = ByteArray(16)
            SecureRandom().nextBytes(random)
            key = random.joinToString("") { "%02x".format(it) }
            root.encode(KEY_CRYPT_KEY, key)
            root.sync()
        }
        return key
    }

    private fun migrateLegacyPrefsIfNeeded(context: Context) {
        val current = mmkv ?: return

        if (current.contains("migrated_from_encrypted")) return

        val legacyPrefs = context.getSharedPreferences("PluviaPreferences", Context.MODE_PRIVATE)
        migrateFromPrefs(legacyPrefs, current)
        context.deleteSharedPreferences("PluviaPreferences")

        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                "PluviaPreferences_enc",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            migrateFromPrefs(encryptedPrefs, current)
        } catch (e: Exception) {
            Timber.d("No encrypted legacy prefs to migrate: ${e.message}")
        }

        current.encode("migrated_from_encrypted", true)
        current.sync()
    }

    private fun migrateFromPrefs(old: SharedPreferences, new: MMKV) {
        val entries = old.all
        if (entries.isEmpty()) return
        for ((key, value) in entries) {
            when (value) {
                is String -> new.encode(key, value)
                is Int -> new.encode(key, value)
                is Long -> new.encode(key, value)
                is Boolean -> new.encode(key, value)
                is Float -> new.encode(key, value)
            }
        }
        new.sync()
        Timber.i("Migrated ${entries.size} keys from legacy prefs to MMKV")
    }

    private fun encode(key: String, value: String) { mmkv?.encode(key, value) }
    private fun encode(key: String, value: Int) { mmkv?.encode(key, value) }
    private fun encode(key: String, value: Long) { mmkv?.encode(key, value) }
    private fun encode(key: String, value: Boolean) { mmkv?.encode(key, value) }

    private fun decodeString(key: String, default: String): String = mmkv?.decodeString(key, default) ?: default
    private fun decodeInt(key: String, default: Int): Int = mmkv?.decodeInt(key, default) ?: default
    private fun decodeLong(key: String, default: Long): Long = mmkv?.decodeLong(key, default) ?: default
    private fun decodeBool(key: String, default: Boolean): Boolean = mmkv?.decodeBool(key, default) ?: default

    var username: String
        get() = decodeString("user_name", "")
        set(value) { encode("user_name", value) }

    var refreshToken: String
        get() = decodeString("refresh_token", "")
        set(value) { encode("refresh_token", value) }

    var accessToken: String
        get() = decodeString("access_token", "")
        set(value) { encode("access_token", value) }

    var steamUserSteamId64: Long
        get() = decodeLong("steam_user_steam_id_64", 0L)
        set(value) { encode("steam_user_steam_id_64", value) }

    var steamUserAccountId: Int
        get() = decodeInt("steam_user_account_id", 0)
        set(value) { encode("steam_user_account_id", value) }

    var cellId: Int
        get() = decodeInt("cell_id", 0)
        set(value) { encode("cell_id", value) }

    var cellIdManuallySet: Boolean
        get() = decodeBool("cell_id_manually_set", false)
        set(value) { encode("cell_id_manually_set", value) }

    var downloadOnWifiOnly: Boolean
        get() = decodeBool("download_on_wifi_only", false)
        set(value) { encode("download_on_wifi_only", value) }
        
    var lastPICSChangeNumber: Int
        get() = decodeInt("last_pics_change_number", 0)
        set(value) { encode("last_pics_change_number", value) }

    var steamUserName: String
        get() = decodeString("steam_user_name", "")
        set(value) { encode("steam_user_name", value) }

    var steamUserAvatarHash: String
        get() = decodeString("steam_user_avatar_hash", "")
        set(value) { encode("steam_user_avatar_hash", value) }

    var personaState: Int
        get() = decodeInt("persona_state", 0)
        set(value) { encode("persona_state", value) }

    var externalStoragePath: String
        get() = decodeString("external_storage_path", "")
        set(value) { encode("external_storage_path", value) }

    var useExternalStorage: Boolean
        get() = decodeBool("use_external_storage", false)
        set(value) { encode("use_external_storage", value) }
        
    var containerLanguage: String
        get() = decodeString("container_language", "english")
        set(value) { encode("container_language", value) }
        
    var downloadSpeed: Int
        get() = decodeInt("download_speed", 16).coerceIn(8, 32)
        set(value) { encode("download_speed", value.coerceIn(8, 32)) }
        
    var clientId: Long
        get() = decodeLong("client_id", 0L)
        set(value) { encode("client_id", value) }

    var libraryLayoutMode: String
        get() = decodeString("library_layout_mode", "GRID_4")
        set(value) { encode("library_layout_mode", value) }

    var enableSteamLogs: Boolean
        get() = decodeBool("enable_steam_logs", false)
        set(value) { encode("enable_steam_logs", value) }

    var useSingleDownloadFolder: Boolean
        get() = decodeBool("use_single_download_folder", true)
        set(value) { encode("use_single_download_folder", value) }

    var defaultDownloadFolder: String
        get() = decodeString("default_download_folder", "")
        set(value) { encode("default_download_folder", value) }

    var steamDownloadFolder: String
        get() = decodeString("steam_download_folder", "")
        set(value) { encode("steam_download_folder", value) }

    var preferredSteamContainerId: Int
        get() = decodeInt("preferred_steam_container_id", 0)
        set(value) { encode("preferred_steam_container_id", value) }

    var epicDownloadFolder: String
        get() = decodeString("epic_download_folder", "")
        set(value) { encode("epic_download_folder", value) }

    var gogDownloadFolder: String
        get() = decodeString("gog_download_folder", "")
        set(value) { encode("gog_download_folder", value) }

    var amazonDownloadFolder: String
        get() = decodeString("amazon_download_folder", "")
        set(value) { encode("amazon_download_folder", value) }

    var downloadQueueSize: Int
        get() = decodeInt("download_queue_size", 3)
        set(value) { encode("download_queue_size", value) }

    var steamOfflineMode: Boolean
        get() = decodeBool("steam_offline_mode", false)
        set(value) { encode("steam_offline_mode", value) }

    var autoUpdateEnabled: Boolean
        get() = decodeBool("auto_update_enabled", false)
        set(value) { encode("auto_update_enabled", value) }

    var autoUpdateWifiOnly: Boolean
        get() = decodeBool("auto_update_wifi_only", true)
        set(value) { encode("auto_update_wifi_only", value) }

    private var pendingSteamCloudSyncAppsRaw: String
        get() = decodeString("pending_steam_cloud_sync_apps", "")
        set(value) { encode("pending_steam_cloud_sync_apps", value) }

    fun getPendingSteamCloudSyncAppIds(): Set<Int> {
        return pendingSteamCloudSyncAppsRaw
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it > 0 }
            .toSet()
    }

    fun hasPendingSteamCloudSyncAppId(appId: Int): Boolean {
        return appId > 0 && getPendingSteamCloudSyncAppIds().contains(appId)
    }

    fun addPendingSteamCloudSyncAppId(appId: Int) {
        if (appId <= 0) return
        val updated = getPendingSteamCloudSyncAppIds().toMutableSet()
        updated.add(appId)
        pendingSteamCloudSyncAppsRaw = updated.sorted().joinToString(",")
    }

    fun removePendingSteamCloudSyncAppId(appId: Int) {
        if (appId <= 0) return
        val updated = getPendingSteamCloudSyncAppIds().toMutableSet()
        updated.remove(appId)
        pendingSteamCloudSyncAppsRaw = updated.sorted().joinToString(",")
    }

    fun getSteamSelectedBranch(appId: Int, defaultBranch: String = "public"): String {
        if (appId <= 0) return defaultBranch
        return decodeString("steam_selected_branch_$appId", defaultBranch).ifBlank { defaultBranch }
    }

    fun setSteamSelectedBranch(appId: Int, branch: String) {
        if (appId <= 0) return
        encode("steam_selected_branch_$appId", branch.ifBlank { "public" })
    }

    fun clearSteamSelectedBranch(appId: Int) {
        if (appId <= 0) return
        mmkv?.remove("steam_selected_branch_$appId")
    }

    var steamFriendsRequestBatchSize: Int
        get() = decodeInt("steam_friends_request_batch_size", 100)
        set(value) { encode("steam_friends_request_batch_size", value) }

    var chatHistoryKeepCount: Int
        get() = decodeInt("chat_history_keep_count", 500)
        set(value) { encode("chat_history_keep_count", value) }

    fun getSteamWorkshopEnabledItemIds(appId: Int): Set<Long> {
        if (appId <= 0) return emptySet()
        return decodeString("steam_workshop_enabled_items_$appId", "")
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it > 0L }
            .toSet()
    }

    fun setSteamWorkshopEnabledItemIds(appId: Int, itemIds: Set<Long>) {
        if (appId <= 0) return
        val serialized = itemIds
            .filter { it > 0L }
            .sorted()
            .joinToString(",")
        encode("steam_workshop_enabled_items_$appId", serialized)
    }

    fun hasSteamWorkshopEnabledItems(appId: Int): Boolean {
        return getSteamWorkshopEnabledItemIds(appId).isNotEmpty()
    }

    fun clearAuthTokens() {
        val kv = mmkv ?: return
        kv.remove("user_name")
        kv.remove("refresh_token")
        kv.remove("access_token")
        kv.remove("steam_user_steam_id_64")
        kv.remove("steam_user_account_id")
        kv.remove("steam_user_name")
        kv.remove("steam_user_avatar_hash")
        kv.remove("persona_state")
        kv.sync()
    }

    fun clearPreferences() {
        mmkv?.clearAll()
        mmkv?.sync()
    }
    
    var graphicsDriver: String
        get() = decodeString("graphics_driver", "virgl")
        set(value) { encode("graphics_driver", value) }
}
