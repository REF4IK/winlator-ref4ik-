package com.winlator.cmod.steam.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber

object PrefManager {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val legacyPrefs = context.getSharedPreferences("PluviaPreferences", Context.MODE_PRIVATE)

        prefs = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "PluviaPreferences_enc",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Timber.e(e, "EncryptedSharedPreferences init failed")
            throw RuntimeException("Failed to initialize secure storage", e)
        }

        migrateLegacyPrefsIfNeeded(legacyPrefs, context)
    }

    private fun migrateLegacyPrefsIfNeeded(legacyPrefs: SharedPreferences, context: Context) {
        val encryptedPrefs = prefs ?: return
        val legacyEntries = legacyPrefs.all
        if (legacyEntries.isEmpty()) return

        if (encryptedPrefs.all.isEmpty()) {
            val editor = encryptedPrefs.edit()
            for ((key, value) in legacyEntries) {
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                }
            }
            editor.apply()
            Timber.i("Migrated legacy Steam preferences into encrypted storage")
        }

        context.deleteSharedPreferences("PluviaPreferences")
    }

    private fun getString(key: String, defaultValue: String): String = prefs?.getString(key, defaultValue) ?: defaultValue
    private fun setString(key: String, value: String) { prefs?.edit()?.putString(key, value)?.apply() }
    
    private fun getInt(key: String, defaultValue: Int): Int = prefs?.getInt(key, defaultValue) ?: defaultValue
    private fun setInt(key: String, value: Int) { prefs?.edit()?.putInt(key, value)?.apply() }
    
    private fun getLong(key: String, defaultValue: Long): Long = prefs?.getLong(key, defaultValue) ?: defaultValue
    private fun setLong(key: String, value: Long) { prefs?.edit()?.putLong(key, value)?.apply() }
    
    private fun getBoolean(key: String, defaultValue: Boolean): Boolean = prefs?.getBoolean(key, defaultValue) ?: defaultValue
    private fun setBoolean(key: String, value: Boolean) { prefs?.edit()?.putBoolean(key, value)?.apply() }

    var username: String
        get() = getString("user_name", "")
        set(value) { setString("user_name", value) }

    var refreshToken: String
        get() = getString("refresh_token", "")
        set(value) { setString("refresh_token", value) }

    var accessToken: String
        get() = getString("access_token", "")
        set(value) { setString("access_token", value) }

    var steamUserSteamId64: Long
        get() = getLong("steam_user_steam_id_64", 0L)
        set(value) { setLong("steam_user_steam_id_64", value) }

    var steamUserAccountId: Int
        get() = getInt("steam_user_account_id", 0)
        set(value) { setInt("steam_user_account_id", value) }

    var cellId: Int
        get() = getInt("cell_id", 0)
        set(value) { setInt("cell_id", value) }

    var cellIdManuallySet: Boolean
        get() = getBoolean("cell_id_manually_set", false)
        set(value) { setBoolean("cell_id_manually_set", value) }

    var downloadOnWifiOnly: Boolean
        get() = getBoolean("download_on_wifi_only", true)
        set(value) { setBoolean("download_on_wifi_only", value) }
        
    var lastPICSChangeNumber: Int
        get() = getInt("last_pics_change_number", 0)
        set(value) { setInt("last_pics_change_number", value) }

    var steamUserName: String
        get() = getString("steam_user_name", "")
        set(value) { setString("steam_user_name", value) }

    var steamUserAvatarHash: String
        get() = getString("steam_user_avatar_hash", "")
        set(value) { setString("steam_user_avatar_hash", value) }

    var personaState: Int
        get() = getInt("persona_state", 0)
        set(value) { setInt("persona_state", value) }

    var externalStoragePath: String
        get() = getString("external_storage_path", "")
        set(value) { setString("external_storage_path", value) }

    var useExternalStorage: Boolean
        get() = getBoolean("use_external_storage", false)
        set(value) { setBoolean("use_external_storage", value) }
        
    var containerLanguage: String
        get() = getString("container_language", "english")
        set(value) { setString("container_language", value) }
        
    var downloadSpeed: Int
        get() = getInt("download_speed", 32)
        set(value) { setInt("download_speed", value) }
        
    var clientId: Long
        get() = getLong("client_id", 0L)
        set(value) { setLong("client_id", value) }

    var libraryLayoutMode: String
        get() = getString("library_layout_mode", "GRID_4")
        set(value) { setString("library_layout_mode", value) }

    var enableSteamLogs: Boolean
        get() = getBoolean("enable_steam_logs", false)
        set(value) { setBoolean("enable_steam_logs", value) }

    var useSingleDownloadFolder: Boolean
        get() = getBoolean("use_single_download_folder", true)
        set(value) { setBoolean("use_single_download_folder", value) }

    var defaultDownloadFolder: String
        get() = getString("default_download_folder", "")
        set(value) { setString("default_download_folder", value) }

    var steamDownloadFolder: String
        get() = getString("steam_download_folder", "")
        set(value) { setString("steam_download_folder", value) }

    var preferredSteamContainerId: Int
        get() = getInt("preferred_steam_container_id", 0)
        set(value) { setInt("preferred_steam_container_id", value) }

    var epicDownloadFolder: String
        get() = getString("epic_download_folder", "")
        set(value) { setString("epic_download_folder", value) }

    var gogDownloadFolder: String
        get() = getString("gog_download_folder", "")
        set(value) { setString("gog_download_folder", value) }

    var amazonDownloadFolder: String
        get() = getString("amazon_download_folder", "")
        set(value) { setString("amazon_download_folder", value) }

    var downloadQueueSize: Int
        get() = getInt("download_queue_size", 1)
        set(value) { setInt("download_queue_size", value) }

    var steamOfflineMode: Boolean
        get() = getBoolean("steam_offline_mode", false)
        set(value) { setBoolean("steam_offline_mode", value) }

    private var pendingSteamCloudSyncAppsRaw: String
        get() = getString("pending_steam_cloud_sync_apps", "")
        set(value) { setString("pending_steam_cloud_sync_apps", value) }

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
        return getString("steam_selected_branch_$appId", defaultBranch).ifBlank { defaultBranch }
    }

    fun setSteamSelectedBranch(appId: Int, branch: String) {
        if (appId <= 0) return
        setString("steam_selected_branch_$appId", branch.ifBlank { "public" })
    }

    fun clearSteamSelectedBranch(appId: Int) {
        if (appId <= 0) return
        prefs?.edit()?.remove("steam_selected_branch_$appId")?.apply()
    }

    fun getSteamWorkshopEnabledItemIds(appId: Int): Set<Long> {
        if (appId <= 0) return emptySet()
        return getString("steam_workshop_enabled_items_$appId", "")
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
        setString("steam_workshop_enabled_items_$appId", serialized)
    }

    fun hasSteamWorkshopEnabledItems(appId: Int): Boolean {
        return getSteamWorkshopEnabledItemIds(appId).isNotEmpty()
    }

    fun clearAuthTokens() {
        prefs?.edit()?.apply {
            remove("user_name")
            remove("refresh_token")
            remove("access_token")
            remove("steam_user_steam_id_64")
            remove("steam_user_account_id")
            remove("steam_user_name")
            remove("steam_user_avatar_hash")
            remove("persona_state")
            commit()
        }
    }

    fun clearPreferences() {
        prefs?.edit()?.clear()?.commit()
    }
    
    // Legacy support for Winlator properties if needed
    var graphicsDriver: String
        get() = getString("graphics_driver", "virgl")
        set(value) { setString("graphics_driver", value) }
}
