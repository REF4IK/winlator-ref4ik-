package com.winlator.cmod.steam.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import android.widget.Toast
import androidx.room.withTransaction
import com.winlator.cmod.BuildConfig
import com.winlator.cmod.R
import com.winlator.cmod.utils.NetworkMonitor
import com.winlator.cmod.PluviaApp
import com.winlator.cmod.steam.utils.PrefManager
import com.winlator.cmod.steam.data.DepotInfo
import com.winlator.cmod.steam.data.DownloadFailedException
import com.winlator.cmod.steam.data.StallTimeoutException
import com.winlator.cmod.steam.enums.DownloadPhase
import com.winlator.cmod.steam.data.DownloadInfo
import com.winlator.cmod.steam.data.GameProcessInfo
import com.winlator.cmod.steam.data.LaunchInfo
import com.winlator.cmod.steam.data.ManifestInfo
import com.winlator.cmod.steam.data.OwnedGames
import com.winlator.cmod.steam.data.PostSyncInfo
import com.winlator.cmod.steam.data.SteamApp
import com.winlator.cmod.steam.data.SteamControllerConfigDetail
import com.winlator.cmod.steam.data.ChatMessage
import com.winlator.cmod.steam.data.ChatMessageEntity
import com.winlator.cmod.steam.data.SteamFriend
import com.winlator.cmod.steam.data.SteamLicense
import com.winlator.cmod.steam.data.UserFileInfo
import com.winlator.cmod.steam.data.EncryptedAppTicket
import com.winlator.cmod.db.PluviaDatabase
import com.winlator.cmod.steam.db.dao.ChangeNumbersDao
import com.winlator.cmod.steam.db.dao.FileChangeListsDao
import com.winlator.cmod.steam.db.dao.SteamAppDao
import com.winlator.cmod.steam.db.dao.SteamLicenseDao
import com.winlator.cmod.steam.db.dao.CachedLicenseDao
import com.winlator.cmod.steam.enums.LoginResult
import com.winlator.cmod.steam.enums.OS
import com.winlator.cmod.steam.enums.OSArch
import com.winlator.cmod.steam.enums.PathType
import com.winlator.cmod.steam.enums.SaveLocation
import com.winlator.cmod.steam.enums.SyncResult
import com.winlator.cmod.steam.events.AndroidEvent
import com.winlator.cmod.steam.events.SteamEvent
import com.winlator.cmod.steam.utils.Net
import com.winlator.cmod.steam.utils.SteamUtils
import com.winlator.cmod.steam.utils.MarkerUtils
import com.winlator.cmod.steam.utils.ContainerUtils
import com.winlator.cmod.steam.enums.Marker
import com.winlator.cmod.steam.utils.generateSteamApp
import com.winlator.cmod.utils.NotificationHelper
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.xenvironment.ImageFs
import com.winlator.cmod.service.DownloadService
import com.winlator.cmod.utils.StorageUtils
import com.winlator.cmod.steam.enums.Language
import com.winlator.cmod.steam.enums.AppType
import com.winlator.cmod.steam.enums.ControllerSupport
import com.winlator.cmod.steam.enums.GameSource
import com.winlator.cmod.core.GPUInformation
import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.enums.EDepotFileFlag
import `in`.dragonbra.javasteam.enums.ELicenseFlags
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.enums.EOSType
import `in`.dragonbra.javasteam.enums.EChatEntryType
import `in`.dragonbra.javasteam.enums.EPersonaState
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientObjects.ECloudPendingRemoteOperation
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesFamilygroupsSteamclient
import `in`.dragonbra.javasteam.rpc.service.FamilyGroups
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUserstats
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesAuthSteamclient.CAuthentication_PollAuthSessionStatus_Request
import `in`.dragonbra.javasteam.rpc.service.Authentication
import `in`.dragonbra.javasteam.steam.authentication.AuthPollResult
import `in`.dragonbra.javasteam.steam.authentication.AuthSessionDetails
import `in`.dragonbra.javasteam.steam.authentication.AuthenticationException
import `in`.dragonbra.javasteam.steam.authentication.IAuthenticator
import `in`.dragonbra.javasteam.steam.authentication.IChallengeUrlChanged
import `in`.dragonbra.javasteam.steam.authentication.QrAuthSession
import `in`.dragonbra.javasteam.depotdownloader.DepotDownloader
import `in`.dragonbra.javasteam.depotdownloader.IDownloadListener
import `in`.dragonbra.javasteam.depotdownloader.Steam3Session
import `in`.dragonbra.javasteam.steam.discovery.FileServerListProvider
import `in`.dragonbra.javasteam.steam.discovery.ServerQuality
import `in`.dragonbra.javasteam.steam.handlers.steamapps.GamePlayedInfo
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.LicenseListCallback
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.SteamFriends
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.FriendMsgCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.PersonaStateCallback
import `in`.dragonbra.javasteam.steam.handlers.steamgameserver.SteamGameServer
import `in`.dragonbra.javasteam.steam.handlers.steammasterserver.SteamMasterServer
import `in`.dragonbra.javasteam.steam.handlers.steamscreenshots.SteamScreenshots
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages
import `in`.dragonbra.javasteam.steam.handlers.steamuser.ChatMode
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuserstats.SteamUserStats
import `in`.dragonbra.javasteam.steam.handlers.steamworkshop.SteamWorkshop
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import `in`.dragonbra.javasteam.types.FileData
import `in`.dragonbra.javasteam.types.KeyValue
import `in`.dragonbra.javasteam.types.PublishedFileID
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.util.log.LogListener
import `in`.dragonbra.javasteam.util.log.LogManager
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Collections
import java.util.EnumSet
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.lang.NullPointerException
import com.winlator.cmod.steam.data.AppInfo
import com.winlator.cmod.steam.db.dao.AppInfoDao
import kotlinx.coroutines.ensureActive
import com.winlator.cmod.steam.utils.LicenseSerializer
import com.winlator.cmod.steam.utils.CdnRankingUtils
import com.winlator.cmod.steam.utils.DownloadSpeedConfig
import com.winlator.cmod.steam.data.CachedLicense
import `in`.dragonbra.javasteam.depotdownloader.data.AppItem
import `in`.dragonbra.javasteam.depotdownloader.data.DownloadItem
import `in`.dragonbra.javasteam.steam.cdn.Server
import `in`.dragonbra.javasteam.steam.handlers.steamapps.License
import `in`.dragonbra.javasteam.steam.handlers.steamcontent.SteamContent
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.PlayingSessionStateCallback
import `in`.dragonbra.javasteam.steam.steamclient.AsyncJobFailedException
import `in`.dragonbra.javasteam.types.DepotManifest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import com.winlator.cmod.steam.data.DownloadingAppInfo
import com.winlator.cmod.steam.db.dao.DownloadingAppInfoDao
import com.winlator.cmod.steam.db.dao.EncryptedAppTicketDao
import kotlinx.coroutines.flow.update
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.nio.ByteBuffer
import java.nio.ByteOrder
import `in`.dragonbra.javasteam.steam.handlers.steamuserstats.Stats
import com.winlator.cmod.steam.statsgen.StatType
import com.winlator.cmod.steam.statsgen.StatsAchievementsGenerator
import com.winlator.cmod.steam.statsgen.VdfParser

class SteamService : Service(), IChallengeUrlChanged {

    // To view log messages in android logcat properly
    private val logger = object : LogListener {
        override fun onLog(clazz: Class<*>, message: String?, throwable: Throwable?) {
            val logMessage = message ?: "No message given"
            Timber.i(throwable, "[${clazz.simpleName}] -> $logMessage")
        }

        override fun onError(clazz: Class<*>, message: String?, throwable: Throwable?) {
            val logMessage = message ?: "No message given"
            Timber.e(throwable, "[${clazz.simpleName}] -> $logMessage")
        }
    }

    lateinit var db: PluviaDatabase

    lateinit var licenseDao: SteamLicenseDao

    lateinit var appDao: SteamAppDao

    lateinit var changeNumbersDao: ChangeNumbersDao

    lateinit var appInfoDao: AppInfoDao

    lateinit var fileChangeListsDao: FileChangeListsDao

    lateinit var cachedLicenseDao: CachedLicenseDao

    lateinit var encryptedAppTicketDao: EncryptedAppTicketDao

    lateinit var downloadingAppInfoDao: DownloadingAppInfoDao
    lateinit var chatMessageDao: com.winlator.cmod.steam.db.dao.ChatMessageDao

    private lateinit var notificationHelper: NotificationHelper

    internal var callbackManager: CallbackManager? = null
    internal var steamClient: SteamClient? = null
    internal val callbackSubscriptions: ArrayList<Closeable> = ArrayList()

    private var _unifiedFriends: SteamUnifiedFriends? = null
    private var _steamUser: SteamUser? = null
    private var _steamApps: SteamApps? = null
    private var _steamFriends: SteamFriends? = null
    private var _steamCloud: SteamCloud? = null
    private var _steamUserStats: SteamUserStats? = null
    private var _steamFamilyGroups: FamilyGroups? = null

    private var _loginResult: LoginResult = LoginResult.Failed

    private var licenses: List<License> = emptyList()

    private var retryAttempt = 0

    private val appPicsChannel = Channel<List<PICSRequest>>(
        capacity = 1_000,
        onBufferOverflow = BufferOverflow.SUSPEND,
        onUndeliveredElement = { droppedApps ->
            Timber.w("App PICS Channel dropped: ${droppedApps.size} apps")
        },
    )

    private val packagePicsChannel = Channel<List<PICSRequest>>(
        capacity = 1_000,
        onBufferOverflow = BufferOverflow.SUSPEND,
        onUndeliveredElement = { droppedPackages ->
            Timber.w("Package PICS Channel dropped: ${droppedPackages.size} packages")
        },
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val onEndProcess: (AndroidEvent.EndProcess) -> Unit = {
        Companion.stop()
    }

    // The current shared family group the logged in user is joined to.
    private var familyGroupMembers: ArrayList<Int> = arrayListOf()

    private val appTokens: ConcurrentHashMap<Int, Long> = ConcurrentHashMap()

    // Connectivity management for Wi-Fi-only downloads
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    // Add these as class properties
    private var picsGetProductInfoJob: Job? = null
    private var picsChangesCheckerJob: Job? = null
    private var friendCheckerJob: Job? = null
    private var autoUpdateCheckerJob: Job? = null
    private var keepaliveJob: Job? = null

    @Volatile
    private var picsConsecutiveFailures = 0

    private val _isPlayingBlocked = MutableStateFlow(false)
    val isPlayingBlocked = _isPlayingBlocked.asStateFlow()

    // Cache in-memory the local persona state.
    private val _localPersona = MutableStateFlow(
        SteamFriend(name = PrefManager.steamUserName, avatarHash = PrefManager.steamUserAvatarHash),
    )
    val localPersona = _localPersona.asStateFlow()

    // Cache in-memory the friends list.
    private val _friendsList = MutableStateFlow<List<SteamFriend>>(emptyList())
    val friendsList = _friendsList.asStateFlow()

    // How often to refresh friends (ms)
    private var friendsAutoRefreshJob: Job? = null

    // Chat messages per friend (steamId64 -> list of messages)
    private val _chatMessages = MutableStateFlow<Map<Long, List<ChatMessage>>>(emptyMap())
    val chatMessages = _chatMessages.asStateFlow()

data class ManifestSizes(
        val installSize: Long = 0L,
        val downloadSize: Long = 0L,
    )

    private data class RunningWineProcessInfo(
        val processId: Int,
        val parentProcessId: Int,
        val name: String,
    )

    companion object {
        const val MAX_PICS_BUFFER = 256

        const val MAX_RETRY_ATTEMPTS = 20

        const val INVALID_APP_ID: Int = Int.MAX_VALUE
        const val INVALID_PKG_ID: Int = Int.MAX_VALUE
        private const val STEAM_CONTROLLER_CONFIG_FILENAME = "steam_controller_config.vdf"
        private const val DOWNLOAD_INFO_DIR = ".DownloadInfo"
        private const val DOWNLOAD_INFO_FILE = "depot_bytes.json"
        private const val LEGACY_DOWNLOAD_INFO_FILE = "bytes_downloaded.txt"
        private const val COMPONENTS_BASE_URL = "https://github.com/maxjivi05/Components/releases/download/Components"

        /**
         * Default timeout to use when making requests
         */
        var requestTimeout = 30.seconds

        /**
         * Default timeout to use when reading the response body
         */
        var responseTimeout = 120.seconds

        private val PROTOCOL_TYPES = EnumSet.of(ProtocolTypes.TCP, ProtocolTypes.WEB_SOCKET)

        internal var instance: SteamService? = null

        var cachedAchievements: List<com.winlator.cmod.steam.statsgen.Achievement>? = null
            private set
        var cachedAchievementsAppId: Int? = null
            private set

        fun clearCachedAchievements() {
            cachedAchievements = null
            cachedAchievementsAppId = null
        }

        val isWifiConnected: Boolean get() = NetworkMonitor.isWifiConnected.value

        private fun downloadUrlsFor(fileName: String): List<String> {
            val alternate = when (fileName) {
                "steam-token.tzst" -> "steam-token-r2.tzst"
                else -> null
            }
            return if (alternate != null) {
                listOf(
                    "$COMPONENTS_BASE_URL/$fileName",
                    "$COMPONENTS_BASE_URL/$alternate",
                )
            } else {
                listOf("$COMPONENTS_BASE_URL/$fileName")
            }
        }

        /** @return true if download may proceed; false if blocked (notifies user) */
        private fun checkWifiOrNotify(): Boolean {
            if (PrefManager.downloadOnWifiOnly && !isWifiConnected) {
                val svc = instance
                if (svc != null) {
                    svc.notificationHelper.notify(svc.getString(R.string.downloads_queue_no_wifi))
                } else {
                    Timber.w("checkWifiOrNotify: no SteamService instance to notify")
                }
                return false
            }
            return true
        }

        fun pauseAll() {
            downloadJobs.values.forEach { info ->
                val status = info.getStatusFlow().value
                when {
                    info.isActive() -> {
                        info.isCancelling = false
                        info.updateStatus(DownloadPhase.PAUSED)
                        info.cancel("Paused all")
                    }
                    status == DownloadPhase.QUEUED -> {
                        info.updateStatus(DownloadPhase.PAUSED)
                        info.setActive(false)
                    }
                }
            }
            Unit
        }

        fun pauseDownload(appId: Int) {
            val info = downloadJobs[appId] ?: return
            val status = info.getStatusFlow().value
            if (status == DownloadPhase.COMPLETE || status == DownloadPhase.CANCELLED) return

            if (info.isActive()) {
                info.isCancelling = false
                info.updateStatus(DownloadPhase.PAUSED)
                info.cancel("Paused by user")
            } else if (status == DownloadPhase.QUEUED) {
                info.updateStatus(DownloadPhase.PAUSED)
                info.setActive(false)
            }
        }

        fun resumeAll() {
            downloadJobs.keys.toList().forEach(::resumeDownload)
            Unit
        }

        fun resumeDownload(appId: Int) {
            val info = downloadJobs[appId] ?: run {
                downloadApp(appId)
                return
            }
            val status = info.getStatusFlow().value
            if (!info.isActive() && (status == DownloadPhase.QUEUED || status == DownloadPhase.PAUSED || status == DownloadPhase.FAILED)) {
                downloadApp(appId)
            }
        }

        fun cancelAll() {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                downloadJobs.entries.toList().forEach { (appId, info) ->
                    info.isCancelling = true
                    info.cancel("Cancelled all")
                    info.awaitCompletion(timeoutMs = 3000L)
                    // Delete partially downloaded files
                    val appDirPath = getAppDirPath(appId)
                    val dirFile = java.io.File(appDirPath)
                    if (dirFile.exists() && dirFile.isDirectory) {
                        MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                        MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                        deleteRecursivelyWithRetries(dirFile)
                    }
                    deleteStagingDir(appId)
                    info.updateStatus(DownloadPhase.CANCELLED)
                    removeDownloadJob(appId, forceRemove = true)
                }
            }
            Unit
        }

        fun cancelDownload(appId: Int) {
            val info = downloadJobs[appId] ?: return
            info.isCancelling = true
            info.cancel("Cancelled by user")
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                info.awaitCompletion(timeoutMs = 3000L)
                val appDirPath = getAppDirPath(appId)
                val dirFile = java.io.File(appDirPath)
                if (dirFile.exists() && dirFile.isDirectory) {
                    MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                    MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                    deleteRecursivelyWithRetries(dirFile)
                }
                deleteStagingDir(appId)
                info.updateStatus(DownloadPhase.CANCELLED)
                removeDownloadJob(appId, forceRemove = true)
            }
        }

        fun checkQueue() {
            val maxParallel = PrefManager.downloadQueueSize
            val activeCount = downloadJobs.values.count { it.isActive() && it.getStatusFlow().value != DownloadPhase.QUEUED }
            val slotsAvailable = maxParallel - activeCount
            
            if (slotsAvailable > 0) {
                // Get all queued downloads and start as many as we have slots
                val queuedEntries = downloadJobs.entries
                    .filter { it.value.getStatusFlow().value == DownloadPhase.QUEUED }
                    .take(slotsAvailable)
                
                for (entry in queuedEntries) {
                    Timber.i("Starting queued download for appId: ${entry.key} (slots: $slotsAvailable)")
                    downloadApp(entry.key)
                }
            }
            Unit
        }

        private val downloadJobs = ConcurrentHashMap<Int, DownloadInfo>()

        private fun notifyDownloadStarted(appId: Int) {
            PluviaApp.events.emit(AndroidEvent.DownloadStatusChanged(appId, true))
        }

        private fun notifyDownloadStopped(appId: Int) {
            PluviaApp.events.emit(AndroidEvent.DownloadStatusChanged(appId, false))
        }

        private fun removeDownloadJob(appId: Int, forceRemove: Boolean = false) {
            if (forceRemove) {
                val removed = downloadJobs.remove(appId)
                if (removed != null) {
                    notifyDownloadStopped(appId)
                }
            } else {
                notifyDownloadStopped(appId)
            }
            checkQueue()
            Unit
        }

        fun clearCompletedDownloads() {
            val toRemove = downloadJobs.filterValues {
                val status = it.getStatusFlow().value
                status == DownloadPhase.COMPLETE || status == DownloadPhase.CANCELLED
            }.keys
            toRemove.forEach { removeDownloadJob(it, forceRemove = true) }
        }

        /** Returns true if there is an incomplete download on disk (in-progress marker or actively downloading). */
        private fun hasPartialDownloadFiles(appDirPath: String): Boolean {
            val appDir = File(appDirPath)
            if (!appDir.exists()) return false

            val persistenceFile = File(File(appDirPath, DOWNLOAD_INFO_DIR), DOWNLOAD_INFO_FILE)
            if (persistenceFile.exists() && persistenceFile.length() > 0L) {
                return true
            }

            // If a complete install marker exists and there is no persisted resume file,
            // treat this as fully installed (not a resumable partial download).
            if (MarkerUtils.hasMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)) {
                return false
            }

            // Check for in-progress marker (this fork's convention)
            if (MarkerUtils.hasMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)) {
                return true
            }

            val rootFiles = appDir.listFiles() ?: return false
            return rootFiles.any { file ->
                if (file.name != DOWNLOAD_INFO_DIR) {
                    true
                } else {
                    val nestedFiles = file.listFiles().orEmpty()
                    nestedFiles.any { nested ->
                        nested.name != DOWNLOAD_INFO_FILE && nested.name != LEGACY_DOWNLOAD_INFO_FILE
                    }
                }
            }
        }

        private fun inferResumeDlcAppIds(appId: Int, appDirPath: String): List<Int> {
            // Try to recover selected DLCs from persisted depot progress when metadata row is missing.
            return runCatching {
                val persistenceFile = File(File(appDirPath, DOWNLOAD_INFO_DIR), DOWNLOAD_INFO_FILE)
                if (!persistenceFile.exists() || !persistenceFile.canRead()) return@runCatching emptyList()

                val text = persistenceFile.readText().trim()
                if (text.isEmpty()) return@runCatching emptyList()

                val persistedDepotIds = mutableSetOf<Int>()
                val json = JSONObject(text)
                for (key in json.keys()) {
                    val depotId = key.toIntOrNull() ?: continue
                    persistedDepotIds.add(depotId)
                }
                if (persistedDepotIds.isEmpty()) return@runCatching emptyList()

                val containerLanguage = PrefManager.containerLanguage
                val depots = getDownloadableDepots(appId = appId, preferredLanguage = containerLanguage)
                depots.asSequence()
                    .filter { (depotId, _) -> depotId in persistedDepotIds }
                    .map { (_, depot) -> depot.dlcAppId }
                    .filter { it != INVALID_APP_ID }
                    .distinct()
                    .toList()
            }.getOrElse {
                emptyList()
            }
        }

        private fun hasPersistedDepotResumeMetadata(appDirPath: String): Boolean {
            return runCatching {
                val persistenceFile = File(File(appDirPath, DOWNLOAD_INFO_DIR), DOWNLOAD_INFO_FILE)
                if (!persistenceFile.exists() || !persistenceFile.canRead()) return@runCatching false

                val text = persistenceFile.readText().trim()
                if (text.isEmpty()) return@runCatching false

                val json = JSONObject(text)
                json.keys().asSequence().any { key -> key.toIntOrNull() != null }
            }.getOrElse {
                false
            }
        }

        private fun clearPersistedProgressSnapshot(appDirPath: String) {
            val persistenceDir = File(appDirPath, DOWNLOAD_INFO_DIR)
            val persistenceFile = File(persistenceDir, DOWNLOAD_INFO_FILE)
            if (persistenceFile.exists()) {
                persistenceFile.delete()
            }
            val legacyFile = File(persistenceDir, LEGACY_DOWNLOAD_INFO_FILE)
            if (legacyFile.exists()) {
                legacyFile.delete()
            }
            if (persistenceDir.exists() && persistenceDir.list().isNullOrEmpty()) {
                persistenceDir.delete()
            }
        }

        private fun clearFailedResumeState(appId: Int) {
            val appDirPath = getAppDirPath(appId)
            clearPersistedProgressSnapshot(appDirPath)
            clearPersistedProgressSnapshot(getStagingDirPath(appId))
            runBlocking(Dispatchers.IO) {
                instance?.downloadingAppInfoDao?.deleteApp(appId)
            }
        }

        private fun deleteRecursivelyWithRetries(target: File, maxAttempts: Int = 5, delayMs: Long = 250L): Boolean {
            if (!target.exists()) return true

            repeat(maxAttempts) {
                if (target.deleteRecursively()) return true
                try {
                    Thread.sleep(delayMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return !target.exists()
                }
            }

            return !target.exists()
        }

        // Атомарный финал: склейка staging -> финал. Пофайловый rename (одна ФС — мгновенно),
        // с copy-fallback. Никогда не бросает исключение наружу.
        private fun mergeStagingIntoFinal(appId: Int, finalDirPath: String) {
            val stagingDir = File(getStagingDirPath(appId))
            if (!stagingDir.exists()) return
            if (stagingDir.absolutePath == File(finalDirPath).absolutePath) return
            try {
                val finalDir = File(finalDirPath)
                if (!finalDir.exists()) finalDir.mkdirs()
                stagingDir.listFiles()?.forEach { child ->
                    val dest = File(finalDir, child.name)
                    val moved = runCatching { child.renameTo(dest) }.getOrDefault(false)
                    if (!moved) {
                        if (child.isDirectory) {
                            child.copyRecursively(dest, overwrite = true)
                            deleteRecursivelyWithRetries(child)
                        } else {
                            child.copyTo(dest, overwrite = true)
                            child.delete()
                        }
                    }
                }
                deleteRecursivelyWithRetries(stagingDir)
                Timber.i("Merged staging into $finalDirPath for appId $appId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to merge staging for appId $appId")
            }
        }

        private fun deleteStagingDir(appId: Int) {
            val stagingDir = File(getStagingDirPath(appId))
            if (stagingDir.exists()) {
                Timber.i("Deleting staging folder for appId $appId: ${stagingDir.path}")
                deleteRecursivelyWithRetries(stagingDir)
            }
        }

        private fun cdnProbeUrl(server: Server): String? {
            val host = server.host?.trim().orEmpty()
            if (host.isEmpty()) return null
            val https = server.protocol == Server.ConnectionProtocol.HTTPS
            val scheme = if (https) "https" else "http"
            val defaultPort = if (https) 443 else 80
            return if (server.port == defaultPort) "$scheme://$host/" else "$scheme://$host:${server.port}/"
        }

        fun hasPartialDownload(appId: Int): Boolean {
            if (isAppInstalled(appId)) return false

            val appDirPath = getAppDirPath(appId)
            val downloadingApp = getDownloadingAppInfoOf(appId)
            val hasCompleteMarker = MarkerUtils.hasMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            val hasPartialFiles = hasPartialDownloadFiles(appDirPath)
            val hasPersistedMetadata = hasPersistedDepotResumeMetadata(appDirPath)
            val isResumable = if (hasCompleteMarker) {
                downloadingApp != null || hasPersistedMetadata
            } else {
                hasPartialFiles
            }

            if (isResumable) {
                return true
            }

            if (downloadingApp != null) {
                runBlocking(Dispatchers.IO) {
                    instance?.downloadingAppInfoDao?.deleteApp(appId)
                }
            }

            if (hasCompleteMarker && !hasPersistedMetadata) {
                clearPersistedProgressSnapshot(appDirPath)
            }

            return false
        }

        private val syncInProgressApps = ConcurrentHashMap<Int, AtomicBoolean>()
        private val pendingCloudSyncProcessing = AtomicBoolean(false)

        private fun getSyncFlag(appId: Int): AtomicBoolean {
            val existing = syncInProgressApps[appId]
            if (existing != null) {
                return existing
            }
            val created = AtomicBoolean(false)
            val prior = syncInProgressApps.putIfAbsent(appId, created)
            return prior ?: created
        }

        private fun tryAcquireSync(appId: Int): Boolean {
            val flag = getSyncFlag(appId)
            return flag.compareAndSet(false, true)
        }

        private fun releaseSync(appId: Int) {
            val flag = syncInProgressApps[appId]
            flag?.set(false)
            if (flag != null && !flag.get()) {
                syncInProgressApps.remove(appId, flag)
            }
        }

        // Track whether a game is currently running to prevent premature service stop
        @JvmStatic
        @Volatile
        var keepAlive: Boolean = false

        data class CloudSyncMessage(val appId: Int, val isUpload: Boolean, val message: String, val progress: Float)
        val cloudSyncStatus = MutableStateFlow<CloudSyncMessage?>(null)

        @Volatile
        var isImporting: Boolean = false

        var isStopping: Boolean = false
            private set
        private val _isConnectedFlow = MutableStateFlow(false)
        val isConnectedFlow = _isConnectedFlow.asStateFlow()

        var isConnected: Boolean 
            get() {
                val real = (instance?.steamClient?.isConnected == true)
                if (real != _isConnectedFlow.value) _isConnectedFlow.value = real
                return real
            }
            private set(value) { _isConnectedFlow.value = value }

        var isRunning: Boolean = false
            private set
        var isLoggingOut: Boolean = false
            private set

        private val _isLoggedInFlow = MutableStateFlow(false)
        val isLoggedInFlow = _isLoggedInFlow.asStateFlow()

        val isLoggedIn: Boolean
            get() {
                if (isLoggingOut) return false
                val real = (instance?.steamClient?.steamID?.isValid == true)
                // Only update flow if instance exists, to avoid overwriting
                // the pre-seeded credential-based state before service starts
                if (instance != null && real != _isLoggedInFlow.value) {
                    _isLoggedInFlow.value = real
                }
                return real
            }

        var isWaitingForQRAuth: Boolean = false
            private set

        fun syncStates() {
            val connected = instance?.steamClient?.isConnected == true
            if (connected != _isConnectedFlow.value) _isConnectedFlow.value = connected

            // Only update login state if the service instance exists (i.e. it has started).
            // Before that, the flow may be pre-seeded from stored credentials and we
            // don't want to overwrite it with false.
            if (instance != null) {
                val loggedIn = !isLoggingOut && (instance?.steamClient?.steamID?.isValid == true)
                if (loggedIn != _isLoggedInFlow.value) {
                    _isLoggedInFlow.value = loggedIn
                }
            }
        }

        /**
         * Checks if the user has stored Steam credentials (refresh token).
         * Used to determine if auto-reconnection should be attempted on app start.
         */
        fun hasStoredCredentials(context: Context): Boolean {
            PrefManager.init(context)
            return PrefManager.refreshToken.isNotBlank()
        }

        /**
         * Pre-seeds the login flow with stored credential state so the UI
         * doesn't flash a "sign in" prompt while the service is connecting.
         */
        fun initLoginStatus(context: Context) {
            if (!isLoggingOut) {
                _isLoggedInFlow.value = hasStoredCredentials(context)
            }
        }

        private val serverListPath: String
            get() = Paths.get(DownloadService.baseCacheDirPath, "server_list.bin").pathString

        val internalAppInstallPath: String
            get() = Paths.get(DownloadService.baseDataDirPath, "Steam", "steamapps", "common").pathString

        val externalAppInstallPath: String
            get() = Paths.get(PrefManager.externalStoragePath, "Steam", "steamapps", "common").pathString

        val allInstallPaths: List<String>
            get() {
                val paths = mutableListOf(internalAppInstallPath)
                if (PrefManager.externalStoragePath.isNotBlank()) {
                    paths += externalAppInstallPath
                }
                for (volumePath in DownloadService.externalVolumePaths) {
                    if (volumePath.isNotBlank()) {
                        paths += Paths.get(volumePath, "Steam", "steamapps", "common").pathString
                    }
                }
                return paths.distinct()
            }

        private val internalAppStagingPath: String
            get() {
                return Paths.get(DownloadService.baseDataDirPath, "Steam", "steamapps", "staging").pathString
            }
        private val externalAppStagingPath: String
            get() {
                return Paths.get(PrefManager.externalStoragePath, "Steam", "steamapps", "staging").pathString
            }

        val defaultStoragePath: String
            get() {
                return if (PrefManager.useExternalStorage && File(PrefManager.externalStoragePath).exists()) {
                    // We still have an SD card file structure as expected
                    Timber.i("External storage path is " + PrefManager.externalStoragePath)
                    PrefManager.externalStoragePath
                } else {
                    if (instance != null) {
                        return DownloadService.baseDataDirPath
                    }
                    return ""
                }
            }

        val defaultAppInstallPath: String
            get() {
                val context = PluviaApp.instance.applicationContext ?: return internalAppInstallPath
                val storeDefaultUri = if (PrefManager.useSingleDownloadFolder) PrefManager.defaultDownloadFolder else PrefManager.steamDownloadFolder
                if (storeDefaultUri.isNotEmpty()) {
                    val baseDir = com.winlator.cmod.core.FileUtils.getFilePathFromUri(context, android.net.Uri.parse(storeDefaultUri))
                    Timber.i("defaultAppInstallPath: resolved baseDir $baseDir from URI $storeDefaultUri")
                    if (baseDir != null) return baseDir
                }

                return if (PrefManager.useExternalStorage && File(PrefManager.externalStoragePath).exists()) {
                    // We still have an SD card file structure as expected
                    Timber.i("Using external storage")
                    Timber.i("install path for external storage is " + externalAppInstallPath)
                    externalAppInstallPath
                } else {
                    Timber.i("Using internal storage")
                    internalAppInstallPath
                }
            }

        val defaultAppStagingPath: String
            get() {
                val context = PluviaApp.instance.applicationContext ?: return internalAppStagingPath
                val storeDefaultUri = if (PrefManager.useSingleDownloadFolder) PrefManager.defaultDownloadFolder else PrefManager.steamDownloadFolder
                if (storeDefaultUri.isNotEmpty()) {
                    val baseDir = com.winlator.cmod.core.FileUtils.getFilePathFromUri(context, android.net.Uri.parse(storeDefaultUri))
                    if (baseDir != null) return Paths.get(baseDir, "staging").pathString
                }

                return if (PrefManager.useExternalStorage) {
                    externalAppStagingPath
                } else {
                    internalAppStagingPath
                }
            }

        private fun getStagingDirPath(appId: Int): String {
            return Paths.get(defaultAppStagingPath, appId.toString()).pathString
        }

        val userSteamId: SteamID?
            get() = instance?.steamClient?.steamID

        val familyMembers: List<Int>
            get() = instance?.familyGroupMembers ?: emptyList()

        val isLoginInProgress: Boolean
            get() = instance?._loginResult == LoginResult.InProgress

        suspend fun setPersonaState(state: EPersonaState) = withContext(Dispatchers.IO) {
            PrefManager.personaState = state.code()
            instance?._steamFriends?.setPersonaState(state)
        }

        suspend fun requestUserPersona() = withContext(Dispatchers.IO) {
            // in order to get user avatar url and other info
            userSteamId?.let { instance?._steamFriends?.requestFriendInfo(it) }
        }

        private val _emptyFriendsFlow = MutableStateFlow(emptyList<SteamFriend>())
        val friendsList: StateFlow<List<SteamFriend>>
            get() = (instance?._friendsList ?: _emptyFriendsFlow).asStateFlow()

        suspend fun requestFriendsInfo() = withContext(Dispatchers.IO) {
            instance?.refreshFriendsList()
        }

        val chatMessages: StateFlow<Map<Long, List<ChatMessage>>>
            get() = (instance?._chatMessages ?: MutableStateFlow(emptyMap())).asStateFlow()

        fun chatMessagesFor(friendSteamId64: Long): List<ChatMessage> {
            return instance?._chatMessages?.value?.get(friendSteamId64) ?: emptyList()
        }

        suspend fun sendChatMessage(steamId64: Long, message: String) = withContext(Dispatchers.IO) {
            val friends = instance?._steamFriends ?: return@withContext
            val steamId = SteamID(steamId64)
            friends.sendChatMessage(steamId, EChatEntryType.ChatMsg, message)
            val localSteamClient = instance?.steamClient?.steamID
            val chatMsg = ChatMessage(
                steamId64 = steamId64,
                senderSteamId64 = localSteamClient?.convertToUInt64() ?: 0L,
                text = message,
                timestamp = System.currentTimeMillis(),
                isIncoming = false,
            )
            instance?._chatMessages?.update { map ->
                val existing = map.toMutableMap()
                val messages = (existing[steamId64] ?: emptyList()) + chatMsg
                existing[steamId64] = messages
                existing
            }
            // Persist to DB
            try {
                instance?.chatMessageDao?.insert(
                    ChatMessageEntity(
                        friendSteamId64 = steamId64,
                        senderSteamId64 = chatMsg.senderSteamId64,
                        text = chatMsg.text,
                        timestamp = chatMsg.timestamp,
                        isIncoming = false,
                    )
                )
                instance?.chatMessageDao?.deleteOldMessages(
                    steamId64,
                    PrefManager.chatHistoryKeepCount.coerceIn(100, 2000),
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to persist sent chat message")
            }
        }

        fun getChatMessagesFromDb(friendSteamId64: Long): List<ChatMessage> {
            val entities = try {
                runBlocking(Dispatchers.IO) {
                    instance?.chatMessageDao?.getMessages(
                        friendSteamId64,
                        PrefManager.chatHistoryKeepCount.coerceIn(100, 2000),
                    ) ?: emptyList()
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load chat history")
                emptyList()
            }
            return entities.reversed().map { entity ->
                ChatMessage(
                    steamId64 = entity.friendSteamId64,
                    senderSteamId64 = entity.senderSteamId64,
                    text = entity.text,
                    timestamp = entity.timestamp,
                    isIncoming = entity.isIncoming,
                )
            }
        }

        suspend fun fetchAchievementsForDisplay(appId: Int): List<com.winlator.cmod.steam.statsgen.Achievement> = withContext(Dispatchers.IO) {
            try {
                withTimeout(15_000) {
                    val service = instance
                    val userStats = service?._steamUserStats
                    val steamUser = service?._steamUser
                    val steamId = steamUser?.steamID
                    if (userStats == null || steamId == null) return@withTimeout readCachedDisplayAchievements(appId)
                    val userStatsResult = userStats.getUserStats(appId, steamId).await()
                    if (userStatsResult.result != EResult.OK) {
                        return@withTimeout readCachedDisplayAchievements(appId)
                            .ifEmpty { cachedAchievements?.takeIf { cachedAchievementsAppId == appId } ?: emptyList() }
                    }
                    val schemaArray = userStatsResult.schema.toByteArray()
                    // Генератор всегда пишет файлы — направляем его в tmp, кэш кладём сами в steam_settings.
                    val context = service?.applicationContext
                        ?: return@withTimeout readCachedDisplayAchievements(appId)
                    val tmpDir = File(context.cacheDir, "ach_tmp/$appId").apply { mkdirs() }
                    val generator = StatsAchievementsGenerator()
                    val result = generator.generateStatsAchievements(schemaArray, tmpDir.absolutePath)
                    runCatching { tmpDir.deleteRecursively() }
                    val display = result.achievements.map { achievement ->
                        var unlocked = false
                        var unlockTs = 0
                        val mapping = result.nameToBlockBit[achievement.name]
                        if (mapping != null) {
                            val (blockId, bitIndex) = mapping
                            val block = userStatsResult.achievementBlocks.firstOrNull { b ->
                                (b.achievementId as? Number)?.toInt() == blockId
                            }
                            val num = block?.unlockTime?.getOrNull(bitIndex) as? Number
                            if (num != null && num.toLong() != 0L) {
                                unlocked = true
                                unlockTs = num.toInt()
                            }
                        }
                        val iconUrl = SteamUtils.resolveAchievementIconUrl(achievement.icon, appId)
                        val iconGrayUrl = SteamUtils.resolveAchievementIconUrl(
                            achievement.iconGray ?: achievement.icongray, appId,
                        )
                        achievement.copy(
                            unlocked = unlocked,
                            unlockTimestamp = unlockTs,
                            icon = iconUrl.ifBlank { achievement.icon },
                            iconGray = iconGrayUrl.ifBlank { achievement.iconGray },
                        )
                    }
                    cachedAchievements = display
                    cachedAchievementsAppId = appId
                    writeDisplayCache(appId, display)
                    display
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Timber.w("fetchAchievementsForDisplay timed out for appId=$appId")
                readCachedDisplayAchievements(appId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch achievements for appId=$appId")
                readCachedDisplayAchievements(appId)
            }
        }

        /** Метаданные ачивки из кэша для вотчера: (название, url иконки). */
        fun getCachedAchievementMeta(appId: Int, name: String): Pair<String, String?>? {
            val list = if (cachedAchievementsAppId == appId) cachedAchievements else null
            val resolved = list ?: readCachedDisplayAchievements(appId).takeIf { it.isNotEmpty() } ?: return null
            val achievement = resolved.firstOrNull { it.name == name } ?: return null
            val displayName = SteamUtils.resolveAchievementText(achievement.displayName, achievement.name)
            val iconUrl = achievement.icon?.takeIf { !it.isNullOrBlank() }
                ?: achievement.iconGray?.takeIf { !it.isNullOrBlank() }
            return displayName to iconUrl
        }

        private fun displayCacheFile(appId: Int): File =
            File(getAppDirPath(appId), "steam_settings/achievements_display_cache.json")

        private fun writeDisplayCache(appId: Int, list: List<com.winlator.cmod.steam.statsgen.Achievement>) {
            runCatching {
                val file = displayCacheFile(appId)
                file.parentFile?.mkdirs()
                val arr = JSONArray()
                for (a in list) {
                    val o = JSONObject()
                    o.put("name", a.name)
                    o.put("hidden", a.hidden)
                    o.put("displayName", JSONObject((a.displayName ?: emptyMap<String, String>()) as Map<*, *>))
                    o.put("description", JSONObject((a.description ?: emptyMap<String, String>()) as Map<*, *>))
                    o.put("icon", a.icon ?: "")
                    o.put("iconGray", a.iconGray ?: "")
                    o.put("unlocked", a.unlocked == true)
                    o.put("unlockTimestamp", a.unlockTimestamp ?: 0)
                    arr.put(o)
                }
                file.writeText(arr.toString(), Charsets.UTF_8)
            }.onFailure { Timber.w(it, "Failed to write achievements display cache for appId=$appId") }
        }

        private fun readCachedDisplayAchievements(appId: Int): List<com.winlator.cmod.steam.statsgen.Achievement> {
            return runCatching {
                val file = displayCacheFile(appId)
                if (!file.isFile) return emptyList()
                val arr = JSONArray(file.readText(Charsets.UTF_8))
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    com.winlator.cmod.steam.statsgen.Achievement(
                        name = o.optString("name"),
                        displayName = o.optJSONObject("displayName")?.let { jo ->
                            jo.keys().asSequence().associateWith { k -> jo.optString(k) }
                        },
                        description = o.optJSONObject("description")?.let { jo ->
                            jo.keys().asSequence().associateWith { k -> jo.optString(k) }
                        },
                        hidden = o.optInt("hidden"),
                        icon = o.optString("icon").takeIf { it.isNotBlank() },
                        iconGray = o.optString("iconGray").takeIf { it.isNotBlank() },
                        unlocked = o.optBoolean("unlocked"),
                        unlockTimestamp = o.optInt("unlockTimestamp"),
                    )
                }
            }.getOrElse {
                Timber.w(it, "Failed to read achievements display cache for appId=$appId")
                emptyList()
            }
        }

        suspend fun getSelfCurrentlyPlayingAppId(): Int? = withContext(Dispatchers.IO) {
            val self = instance?.localPersona?.value ?: return@withContext null
            if (self.isPlayingGame) self.gameAppID else null
        }

        suspend fun kickPlayingSession(onlyGame: Boolean = true): Boolean = withContext(Dispatchers.IO) {
            val user = instance?._steamUser ?: return@withContext false
            try {
                instance?._isPlayingBlocked?.value = true
                user.kickPlayingSession(onlyStopGame = onlyGame)

                // Wait for PlayingSessionStateCallback to indicate unblocked
                val deadline = System.currentTimeMillis() + 5000
                while (System.currentTimeMillis() < deadline) {
                    if (instance?._isPlayingBlocked?.value == false) return@withContext true
                    delay(100)
                }
                false
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Get licenses from database for use with DepotDownloader
         */
        suspend fun getLicensesFromDb(): List<License> = withContext(Dispatchers.IO) {
            val cached = instance?.cachedLicenseDao?.getAll() ?: return@withContext emptyList()
            cached.mapNotNull { cachedLicense ->
                LicenseSerializer.deserializeLicense(cachedLicense.licenseJson)
            }
        }

        fun getPkgInfoOf(appId: Int): SteamLicense? {
            return runBlocking(Dispatchers.IO) {
                instance?.licenseDao?.findLicense(
                    instance?.appDao?.findApp(appId)?.packageId ?: INVALID_PKG_ID,
                )
            }
        }

        fun getAppInfoOf(appId: Int): SteamApp? {
            return runBlocking(Dispatchers.IO) { instance?.appDao?.findApp(appId) }
        }

        fun getDownloadingAppInfoOf(appId: Int): DownloadingAppInfo? {
            return runBlocking(Dispatchers.IO) { instance?.downloadingAppInfoDao?.getDownloadingApp(appId) }
        }

        fun getDownloadableDlcAppsOf(appId: Int): List<SteamApp>? {
            return runBlocking(Dispatchers.IO) { instance?.appDao?.findDownloadableDLCApps(appId) }
        }

        fun getHiddenDlcAppsOf(appId: Int): List<SteamApp>? {
            return runBlocking(Dispatchers.IO) { instance?.appDao?.findHiddenDLCApps(appId) }
        }

        /**
         * Get ALL selectable DLC apps for a given app ID.
         * Collects from all possible sources:
         * 1. Depot DLC markers (dlcAppId field in depots)
         * 2. Database findDownloadableDLCApps (dlc_for_app_id matching + license check)
         * 3. Database findHiddenDLCApps (dlc_for_app_id matching + license check, no depots)
         * 4. Grouped base app DLC content depots
         * 5. Declared dlcAppIds field from PICS
         * @return deduplicated sorted list of SteamApp DLCs
         */
        private suspend fun fixDlcForAppIdInDb(parentAppId: Int): Int {
            val service = instance ?: return 0
            val appInfo = service.appDao.findApp(parentAppId) ?: return 0
            val candidateIds = (
                appInfo.depots.values.map { it.dlcAppId }.filter { it != INVALID_APP_ID } +
                    appInfo.dlcAppIds
                ).distinct().toList()
            if (candidateIds.isEmpty()) return 0
            var fixed = 0
            for (dlcId in candidateIds) {
                try {
                    val dlcApp = service.appDao.findApp(dlcId)
                    if (dlcApp != null && dlcApp.dlcForAppId != parentAppId) {
                        service.appDao.update(dlcApp.copy(dlcForAppId = parentAppId))
                        Timber.d("DLC fix: updated dlcForAppId for appId=$dlcId from ${dlcApp.dlcForAppId} to $parentAppId")
                        fixed++
                    }
                } catch (_: Exception) { }
            }
            return fixed
        }

        fun getSelectableDlcAppsOf(appId: Int): List<SteamApp> =
            runBlocking(Dispatchers.IO) {
                val service = instance ?: return@runBlocking emptyList()
                val appInfo = service.appDao.findApp(appId) ?: return@runBlocking emptyList()
                val preferredLanguage = PrefManager.containerLanguage
                val has64Bit =
                    appInfo.depots.values.any {
                        it.osArch == OSArch.Arch64 &&
                            (it.osList.contains(OS.windows) || (it.osList.isEmpty() || it.osList.contains(OS.none)))
                    }

                val mainAppDlcIds =
                    appInfo.depots.values
                        .asSequence()
                        .filter { depot ->
                            depot.dlcAppId != INVALID_APP_ID &&
                                filterForDownloadableDepots(depot, has64Bit, preferredLanguage, ownedDlc = null)
                        }.map { it.dlcAppId }
                        .toList()

                var indirectDlcApps = service.appDao.findDownloadableDLCApps(appId).orEmpty()
                var hiddenDlcApps = service.appDao.findHiddenDLCApps(appId).orEmpty()

                // Fix dlc_for_app_id in DB if queries return empty
                if (indirectDlcApps.isEmpty() && hiddenDlcApps.isEmpty()) {
                    Timber.d("DLC fix: dlc_for_app_id queries empty for appId=$appId, fixing DB...")
                    val fixed = runBlocking(Dispatchers.IO) { fixDlcForAppIdInDb(appId) }
                    if (fixed > 0) {
                        Timber.d("DLC fix: fixed $fixed entries, retrying queries")
                        indirectDlcApps = service.appDao.findDownloadableDLCApps(appId).orEmpty()
                        hiddenDlcApps = service.appDao.findHiddenDLCApps(appId).orEmpty()
                        if (indirectDlcApps.isEmpty() && hiddenDlcApps.isEmpty()) {
                            indirectDlcApps = service.appDao.findDownloadableDLCAppsNoLicense(appId).orEmpty()
                            hiddenDlcApps = service.appDao.findHiddenDLCAppsNoLicense(appId).orEmpty()
                        }
                    } else {
                        // No DLCs in DB at all — try without license
                        indirectDlcApps = service.appDao.findDownloadableDLCAppsNoLicense(appId).orEmpty()
                        hiddenDlcApps = service.appDao.findHiddenDLCAppsNoLicense(appId).orEmpty()
                    }
                }

                val dlcAppsById = (indirectDlcApps + hiddenDlcApps).associateBy { it.id }
                val indirectDlcIds = indirectDlcApps.map { it.id }
                val hiddenDlcIds = hiddenDlcApps.map { it.id }
                val groupedBaseDlcIds =
                    getGroupedBaseAppDlcIds(
                        appInfo = appInfo,
                        preferredLanguage = preferredLanguage,
                        has64Bit = has64Bit,
                    ).toList()

                val declaredDlcIds = appInfo.dlcAppIds

                val selectableDlcIds = (mainAppDlcIds + groupedBaseDlcIds + indirectDlcIds + hiddenDlcIds + declaredDlcIds).distinct().toList()

                if (selectableDlcIds.isEmpty()) {
                    Timber.w("DLC diagnostics: ALL sources empty for appId=$appId")
                    Timber.d("DLC diagnostics: depots keys=${appInfo.depots.keys.take(20)} dlcAppIds=${appInfo.dlcAppIds}")
                    return@runBlocking emptyList()
                }

                Timber.d("DLC diagnostics: appId=$appId | mainAppDlcIds=${mainAppDlcIds.size} | groupedBase=${groupedBaseDlcIds.size} | indirect=${indirectDlcIds.size} | hidden=${hiddenDlcIds.size} | declared=${declaredDlcIds.size} | total=${selectableDlcIds.size}")
                if (selectableDlcIds.isNotEmpty()) {
                    Timber.d("DLC diagnostics: IDs=${selectableDlcIds.take(20)}")
                }

                val dlcFromDb = service.appDao.findApps(selectableDlcIds).associateBy { it.id }
                val result = selectableDlcIds
                    .mapNotNull { dlcAppId ->
                        val app = dlcFromDb[dlcAppId] ?: dlcAppsById[dlcAppId]
                        if (app == null) {
                            Timber.d("DLC diagnostics: appId=$dlcAppId not found in DB (missing PICS data)")
                            null
                        } else if (app.name.isBlank()) {
                            Timber.d("DLC diagnostics: appId=$dlcAppId name is BLANK (dlcForAppId=${app.dlcForAppId})")
                            null
                        } else app
                    }
                    .sortedBy { it.name.lowercase() }
                Timber.d("DLC diagnostics: returning ${result.size} DLCs for appId=$appId")
                result
            }

        /** Helper - get DLC IDs from grouped base app content depots (WinNative pattern) */
        private fun getGroupedBaseAppDlcIds(
            appInfo: SteamApp,
            preferredLanguage: String = PrefManager.containerLanguage,
            has64Bit: Boolean = appInfo.depots.values.any {
                it.osArch == OSArch.Arch64 &&
                    (it.osList.contains(OS.windows) || it.osList.isEmpty() || it.osList.contains(OS.none))
            },
        ): Set<Int> {
            return getGroupedBaseAppDlcDepots(appInfo)
                .filter { groupedDepot ->
                    filterForDownloadableDepots(groupedDepot.depot, has64Bit, preferredLanguage, ownedDlc = null)
                }.map { it.dlcAppId }
                .toSet()
        }

        private data class GroupedBaseAppDlcDepot(
            val depotId: Int,
            val dlcAppId: Int,
            val depot: DepotInfo,
        )

        private fun getGroupedBaseAppDlcDepots(appInfo: SteamApp): List<GroupedBaseAppDlcDepot> {
            val declaredDlcIds =
                (
                    appInfo.dlcAppIds.asSequence() +
                        appInfo.depots.values.asSequence()
                            .map { it.dlcAppId }
                            .filter { it != INVALID_APP_ID }
                    ).toSet()
            if (declaredDlcIds.isEmpty()) return emptyList()

            val depotIds = mutableListOf<GroupedBaseAppDlcDepot>()
            var activeDlcAppId: Int? = null
            for ((depotId, depot) in appInfo.depots) {
                val isDlcMarkerDepot =
                    depotId in declaredDlcIds &&
                        depot.manifests.isEmpty()
                if (isDlcMarkerDepot) {
                    activeDlcAppId = depotId
                    continue
                }

                val dlcAppId = activeDlcAppId
                if (dlcAppId != null && depot.dlcAppId == INVALID_APP_ID) {
                    depotIds += GroupedBaseAppDlcDepot(depotId, dlcAppId, depot)
                }
            }

            return depotIds
        }

        /** Also need findApps in DAO for bulk SELECT */
        // findApps is added to SteamAppDao below via Query

        fun getInstalledApp(appId: Int): AppInfo? {
            return runBlocking(Dispatchers.IO) { instance?.appInfoDao?.getInstalledApp(appId) }
        }

        fun getInstalledDepotsOf(appId: Int): List<Int>? {
            return getInstalledApp(appId)?.downloadedDepots
        }

        fun getInstalledDlcDepotsOf(appId: Int): List<Int>? {
            val installedApp = getInstalledApp(appId) ?: return null
            val installedDlcIds = installedApp.dlcDepots.toMutableList()
            // Dynamic DLC discovery: check each selectable DLC if it has a download marker (WinNative pattern)
            val selectableDlcs = getSelectableDlcAppsOf(appId)
            for (dlc in selectableDlcs) {
                if (dlc.id in installedDlcIds) continue
                val dlcInfo = getInstalledApp(dlc.id)
                if (dlcInfo?.isDownloaded == true && dlc.id !in installedDlcIds) {
                    installedDlcIds.add(dlc.id)
                }
            }
            if (installedDlcIds != installedApp.dlcDepots) {
                runBlocking(Dispatchers.IO) {
                    instance?.appInfoDao?.update(installedApp.copy(dlcDepots = installedDlcIds.sorted()))
                }
            }
            return installedDlcIds.sorted()
        }

        private fun tryRecoverInstalledAppInfo(appId: Int): AppInfo? {
            val dirPath = getAppDirPath(appId)
            val hasCompleteMarker = MarkerUtils.hasMarker(dirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            val hasInProgressMarker = MarkerUtils.hasMarker(dirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
            if (!hasCompleteMarker || hasInProgressMarker) return null

            val dir = File(dirPath)
            if (!dir.exists() || !dir.isDirectory) return null

            val downloadedDepotIds = runCatching { getMainAppDepots(appId).keys.sorted() }.getOrDefault(emptyList())
            val recovered = AppInfo(
                id = appId,
                isDownloaded = true,
                downloadedDepots = downloadedDepotIds,
                dlcDepots = emptyList(),
            )

            runBlocking(Dispatchers.IO) {
                PluviaDatabase.getInstance().appInfoDao().insert(recovered)
            }
            Timber.i("Recovered Steam installed metadata from disk for appId=$appId at $dirPath")
            return recovered
        }

        fun repairInstalledMetadataFromDisk(): Int {
            return runBlocking(Dispatchers.IO) {
                val db = PluviaDatabase.getInstance()
                val apps = runCatching { db.steamAppDao().getAllAsList() }.getOrElse {
                    Timber.e(it, "Failed to load Steam apps for install repair")
                    return@runBlocking 0
                }

                var repairedCount = 0
                for (app in apps) {
                    val installedApp = db.appInfoDao().getInstalledApp(app.id)
                    if (installedApp?.isDownloaded == true) continue
                    if (tryRecoverInstalledAppInfo(app.id) != null) {
                        repairedCount++
                    }
                }
                repairedCount
            }
        }

        fun getAllDownloads(): Map<Int, DownloadInfo> {
            return downloadJobs
        }

        fun getAppDownloadInfo(appId: Int): DownloadInfo? {
            return downloadJobs[appId]
        }

        fun isAppInstalled(appId: Int): Boolean {
            val appInfo = getInstalledApp(appId) ?: tryRecoverInstalledAppInfo(appId)
            if (appInfo?.isDownloaded != true) return false
            val dirPath = getAppDirPath(appId)
            return MarkerUtils.hasMarker(dirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
        }

        fun uninstallApp(appId: Int, onComplete: (Boolean) -> Unit = {}) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val appInfo = getInstalledApp(appId)
                    if (appInfo != null) {
                        instance?.appInfoDao?.update(appInfo.copy(isDownloaded = false))
                    }
                    val dirPath = getAppDirPath(appId)
                    val dirFile = java.io.File(dirPath)
                    if (dirFile.exists() && dirFile.isDirectory) {
                        deleteRecursivelyWithRetries(dirFile)
                    }
                    deleteStagingDir(appId)
                    PluviaApp.events.emit(AndroidEvent.LibraryInstallStatusChanged(appId))
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onComplete(true)
                    }
                } catch (e: Exception) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onComplete(false)
                    }
                }
            }
        }

        fun getAppDlc(appId: Int): Map<Int, DepotInfo> {
            return getAppInfoOf(appId)?.let {
                it.depots.filter { it.value.dlcAppId != INVALID_APP_ID }
            }.orEmpty()
        }

        suspend fun getOwnedAppDlc(appId: Int): Map<Int, DepotInfo> {
            val client = instance?.steamClient ?: return emptyMap()
            val accountId = client.steamID?.accountID?.toInt() ?: return emptyMap()
            val ownedGameIds = getOwnedGames(userSteamId!!.convertToUInt64()).map { it.appId }.toHashSet()


            return getAppDlc(appId).filter { (_, depot) ->
                when {
                    /* Base-game depots always download */
                    depot.dlcAppId == INVALID_APP_ID -> true

                    /* ① licence cache */
                    instance?.licenseDao?.findLicense(depot.dlcAppId) != null -> true

                    /* ② PICS row */
                    instance?.appDao?.findApp(depot.dlcAppId) != null -> true

                    /* ③ owned-games list */
                    depot.dlcAppId in ownedGameIds -> true

                    /* ④ final online / cached call */
                    else -> false
                }
            }.toMap()
        }

        fun getMainAppDlcIdsWithoutProperDepotDlcIds(appId: Int): MutableList<Int> {
            val mainAppDlcIds = mutableListOf<Int>()
            val hiddenDlcAppIds = getHiddenDlcAppsOf(appId).orEmpty().map { it.id }

            val appInfo = getAppInfoOf(appId)
            if (appInfo != null) {
                // for each of the dlcAppId found in main depots, filter the count = 1, add that dlcAppId to dlcAppIds
                val checkingAppDlcIds = appInfo.depots.filter { it.value.dlcAppId != INVALID_APP_ID }.map { it.value.dlcAppId }.distinct()
                checkingAppDlcIds.forEach { checkingDlcId ->
                    val checkMap = appInfo.depots.filter { it.value.dlcAppId == checkingDlcId }
                    if (checkMap.size == 1) {
                        val depotInfo = checkMap[checkMap.keys.first()]!!
                        if (depotInfo.osList.contains(OS.none) &&
                            depotInfo.manifests.isEmpty() &&
                            hiddenDlcAppIds.isNotEmpty() && hiddenDlcAppIds.contains(checkingDlcId)) {
                            mainAppDlcIds.add(checkingDlcId)
                        }
                    }
                }
            }

            return mainAppDlcIds
        }

        /**
         * Refresh the owned games list by querying Steam, diffing with the local DB, and
         * queueing PICS requests for anything new so metadata gets populated.
         *
         * @return number of newly discovered appIds that were scheduled for PICS.
         */
        suspend fun refreshOwnedGamesFromServer(): Int = withContext(Dispatchers.IO) {
            val service = instance ?: return@withContext 0
            val unifiedFriends = service._unifiedFriends ?: return@withContext 0
            val steamId = userSteamId ?: return@withContext 0

            runCatching {
                val ownedGames = unifiedFriends.getOwnedGames(steamId.convertToUInt64())
                val remoteAppIds = ownedGames.map { it.appId }.filter { it > 0 }.toSet()
                if (remoteAppIds.isEmpty()) {
                    return@runCatching 0
                }

                val localAppIds = service.appDao.getAllAppIds().toSet()
                val missingAppIds = remoteAppIds - localAppIds
                if (missingAppIds.isEmpty()) {
                    return@runCatching 0
                }

                missingAppIds
                    .chunked(MAX_PICS_BUFFER)
                    .forEach { chunk ->
                        val requests = chunk.map { PICSRequest(id = it) }
                        service.appPicsChannel.send(requests)
                    }

                missingAppIds.size
            }.onFailure { error ->
                Timber.tag("SteamService").e(error, "Failed to refresh owned games from server")
            }.getOrDefault(0)
        }

        /**
         * Common Filter for downloadable depots
         */
        fun filterForDownloadableDepots(depot: DepotInfo, has64Bit: Boolean, preferredLanguage: String, ownedDlc: Map<Int, DepotInfo>?): Boolean {
            if (depot.manifests.isEmpty() && depot.encryptedManifests.isNotEmpty())
                return false
            // 1. Has something to download
            if (depot.manifests.isEmpty() && !depot.sharedInstall)
                return false
            // 2. Supported OS
            if (!(depot.osList.contains(OS.windows) ||
                        (!depot.osList.contains(OS.linux) && !depot.osList.contains(OS.macos)))
            )
                return false
            // 3. 64-bit or indeterminate
            // Arch selection: allow 64-bit and Unknown always.
            // Allow 32-bit only when no 64-bit depot exists.
            val archOk = when (depot.osArch) {
                OSArch.Arch64, OSArch.Unknown -> true
                OSArch.Arch32 -> !has64Bit
                else -> false
            }
            if (!archOk) return false
            // 4. DLC you actually own
            if (depot.dlcAppId != INVALID_APP_ID && ownedDlc != null && !ownedDlc.containsKey(depot.depotId))
                return false
            // 5. Language filter - if depot has language, it must match preferred language
            if (depot.language.isNotEmpty() && !depot.language.equals(preferredLanguage, ignoreCase = true))
                return false

            return true
        }

        fun getMainAppDepots(appId: Int): Map<Int, DepotInfo> {
            val appInfo = getAppInfoOf(appId) ?: return emptyMap()
            val ownedDlc = runBlocking { getOwnedAppDlc(appId) }
            val preferredLanguage = PrefManager.containerLanguage
            val entitledDepotIds = getEntitledDepotIds(appInfo.packageId)

            // If the game ships any 64-bit depot for Windows, prefer those and ignore x86 ones
            val has64Bit = appInfo.depots.values.any { 
                it.osArch == OSArch.Arch64 && (it.osList.contains(OS.windows) || (it.osList.isEmpty() || it.osList.contains(OS.none)))
            }

            return appInfo.depots.asSequence()
                .filter { (depotId, depot) ->
                    return@filter isDepotEntitled(depotId, depot, entitledDepotIds) &&
                        filterForDownloadableDepots(depot, has64Bit, preferredLanguage, ownedDlc)
                }
                .associate { it.toPair() }
        }

        /**
         * Get downloadable depots for a given app, including all DLCs
         * @return Map of app ID to depot ID to depot info
         */
        fun getDownloadableDepots(appId: Int, preferredLanguage: String = PrefManager.containerLanguage): Map<Int, DepotInfo> {
            val appInfo = getAppInfoOf(appId) ?: return emptyMap()
            val ownedDlc = runBlocking { getOwnedAppDlc(appId) }
            val entitledDepotIds = getEntitledDepotIds(appInfo.packageId)

            // If the game ships any 64-bit depot for Windows, prefer those and ignore x86 ones
            val has64Bit = appInfo.depots.values.any { 
                it.osArch == OSArch.Arch64 && (it.osList.contains(OS.windows) || (it.osList.isEmpty() || it.osList.contains(OS.none)))
            }

            val map = mutableMapOf<Int, DepotInfo>()
            for ((depotId, depot) in appInfo.depots) {
                if (isDepotEntitled(depotId, depot, entitledDepotIds) &&
                    filterForDownloadableDepots(depot, has64Bit, preferredLanguage, ownedDlc)
                ) {
                    map[depotId] = depot
                }
            }

            val relatedDlcApps = buildList {
                addAll(getDownloadableDlcAppsOf(appId).orEmpty())
                addAll(getHiddenDlcAppsOf(appId).orEmpty())
                appInfo.dlcAppIds
                    .filter { it > 0 && it != appId }
                    .forEach { candidateId ->
                        getAppInfoOf(candidateId)?.let(::add)
                    }
            }.distinctBy { it.id }
            for (dlcApp in relatedDlcApps) {
                val entitledDlcDepotIds = getEntitledDepotIds(dlcApp.packageId)
                for ((depotId, depot) in dlcApp.depots) {
                    if (isDepotEntitled(depotId, depot, entitledDlcDepotIds) &&
                        filterForDownloadableDepots(depot, has64Bit, preferredLanguage, null)
                    ) {
                        // Add DLC Depots with custom object
                        map[depotId] = DepotInfo(
                            depotId = depot.depotId,
                            dlcAppId = dlcApp.id, // Set to DLC App ID
                            optionalDlcId = depot.optionalDlcId,
                            depotFromApp = depot.depotFromApp,
                            sharedInstall = depot.sharedInstall,
                            osList = depot.osList,
                            osArch = depot.osArch,
                            language = depot.language,
                            manifests = depot.manifests,
                            encryptedManifests = depot.encryptedManifests,
                        )
                    }
                }
            }

            return map
        }

        private fun getEntitledDepotIds(packageId: Int): Set<Int>? {
            if (packageId == INVALID_PKG_ID) return null
            val depotIds = runBlocking(Dispatchers.IO) {
                instance?.licenseDao?.findLicense(packageId)?.depotIds.orEmpty()
            }
            return depotIds.takeIf { it.isNotEmpty() }?.toSet()
        }

        private fun isDepotEntitled(
            depotId: Int,
            depot: DepotInfo,
            entitledDepotIds: Set<Int>?,
        ): Boolean {
            if (entitledDepotIds == null) return true
            if (depotId in entitledDepotIds) return true

            // Shared/proxied depots may not be listed directly on the package even though
            // they are required to resolve the owning depot's content.
            return depot.sharedInstall || depot.depotFromApp != INVALID_APP_ID
        }

        private fun getSelectedDownloadDepots(
            appId: Int,
            userSelectedDlcAppIds: Collection<Int>,
            preferredLanguage: String = PrefManager.containerLanguage,
        ): Map<Int, DepotInfo> {
            val downloadableDepots = getDownloadableDepots(appId, preferredLanguage)
            if (downloadableDepots.isEmpty()) return emptyMap()

            val selectedDlcIds = userSelectedDlcAppIds.toSet()
            val mainDepots = getMainAppDepots(appId)

            val selectedMainDepots = mainDepots.filter { (_, depot) ->
                depot.dlcAppId == INVALID_APP_ID ||
                    (depot.dlcAppId in selectedDlcIds && depot.manifests.isNotEmpty())
            }

            val selectedDlcDepots = downloadableDepots.filter { (depotId, depot) ->
                depotId !in selectedMainDepots &&
                    depot.dlcAppId in selectedDlcIds &&
                    depot.manifests.isNotEmpty()
            }

            return selectedMainDepots + selectedDlcDepots
        }

        private fun resolveDepotManifestInfo(
            depot: DepotInfo,
            branch: String,
            visitedApps: MutableSet<Int> = mutableSetOf(),
        ): ManifestInfo? {
            depot.manifests[branch]?.let { return it }
            depot.encryptedManifests[branch]?.let { return it }

            if (!branch.equals("public", ignoreCase = true)) {
                depot.manifests["public"]?.let { return it }
                depot.encryptedManifests["public"]?.let { return it }
            }

            val sourceAppId = depot.depotFromApp
            if (sourceAppId == INVALID_APP_ID || !visitedApps.add(sourceAppId)) {
                return null
            }

            val sourceDepot = getAppInfoOf(sourceAppId)?.depots?.get(depot.depotId) ?: return null
            return resolveDepotManifestInfo(sourceDepot, branch, visitedApps)
        }

        fun getSelectedManifestSizes(
            appId: Int,
            userSelectedDlcAppIds: Collection<Int> = emptyList(),
            preferredLanguage: String = PrefManager.containerLanguage,
            branch: String = "public",
        ): ManifestSizes {
            val selectedDepots = getSelectedDownloadDepots(appId, userSelectedDlcAppIds, preferredLanguage)
            if (selectedDepots.isEmpty()) return ManifestSizes()

            var totalInstallSize = 0L
            var totalDownloadSize = 0L

            selectedDepots.values.forEach { depot ->
                val manifest = resolveDepotManifestInfo(depot, branch)
                totalInstallSize += manifest?.size ?: 0L
                totalDownloadSize += manifest?.download ?: 0L
            }

            return ManifestSizes(
                installSize = totalInstallSize,
                downloadSize = totalDownloadSize,
            )
        }

        fun getAppDirName(app: SteamApp?): String {
            // The folder name, if it got made
            var appName = app?.config?.installDir.orEmpty()
            if (appName.isEmpty()) {
                appName = app?.name.orEmpty()
            }
            return appName
        }

        fun getAppDirPath(gameId: Int): String {

            val info = getAppInfoOf(gameId)

            // Check custom install directory first (only if it's a full absolute path)
            // installDir from PICS metadata is just a folder name, custom installs save full path
            val customDir = info?.installDir.orEmpty()
            if (customDir.isNotEmpty() && (customDir.startsWith("/") || customDir.contains(File.separator))) {
                // It's a full path (custom install location)
                return customDir
            }

            val appName = getAppDirName(info)
            val oldName = info?.name.orEmpty()

            // Respect user-selected default download folder
            val context = PluviaApp.instance.applicationContext
            if (context != null) {
                val storeDefaultUri = if (PrefManager.useSingleDownloadFolder) PrefManager.defaultDownloadFolder else PrefManager.steamDownloadFolder
                if (storeDefaultUri.isNotEmpty()) {
                    val baseDir = com.winlator.cmod.core.FileUtils.getFilePathFromUri(context, android.net.Uri.parse(storeDefaultUri))
                    Timber.i("getAppDirPath: resolved baseDir $baseDir from URI $storeDefaultUri")
                    if (baseDir != null) {
                        val path = Paths.get(baseDir, appName)
                        if (Files.exists(path)) {
                            Timber.i("getAppDirPath: found existing path $path")
                            return path.pathString
                        }
                        if (oldName.isNotEmpty()) {
                            val oldPath = Paths.get(baseDir, oldName)
                            if (Files.exists(oldPath)) {
                                Timber.i("getAppDirPath: found existing oldPath $oldPath")
                                return oldPath.pathString
                            }
                        }
                        // If it doesn't exist yet, this is where we'll install it
                        Timber.i("getAppDirPath: returning new path $path")
                        return path.pathString
                    }
                }
            }

            for (basePath in allInstallPaths) {
                val candidate = Paths.get(basePath, appName)
                if (Files.exists(candidate)) return candidate.pathString
                if (oldName.isNotEmpty()) {
                    val oldCandidate = Paths.get(basePath, oldName)
                    if (Files.exists(oldCandidate)) return oldCandidate.pathString
                }
            }

            // Nothing on disk yet – default to whatever location you want new installs to use
            if (PrefManager.useExternalStorage) {
                return Paths.get(externalAppInstallPath, appName).pathString
            }
            return Paths.get(internalAppInstallPath, appName).pathString
        }

        private fun createSteamShortcut(context: Context, appId: Int) {
            try {
                val container = ContainerUtils.getOrCreateContainer(context, "STEAM_$appId")
                val appInfo = getAppInfoOf(appId) ?: return
                val installPath = getAppDirPath(appId)
                val launchExecutable = getInstalledExe(appId)
                val desktopDir = container.getDesktopDir()
                if (!desktopDir.exists()) desktopDir.mkdirs()

                val shortcutFile = File(desktopDir, "${appInfo.name}.desktop")
                val content = StringBuilder()
                content.append("[Desktop Entry]\n")
                content.append("Type=Application\n")
                content.append("Name=${appInfo.name}\n")
                content.append("Exec=wine C:/Program Files (x86)/Steam/steamclient_loader_x64.exe\n")
                content.append("Icon=steam_icon_$appId\n")
                content.append("\n[Extra Data]\n")
                content.append("game_source=STEAM\n")
                content.append("app_id=$appId\n")
                content.append("container_id=${container.id}\n")
                content.append("game_install_path=$installPath\n")
                content.append("launch_exe_path=$launchExecutable\n")
                content.append("use_container_defaults=1\n")

                com.winlator.cmod.core.FileUtils.writeString(shortcutFile, content.toString())
                Timber.i("Created Steam shortcut for ${appInfo.name} in container ${container.id}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to create Steam shortcut for appId $appId")
            }
        }

        private fun isExecutable(flags: Any): Boolean = when (flags) {
            // SteamKit-JVM (most forks) – flags is EnumSet<EDepotFileFlag>
            is EnumSet<*> -> {
                flags.contains(EDepotFileFlag.Executable) ||
                        flags.contains(EDepotFileFlag.CustomExecutable)
            }

            // SteamKit-C# protobuf port – flags is UInt / Int / Long
            is Int -> (flags and 0x20) != 0 || (flags and 0x80) != 0
            is Long -> ((flags and 0x20L) != 0L) || ((flags and 0x80L) != 0L)

            else -> false
        }

        /* -------------------------------------------------------------------------- */
        /* 1. Extra patterns & word lists                                             */
        /* -------------------------------------------------------------------------- */

        /** Windows или без OS-метки (как GameNative isWindowsCompatible) */
        private fun isWindowsDepot(depot: DepotInfo): Boolean =
            depot.osList.contains(OS.windows) ||
                (!depot.osList.contains(OS.linux) && !depot.osList.contains(OS.macos))

        // Unreal Engine "Shipping" binaries (e.g. Stray-Win64-Shipping.exe)
        private val UE_SHIPPING = Regex(
            """.*-win(32|64)(-shipping)?\.exe$""",
            RegexOption.IGNORE_CASE,
        )

        // UE folder hint …/Binaries/Win32|64/…
        private val UE_BINARIES = Regex(
            """.*/binaries/win(32|64)/.*\.exe$""",
            RegexOption.IGNORE_CASE,
        )

        // Tools / crash-dumpers to push down
        private val NEGATIVE_KEYWORDS = listOf(
            "crash", "handler", "viewer", "compiler", "tool",
            "setup", "unins", "eac", "launcher", "steam",
        )

        /* add near-name helper */
        private fun fuzzyMatch(a: String, b: String): Boolean {
            /* strip digits & punctuation, compare first 5 letters */
            val cleanA = a.replace(Regex("[^a-z]"), "")
            val cleanB = b.replace(Regex("[^a-z]"), "")
            return cleanA.take(5) == cleanB.take(5)
        }

        /* add generic short-name detector: one letter + digits, ≤4 chars  */
        private val GENERIC_NAME = Regex("^[a-z]\\d{1,3}\\.exe$", RegexOption.IGNORE_CASE)

        /* -------------------------------------------------------------------------- */
        /* 2. Heuristic score (same signature!)                                       */
        /* -------------------------------------------------------------------------- */

        private fun scoreExe(
            file: FileData,
            gameName: String,
            hasExeFlag: Boolean,
        ): Int {
            var s = 0
            val path = file.fileName.lowercase()

            // 1️⃣ UE shipping or binaries folder bonus
            if (UE_SHIPPING.matches(path)) s += 300
            if (UE_BINARIES.containsMatchIn(path)) s += 250

            // 2️⃣ root-folder exe bonus
            if (!path.contains('/')) s += 200

            // 3️⃣ filename contains the game / installDir
            if (path.contains(gameName) || fuzzyMatch(path, gameName)) s += 100

            // 4️⃣ obvious tool / crash-dumper penalty
            if (NEGATIVE_KEYWORDS.any { it in path }) s -= 150
            if (GENERIC_NAME.matches(file.fileName)) s -= 200   // ← new

            // 5️⃣ Executable | CustomExecutable flag
            if (hasExeFlag) s += 50

            Timber.i("Score for $path: $s")

            return s
        }

        fun FileData.isStub(): Boolean {
            /* stub detector (same short rules) */
            val generic = Regex("^[a-z]\\d{1,3}\\.exe$", RegexOption.IGNORE_CASE)
            val bad = listOf("launcher", "steam", "crash", "handler", "setup", "unins", "eac")
            val n = fileName.lowercase()
            val stub = generic.matches(n) || bad.any { it in n } || totalSize < 1_000_000
            if (stub) Timber.d("Stub filtered: $fileName  size=$totalSize")
            return stub
        }

        /** select the primary binary */
        fun choosePrimaryExe(
            files: List<FileData>?,
            gameName: String,
        ): FileData? = files?.maxWithOrNull { a, b ->
            val sa = scoreExe(a, gameName, isExecutable(a.flags))   // <- fixed
            val sb = scoreExe(b, gameName, isExecutable(b.flags))

            when {
                sa != sb -> sa - sb                                 // higher score wins
                else -> (a.totalSize - b.totalSize).toInt()     // tie-break on size
            }
        }

        /**
         * Picks the real shipped EXE for a Steam app.
         *
         * ❶ try the dev-supplied launch entry (skip obvious stubs)
         * ❷ else score all manifest-flagged EXEs and keep the best
         * ❸ else fall back to the largest flagged EXE in the biggest depot
         * If everything fails, return the game's install directory.
         */
        fun getInstalledExe(appId: Int): String {
            val appInfo = getAppInfoOf(appId) ?: return ""

            val installDir = appInfo.config.installDir.ifEmpty { appInfo.name }

            val depots = appInfo.depots.values.filter { d ->
                !d.sharedInstall && isWindowsDepot(d)
            }
            Timber.i("Depots considered: $depots")

            /* launch targets (lower-case) */
            val launchTargets = appInfo.config.launch
                .mapNotNull { it.executable.lowercase() }.toSet() ?: emptySet()

            Timber.i("Launch targets from appinfo: $launchTargets")

            /* ---------------------------------------------------------- */
            val flagged = mutableListOf<Pair<FileData, Long>>()   // (file, depotSize)
            var largestDepotSize = 0L

            // Use DepotDownloader to fetch manifests
            val steamClient = instance?.steamClient
            val licenses = runBlocking { getLicensesFromDb() }
            if (steamClient == null || licenses.isEmpty()) {
                Timber.w("Cannot fetch manifests: steamClient or licenses not available")
                // Fallback to last resort
                return (getAppInfoOf(appId)?.let { appInfo ->
                    getWindowsLaunchInfos(appId).firstOrNull()
                })?.executable ?: ""
            }

            val installedBranch = PrefManager.getSteamSelectedBranch(appId)
            for (depot in depots) {
                val mi = depot.manifests[installedBranch]
                    ?: depot.encryptedManifests[installedBranch]
                    ?: depot.manifests["public"]
                    ?: continue
                if (mi.size > largestDepotSize) largestDepotSize = mi.size

                // Check cache first
                val man = DepotManifest.loadFromFile("${getAppDirPath(appId)}/.DepotDownloader/${depot.depotId}_${mi.gid}.manifest")

                Timber.d("Using manifest for depot ${depot.depotId}  size=${mi.size}")

                /* 1️⃣ exact launch entry that isn't a stub */
                man?.files?.firstOrNull { f ->
                    f.fileName.lowercase() in launchTargets && !f.isStub()
                }?.let {
                    Timber.i("Picked via launch entry: ${it.fileName}")
                    return it.fileName.replace('\\', '/').toString()
                }

                /* collect for later */
                man?.files?.filter { isExecutable(it.flags) || it.fileName.endsWith(".exe", true) }
                    ?.forEach { flagged += it to mi.size }
            }

            Timber.i("Flagged executable candidates: ${flagged.map { it.first.fileName }}")

            /* 2️⃣ scorer (unchanged) */
            choosePrimaryExe(
                flagged
                    .map { it.first }
                    .let { pool ->
                        val noStubs = pool.filterNot { it.isStub() }
                        if (noStubs.isNotEmpty()) noStubs else pool
                    },
                installDir.lowercase(),
            )?.let {
                Timber.i("Picked via scorer: ${it.fileName}")
                return it.fileName.replace('\\', '/')
            }

            /* 3️⃣ fallback: biggest exe from the biggest depot */
            flagged
                .filter { it.second == largestDepotSize }
                .maxByOrNull { it.first.totalSize }
                ?.let {
                    Timber.i("Picked via largest-depot fallback: ${it.first.fileName}")
                    return it.first.fileName.replace('\\', '/').toString()
                }

            /* 4️⃣ last resort */
            Timber.w("No executable found; falling back to install dir")
            return (getAppInfoOf(appId)?.let { appInfo ->
                getWindowsLaunchInfos(appId).firstOrNull()
            })?.executable ?: ""
        }

        fun getLaunchExecutable(appId: String, container: Container): String {
            val gameId = ContainerUtils.extractGameIdFromContainerId(appId)
            return getInstalledExe(gameId)
        }

        suspend fun deleteApp(appId: Int): Boolean = withContext(Dispatchers.IO) {
            val appDirPath = getAppDirPath(appId)
            val isUnsafeDeleteTarget = appDirPath == internalAppInstallPath || appDirPath == externalAppInstallPath

            // Guard against accidental root deletion if path resolution failed.
            if (isUnsafeDeleteTarget) {
                Timber.e("Refusing to delete appId=$appId because resolved path points to install root: $appDirPath")
                return@withContext false
            }

            // If an active download exists, stop it and wait briefly before deleting files.
            downloadJobs[appId]?.let { info ->
                info.isDeleting = true
                info.cancel("Cancelled for delete")
                info.awaitCompletion(timeoutMs = 5000L)
                removeDownloadJob(appId)
            }

            // Remove any download-complete marker
            if (!isUnsafeDeleteTarget) {
                MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                clearPersistedProgressSnapshot(appDirPath)
            }

            // Also delete staging and shadercache folders if they exist
            val stagingPath = Paths.get(defaultAppStagingPath, appId.toString()).pathString
            val stagingDir = File(stagingPath)
            if (stagingDir.exists()) {
                Timber.i("Deleting staging folder for appId $appId: $stagingPath")
                deleteRecursivelyWithRetries(stagingDir)
            }

            val shaderCachePath = Paths.get(defaultStoragePath, "Steam", "steamapps", "shadercache", appId.toString()).pathString
            val shaderCacheDir = File(shaderCachePath)
            if (shaderCacheDir.exists()) {
                Timber.i("Deleting shadercache folder for appId $appId: $shaderCachePath")
                deleteRecursivelyWithRetries(shaderCacheDir)
            }

            // Remove from DB synchronously so immediate reinstall cannot race with stale metadata.
            with(instance!!) {
                db.withTransaction {
                    appInfoDao.deleteApp(appId)
                    changeNumbersDao.deleteByAppId(appId)
                    fileChangeListsDao.deleteByAppId(appId)
                    downloadingAppInfoDao.deleteApp(appId)

                    // Clear installDir in steam_app table
                    appDao.findApp(appId)?.let { steamApp ->
                        if (steamApp.installDir.isNotEmpty()) {
                            appDao.update(steamApp.copy(installDir = ""))
                            Timber.i("Cleared installDir for appId $appId in DB")
                        }
                    }

                    val indirectDlcAppIds = getDownloadableDlcAppsOf(appId).orEmpty().map { it.id }
                    indirectDlcAppIds.forEach { dlcAppId ->
                        appInfoDao.deleteApp(dlcAppId)
                        changeNumbersDao.deleteByAppId(dlcAppId)
                        fileChangeListsDao.deleteByAppId(dlcAppId)
                    }
                }
            }

            return@withContext deleteRecursivelyWithRetries(File(appDirPath))
        }

        fun setCustomInstallPath(appId: Int, customInstallPath: String): String {
            val appInfo = getAppInfoOf(appId)
            val folderName = getAppDirName(appInfo)
            val safeFolderName = if (folderName.isNotEmpty()) folderName else appId.toString()

            val customFile = File(customInstallPath)
            val finalPath = if (customFile.name.equals(safeFolderName, ignoreCase = true)) {
                // User selected the game folder itself
                customFile.absolutePath
            } else {
                // User selected parent folder, create/use subfolder
                File(customInstallPath, safeFolderName).absolutePath
            }

            // Update SteamApp in DB
            runBlocking(Dispatchers.IO) {
                instance?.appDao?.findApp(appId)?.let { steamApp ->
                    instance?.appDao?.update(steamApp.copy(installDir = finalPath))
                    Timber.i("Updated SteamApp installDir in DB to: $finalPath")
                }
            }
            return finalPath
        }

        fun downloadApp(appId: Int): DownloadInfo? {
            val currentDownloadInfo = downloadJobs[appId]
            if (currentDownloadInfo != null) {
                if (!currentDownloadInfo.isActive()) {
                    removeDownloadJob(appId)
                } else {
                    return downloadApp(appId, currentDownloadInfo.downloadingAppIds, isUpdateOrVerify = false)
                }
            }

            val downloadingAppInfo = getDownloadingAppInfoOf(appId)
            val appDirPath = getAppDirPath(appId)
            val stagingDirPath = getStagingDirPath(appId)
            val hasCompleteMarker = MarkerUtils.hasMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            val hasPartialFiles = hasPartialDownloadFiles(appDirPath) || hasPartialDownloadFiles(stagingDirPath)
            val hasPersistedMetadata = hasPersistedDepotResumeMetadata(appDirPath) || hasPersistedDepotResumeMetadata(stagingDirPath)
            // Снапшот прогресса при staging-закачке лежит в staging.
            val resumeSnapshotDir = if (hasPersistedDepotResumeMetadata(stagingDirPath)) stagingDirPath else appDirPath
            val hasResumablePayload = if (hasCompleteMarker) {
                downloadingAppInfo != null || hasPersistedMetadata
            } else {
                hasPartialFiles
            }
            if (hasResumablePayload) {
                // Resume persisted progress whenever partial files exist, even if the
                // DownloadingAppInfo row is missing (can happen after cancellation races).
                val resumeDlcAppIds = downloadingAppInfo?.dlcAppIds
                    ?: run {
                        val inferred = inferResumeDlcAppIds(appId, resumeSnapshotDir)
                        if (inferred.isNotEmpty()) {
                            inferred
                        } else {
                            resolveInstalledDlcIdsForUpdateOrVerify(appId)
                        }
                    }
                return downloadApp(
                    appId = appId,
                    dlcAppIds = resumeDlcAppIds,
                    includeInstalledDepots = false,
                    enableVerify = false,
                    allowPersistedProgress = true,
                    hasPersistedResumeRow = downloadingAppInfo != null,
                )
            }

            if (downloadingAppInfo != null) {
                runBlocking(Dispatchers.IO) {
                    instance?.downloadingAppInfoDao?.deleteApp(appId)
                }
            }

            if (hasCompleteMarker && !hasPersistedMetadata) {
                clearPersistedProgressSnapshot(appDirPath)
            }

            if (!hasPartialFiles) {
                clearPersistedProgressSnapshot(appDirPath)
            }

            return downloadApp(
                appId = appId,
                dlcAppIds = resolveInstalledDlcIdsForUpdateOrVerify(appId),
                includeInstalledDepots = false,
                enableVerify = false,
                allowPersistedProgress = false,
            )
        }

        fun downloadAppForUpdate(appId: Int): DownloadInfo? {
            return downloadApp(
                appId,
                resolveInstalledDlcIdsForUpdateOrVerify(appId),
                includeInstalledDepots = true,
                enableVerify = false,
                allowPersistedProgress = false,
            )
        }

        fun downloadAppForUpdateTargeted(appId: Int): DownloadInfo? {
            val changedDepots = runBlocking(Dispatchers.IO) {
                getChangedDepotsForUpdate(appId)
            }
            if (changedDepots.isEmpty()) return null

            val branch = PrefManager.getSteamSelectedBranch(appId)
            val allDownloadable = getDownloadableDepots(appId)
            val targetedDepots = allDownloadable.filterKeys { it in changedDepots }

            if (targetedDepots.isEmpty()) return null

            val dlcIds = resolveInstalledDlcIdsForUpdateOrVerify(appId)
            return downloadApp(
                appId = appId,
                downloadableDepots = targetedDepots,
                userSelectedDlcAppIds = dlcIds,
                branch = branch,
                includeInstalledDepots = false,
                enableVerify = false,
                allowPersistedProgress = false,
            )
        }

        fun downloadAppForVerify(appId: Int): DownloadInfo? {
            return downloadApp(
                appId,
                resolveInstalledDlcIdsForUpdateOrVerify(appId),
                includeInstalledDepots = true,
                enableVerify = true,
                allowPersistedProgress = false,
            )
        }

        suspend fun checkAndRunAutoUpdates() {
            if (!PrefManager.autoUpdateEnabled) return
            if (!isConnected || !isLoggedIn) return
            if (PrefManager.autoUpdateWifiOnly && !isOnWifi()) return

            val appIds = instance?.appInfoDao?.getAllInstalledAppIds() ?: return
            for (appId in appIds) {
                if (downloadJobs.containsKey(appId)) continue
                try {
                    if (isUpdatePending(appId)) {
                        Timber.d("Auto-update: update pending for appId=$appId")
                        val dl = downloadAppForUpdateTargeted(appId)
                        if (dl == null) {
                            Timber.d("Auto-update: targeted update returned nothing for appId=$appId, falling back to full update")
                            downloadAppForUpdate(appId)
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Auto-update check failed for appId=$appId")
                }
            }
        }

        private fun isOnWifi(): Boolean {
            val instance = instance ?: return false
            val cm = instance.connectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        }

        private fun resolveInstalledDlcIdsForUpdateOrVerify(appId: Int): List<Int> {
            val dlcAppIds = getInstalledDlcDepotsOf(appId).orEmpty().toMutableList()

            getDownloadableDlcAppsOf(appId)?.forEach { dlcApp ->
                val installedDlcApp = getInstalledApp(dlcApp.id)
                if (installedDlcApp != null) {
                    dlcAppIds.add(installedDlcApp.id)
                }
            }

            return dlcAppIds.distinct()
        }

        fun downloadApp(appId: Int, dlcAppIds: List<Int>, isUpdateOrVerify: Boolean, customInstallPath: String? = null): DownloadInfo? {
            // Backward-compatible API:
            // true => include already-downloaded depots (update scope), but do not force verify.
            return downloadApp(
                appId = appId,
                dlcAppIds = dlcAppIds,
                includeInstalledDepots = isUpdateOrVerify,
                enableVerify = false,
                allowPersistedProgress = false,
                customInstallPath = customInstallPath,
            )
        }

        private fun downloadApp(
            appId: Int,
            dlcAppIds: List<Int>,
            includeInstalledDepots: Boolean,
            enableVerify: Boolean,
            allowPersistedProgress: Boolean = false,
            hasPersistedResumeRow: Boolean = false,
            customInstallPath: String? = null,
        ): DownloadInfo? {
            if (!checkWifiOrNotify()) {
                Timber.w("Download aborted: Wi-Fi only enabled but not connected to Wi-Fi")
                return null
            }
            val appInfo = getAppInfoOf(appId)
            if (appInfo == null) {
                Timber.e("Download aborted: Could not find AppInfo for appId: $appId")
                return null
            }

            val downloadableDepots = getDownloadableDepots(appId)
            if (downloadableDepots.isEmpty()) {
                Timber.w("Download aborted: No downloadable depots found for appId: $appId")
                instance?.let { service ->
                    service.scope.launch(Dispatchers.Main) {
                        Toast.makeText(service.applicationContext, "No downloadable content found for this game", Toast.LENGTH_LONG).show()
                    }
                }
                return null
            }

            // Delegate to the full depot-level downloadApp overload
            return downloadApp(
                appId = appId,
                downloadableDepots = downloadableDepots,
                userSelectedDlcAppIds = dlcAppIds,
                branch = PrefManager.getSteamSelectedBranch(appId),
                includeInstalledDepots = includeInstalledDepots,
                enableVerify = enableVerify,
                allowPersistedProgress = allowPersistedProgress,
                hasPersistedResumeRow = hasPersistedResumeRow,
                customInstallPath = customInstallPath,
            )
        }

        fun isImageFsInstalled(context: Context): Boolean {
            return ImageFs.find(context).isValid()
        }

        fun isImageFsInstallable(context: Context, variant: String): Boolean {
            if (variant.equals("BIONIC")) {
                return File(context.filesDir, "imagefs_bionic.txz").exists() || context.assets.list("")
                    ?.contains("imagefs_bionic.txz") == true
            } else {
                return File(context.filesDir, "imagefs_gamenative.txz").exists() || context.assets.list("")
                    ?.contains("imagefs_gamenative.txz") == true
            }
        }

        fun isSteamInstallable(context: Context): Boolean {
            return File(context.filesDir, "steam.tzst").exists()
        }

        fun isFileInstallable(context: Context, filename: String): Boolean {
            return File(context.filesDir, filename).exists()
        }

        suspend fun fetchFile(
            url: String,
            dest: File,
            onProgress: (Float) -> Unit,
        ) = withContext(Dispatchers.IO) {
            val tmp = File(dest.absolutePath + ".part")
            try {
                val http = SteamUtils.http

                val req = Request.Builder().url(url).build()
                http.newCall(req).execute().use { rsp ->
                    check(rsp.isSuccessful) { "HTTP ${rsp.code}" }
                    val body = rsp.body ?: error("empty body")
                    val total = body.contentLength()
                    tmp.outputStream().use { out ->
                        body.byteStream().copyTo(out, 8 * 1024) { read ->
                            onProgress(read.toFloat() / total)
                        }
                    }
                    if (total > 0 && tmp.length() != total) {
                        tmp.delete()
                        error("incomplete download")
                    }
                    if (!tmp.renameTo(dest)) {
                        tmp.copyTo(dest, overwrite = true)
                        tmp.delete()
                    }
                }
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
        }

        suspend fun fetchFileWithFallback(
            fileName: String,
            dest: File,
            context: Context,
            onProgress: (Float) -> Unit,
        ) = withContext(Dispatchers.IO) {
            val urls = downloadUrlsFor(fileName)
            var lastError: Exception? = null
            for ((index, url) in urls.withIndex()) {
                try {
                    fetchFile(url, dest, onProgress)
                    return@withContext
                } catch (e: Exception) {
                    lastError = e
                    if (index < urls.lastIndex) {
                        Timber.w(e, "Download failed from $url; retrying with next URL")
                    }
                }
            }

            dest.delete()
            withContext(Dispatchers.Main) {
                val msg = "Download failed with ${lastError?.message ?: "unknown error"}. Please disable VPN or try a different network."
                android.widget.Toast.makeText(context.applicationContext, msg, android.widget.Toast.LENGTH_LONG).show()
            }
            throw IOException(
                "Failed to download $fileName. Please check your network connection or try a VPN.",
                lastError,
            )
        }

        /** copyTo with progress callback */
        private inline fun InputStream.copyTo(
            out: OutputStream,
            bufferSize: Int = DEFAULT_BUFFER_SIZE,
            progress: (Long) -> Unit,
        ) {
            val buf = ByteArray(bufferSize)
            var bytesRead: Int
            var total = 0L
            while (read(buf).also { bytesRead = it } >= 0) {
                if (bytesRead == 0) continue
                out.write(buf, 0, bytesRead)
                total += bytesRead
                progress(total)
            }
        }

        fun downloadImageFs(
            onDownloadProgress: (Float) -> Unit,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            variant: String,
            context: Context,
        ) = parentScope.async {
            Timber.i("imagefs will be downloaded")
            if (variant == "BIONIC") {
                val dest = File(context.filesDir, "imagefs_bionic.txz")
                Timber.d("Downloading imagefs_bionic to " + dest.toString());
                fetchFileWithFallback("imagefs_bionic.txz", dest, context, onDownloadProgress)
            } else {
                Timber.d("Downloading imagefs_gamenative to " + File(context.filesDir, "imagefs_gamenative.txz"));
                fetchFileWithFallback(
                    "imagefs_gamenative.txz",
                    File(context.filesDir, "imagefs_gamenative.txz"),
                    context,
                    onDownloadProgress,
                )
            }
        }

        fun downloadImageFsPatches(
            onDownloadProgress: (Float) -> Unit,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            context: Context,
        ) = parentScope.async {
            Timber.i("imagefs will be downloaded")
            val dest = File(context.filesDir, "imagefs_patches_gamenative.tzst")
            Timber.d("Downloading imagefs_patches_gamenative.tzst to " + dest.toString());
            fetchFileWithFallback("imagefs_patches_gamenative.tzst", dest, context, onDownloadProgress)
        }

        fun downloadFile(
            onDownloadProgress: (Float) -> Unit,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            context: Context,
            fileName: String
        ) = parentScope.async {
            Timber.i("${fileName} will be downloaded")
            val dest = File(context.filesDir, fileName)
            Timber.d("Downloading ${fileName} to " + dest.toString());
            fetchFileWithFallback(fileName, dest, context, onDownloadProgress)
        }

        fun downloadSteam(
            onDownloadProgress: (Float) -> Unit,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            context: Context,
        ) = parentScope.async {
            Timber.i("imagefs will be downloaded")
            val dest = File(context.filesDir, "steam.tzst")
            Timber.d("Downloading steam.tzst to " + dest.toString());
            fetchFileWithFallback("steam.tzst", dest, context, onDownloadProgress)
        }

        private fun selectSteamControllerConfig(
            details: List<SteamControllerConfigDetail>,
        ): SteamControllerConfigDetail? {
            if (details.isEmpty()) return null

            val branchPriority = listOf("default", "public")
            val controllerPriority = listOf(
                "controller_xbox360",
                "controller_xboxone",
                "controller_steamcontroller_gordon",
                "controller_generic",
            )

            for (branch in branchPriority) {
                for (controllerType in controllerPriority) {
                    val match = details.firstOrNull { detail ->
                        detail.controllerType.equals(controllerType, ignoreCase = true) &&
                            detail.enabledBranches.any { it.equals(branch, ignoreCase = true) }
                    }
                    if (match != null) return match
                }
            }

            return null
        }

        private fun resolveSteamInputManifestFile(
            appId: Int,
            appDirPath: String,
        ): File? {
            val manifestPath = getAppInfoOf(appId)
                ?.config
                ?.steamInputManifestPath
                ?.trim()
                .orEmpty()
            if (manifestPath.isEmpty()) return null

            return resolvePathCaseInsensitive(appDirPath, manifestPath)
        }

        private fun loadConfigFromManifest(
            manifestFile: File,
        ): String? {
            if (!manifestFile.exists()) return null
            val manifestDirPath = manifestFile.parentFile?.path ?: return null

            val manifestText = manifestFile.readText(Charsets.UTF_8)
            val configText = try {
                parseManifestForConfig(manifestDirPath, manifestText)
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse Steam Input manifest config at ${manifestFile.path}")
                return null
            }
            return configText ?: manifestText
        }

        private fun parseManifestForConfig(
            manifestDirPath: String,
            manifestText: String,
        ): String? {
            return try {
                val kv = KeyValue.loadFromString(manifestText) ?: return null
                val actionManifest = if (kv.name?.equals("Action Manifest", ignoreCase = true) == true) {
                    kv
                } else {
                    kv["Action Manifest"]
                }
                if (actionManifest === KeyValue.INVALID) return null

                val configs = actionManifest["configurations"]
                if (configs === KeyValue.INVALID || configs.children.isEmpty()) {
                    throw IllegalStateException("No configurations found in Action Manifest")
                }

                val preferredControllers = listOf(
                    "controller_xboxone",
                    "controller_steamcontroller_gordon",
                    "controller_generic",
                    "controller_xbox360",
                )

                for (controllerType in preferredControllers) {
                    val controllerBlock = configs[controllerType]
                    if (controllerBlock === KeyValue.INVALID) continue

                    for (entry in controllerBlock.children) {
                        val pathNode = entry["path"]
                        val configPath = pathNode.asString().orEmpty()
                        if (pathNode === KeyValue.INVALID || configPath.isEmpty()) continue

                        val configFile = resolvePathCaseInsensitive(manifestDirPath, configPath)
                            ?: continue
                        return configFile.readText(Charsets.UTF_8)
                    }
                }

                throw IllegalStateException("No valid controller configuration found in Action Manifest")
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse Steam Input manifest config")
                null
            }
        }

        private fun resolvePathCaseInsensitive(
            baseDirPath: String,
            relativePath: String,
        ): File? {
            val normalizedPath = relativePath.replace('\\', '/')
            val directFile = File(baseDirPath, normalizedPath)
            if (directFile.exists()) return directFile

            var currentDir = File(baseDirPath)
            if (!currentDir.exists() || !currentDir.isDirectory) return null

            val segments = normalizedPath.split('/').filter { it.isNotEmpty() }
            for ((index, segment) in segments.withIndex()) {
                if (segment == ".") continue
                if (segment == "..") {
                    currentDir = currentDir.parentFile ?: return null
                    continue
                }
                val entries = currentDir.listFiles() ?: return null
                val matched = entries.firstOrNull {
                    it.name.equals(segment, ignoreCase = true)
                } ?: return null

                if (index == segments.lastIndex) {
                    return matched
                }

                if (!matched.isDirectory) return null
                currentDir = matched
            }

            return null
        }

        private fun readBuiltInSteamInputTemplate(fileName: String): String? {
            val assets = instance?.assets ?: return null
            return runCatching {
                assets.open("steaminput/$fileName").use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                }
            }.getOrNull()
        }

        private fun readDownloadedSteamInputTemplate(appId: Int): String? {
            val configFile = File(getAppDirPath(appId), STEAM_CONTROLLER_CONFIG_FILENAME)
            if (!configFile.exists()) return null
            return configFile.readText(Charsets.UTF_8)
        }

        fun resolveSteamControllerVdfText(appId: Int): String? {
            val config = getAppInfoOf(appId)?.config ?: return null
            return when (config.steamControllerTemplateIndex) {
                1 -> readDownloadedSteamInputTemplate(appId)
                13 -> {
                    val manifestFile = resolveSteamInputManifestFile(appId, getAppDirPath(appId))
                        ?: return null
                    loadConfigFromManifest(manifestFile)
                }
                2, 12 -> readBuiltInSteamInputTemplate("controller_xboxone_gamepad_fps.vdf")
                6 -> readBuiltInSteamInputTemplate("controller_xboxone_wasd.vdf")
                4, 5 -> readBuiltInSteamInputTemplate("gamepad_joystick.vdf")
                else -> readBuiltInSteamInputTemplate("gamepad_joystick.vdf")
            }
        }

        fun downloadApp(
            appId: Int,
            downloadableDepots: Map<Int, DepotInfo>,
            userSelectedDlcAppIds: List<Int>,
            branch: String,
            includeInstalledDepots: Boolean,
            enableVerify: Boolean,
            allowPersistedProgress: Boolean = false,
            hasPersistedResumeRow: Boolean = false,
            customInstallPath: String? = null,
        ): DownloadInfo? {
            var appDirPath = getAppDirPath(appId)
            Timber.i("downloadApp called for appId: $appId, customInstallPath: $customInstallPath")

            if (customInstallPath != null) {
                // Determine if customInstallPath is the game folder itself or the parent
                val appInfo = getAppInfoOf(appId)
                val folderName = getAppDirName(appInfo)
                val safeFolderName = if (folderName.isNotEmpty()) folderName else appId.toString()
                
                val customFile = File(customInstallPath)
                val finalPath = if (customFile.name.equals(safeFolderName, ignoreCase = true)) {
                    // User selected the game folder itself
                    customFile.absolutePath
                } else {
                    // User selected parent folder, create/use subfolder
                    File(customInstallPath, safeFolderName).absolutePath
                }
                
                appDirPath = finalPath
                Timber.i("Final custom appDirPath: $appDirPath")
                
                // Update SteamApp in DB
                runBlocking {
                    if (appInfo != null) {
                        val updatedApp = appInfo.copy(installDir = finalPath)
                        instance?.appDao?.update(updatedApp)
                        Timber.i("Updated SteamApp installDir in DB to: $finalPath")
                    }
                }
            }

            if (!checkWifiOrNotify()) {
                Timber.w("Download aborted: Wi-Fi only enabled but not connected to Wi-Fi")
                return null
            }

            // Free-space gate: manifest-declared size vs StatFs на томе установки. Тост + abort.
            val manifestRequiredBytes = downloadableDepots.values.sumOf { depot ->
                (depot.manifests[branch] ?: depot.encryptedManifests[branch])?.size ?: 0L
            }
            if (manifestRequiredBytes > 0L) {
                val spaceTarget = customInstallPath ?: appDirPath
                val freeBytes = runCatching { StorageUtils.getAvailableSpace(spaceTarget) }.getOrDefault(-1L)
                if (freeBytes >= 0L && freeBytes < manifestRequiredBytes) {
                    Timber.w("Download aborted: need ${manifestRequiredBytes}B, free ${freeBytes}B at $spaceTarget (appId=$appId)")
                    instance?.let { service ->
                        service.scope.launch(Dispatchers.Main) {
                            Toast.makeText(
                                service.applicationContext,
                                "Not enough free space: need ${StorageUtils.formatBinarySize(manifestRequiredBytes)}, free ${StorageUtils.formatBinarySize(freeBytes)}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                    return null
                }
            }

            // Ensure the download directory exists
            try {
                val dir = File(appDirPath)
                if (!dir.exists()) {
                    if (dir.mkdirs()) {
                        Timber.i("Created download directory: $appDirPath")
                    } else {
                        Timber.e("Failed to create download directory (mkdirs returned false): $appDirPath")
                        instance?.let { service ->
                            service.scope.launch(Dispatchers.Main) {
                                Toast.makeText(service.applicationContext, "Failed to create download directory. Check permissions.", Toast.LENGTH_LONG).show()
                            }
                        }
                        return null
                    }
                }
                
                // Add in-progress marker
                if (!MarkerUtils.addMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)) {
                    Timber.e("Failed to add DOWNLOAD_IN_PROGRESS_MARKER at $appDirPath")
                }
                
                // If this is not an update/verify, remove the complete marker to reset state
                if (!includeInstalledDepots) {
                    MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error preparing download directory or markers: $appDirPath")
            }

            // If a custom path is provided, we want to force a new download at that location
            if (customInstallPath != null) {
                Timber.i("Custom path provided, cancelling any existing job for appId: $appId")
                downloadJobs[appId]?.cancel("Restarting download at custom path")
                downloadJobs.remove(appId)
            } else {
                // Only return existing job if it's still active
                val existingJob = downloadJobs[appId]
                if (existingJob != null && existingJob.isActive()) {
                    Timber.i("Returning existing active download job for appId: $appId")
                    return existingJob
                }
            }

            Timber.d("Checking depots for appId: $appId. downloadableDepots count: ${downloadableDepots.size}")
            if (downloadableDepots.isEmpty()) {
                Timber.w("Download aborted: downloadableDepots is empty for appId: $appId")
                return null
            }

            val indirectDlcAppIds = getDownloadableDlcAppsOf(appId).orEmpty().map { it.id }
            Timber.d("Indirect DLC app IDs for appId $appId: $indirectDlcAppIds")

            // Depots from Main game
            val mainDepots = getMainAppDepots(appId)
            Timber.d("Main app depots count: ${mainDepots.size}")
            val originalMainAppDepots = mainDepots.filter { (_, depot) ->
                depot.dlcAppId == INVALID_APP_ID
            } + mainDepots.filter { (_, depot) ->
                userSelectedDlcAppIds.contains(depot.dlcAppId) && depot.manifests.isNotEmpty()
            }
            var mainAppDepots = originalMainAppDepots
            Timber.d("Filtered main app depots count: ${mainAppDepots.size}")

            // Depots from DLC App
            val dlcAppDepots = downloadableDepots.filter { (_, depot) ->
                !mainAppDepots.map { it.key }.contains(depot.depotId) &&
                userSelectedDlcAppIds.contains(depot.dlcAppId) && indirectDlcAppIds.contains(depot.dlcAppId) && depot.manifests.isNotEmpty()
            }
            Timber.d("Filtered DLC app depots count: ${dlcAppDepots.size}")

            // Remove depots that are already downloaded only when install metadata is trusted.
            // But if a custom path is provided, we want to check/download everything at the new location
            var installedApp = getInstalledApp(appId)
            val hasCompleteMarker = MarkerUtils.hasMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            var hasTrustedInstalledState = installedApp?.isDownloaded == true && hasCompleteMarker
            if (!includeInstalledDepots && installedApp != null && !hasTrustedInstalledState && customInstallPath == null) {
                val hasStaleInstallMetadata = installedApp.isDownloaded ||
                    installedApp.downloadedDepots.isNotEmpty() ||
                    installedApp.dlcDepots.isNotEmpty()
                if (hasStaleInstallMetadata) {
                    Timber.w(
                        "Clearing stale install metadata for appId=$appId " +
                            "(isDownloaded=${installedApp.isDownloaded}, marker=$hasCompleteMarker)",
                    )
                    runBlocking(Dispatchers.IO) {
                        instance?.appInfoDao?.deleteApp(appId)
                    }
                    installedApp = null
                }
                hasTrustedInstalledState = false
            }
            if (installedApp != null && !includeInstalledDepots && hasTrustedInstalledState && customInstallPath == null) {
                val beforeCount = mainAppDepots.size
                mainAppDepots = mainAppDepots.filter { it.key !in installedApp.downloadedDepots }
                Timber.d("Removed already downloaded depots. Count before: $beforeCount, after: ${mainAppDepots.size}")
            }

            // Атомарный финал: свежие закачки идут в staging, в финал — rename в completeAppDownload.
            // Апдейт/verify/доверенное состояние и кастомный путь пишут напрямую в финал.
            val stagingDirPath = getStagingDirPath(appId)
            val useStaging = !includeInstalledDepots && !enableVerify && customInstallPath == null && !hasTrustedInstalledState
            val workDirPath = if (useStaging) stagingDirPath else appDirPath
            if (useStaging) {
                runCatching { File(stagingDirPath).mkdirs() }
            }

            val allDepots = originalMainAppDepots + dlcAppDepots
            // Use install (uncompressed) size for progress tracking
            val depotSizeById = allDepots.mapValues { (_, depot) ->
                val mInfo = depot.manifests[branch] ?: depot.encryptedManifests[branch]
                (mInfo?.size ?: 1L).coerceAtLeast(1L)
            }

            // Load persisted progress snapshot to skip fully downloaded depots.
            // Снапшот могут лежать в staging (resume) или в финале (старый layout) — читаем
            // только из workDir, чужой снапшот сносим чтобы не врал прогресс.
            val persistedSourceDir = if (allowPersistedProgress && hasPersistedDepotResumeMetadata(stagingDirPath)) stagingDirPath else appDirPath
            val persistedDepotBytes = if (allowPersistedProgress && persistedSourceDir == workDirPath) {
                DownloadInfo.loadPersistedDepotBytes(workDirPath)
            } else {
                if (allowPersistedProgress && persistedSourceDir != workDirPath) {
                    clearPersistedProgressSnapshot(persistedSourceDir)
                }
                emptyMap()
            }

            val fullyDownloadedDepotsFromSnapshot = mutableSetOf<Int>()
            if (persistedDepotBytes.isNotEmpty()) {
                for ((depotId, _) in allDepots) {
                    val depotSize = depotSizeById[depotId] ?: 1L
                    val downloadedBytes = persistedDepotBytes[depotId] ?: 0L
                    if (downloadedBytes >= depotSize) {
                        fullyDownloadedDepotsFromSnapshot.add(depotId)
                    }
                }
                if (fullyDownloadedDepotsFromSnapshot.isNotEmpty()) {
                    Timber.i("Skipping ${fullyDownloadedDepotsFromSnapshot.size} fully downloaded depots from snapshot")
                    mainAppDepots = mainAppDepots.filter { it.key !in fullyDownloadedDepotsFromSnapshot }
                }
            }

            // Combine main app and DLC depots
            val filteredDlcAppDepots = dlcAppDepots.filter { it.key !in fullyDownloadedDepotsFromSnapshot }
            val selectedDepots = mainAppDepots + filteredDlcAppDepots
            Timber.i("Total selected depots for download: ${selectedDepots.size}")

            if (selectedDepots.isEmpty()) {
                // Check if it was empty even before snapshot filtering
                var preSnapshotMainAppDepots = originalMainAppDepots
                if (installedApp != null && !includeInstalledDepots && hasTrustedInstalledState) {
                    preSnapshotMainAppDepots = preSnapshotMainAppDepots.filter { it.key !in installedApp.downloadedDepots }
                }
                val preSnapshotSelectedDepots = preSnapshotMainAppDepots + dlcAppDepots
                
                if (preSnapshotSelectedDepots.isEmpty()) {
                    Timber.i("selectedDepots is empty before snapshot filtering - App already installed.")
                    
                    // Instead of returning null, create a completed/verifying job so it shows in UI
                    val info = DownloadInfo(1, appId, getAppInfoOf(appId)?.name ?: "Game", CopyOnWriteArrayList(listOf(appId)), { name, prog, down, tot, speed ->
                        instance?.notificationHelper?.notifyProgress(name, prog, down, tot, speed)
                    })
                    info.updateStatus(DownloadPhase.COMPLETE)
                    info.setProgress(1f)
                    downloadJobs[appId] = info
                    
                    if (allowPersistedProgress) {
                        Timber.i("Resume became a no-op; clearing stale persisted resume state")
                        clearFailedResumeState(appId)
                    }
                    
                    MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                    MarkerUtils.addMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                    
                    // Show success message to user
                    instance?.let { service ->
                        service.scope.launch(Dispatchers.Main) {
                            Toast.makeText(service.applicationContext, "Download complete", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    return info
                }

                // Snapshot says all depots are complete but marker is missing.
                // Finalize metadata/markers directly instead of re-queuing depots.
                val canFinalizeFromSnapshot = allowPersistedProgress &&
                    fullyDownloadedDepotsFromSnapshot.isNotEmpty() &&
                    (hasCompleteMarker || hasPersistedResumeRow)
                if (canFinalizeFromSnapshot) {
                    Timber.i("All resume depots appear complete from snapshot; finalizing without downloader")
                    val info = finalizeSnapshotResumeAsComplete(
                        appId = appId,
                        appDirPath = appDirPath,
                        mainAppDepots = preSnapshotMainAppDepots,
                        dlcAppDepots = dlcAppDepots,
                        userSelectedDlcAppIds = userSelectedDlcAppIds,
                    )
                    MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                    return info
                } else {
                    if (allowPersistedProgress) {
                        if (fullyDownloadedDepotsFromSnapshot.isNotEmpty()) {
                            Timber.w(
                                "Snapshot indicates completion for appId=$appId but state is untrusted " +
                                    "(marker=$hasCompleteMarker, resumeRow=$hasPersistedResumeRow); clearing resume metadata",
                            )
                        } else {
                            Timber.i("selectedDepots resolved empty on resume; clearing stale resume metadata")
                        }
                        clearFailedResumeState(appId)
                    } else {
                        Timber.i("selectedDepots resolved empty after filtering; skipping download start")
                    }
                }
                MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                return null
            }

            val downloadingAppIds = CopyOnWriteArrayList<Int>()
            val calculatedDlcAppIds = CopyOnWriteArrayList<Int>()
            val allDepotIdsByDlcAppId = dlcAppDepots.values
                .groupBy(keySelector = { it.dlcAppId }, valueTransform = { it.depotId })
                .mapValues { (_, depotIds) -> depotIds.sorted() }
            val selectedDepotIdsByDlcAppId = selectedDepots.values
                .groupBy(keySelector = { it.dlcAppId }, valueTransform = { it.depotId })
                .mapValues { (_, depotIds) -> depotIds.sorted() }

            userSelectedDlcAppIds.forEach { dlcAppId ->
                if (allDepotIdsByDlcAppId[dlcAppId]?.isNotEmpty() == true) {
                    downloadingAppIds.add(dlcAppId)
                    calculatedDlcAppIds.add(dlcAppId)
                }
            }

            // Add main app ID if there are main app depots
            if (mainAppDepots.isNotEmpty()) {
                downloadingAppIds.add(appId)
            }

            // There are some apps, the dlc depots does not have dlcAppId in the data, need to set it back
            val mainAppDlcIds = getMainAppDlcIdsWithoutProperDepotDlcIds(appId)

            // If there are no DLC depots, download the main app only
            if (dlcAppDepots.isEmpty()) {
                // Because all dlcIDs are coming from main depots, need to add the dlcID to main app in order to save it to db after finish download
                mainAppDlcIds.addAll(mainAppDepots.filter { it.value.dlcAppId != INVALID_APP_ID }.map { it.value.dlcAppId }.distinct())

                // Refresh id List, so only main app is downloaded
                calculatedDlcAppIds.clear()
                downloadingAppIds.clear()
                downloadingAppIds.add(appId)
            }

            Timber.i("Starting download for $appId")
            Timber.i("App contains ${mainAppDepots.size} depot(s): ${mainAppDepots.keys}")
            Timber.i("DLC contains ${dlcAppDepots.size} depot(s): ${dlcAppDepots.keys}")
            Timber.i("downloadingAppIds: $downloadingAppIds")

            val service = instance ?: run {
                Timber.e("SteamService instance is null, cannot start download job.")
                return null
            }

            // Save downloading app info
            runBlocking {
                service.downloadingAppInfoDao.insert(
                    DownloadingAppInfo(
                        appId,
                        dlcAppIds = userSelectedDlcAppIds
                    ),
                )
                Unit
            }

            val maxParallel = PrefManager.downloadQueueSize
            val activeCount = downloadJobs.values.count { it.isActive() && it.getStatusFlow().value != DownloadPhase.QUEUED }

            if (activeCount >= maxParallel) {
                Timber.i("Download limit reached ($maxParallel), queuing appId: $appId")
                val queueNotify: (String, Float, String, String, String) -> Unit = { name, prog, down, tot, speed ->
                    instance?.notificationHelper?.notifyProgress(name, prog, down, tot, speed)
                }
                val info = DownloadInfo(selectedDepots.size, appId, getAppInfoOf(appId)?.name ?: "Game", downloadingAppIds, queueNotify).also { di ->
                    di.setPersistencePath(workDirPath)
                    di.updateStatus(DownloadPhase.QUEUED, "Queued...")
                    di.setActive(false)
                }
                downloadJobs[appId] = info
                notifyDownloadStarted(appId)
                return info
            }

            val info = DownloadInfo(selectedDepots.size, appId, getAppInfoOf(appId)?.name ?: "Game", downloadingAppIds, { name, prog, down, tot, speed ->
                instance?.notificationHelper?.notifyProgress(name, prog, down, tot, speed)
            }).also { di ->
                di.setPersistencePath(workDirPath)

                // Set weights for each depot based on manifest sizes
                val selectedDepotSizes = selectedDepots.mapValues { (depotId, _) ->
                    depotSizeById[depotId] ?: 1L
                }
                selectedDepots.keys.forEachIndexed { index, depotId ->
                    di.setWeight(index, selectedDepotSizes[depotId] ?: 1L)
                }

                // Track progress only for depots in this active run so excluded/complete depots
                // (including DLC already marked complete) cannot pre-fill progress at startup.
                val selectedTotalBytes = selectedDepotSizes.values.sum()
                val totalBytes = selectedTotalBytes.coerceAtLeast(1L)

                // Total expected size (used for ETA based on recent download speed)
                di.setTotalExpectedBytes(totalBytes)

                var resumedBytes = 0L

                if (allowPersistedProgress) {
                    for ((depotId, bytes) in persistedDepotBytes) {
                        // If the depot was excluded because it's fully downloaded, we still need to track its bytes
                        // so that future snapshots retain this progress.
                        val depotSize = depotSizeById[depotId] ?: continue
                        val safeBytes = bytes.coerceIn(0L, depotSize)
                        di.depotCumulativeUncompressedBytes[depotId] = java.util.concurrent.atomic.AtomicLong(safeBytes)
                        // Count resumed bytes only for depots actively downloading in this run.
                        if (depotId in selectedDepots) {
                            resumedBytes += safeBytes
                        }
                    }
                } else {
                    di.clearPersistedBytesDownloaded(workDirPath)
                }
                resumedBytes = resumedBytes.coerceIn(0L, totalBytes)

                if (resumedBytes > 0L) {
                    di.initializeBytesDownloaded(resumedBytes)
                    Timber.i("Resumed download: initialized with $resumedBytes bytes")
                }

                val downloadJob = service.scope.launch {
                    var depotDownloader: DepotDownloader? = null
                    try {
                        // Эталон GameNative: перед update/verify чистим stale DRM-бэкапы,
                        // иначе restore-pass перезатрёт свежие файлы старыми
                        if (includeInstalledDepots || enableVerify) {
                            runCatching { SteamUtils.clearStaleDrmBackups(appDirPath) }
                        }
                        // Retry loop for transient Steam API failures (AsyncJobFailedException),
                        // missing client or download stalls (StallTimeoutException).
                        val maxRetries = 5
                        var lastException: Exception? = null
                        // Нет активности дольше — считаем закачку зависшей (телефон захлебнулся
                        // или CDN-хост умер) и перезапускаем попытку. 60с: 90с слишком долго
                        // держали мёртвую сессию (скорость 0, прогресс стоит).
                        val stallTimeoutMs = 60_000L

                        for (attempt in 1..maxRetries) {
                            lastException = null
                            try {
                                if (attempt > 1) {
                                    Timber.i("Retry attempt $attempt/$maxRetries for appId: $appId")
                                    di.updateStatusMessage("Retrying download (attempt $attempt/$maxRetries)...")
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(instance?.applicationContext ?: return@withContext, "Retrying download (attempt $attempt/$maxRetries)...", Toast.LENGTH_SHORT).show()
                                    }
                                    // Экспоненциальный бэкофф 5/10/20/30/45с: долбёжка по 3с
                                    // возвращала ту же мёртвую сессию/CDN-хост.
                                    val backoffMs = when (attempt) {
                                        2 -> 5_000L
                                        3 -> 10_000L
                                        4 -> 20_000L
                                        5 -> 30_000L
                                        else -> 45_000L
                                    }
                                    kotlinx.coroutines.delay(backoffMs)
                                }

                                // Wait for steamClient to be connected and logged in
                                var client = instance?.steamClient
                                var waitAttempts = 0
                                // Increased wait limit and robustness
                                while ((client == null || !isConnected || !isLoggedIn) && waitAttempts < 60) {
                                    val reason = when {
                                        client == null -> "initializing"
                                        !isConnected -> "connecting"
                                        !isLoggedIn -> "logging in"
                                        else -> "waiting"
                                    }
                                    Timber.i("Waiting for Steam client ($reason, attempt $waitAttempts)...")
                                    di.updateStatusMessage("Waiting for connection ($reason)...")
                                    delay(1000L)
                                    client = instance?.steamClient
                                    waitAttempts++
                                    
                                    // If waiting too long, check network
                                    if (waitAttempts % 5 == 0 && !isWifiConnected && PrefManager.downloadOnWifiOnly) {
                                         di.updateStatusMessage("Waiting for Wi-Fi...")
                                    }
                                }

                                if (client == null || !isConnected || !isLoggedIn) {
                                    throw Exception("Steam client not connected or logged in. Please check your connection and login status.")
                                }

                                // Get licenses from database
                                Timber.i("Retrieving licenses from database for appId: $appId")
                                di.updateStatusMessage("Retrieving licenses...")
                                var licenses = getLicensesFromDb()
                                waitAttempts = 0
                                while (licenses.isEmpty() && waitAttempts < 10) {
                                    Timber.i("Waiting for licenses to be available (attempt $waitAttempts)...")
                                    di.updateStatusMessage("Waiting for licenses...")
                                    delay(1000L)
                                    licenses = getLicensesFromDb()
                                    waitAttempts++
                                }

                                if (licenses.isEmpty()) {
                                    throw Exception("No Steam licenses found. Please ensure you are logged in.")
                                }
                                Timber.i("Retrieved ${licenses.size} licenses from database")

                                // Memory-safe thread limits for mobile devices.
                                // Каждый decompress-поток ест ~8MB ThreadLocal — телефон захлёбывается
                                // на 128 потоках: скорость прёт, потом decompress/диск не вывозят и всё встаёт в 0.
                                // Лимиты — из DownloadSpeedConfig (тиры по ядрам 8/16/24/32, дефолт cores/2).
                                val speedConfig = DownloadSpeedConfig()
                                val cpuCores = speedConfig.cpuCores
                                val maxDownloads = speedConfig.maxDownloads
                                val maxDecompress = speedConfig.maxDecompress.coerceAtMost(maxDownloads)

                                Timber.i("Download Config - Cores: $cpuCores, Speed setting: ${PrefManager.downloadSpeed}")
                                Timber.i("Threads - Max Downloads: $maxDownloads, Max Decompress: $maxDecompress")

                                // CDN warm-up: HEAD-ранкинг SteamPipe-хостов перед DepotDownloader.
                                // Пул даунлоадера 1.8.0 всё равно сортирует по weightedLoad сам — это только
                                // греет DNS/TLS и пишет быстрейшие хосты в лог для разбора сталлов.
                                // На каждой попытке: после сталла server_list сносится и хосты уже
                                // другие — ранкать надо свежий список. Best-effort, ошибка — дальше.
                                runCatching {
                                    di.updateStatusMessage("Checking CDN...")
                                    val steamClient = client
                                    if (steamClient != null) {
                                        runCatching {
                                            val pipeServers = withTimeoutOrNull(8_000L) {
                                                steamClient.getHandler(SteamContent::class.java)
                                                    ?.getServersForSteamPipe(parentScope = service.scope)
                                                    ?.await()
                                            }.orEmpty()
                                            val probeUrls = pipeServers
                                                .filter { it.type == "SteamCache" || it.type == "CDN" }
                                                .mapNotNull { server -> cdnProbeUrl(server) }
                                                .distinct()
                                            if (probeUrls.size > 1) {
                                                val ranked = CdnRankingUtils.rankBaseUrlsByHeadProbe(probeUrls, Net.http)
                                                Timber.i("CDN rank for appId $appId: fastest=${ranked.take(3)} (${ranked.size} hosts)")
                                            }
                                        }.onFailure { e ->
                                            Timber.d("CDN pre-rank skipped for appId $appId: ${e.message}")
                                        }
                                    }
                                }


                                // Create DepotDownloader instance
                                Timber.i("Initializing DepotDownloader for appId: $appId (attempt $attempt)")
                                di.updateStatusMessage("Initializing downloader...")
                                depotDownloader = DepotDownloader(
                                    client,
                                    licenses,
                                    debug = false,
                                    androidEmulation = true,
                                    maxDownloads = maxDownloads,
                                    maxDecompress = maxDecompress,
                                    maxFileWrites = 1,
                                    parentJob = coroutineContext[Job],
                                )

                                // Create listeners for DLC apps
                                val depotIdToIndex = selectedDepots.keys.mapIndexed { index, depotId -> depotId to index }.toMap()
                                val listener = AppDownloadListener(
                                    di,
                                    depotIdToIndex,
                                    selectedDepotSizes,
                                )
                                depotDownloader!!.addListener(listener)

                                if (mainAppDepots.isNotEmpty()) {
                                    val mainAppDepotIds = mainAppDepots.keys.sorted()
                                    val mainAppItem = AppItem(
                                        appId,
                                        installDirectory = workDirPath,
                                        depot = mainAppDepotIds,
                                        verify = enableVerify,
                                    )
                                    depotDownloader!!.add(mainAppItem)
                                }

                                calculatedDlcAppIds.forEach { dlcAppId ->
                                    val dlcDepotIds = selectedDepotIdsByDlcAppId[dlcAppId].orEmpty()
                                    if (dlcDepotIds.isEmpty()) return@forEach

                                    val dlcAppItem = AppItem(
                                        dlcAppId,
                                        installDirectory = workDirPath,
                                        depot = dlcDepotIds,
                                        verify = enableVerify,
                                    )
                                    depotDownloader!!.add(dlcAppItem)
                                }

                                // Steam Controller Config download
                                val appConfig = getAppInfoOf(appId)?.config
                                if (appConfig?.steamControllerTemplateIndex == 1) {
                                    val controllerConfig = appConfig.steamControllerConfigDetails
                                        .let { selectSteamControllerConfig(it) }

                                    if (controllerConfig != null) {
                                        val publishedFileId = controllerConfig.publishedFileId
                                        runCatching {
                                            val requestBody = FormBody.Builder().add("itemcount", "1").add("publishedfileids[0]", publishedFileId.toString()).build()
                                            val request = Request.Builder().url("https://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1").post(requestBody).build()
                                            Net.http.newCall(request).execute().use { response ->
                                                if (response.isSuccessful) {
                                                    val responseBody = response.body?.string()
                                                    if (!responseBody.isNullOrEmpty()) {
                                                        val responseJson = JSONObject(responseBody)
                                                        val responseData = responseJson.optJSONObject("response")
                                                        val fileUrl = responseData?.optJSONArray("publishedfiledetails")?.optJSONObject(0)?.optString("file_url", "")?.trim()
                                                        if (!fileUrl.isNullOrEmpty()) {
                                                            val configFile = File(workDirPath, STEAM_CONTROLLER_CONFIG_FILENAME)
                                                            val downloadRequest = Request.Builder().url(fileUrl).get().build()
                                                            Net.http.newCall(downloadRequest).execute().use { downloadResponse ->
                                                                if (downloadResponse.isSuccessful) {
                                                                    downloadResponse.body?.byteStream()?.use { input ->
                                                                        configFile.outputStream().use { output -> input.copyTo(output) }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Signal that no more items will be added
                                depotDownloader!!.finishAdding()

                                Timber.i("Downloading game to $appDirPath (attempt $attempt)")

                                // Wait for completion - safely handle the deferred result to avoid Unit cast errors.
                                // Плюс stall-watchdog: если чанки встали (скорость 0), await висел бы вечно.
                                di.markActivity()
                                try {
                                    val completion = depotDownloader?.getCompletion()
                                    if (completion is kotlinx.coroutines.Deferred<*>) {
                                        Timber.i("Waiting for DepotDownloader Deferred completion...")
                                        var stallFired = false
                                        while (!completion.isCompleted) {
                                            coroutineContext.ensureActive()
                                            kotlinx.coroutines.delay(10_000L)
                                            val idleMs = System.currentTimeMillis() - di.lastActivityMs
                                            val phase = di.getStatusFlow().value
                                            val stalled = !stallFired && idleMs > stallTimeoutMs &&
                                                di.isActive() && !di.isCancelling &&
                                                (phase == DownloadPhase.DOWNLOADING || phase == DownloadPhase.PREPARING) &&
                                                di.getProgress() < 1f
                                            if (stalled) {
                                                stallFired = true
                                                Timber.w("Download stall: no activity for ${idleMs / 1000}s (phase=$phase), restarting attempt")
                                                di.updateStatusMessage("Закачка зависла, перезапуск...")
                                                runCatching { depotDownloader?.close() }
                                                // Сносим кэш CDN-листа — следующая попытка возьмёт свежие хосты
                                                runCatching { java.io.File(serverListPath).delete() }
                                                throw StallTimeoutException("No download activity for ${idleMs / 1000}s")
                                            }
                                        }
                                        completion.await()
                                    } else if (completion != null) {
                                        // If it's a CompletableFuture or other type, try to join it
                                        Timber.i("Downloader completion is ${completion.javaClass.simpleName}, waiting...")
                                        if (completion is java.util.concurrent.CompletableFuture<*>) {
                                            completion.await() // Suspend instead of block, allowing cancellation
                                        }
                                    } else {
                                        Timber.i("Downloader completion is null, assuming immediate success")
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    if (e is StallTimeoutException) throw e // в ретрай-цикл, не глотать
                                    Timber.w(e, "DepotDownloader completion await encountered an error")
                                }
                                
                                coroutineContext.ensureActive()
                                if (!di.isActive() || di.isCancelling) {
                                    Timber.i(
                                        "DepotDownloader completion returned but DownloadInfo is no longer active " +
                                        "(isActive=${di.isActive()}, isCancelling=${di.isCancelling}). " +
                                        "Skipping completeAppDownload — the user paused or cancelled."
                                    )
                                    throw CancellationException(if (di.isCancelling) "Cancelled by user" else "Paused by user")
                                }

                                Timber.i("DepotDownloader finished for appId: $appId")

                                // If it was extremely fast (e.g. already downloaded), ensure some visibility in UI
                                if (di.getProgress() >= 1.0f) {
                                    delay(1000)
                                }

                                // If we got here without exception, download succeeded
                                break
                            } catch (e: AsyncJobFailedException) {
                                lastException = e
                                Timber.w(e, "AsyncJobFailedException on attempt $attempt/$maxRetries for appId: $appId")
                                // Close the downloader from the failed attempt
                                runCatching { depotDownloader?.close() }.onFailure { closeError ->
                                    Timber.w(closeError, "Failed to close downloader on retry for app $appId")
                                }
                                depotDownloader = null
                                if (attempt >= maxRetries) {
                                    Timber.e("All $maxRetries retry attempts failed for appId: $appId")
                                    throw e
                                }
                                di.setActive(true)
                                continue
                            } catch (e: StallTimeoutException) {
                                lastException = e
                                Timber.w(e, "StallTimeoutException on attempt $attempt/$maxRetries for appId: $appId")
                                // Close the downloader from the stalled attempt
                                runCatching { depotDownloader?.close() }.onFailure { closeError ->
                                    Timber.w(closeError, "Failed to close downloader on stall retry for app $appId")
                                }
                                depotDownloader = null
                                if (attempt >= maxRetries) {
                                    Timber.e("All $maxRetries retry attempts failed for appId: $appId")
                                    throw e
                                }
                                di.setActive(true)
                                di.markActivity()
                                continue
                            }
                        }

                        // Complete app download - Wrap in try-catch to ensure we don't crash at the finish line
                        try {
                            di.updateStatusMessage("Finalizing installation...")
                            Timber.i("Finalizing installation at path: $appDirPath")
                            if (originalMainAppDepots.isNotEmpty()) {
                                val mainAppDepotIds = originalMainAppDepots.keys.sorted()
                                completeAppDownload(di, appId, mainAppDepotIds, mainAppDlcIds, appDirPath)
                            }

                            calculatedDlcAppIds.forEach { dlcAppId ->
                                val dlcDepotIds = allDepotIdsByDlcAppId[dlcAppId].orEmpty()
                                completeAppDownload(di, dlcAppId, dlcDepotIds, emptyList(), appDirPath)
                            }
                            Timber.i("Installation finalized for appId: $appId")

                            // Show success message to user
                            instance?.let { service ->
                                service.scope.launch(Dispatchers.Main) {
                                    Toast.makeText(service.applicationContext, "Download complete", Toast.LENGTH_SHORT).show()
                                    Unit
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error during finalize/database update for appId: $appId")
                            throw e
                        }

                        // Remove the job here
                        removeDownloadJob(appId)

                        // Remove the downloading app info
                        runBlocking {
                            instance?.downloadingAppInfoDao?.deleteApp(appId)
                            Unit
                        }
                        Unit
                    } catch (e: DownloadFailedException) {
                        Timber.d(e, "Download failed for app $appId via cancellation")
                        // Resume-мету НЕ трём: снапшот уже сфорсирован в failedToDownload(),
                        // юзер должен мочь продолжить кнопкой Resume, а не качать заново.
                        di.updateStatus(DownloadPhase.FAILED, e.message)
                        di.setActive(false)
                        // Clean up markers
                        MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                        removeDownloadJob(appId)
                        return@launch
                    } catch (e: CancellationException) {
                        if (di.isDeleting) {
                            Timber.d("Download cancelled for deletion for app $appId")
                            return@launch
                        }

                        if (di.isCancelling) {
                            Timber.d("Download cancelled by user for app $appId")
                            di.persistProgressSnapshot(force = true)
                            di.updateStatus(DownloadPhase.CANCELLED)
                            di.setActive(false)
                            throw e
                        }

                        Timber.d(e, "Download paused for app $appId")
                        // Keep downloadingAppInfo on cancellation so resume does not fall into verify mode.
                        di.persistProgressSnapshot(force = true)
                        di.updateStatus(DownloadPhase.PAUSED)
                        di.setActive(false)
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Download failed for app $appId")
                        // Resume-мету и строку БД НЕ трём: частичные файлы + снапшот остаются,
                        // докачка продолжится с места обрыва, а не с нуля.

                        val errorMsg = when (e) {
                            is ClassCastException -> "Casting error: ${e.message}"
                            is NullPointerException -> "Null reference: ${e.message}"
                            else -> e.localizedMessage ?: e.message ?: e.javaClass.simpleName
                        }

                        di.updateStatus(DownloadPhase.FAILED, errorMsg)
                        di.setActive(false)
                        // In-progress маркер снимаем, остальное (снапшот, строка БД,
                        // частичные файлы) оставляем для Resume с места обрыва.
                        MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                        removeDownloadJob(appId)
                        // Show error to user
                        instance?.let { service ->
                            service.scope.launch(Dispatchers.Main) {
                                Toast.makeText(service.applicationContext, "Download failed: $errorMsg", Toast.LENGTH_LONG).show()
                                Unit
                            }
                        }
                        PluviaApp.events.emit(AndroidEvent.DownloadStatusChanged(appId, false))
                        Unit
                    } finally {
                        runCatching {
                            depotDownloader?.close()
                            Unit
                        }.onFailure { closeError ->
                            Timber.w(closeError, "Failed to close downloader for app $appId")
                        }
                        Unit
                    }
                    Unit
                }
                downloadJob.invokeOnCompletion { throwable ->
                    if (throwable is CancellationException && throwable !is DownloadFailedException) {
                        if (di.isDeleting) {
                            // Deletion handled externally
                        } else if (di.isCancelling) {
                            // Keep in downloadJobs for UI visibility, but still check queue
                            checkQueue()
                        } else {
                            Timber.d(throwable, "Download paused for app $appId")
                            removeDownloadJob(appId)
                        }
                    }
                }
                di.setDownloadJob(downloadJob)
            }

            downloadJobs[appId] = info
            info.updateStatus(DownloadPhase.PREPARING)
            notifyDownloadStarted(appId)
            return info
        }

        private fun finalizeSnapshotResumeAsComplete(
            appId: Int,
            appDirPath: String,
            mainAppDepots: Map<Int, DepotInfo>,
            dlcAppDepots: Map<Int, DepotInfo>,
            userSelectedDlcAppIds: List<Int>,
        ): DownloadInfo {
            val downloadingAppIds = CopyOnWriteArrayList<Int>()
            val calculatedDlcAppIds = CopyOnWriteArrayList<Int>()
            val allDepotIdsByDlcAppId = dlcAppDepots.values
                .groupBy(keySelector = { it.dlcAppId }, valueTransform = { it.depotId })
                .mapValues { (_, depotIds) -> depotIds.sorted() }

            userSelectedDlcAppIds.forEach { dlcAppId ->
                if (allDepotIdsByDlcAppId[dlcAppId]?.isNotEmpty() == true) {
                    downloadingAppIds.add(dlcAppId)
                    calculatedDlcAppIds.add(dlcAppId)
                }
            }

            // Add main app ID if there are main app depots
            if (mainAppDepots.isNotEmpty() && !downloadingAppIds.contains(appId)) {
                downloadingAppIds.add(appId)
            }

            val info = DownloadInfo(1, appId, getAppInfoOf(appId)?.name ?: "Game", downloadingAppIds, { name, prog, down, tot, speed ->
                instance?.notificationHelper?.notifyProgress(name, prog, down, tot, speed)
            })
            info.setPersistencePath(appDirPath)
            info.updateStatus(DownloadPhase.COMPLETE)
            info.setProgress(1f)
            downloadJobs[appId] = info
            notifyDownloadStarted(appId)

            val mainAppDlcIds = getMainAppDlcIdsWithoutProperDepotDlcIds(appId)
            if (dlcAppDepots.isEmpty()) {
                mainAppDlcIds.addAll(mainAppDepots.filter { it.value.dlcAppId != INVALID_APP_ID }.map { it.value.dlcAppId }.distinct())
            }

            runBlocking(Dispatchers.IO) {
                if (mainAppDepots.isNotEmpty()) {
                    completeAppDownload(
                        downloadInfo = info,
                        downloadingAppId = appId,
                        entitledDepotIds = mainAppDepots.keys.sorted(),
                        selectedDlcAppIds = mainAppDlcIds,
                        appDirPath = appDirPath,
                    )
                }

                calculatedDlcAppIds.forEach { dlcAppId ->
                    val dlcDepotIds = allDepotIdsByDlcAppId[dlcAppId].orEmpty()
                    completeAppDownload(
                        downloadInfo = info,
                        downloadingAppId = dlcAppId,
                        entitledDepotIds = dlcDepotIds,
                        selectedDlcAppIds = emptyList(),
                        appDirPath = appDirPath,
                    )
                }

                instance?.downloadingAppInfoDao?.deleteApp(appId)
                Unit
            }

            // Show success message to user for no-op/resume completion
            instance?.let { service ->
                service.scope.launch(Dispatchers.Main) {
                    Toast.makeText(service.applicationContext, "Download complete", Toast.LENGTH_SHORT).show()
                    Unit
                }
            }
            return info
        }
        private suspend fun completeAppDownload(
            downloadInfo: DownloadInfo,
            downloadingAppId: Int,
            entitledDepotIds: List<Int>,
            selectedDlcAppIds: List<Int>,
            appDirPath: String,
        ) {
            Timber.i("Item $downloadingAppId download completed, saving database")

            // Update database
            val appInfo = instance?.appInfoDao?.getInstalledApp(downloadingAppId)

            // Update Saved AppInfo
            if (appInfo != null) {
                val updatedDownloadedDepots = (appInfo.downloadedDepots + entitledDepotIds).distinct()
                val updatedDlcDepots = (appInfo.dlcDepots + selectedDlcAppIds).distinct()

                instance?.appInfoDao?.update(
                    AppInfo(
                        downloadingAppId,
                        isDownloaded = true,
                        downloadedDepots = updatedDownloadedDepots.sorted(),
                        dlcDepots = updatedDlcDepots.sorted(),
                    ),
                )
            } else {
                instance?.appInfoDao?.insert(
                    AppInfo(
                        downloadingAppId,
                        isDownloaded = true,
                        downloadedDepots = entitledDepotIds.sorted(),
                        dlcDepots = selectedDlcAppIds.sorted(),
                    ),
                )
            }

            // Remove completed appId from downloadInfo.dlcAppIds and check if it was actually removed
            val wasRemoved = downloadInfo.downloadingAppIds.remove(downloadingAppId)
            if (!wasRemoved) {
                Timber.d("Item $downloadingAppId was already removed from downloading list, skipping redundant completion.")
                return
            }

            // All downloading appIds are removed
            if (downloadInfo.downloadingAppIds.isEmpty()) {
                Timber.i("All items for game ${downloadInfo.gameId} completed, running final completion logic.")
                // Атомарный финал: staging -> финал до маркеров/БД. Дотяжка remaining уже
                // сделана в onDepotCompleted — здесь не дублируем, чтобы не было прыжков прогресса.
                mergeStagingIntoFinal(downloadInfo.gameId, appDirPath)

                // Handle completion: add markers
                withContext(Dispatchers.IO) {
                    MarkerUtils.addMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
                    MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
                    MarkerUtils.removeMarker(appDirPath, Marker.STEAM_DLL_REPLACED)
                    MarkerUtils.removeMarker(appDirPath, Marker.STEAM_COLDCLIENT_USED)
                    MarkerUtils.removeMarker(appDirPath, Marker.STEAM_DRM_PATCHED)

                    // Ensure the main app is marked as downloaded in the DB
                    val mainAppId = downloadInfo.gameId
                    val service = instance
                    if (service != null) {
                        val mainAppInfo = service.appInfoDao.getInstalledApp(mainAppId)
                        if (mainAppInfo != null) {
                            if (!mainAppInfo.isDownloaded) {
                                service.appInfoDao.update(mainAppInfo.copy(isDownloaded = true))
                                Timber.i("Marked main app $mainAppId as downloaded in DB")
                            }
                        } else {
                            service.appInfoDao.insert(AppInfo(mainAppId, isDownloaded = true))
                            Timber.i("Inserted main app $mainAppId as downloaded in DB")
                        }
                    }
                    Unit
                }

                val service = instance
                if (service != null) {
                    createSteamShortcut(service, downloadInfo.gameId)
                }

                downloadInfo.updateStatus(DownloadPhase.COMPLETE)
                PluviaApp.events.emit(AndroidEvent.LibraryInstallStatusChanged(downloadInfo.gameId))

                // Clear persisted bytes file on successful completion
                downloadInfo.clearPersistedBytesDownloaded(appDirPath, sync = true)
                checkQueue()
            }
            Unit
        }

        // onChunkCompleted reports GLOBAL cumulative bytes across all depots, not per depot.
        // We diff successive global values to get the real per chunk delta.
        private class AppDownloadListener(
            private val downloadInfo: DownloadInfo,
            private val depotIdToIndex: Map<Int, Int>,
            private val depotMaxBytesById: Map<Int, Long>,
        ) : IDownloadListener {
            private var lastByteProgressAtMs: Long = 0L

            // Last global cumulative uncompressed bytes from onChunkCompleted
            private val lastGlobalUncompressedBytes = java.util.concurrent.atomic.AtomicLong(0L)

            // Consumes monotonic global cumulative bytes and returns only newly observed delta.
            // This is resilient to out-of-order callbacks from concurrent decompression workers.
            private fun consumeGlobalUncompressedDelta(currentGlobalBytes: Long): Long {
                if (currentGlobalBytes <= 0L) return 0L

                while (true) {
                    val previousGlobalBytes = lastGlobalUncompressedBytes.get()
                    if (currentGlobalBytes <= previousGlobalBytes) {
                        return 0L
                    }

                    if (lastGlobalUncompressedBytes.compareAndSet(previousGlobalBytes, currentGlobalBytes)) {
                        return currentGlobalBytes - previousGlobalBytes
                    }
                }
            }

            // Sets per depot cumulative value, returns delta
            private fun updateDepotBytesAndGetDelta(depotId: Int, reportedBytes: Long): Long {
                if (!depotIdToIndex.containsKey(depotId)) {
                    return 0L
                }

                var atomicBytes = downloadInfo.depotCumulativeUncompressedBytes[depotId]
                if (atomicBytes == null) {
                    atomicBytes = java.util.concurrent.atomic.AtomicLong(0L)
                    val existing = downloadInfo.depotCumulativeUncompressedBytes.putIfAbsent(depotId, atomicBytes)
                    if (existing != null) {
                        atomicBytes = existing
                    }
                }

                if (reportedBytes <= 0L) {
                    return 0L
                }

                val maxBytes = depotMaxBytesById[depotId]?.coerceAtLeast(1L)
                val clampedReportedBytes = if (maxBytes != null) {
                    reportedBytes.coerceIn(0L, maxBytes)
                } else {
                    reportedBytes
                }

                var deltaBytes = 0L
                while (true) {
                    val prev = atomicBytes.get()
                    val next = maxOf(prev, clampedReportedBytes)
                    if (prev == next) {
                        break
                    }
                    if (atomicBytes.compareAndSet(prev, next)) {
                        deltaBytes = (next - prev).coerceAtLeast(0L)
                        break
                    }
                }
                return deltaBytes
            }

            // Adds delta to per depot cumulative bytes, clamped to depot max
            private fun addDeltaToDepotBytes(depotId: Int, delta: Long) {
                if (delta <= 0L) return
                if (!depotIdToIndex.containsKey(depotId)) return

                var atomicBytes = downloadInfo.depotCumulativeUncompressedBytes[depotId]
                if (atomicBytes == null) {
                    atomicBytes = java.util.concurrent.atomic.AtomicLong(0L)
                    val existing = downloadInfo.depotCumulativeUncompressedBytes.putIfAbsent(depotId, atomicBytes)
                    if (existing != null) {
                        atomicBytes = existing
                    }
                }

                val maxBytes = depotMaxBytesById[depotId]?.coerceAtLeast(1L) ?: Long.MAX_VALUE
                while (true) {
                    val prev = atomicBytes.get()
                    val next = (prev + delta).coerceIn(0L, maxBytes)
                    if (prev == next) break
                    if (atomicBytes.compareAndSet(prev, next)) break
                }
            }

            override fun onItemAdded(item: DownloadItem) {
                Timber.d("Item ${item.appId} added to queue")
                Unit
            }

            override fun onDownloadStarted(item: DownloadItem) {
                Timber.i("Item ${item.appId} download started")
                downloadInfo.updateStatus(DownloadPhase.DOWNLOADING)
                Unit
            }

            override fun onDownloadCompleted(item: DownloadItem) {
                Timber.i("Item ${item.appId} download completed")
                Unit
            }

            override fun onFileCompleted(depotId: Int, fileName: String, depotPercentComplete: Float) {
                Timber.d("File completed: $fileName (Depot $depotId: ${depotPercentComplete * 100}%)")

                // Граница файла — тоже жизнь: на толстых файлах чанки могут молчать,
                // watchdog не должен рвать живую сессию.
                downloadInfo.markActivity()
                depotIdToIndex[depotId]?.let { index ->
                    downloadInfo.setProgress(depotPercentComplete, index)
                }

                // If onChunkCompleted missed some updates or out-of-order, this ensures progress is emitted.
                downloadInfo.emitProgressChange()
                Unit
            }

            override fun onDownloadFailed(item: DownloadItem, error: Throwable) {
                if (error is CancellationException && error !is DownloadFailedException) {
                    if (downloadInfo.isDeleting) {
                        Timber.d("Item ${item.appId} download cancelled for deletion")
                        return
                    }
                    if (downloadInfo.isCancelling) {
                        Timber.d("Item ${item.appId} download cancelled by user")
                        downloadInfo.persistProgressSnapshot(force = true)
                        downloadInfo.updateStatus(DownloadPhase.CANCELLED)
                        downloadInfo.setActive(false)
                        return
                    }
                    // Treat cancellation as pause: preserve resume metadata/state.
                    Timber.d(error, "Item ${item.appId} download paused")
                    downloadInfo.persistProgressSnapshot(force = true)
                    downloadInfo.updateStatus(DownloadPhase.PAUSED)
                    downloadInfo.setActive(false)
                    return
                }

                Timber.e(error, "Item ${item.appId} failed to download")
                // Hard stop current session on item failure so we do not continue toward completion
                // with missing/corrupt files. Resume metadata is preserved for retry.
                downloadInfo.persistProgressSnapshot(force = true)
                downloadInfo.updateStatus(DownloadPhase.FAILED)
                downloadInfo.failedToDownload()

                instance?.let { service ->
                    service.scope.launch(Dispatchers.Main) {
                        Toast.makeText(
                            service.applicationContext,
                            "Download error for depot ${item.appId}: ${error.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                Unit
            }

            override fun onStatusUpdate(message: String) {
                Timber.d("Download status: $message")
                
                // Extract filename if present (usually "Downloading filename..." or "Verifying filename...")
                if (message.startsWith("Downloading ", ignoreCase = true) || 
                    message.startsWith("Verifying ", ignoreCase = true) ||
                    message.startsWith("Patching ", ignoreCase = true)) {
                    val parts = message.split(" ")
                    if (parts.size > 1) {
                        val fileName = parts[1].removeSuffix("...")
                        downloadInfo.updateCurrentFileName(fileName)
                    }
                }

                val mappedStatus = DownloadPhase.fromMessage(message)
                if (mappedStatus != null) {
                    if (
                        mappedStatus == DownloadPhase.PREPARING ||
                        mappedStatus == DownloadPhase.VERIFYING ||
                        mappedStatus == DownloadPhase.PATCHING
                    ) {
                        val nowMs = System.currentTimeMillis()
                        val recentlyMovedBytes = nowMs - lastByteProgressAtMs <= 5_000L
                        if (recentlyMovedBytes && downloadInfo.getStatusFlow().value == DownloadPhase.DOWNLOADING) {
                            return
                        }
                    }
                    downloadInfo.updateStatus(mappedStatus, message)
                } else {
                    downloadInfo.updateStatusMessage(message)
                }
                Unit
            }
            override fun onChunkCompleted(
                depotId: Int,
                depotPercentComplete: Float,
                compressedBytes: Long,
                uncompressedBytes: Long,
            ) {
                // uncompressedBytes is global cumulative across all depots.
                val deltaBytes = consumeGlobalUncompressedDelta(uncompressedBytes)

                if (deltaBytes > 0L) {
                    lastByteProgressAtMs = System.currentTimeMillis()

                    addDeltaToDepotBytes(depotId, deltaBytes)
                    downloadInfo.updateBytesDownloaded(deltaBytes, lastByteProgressAtMs)
                    downloadInfo.markProgressSnapshotDirty()

                    // If resume verification/prepare phases already ran and bytes are now moving again,
                    // report active downloading.
                    val currentPhase = downloadInfo.getStatusFlow().value
                    if (
                        currentPhase == DownloadPhase.UNKNOWN ||
                        currentPhase == DownloadPhase.PREPARING ||
                        currentPhase == DownloadPhase.VERIFYING ||
                        currentPhase == DownloadPhase.PATCHING
                    ) {
                        downloadInfo.updateStatus(DownloadPhase.DOWNLOADING)
                    }
                }

                depotIdToIndex[depotId]?.let { index ->
                    downloadInfo.setProgress(depotPercentComplete, index)
                }

                // Emit progress change
                downloadInfo.emitProgressChange()
                Unit
            }

            override fun onDepotCompleted(depotId: Int, compressedBytes: Long, uncompressedBytes: Long) {
                Timber.i("Depot $depotId completed (compressed: $compressedBytes, uncompressed: $uncompressedBytes)")

                // Catch up any uncompressed bytes not covered by onChunkCompleted
                val deltaBytes = updateDepotBytesAndGetDelta(depotId, uncompressedBytes)

                if (deltaBytes > 0L) {
                    lastByteProgressAtMs = System.currentTimeMillis()
                    downloadInfo.updateBytesDownloaded(deltaBytes, lastByteProgressAtMs)
                    downloadInfo.markProgressSnapshotDirty()
                }

                // Credit full manifest size so dedup gaps don't stall progress.
                // Дотяжка remaining — только здесь: и потрековый учёт, и общий счётчик.
                val manifestSize = depotMaxBytesById[depotId] ?: 0L
                val tracked = downloadInfo.depotCumulativeUncompressedBytes[depotId]?.get() ?: 0L
                val remaining = manifestSize - tracked
                if (remaining > 0L) {
                    addDeltaToDepotBytes(depotId, remaining)
                    downloadInfo.updateBytesDownloaded(remaining, System.currentTimeMillis())
                    downloadInfo.markProgressSnapshotDirty()
                }

                val currentPhase = downloadInfo.getStatusFlow().value
                if (
                    currentPhase == DownloadPhase.UNKNOWN ||
                    currentPhase == DownloadPhase.PREPARING ||
                    currentPhase == DownloadPhase.VERIFYING ||
                    currentPhase == DownloadPhase.PATCHING
                ) {
                    downloadInfo.updateStatus(DownloadPhase.DOWNLOADING)
                }

                depotIdToIndex[depotId]?.let { index ->
                    downloadInfo.setProgress(1f, index)
                }

                // Emit progress change
                downloadInfo.emitProgressChange()
                Unit
            }
        }


        fun getWindowsLaunchInfos(appId: Int): List<LaunchInfo> {
            return getAppInfoOf(appId)?.let { appInfo ->
                appInfo.config.launch.filter { launchInfo ->
                    // since configOS was unreliable and configArch was even more unreliable
                    launchInfo.executable.endsWith(".exe")
                }
            }.orEmpty()
        }

        suspend fun notifyRunningProcesses(vararg gameProcesses: GameProcessInfo) = withContext(Dispatchers.IO) {
            instance?.let { steamInstance ->
                if (isConnected) {
                    val gamesPlayed = gameProcesses.mapNotNull { gameProcess ->
                        getAppInfoOf(gameProcess.appId)?.let { appInfo ->
                            getPkgInfoOf(gameProcess.appId)?.let { pkgInfo ->
                                appInfo.branches[gameProcess.branch]?.let { branch ->
                                    val processId = gameProcess.processes
                                        .firstOrNull { it.parentIsSteam }
                                        ?.processId
                                        ?: gameProcess.processes.firstOrNull()?.processId
                                        ?: 0

                                    val userAccountId = userSteamId!!.accountID.toInt()
                                    GamePlayedInfo(
                                        gameId = gameProcess.appId.toLong(),
                                        processId = processId,
                                        ownerId = if (pkgInfo.ownerAccountId.contains(userAccountId)) {
                                            userAccountId
                                        } else {
                                            pkgInfo.ownerAccountId.first()
                                        },
                                        // TODO: figure out what this is and un-hardcode
                                        launchSource = 100,
                                        gameBuildId = branch.buildId.toInt(),
                                        processIdList = gameProcess.processes,
                                    )
                                }
                            }
                        }
                    }

                    Timber.i(
                        "GameProcessInfo:%s",
                        gamesPlayed.joinToString("\n") { game ->
                            """
                        |   processId: ${game.processId}
                        |   gameId: ${game.gameId}
                        |   processes: ${
                                game.processIdList.joinToString("\n") { process ->
                                    """
                                |   processId: ${process.processId}
                                |   processIdParent: ${process.processIdParent}
                                |   parentIsSteam: ${process.parentIsSteam}
                                """.trimMargin()
                                }
                            }
                        """.trimMargin()
                        },
                    )

                    steamInstance._steamApps?.notifyGamesPlayed(
                        gamesPlayed = gamesPlayed,
                        clientOsType = EOSType.AndroidUnknown,
                    )
                }
            }
        }

        @JvmStatic
        fun notifyGameRunningFromWineProcesses(
            appId: Int,
            launchExePath: String? = null,
            shortcutName: String? = null,
        ) {
            if (appId <= 0 || !isConnected || !isLoggedIn) return
            Timber.d("notifyGameRunningFromWineProcesses: appId=%d launchExePath=%s shortcutName=%s", appId, launchExePath, shortcutName)
            runBlocking(Dispatchers.IO) {
                notifyRunningProcesses(
                    GameProcessInfo(
                        appId = appId,
                        branch = PrefManager.getSteamSelectedBranch(appId),
                        processes = listOf(
                            `in`.dragonbra.javasteam.steam.handlers.steamapps.AppProcessInfo(
                                /* processId */ 0,
                                /* processIdParent */ 0,
                                /* parentIsSteam */ true,
                            ),
                        ),
                    ),
                )
            }
        }

        private fun listRunningWineProcessInfos(): List<RunningWineProcessInfo> {
            val procDir = File("/proc")
            val entries = procDir.listFiles { file -> file.isDirectory && file.name.all(Char::isDigit) } ?: return emptyList()

            return entries.mapNotNull { pidDir ->
                runCatching {
                    val statLine = File(pidDir, "stat").readText(Charsets.UTF_8)
                    val firstParen = statLine.indexOf('(')
                    val lastParen = statLine.lastIndexOf(')')
                    if (firstParen < 0 || lastParen <= firstParen) return@runCatching null

                    val pid = pidDir.name.toIntOrNull() ?: return@runCatching null
                    val statName = statLine.substring(firstParen + 1, lastParen)
                    val statTokens = statLine.substring(lastParen + 2).trim().split(Regex("\\s+"))
                    val parentPid = statTokens.getOrNull(1)?.toIntOrNull() ?: 0

                    val cmdlineFile = File(pidDir, "cmdline")
                    val cmdlineName = runCatching {
                        val raw = cmdlineFile.readBytes()
                        raw.toString(Charsets.UTF_8)
                            .replace('\u0000', ' ')
                            .trim()
                            .substringAfterLast('/')
                            .substringAfterLast('\\')
                    }.getOrDefault("")

                    val displayName = cmdlineName.ifBlank { statName }.trim()
                    if (!displayName.contains("wine", ignoreCase = true) && !displayName.contains(".exe", ignoreCase = true)) {
                        return@runCatching null
                    }

                    RunningWineProcessInfo(
                        processId = pid,
                        parentProcessId = parentPid,
                        name = displayName,
                    )
                }.getOrNull()
            }
        }

        fun beginLaunchApp(
            appId: Int,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            ignorePendingOperations: Boolean = false,
            preferredSave: SaveLocation = SaveLocation.None,
            prefixToPath: (String) -> String,
            isOffline: Boolean = false,
            onProgress: ((message: String, progress: Float) -> Unit)? = null,
            onConflict: (suspend (PostSyncInfo) -> SaveLocation)? = null,
        ): Deferred<PostSyncInfo> = parentScope.async {
            if (isOffline || !isConnected) {
                return@async PostSyncInfo(SyncResult.UpToDate)
            }
            if (!tryAcquireSync(appId)) {
                Timber.w("Cannot launch app when sync already in progress for appId=$appId")
                return@async PostSyncInfo(SyncResult.InProgress)
            }

            try {
                val progressWrapper: (String, Float) -> Unit = { msg, prog ->
                    cloudSyncStatus.value = CloudSyncMessage(appId, false, msg, prog)
                    onProgress?.invoke(msg, prog)
                }
                var syncResult = PostSyncInfo(SyncResult.UnknownFail)

                val maxAttempts = 3
                for (attempt in 1..maxAttempts) {
                    try {
                        val clientId = PrefManager.clientId
                        val steamInstance = instance
                        val appInfo = getAppInfoOf(appId)
                        val steamCloud = steamInstance?._steamCloud

                        if (steamInstance != null && appInfo != null && steamCloud != null) {
                            progressWrapper("Checking Cloud Saves...", 0f)
                            val postSyncInfo = SteamAutoCloud.syncUserFiles(
                                appInfo = appInfo,
                                clientId = clientId,
                                steamInstance = steamInstance,
                                steamCloud = steamCloud,
                                preferredSave = preferredSave,
                                parentScope = parentScope,
                                prefixToPath = prefixToPath,
                                onProgress = progressWrapper,
                            ).await()

                            postSyncInfo?.let { info ->
                                syncResult = info

                                if (info.syncResult == SyncResult.Success || info.syncResult == SyncResult.UpToDate) {
                                    Timber.i(
                                        "Signaling app launch:\n\tappId: %d\n\tclientId: %s\n\tosType: %s",
                                        appId,
                                        PrefManager.clientId,
                                        EOSType.AndroidUnknown,
                                    )

                                    val pendingRemoteOperations = steamCloud.signalAppLaunchIntent(
                                        appId = appId,
                                        clientId = clientId,
                                        machineName = SteamUtils.getMachineName(steamInstance),
                                        ignorePendingOperations = ignorePendingOperations,
                                        osType = EOSType.AndroidUnknown,
                                    ).await()

                                    if (pendingRemoteOperations.isNotEmpty() && !ignorePendingOperations) {
                                        syncResult = PostSyncInfo(
                                            syncResult = SyncResult.PendingOperations,
                                            pendingRemoteOperations = pendingRemoteOperations,
                                        )
                                    } else if (ignorePendingOperations &&
                                        pendingRemoteOperations.any {
                                            it.operation == ECloudPendingRemoteOperation.k_ECloudPendingRemoteOperationAppSessionActive
                                        }
                                    ) {
                                        steamInstance._steamUser!!.kickPlayingSession()
                                    }
                                }
                            }
                        }
                        break
                    } catch (e: AsyncJobFailedException) {
                        if (attempt == maxAttempts) {
                            Timber.e(e, "Cloud sync failed after $maxAttempts attempts for app $appId (AsyncJobFailedException)")
                            syncResult = PostSyncInfo(SyncResult.UnknownFail)
                        } else {
                            Timber.w("Cloud sync attempt $attempt failed for app $appId (AsyncJobFailedException), retrying in ${attempt}s...")
                            delay(1000L * attempt)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Cloud sync error for app $appId (attempt $attempt/$maxAttempts): ${e.message}")
                        if (attempt == maxAttempts) {
                            syncResult = PostSyncInfo(SyncResult.UnknownFail)
                        } else {
                            delay(1000L * attempt)
                        }
                    }
                }

                if (syncResult.syncResult == SyncResult.Conflict && onConflict != null) {
                    val choice = try {
                        onConflict(syncResult)
                    } catch (e: Exception) {
                        Timber.w(e, "Conflict resolver failed for app $appId")
                        SaveLocation.None
                    }
                    if (choice != SaveLocation.None) {
                        Timber.i("Resolving cloud conflict for app $appId with $choice")
                        try {
                            val steamInstance = instance
                            val appInfo = getAppInfoOf(appId)
                            val steamCloud = steamInstance?._steamCloud
                            if (steamInstance != null && appInfo != null && steamCloud != null) {
                                progressWrapper("Resolving Cloud Conflict...", 0f)
                                SteamAutoCloud.syncUserFiles(
                                    appInfo = appInfo,
                                    clientId = PrefManager.clientId,
                                    steamInstance = steamInstance,
                                    steamCloud = steamCloud,
                                    preferredSave = choice,
                                    parentScope = parentScope,
                                    prefixToPath = prefixToPath,
                                    onProgress = progressWrapper,
                                ).await()?.let { syncResult = it }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Conflict resolution sync failed for app $appId")
                        }
                    }
                }

                return@async syncResult
            } finally {
                cloudSyncStatus.value = null
                releaseSync(appId)
            }
        }

        /**
         * Lightweight probe: checks whether the cloud save change number for
         * [appId] differs from the locally stored value.  No files are
         * downloaded or uploaded – only a single metadata call is made.
         *
         * @return `true` if cloud differs, `false` if in sync, `null` if the
         *         check could not be performed (service unavailable, etc.).
         */
        suspend fun cloudSavesDiffer(appId: Int): Boolean? {
            val steamInstance = instance ?: return null
            val steamCloud = steamInstance._steamCloud ?: return null
            val localCN = steamInstance.changeNumbersDao.getByAppId(appId)?.changeNumber
            if (localCN == null) {
                Timber.d("Cloud diagnostics: no local change number for appId=$appId, treating as unknown")
                return null
            }
            return try {
                val fileListChange = steamCloud.getAppFileListChange(appId, localCN).await()
                val changed = fileListChange.currentChangeNumber != localCN
                Timber.d("Cloud diagnostics: appId=$appId localCN=$localCN remoteCN=${fileListChange.currentChangeNumber} changed=$changed")
                changed
            } catch (e: Exception) {
                Timber.e(e, "Cloud diagnostics: Failed to probe Steam cloud change number for appId=$appId: ${e.message}")
                null
            }
        }

        suspend fun forceSyncUserFiles(
            appId: Int,
            prefixToPath: (String) -> String,
            preferredSave: SaveLocation = SaveLocation.None,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            overrideLocalChangeNumber: Long? = null,
        ): Deferred<PostSyncInfo> = parentScope.async {
            if (!tryAcquireSync(appId)) {
                Timber.w("Cannot force sync when sync already in progress for appId=$appId")
                return@async PostSyncInfo(SyncResult.InProgress)
            }

            try {
                var syncResult = PostSyncInfo(SyncResult.UnknownFail)

                val maxAttempts = 3
                for (attempt in 1..maxAttempts) {
                    try {
                        val clientId = PrefManager.clientId
                        val steamInstance = instance
                        val appInfo = getAppInfoOf(appId)
                        val steamCloud = steamInstance?._steamCloud

                        if (steamInstance != null && appInfo != null && steamCloud != null) {
                            val postSyncInfo = SteamAutoCloud.syncUserFiles(
                                appInfo = appInfo,
                                clientId = clientId,
                                steamInstance = steamInstance,
                                steamCloud = steamCloud,
                                preferredSave = preferredSave,
                                parentScope = parentScope,
                                prefixToPath = prefixToPath,
                                overrideLocalChangeNumber = overrideLocalChangeNumber,
                            ).await()

                            postSyncInfo?.let { info ->
                                syncResult = info
                                Timber.i("Force cloud sync completed for app $appId with result: ${info.syncResult}")
                            }
                        }
                        break
                    } catch (e: AsyncJobFailedException) {
                        if (attempt == maxAttempts) {
                            Timber.e(e, "Force cloud sync failed after $maxAttempts attempts for app $appId (AsyncJobFailedException)")
                        } else {
                            Timber.w("Force cloud sync attempt $attempt failed for app $appId (AsyncJobFailedException), retrying in ${attempt}s...")
                            delay(1000L * attempt)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Force cloud sync error for app $appId (attempt $attempt/$maxAttempts): ${e.message}")
                        if (attempt == maxAttempts) {
                            syncResult = PostSyncInfo(SyncResult.UnknownFail)
                        } else {
                            delay(1000L * attempt)
                        }
                    }
                }

                return@async syncResult
            } finally {
                releaseSync(appId)
            }
        }

        suspend fun generateAchievements(appId: Int, configDirectory: String) = runCatching {
            val steamUser = instance?._steamUser ?: return@runCatching
            val userStats = instance?._steamUserStats?.getUserStats(appId, steamUser.steamID!!)?.await() ?: return@runCatching
            val schemaArray = userStats.schema.toByteArray()
            val generator = StatsAchievementsGenerator()
            val result = generator.generateStatsAchievements(schemaArray, configDirectory)
            cachedAchievements = result.achievements
            cachedAchievementsAppId = appId

            val nameToBlockBit = result.nameToBlockBit
            if (nameToBlockBit.isNotEmpty()) {
                val mappingJson = JSONObject()
                nameToBlockBit.forEach { (name, pair) ->
                    mappingJson.put(name, JSONArray(listOf(pair.first, pair.second)))
                }
                File(configDirectory, "achievement_name_to_block.json").writeText(mappingJson.toString(), Charsets.UTF_8)
            }
        }.onFailure { e ->
            Timber.w(e, "Failed to generate achievements for appId=$appId")
        }

        fun getGseSaveDirs(appId: Int): List<File> {
            val context = instance?.applicationContext ?: return emptyList()
            val imageFs = ImageFs.find(context)
            val dirs = mutableListOf<File>()
            dirs.add(File(
                imageFs.rootDir,
                "${ImageFs.WINEPREFIX}/drive_c/users/xuser/AppData/Roaming/GSE Saves/$appId"
            ))
            val accountId = userSteamId?.accountID?.toInt()
                ?: PrefManager.steamUserAccountId.takeIf { it != 0 }
            if (accountId != null) {
                dirs.add(File(
                    imageFs.rootDir,
                    "${ImageFs.WINEPREFIX}/drive_c/Program Files (x86)/Steam/userdata/$accountId/$appId"
                ))
            }
            return dirs
        }

        suspend fun syncAchievementsFromGoldberg(appId: Int) {
            val context = instance?.applicationContext ?: return
            val gseSaveDirs = getGseSaveDirs(appId).filter { it.isDirectory }
            if (gseSaveDirs.isEmpty()) {
                Timber.d("No GSE save directory found for appId=$appId")
                return
            }

            val unlockedNames = mutableSetOf<String>()
            var gseStatsDir: File? = null

            for (gseSaveDir in gseSaveDirs) {
                val goldbergAchFile = File(gseSaveDir, "achievements.json")
                if (goldbergAchFile.exists()) {
                    try {
                        val json = JSONObject(goldbergAchFile.readText(Charsets.UTF_8))
                        for (name in json.keys()) {
                            val entry = json.optJSONObject(name) ?: continue
                            if (entry.optBoolean("earned", false)) {
                                unlockedNames.add(name)
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to parse Goldberg achievements.json in ${gseSaveDir.absolutePath} for appId=$appId")
                    }
                }

                val statsDir = File(gseSaveDir, "stats")
                if (gseStatsDir == null && statsDir.isDirectory && (statsDir.listFiles()?.isNotEmpty() == true)) {
                    gseStatsDir = statsDir
                }
            }

            val hasStats = gseStatsDir != null

            if (unlockedNames.isEmpty() && !hasStats) {
                Timber.d("No earned achievements or stats found in Goldberg output for appId=$appId")
                return
            }

            val configDirectory = findSteamSettingsDir(context, appId)
            if (configDirectory == null) {
                Timber.w("Could not find steam_settings directory for appId=$appId")
                return
            }

            val result = storeAchievementUnlocks(appId, configDirectory, unlockedNames, gseStatsDir ?: gseSaveDirs.first().resolve("stats"))
            result.onFailure { e ->
                Timber.e(e, "Failed to sync achievements and stats to Steam for appId=$appId")
            }
        }

        private fun findSteamSettingsDir(context: Context, appId: Int): String? {
            val appDirPath = getAppDirPath(appId)
            val appDirSettings = File(appDirPath, "steam_settings")
            if (appDirSettings.isDirectory) {
                return appDirSettings.absolutePath
            }

            val container = ContainerUtils.getContainer(context, "STEAM_$appId") ?: return null
            val coldclientSettings = File(
                container.rootDir,
                ".wine/drive_c/Program Files (x86)/Steam/steam_settings"
            )
            if (coldclientSettings.isDirectory) {
                return coldclientSettings.absolutePath
            }

            return null
        }

        suspend fun storeAchievementUnlocks(
            appId: Int,
            configDirectory: String,
            unlockedNames: Set<String>,
            gseStatsDir: File
        ): Result<Unit> = runCatching {
            val steamUser = instance!!._steamUser!!
            val userStats = instance?._steamUserStats!!.getUserStats(appId, steamUser.steamID!!).await()
            if (userStats.result != EResult.OK) {
                throw IllegalStateException("getUserStats failed: ${userStats.result}")
            }

            val allStats = mutableMapOf<Int, Int>()

            val mappingFile = File(configDirectory, "achievement_name_to_block.json")
            if (!mappingFile.exists() && unlockedNames.isNotEmpty()) {
                generateAchievements(appId, configDirectory)
            }

            if (mappingFile.exists() && unlockedNames.isNotEmpty()) {
                val mappingJson = JSONObject(mappingFile.readText(Charsets.UTF_8))
                val nameToBlockBit = mutableMapOf<String, Pair<Int, Int>>()
                for (key in mappingJson.keys()) {
                    val arr = mappingJson.optJSONArray(key) ?: continue
                    if (arr.length() >= 2) {
                        nameToBlockBit[key] = Pair(arr.getInt(0), arr.getInt(1))
                    }
                }

                for (block in userStats.achievementBlocks ?: emptyList()) {
                    val blockId = (block.achievementId as? Number)?.toInt() ?: continue
                    var bitmask = 0
                    val unlockTimes = block.unlockTime ?: emptyList()
                    for (i in unlockTimes.indices) {
                        val t = unlockTimes[i]
                        if ((t as? Number)?.toLong() != 0L) bitmask = bitmask or (1 shl i)
                    }
                    allStats[blockId] = bitmask
                }

                for (name in unlockedNames) {
                    val mapped = nameToBlockBit[name] ?: continue
                    val current = allStats.getOrDefault(mapped.first, 0)
                    allStats[mapped.first] = current or (1 shl mapped.second)
                }
            }

            if (gseStatsDir.isDirectory) {
                val statNameToId = mutableMapOf<String, Int>()
                try {
                    val parsedSchema = VdfParser().binaryLoads(userStats.schema.toByteArray())
                    for ((_, appData) in parsedSchema) {
                        if (appData !is Map<*, *>) continue
                        val statInfo = (appData as Map<String, Any>)["stats"] as? Map<String, Any> ?: continue
                        for ((statKey, statData) in statInfo) {
                            if (statData !is Map<*, *>) continue
                            val stat = statData as Map<String, Any>
                            val statType = stat["type"]?.toString() ?: continue
                            if (statType == StatType.STAT_TYPE_BITS || statType == StatType.ACHIEVEMENTS) continue
                            val name = stat["name"]?.toString()?.lowercase() ?: continue
                            val id = statKey.toIntOrNull() ?: continue
                            statNameToId[name] = id
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse schema for stat name mapping, appId=$appId")
                }

                if (statNameToId.isNotEmpty()) {
                    for (statFile in gseStatsDir.listFiles() ?: emptyArray()) {
                        if (!statFile.isFile) continue
                        val statId = statNameToId[statFile.name.lowercase()] ?: continue
                        val bytes = statFile.readBytes()
                        if (bytes.size >= 4) {
                            val value = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
                            allStats[statId] = value
                            Timber.d("Read GSE stat: ${statFile.name} -> statId=$statId, value=$value")
                        }
                    }
                }
            }

            if (allStats.isEmpty()) {
                Timber.d("No stats or achievements to store for appId=$appId")
                return@runCatching
            }

            val statsToStore = allStats.map { (id, value) -> Stats(statId = id, statValue = value) }
            val mySteamId = steamUser.steamID!!
            Timber.d("storeUserStats: appId=$appId, crcStats=${userStats.crcStats}, stats=$statsToStore")
            sendStoreUserStats(appId, statsToStore, mySteamId, userStats.crcStats)
        }

        private fun sendStoreUserStats(
            appId: Int,
            stats: List<Stats>,
            steamId: SteamID,
            crcStats: Int
        ) {
            val client = instance?.steamClient ?: return
            runCatching {
                val msg: ClientMsgProtobuf<SteammessagesClientserverUserstats.CMsgClientStoreUserStats2.Builder> = ClientMsgProtobuf(
                    SteammessagesClientserverUserstats.CMsgClientStoreUserStats2::class.java,
                    EMsg.ClientStoreUserStats2,
                )
                msg.sourceJobID = client.getNextJobID()
                msg.protoHeader.setRoutingAppid(appId)
                msg.body.setGameId(appId.toLong())
                msg.body.setSettorSteamId(steamId.convertToUInt64())
                msg.body.setSetteeSteamId(steamId.convertToUInt64())
                msg.body.setCrcStats(crcStats)
                stats.forEach { stat ->
                    msg.body.addStats(
                        SteammessagesClientserverUserstats.CMsgClientStoreUserStats2.Stats.newBuilder()
                            .setStatId(stat.statId)
                            .setStatValue(stat.statValue)
                            .build()
                    )
                }
                client.send(msg)
            }.onFailure { e ->
                Timber.e(e, "Failed to send storeUserStats for appId=$appId")
            }
        }

        suspend fun closeApp(
            appId: Int,
            isOffline: Boolean,
            prefixToPath: (String) -> String,
            onProgress: ((message: String, progress: Float) -> Unit)? = null,
        ) = withContext(Dispatchers.IO) {
            async {
                if (isOffline || !isConnected) {
                    return@async PostSyncInfo(SyncResult.UpToDate)
                }

                if (!tryAcquireSync(appId)) {
                    Timber.w("Cannot close app when sync already in progress for appId=$appId")
                    return@async PostSyncInfo(SyncResult.InProgress)
                }

                var syncInfo = PostSyncInfo(SyncResult.UnknownFail)
                try {
                    try {
                        syncAchievementsFromGoldberg(appId)
                    } catch (e: Exception) {
                        Timber.e(e, "Achievement sync failed for appId=$appId, continuing with cloud save sync")
                    }

                    val progressWrapper: (String, Float) -> Unit = { msg, prog ->
                        cloudSyncStatus.value = CloudSyncMessage(appId, true, msg, prog)
                        onProgress?.invoke(msg, prog)
                    }
                    val maxAttempts = 3
                    for (attempt in 1..maxAttempts) {
                        try {
                            val clientId = PrefManager.clientId
                            val steamInstance = instance
                            val appInfo = getAppInfoOf(appId)
                            val steamCloud = steamInstance?._steamCloud

                            if (steamInstance != null && appInfo != null && steamCloud != null) {
                                progressWrapper("Checking Local Saves...", 0f)
                                val postSyncInfo = SteamAutoCloud.syncUserFiles(
                                    appInfo = appInfo,
                                    clientId = clientId,
                                    steamInstance = steamInstance,
                                    steamCloud = steamCloud,
                                    parentScope = this@async,
                                    prefixToPath = prefixToPath,
                                    onProgress = progressWrapper,
                                ).await()
                                syncInfo = postSyncInfo ?: PostSyncInfo(SyncResult.UnknownFail)

                                steamCloud.signalAppExitSyncDone(
                                    appId = appId,
                                    clientId = clientId,
                                    uploadsCompleted = postSyncInfo?.uploadsCompleted == true,
                                    uploadsRequired = postSyncInfo?.uploadsRequired == false,
                                )
                            }
                            break
                        } catch (e: AsyncJobFailedException) {
                            if (attempt == maxAttempts) {
                                Timber.e(e, "Close app sync failed after $maxAttempts attempts for app $appId")
                                syncInfo = PostSyncInfo(SyncResult.UnknownFail)
                            } else {
                                Timber.w("Close app sync attempt $attempt failed for app $appId, retrying...")
                                delay(1000L * attempt)
                            }
                        }
                    }
                    syncInfo
                } finally {
                    cloudSyncStatus.value = null
                    releaseSync(appId)
                }
            }
        }

        interface CloudSyncCallback {
            fun onProgress(message: String, progress: Float)
            fun onComplete()
        }

        /**
         * Sync cloud saves for backup/restore purposes without closing the app.
         * @param preferredAction "download" or "upload"
         * @return true if sync succeeded
         */
        suspend fun syncCloudSavesForBackup(context: android.content.Context, appId: Int, preferredAction: String): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    val accountId = userSteamId?.accountID?.toLong()
                        ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toLong()
                        ?: 0L
                    val prefixToPath: (String) -> String = { prefix ->
                        com.winlator.cmod.steam.enums.PathType.from(prefix).toAbsPath(context, appId, accountId)
                    }
                    val steamInst = instance
                    val appInfo = getAppInfoOf(appId)
                    val steamCloud = steamInst?._steamCloud
                    val clientId = PrefManager.clientId

                    if (steamInst == null || appInfo == null || steamCloud == null) {
                        return@withContext false
                    }

                    SteamAutoCloud.syncUserFiles(
                        appInfo = appInfo,
                        clientId = clientId,
                        steamInstance = steamInst,
                        steamCloud = steamCloud,
                        prefixToPath = prefixToPath,
                        onProgress = { _, _ -> },
                    ).await()
                    true
                } catch (e: Exception) {
                    timber.log.Timber.tag("SteamService").e(e, "syncCloudSavesForBackup failed")
                    false
                }
            }
        }

        @JvmStatic
        fun syncCloudOnExit(context: android.content.Context, appId: Int, callback: CloudSyncCallback) {
            val accountId = userSteamId?.accountID?.toLong()
                ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toLong()
                ?: 0L
            val prefixToPath: (String) -> String = { prefix ->
                com.winlator.cmod.steam.enums.PathType.from(prefix).toAbsPath(context, appId, accountId)
            }
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val container = ContainerUtils.getOrCreateContainer(context, "STEAM_$appId")
                    ContainerManager(context).activateContainer(container)
                }.onFailure { throwable ->
                    Timber.w(throwable, "Failed to activate container before explicit exit sync for appId=$appId")
                }
                notifyRunningProcesses()
                val syncInfo = closeApp(
                    appId = appId,
                    isOffline = false,
                    prefixToPath = prefixToPath,
                    onProgress = { msg, prog -> callback.onProgress(msg, prog) }
                ).await()
                withContext(Dispatchers.Main) {
                    callback.onComplete()
                }
            }
        }

        data class FileChanges(
            val filesDeleted: List<UserFileInfo>,
            val filesModified: List<UserFileInfo>,
            val filesCreated: List<UserFileInfo>,
        )

        /**
         * loginusers.vdf writer for the OAuth-style refresh-token flow introduced in 2024.
         *
         * @param steamId64    64-bit SteamID of the logged-in user
         * @param account      AccountName (same as you passed to logOn / poll result)
         * @param refreshToken Long-lived token you get from AuthSession / QR / credentials
         * @param accessToken  Optional – short-lived access token, Steam ignores it if absent
         * @param personaName  What the client shows in the drop-down; defaults to AccountName
         */
        internal fun getLoginUsersVdfOauth(
            steamId64: String,
            account: String,
            refreshToken: String,
            accessToken: String? = null,
            personaName: String = account,
        ): String {
            val epoch = System.currentTimeMillis() / 1_000

            val vdf = buildString {
                appendLine("\"users\"")
                appendLine("{")
                appendLine("    \"$steamId64\"")
                appendLine("    {")
                appendLine("        \"AccountName\"          \"$account\"")
                appendLine("        \"PersonaName\"          \"$personaName\"")
                appendLine("        \"RememberPassword\"     \"1\"")
                appendLine("        \"WantsOfflineMode\"     \"0\"")
                appendLine("        \"SkipOfflineModeWarning\"     \"0\"")
                appendLine("        \"AllowAutoLogin\"       \"1\"")
                appendLine("        \"MostRecent\"           \"1\"")
                appendLine("        \"Timestamp\"            \"$epoch\"")
                appendLine("    }")
                appendLine("}")
            }

            return vdf;
        }

        private fun login(
            username: String,
            accessToken: String? = null,
            refreshToken: String? = null,
            password: String? = null,
            rememberSession: Boolean = false,
            twoFactorAuth: String? = null,
            emailAuth: String? = null,
            clientId: Long? = null,
        ) {
            isLoggingOut = false
            val steamUser = instance!!._steamUser!!

            // Sensitive info, only print in DEBUG build.
//            if (BuildConfig.DEBUG) {
//                Timber.d(
//                    """
//                    Login Information:
//                     Username: $username
//                     AccessToken: $accessToken
//                     RefreshToken: $refreshToken
//                     Password: $password
//                     Remember Session: $rememberSession
//                     TwoFactorAuth: $twoFactorAuth
//                     EmailAuth: $emailAuth
//                    """.trimIndent(),
//                )
//            }

            PrefManager.username = username

            if ((password != null && rememberSession) || refreshToken != null) {
                if (accessToken != null) {
                    PrefManager.accessToken = accessToken
                }

                if (refreshToken != null) {
                    PrefManager.refreshToken = refreshToken
                }

                if (clientId != null) {
                    PrefManager.clientId = clientId
                }
            }

            val event = SteamEvent.LogonStarted(username)
            PluviaApp.events.emit(event)

            steamUser.logOn(
                LogOnDetails(
                    username = SteamUtils.removeSpecialChars(username).trim(),
                    password = password?.let { SteamUtils.removeSpecialChars(it).trim() },
                    shouldRememberPassword = rememberSession,
                    twoFactorCode = twoFactorAuth,
                    authCode = emailAuth,
                    accessToken = refreshToken,
                    loginID = SteamUtils.getUniqueDeviceId(instance!!),
                    machineName = SteamUtils.getMachineName(instance!!),
                    chatMode = ChatMode.NEW_STEAM_CHAT,
                ),
            )
        }

        suspend fun startLoginWithCredentials(
            username: String,
            password: String,
            rememberSession: Boolean,
            authenticator: IAuthenticator,
        ) = withContext(Dispatchers.IO) {
            try {
                Timber.i("Logging in via credentials.")
                instance!!._loginResult = LoginResult.InProgress
                Timber.i("Set login result to InProgress.")
                instance!!.steamClient?.let { steamClient ->
                    val authDetails = AuthSessionDetails().apply {
                        this.username = username.trim()
                        this.password = password.trim()
                        this.persistentSession = rememberSession
                        this.authenticator = authenticator
                        this.deviceFriendlyName = SteamUtils.getMachineName(instance!!)
                        this.clientOSType = EOSType.WinUnknown
                    }

                    val event = SteamEvent.LogonStarted(username)
                    PluviaApp.events.emit(event)

                    // Outer loop: retries the entire auth session when the connection
                    // drops (e.g. TryAnotherCM) during 2FA polling.
                    var loginComplete = false
                    while (isActive && !loginComplete) {
                        val currentClient = instance!!.steamClient ?: break
                        val authSession = currentClient.authentication.beginAuthSessionViaCredentials(authDetails).await()

                        Timber.d("Waiting for authentication result (handles 2FA). Interval: ${authSession.pollingInterval}s")

                        // pollingWaitForResult handles the full 2FA flow:
                        // - Checks allowedConfirmations for the required guard type
                        // - Calls IAuthenticator callbacks (acceptDeviceConfirmation, getDeviceCode, getEmailCode)
                        // - Submits the guard code to Steam via sendSteamGuardCode
                        // - Polls until authentication completes
                        var pollResult: AuthPollResult? = null
                        var disconnected = false
                        while (isActive && pollResult == null && !disconnected) {
                            try {
                                pollResult = authSession.pollingWaitForResult().await()
                            } catch (e: AuthenticationException) {
                                if (e.result == EResult.Expired || e.result == EResult.FileNotFound) {
                                    Timber.w("Auth session expired during 2FA wait, retrying...")
                                    delay(authSession.pollingInterval.toLong() * 1000L)
                                    continue
                                }
                                throw e
                            } catch (e: java.util.concurrent.CancellationException) {
                                // Connection dropped (TryAnotherCM) — all pending futures
                                // are cancelled. Wait for reconnection and restart the
                                // auth session so the user doesn't see a spurious failure.
                                if (!isActive) throw CancellationException("Coroutine cancelled")
                                Timber.w("Auth poll cancelled (likely TryAnotherCM), waiting for reconnection...")
                                disconnected = true
                            }
                        }

                        if (disconnected) {
                            // Wait for the service to reconnect to a new CM server
                            Timber.i("Waiting for Steam reconnection before retrying credential auth...")
                            try {
                                withTimeout(30_000L) {
                                    isConnectedFlow.first { it }
                                }
                            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                throw Exception("Timed out waiting for Steam reconnection")
                            }
                            Timber.i("Reconnected, restarting credential auth session...")
                            delay(500L) // brief settle time after reconnect
                            continue
                        }

                        if (pollResult == null) {
                            throw CancellationException("Credential auth polling cancelled")
                        }

                        Timber.i("Authentication successful for ${pollResult.accountName}")

                        if (pollResult.accountName.isEmpty() && pollResult.refreshToken.isEmpty()) {
                            throw Exception("No account name or refresh token received.")
                        }

                        login(
                            clientId = authSession.clientID,
                            username = pollResult.accountName,
                            accessToken = pollResult.accessToken,
                            refreshToken = pollResult.refreshToken,
                            rememberSession = rememberSession,
                        )
                        loginComplete = true
                    }
                } ?: run {
                    Timber.e("Could not logon: Failed to connect to Steam")

                    val event = SteamEvent.LogonEnded(username, LoginResult.Failed, "No connection to Steam")
                    PluviaApp.events.emit(event)
                }
            } catch (e: Exception) {
                Timber.e(e, "Login failed")

                val message = when (e) {
                    is CancellationException -> "Unknown cancellation"
                    is AuthenticationException -> e.result?.name ?: e.message
                    else -> e.message ?: e.javaClass.name
                }

                val event = SteamEvent.LogonEnded(username, LoginResult.Failed, message)
                PluviaApp.events.emit(event)
            }
        }

        suspend fun startLoginWithQr() = withContext(Dispatchers.IO) {
            try {
                Timber.i("Logging in via QR.")

                instance!!.steamClient?.let { steamClient ->
                    isWaitingForQRAuth = true

                    val authDetails = AuthSessionDetails().apply {
                        this.deviceFriendlyName = SteamUtils.getMachineName(instance!!)
                        this.clientOSType = EOSType.WinUnknown
                        this.persistentSession = true
                    }

                    val authSession = steamClient.authentication.beginAuthSessionViaQR(authDetails).await()

                    // Steam will periodically refresh the challenge url, this callback allows you to draw a new qr code.
                    authSession.challengeUrlChanged = instance

                    val qrEvent = SteamEvent.QrChallengeReceived(authSession.challengeUrl)
                    PluviaApp.events.emit(qrEvent)

                    Timber.d("PollingInterval: ${authSession.pollingInterval.toLong()}")

                    var authPollResult: AuthPollResult? = null

                    // FIX: Poll via raw protobuf RPC instead of JavaSteam's pollAuthSessionStatus()
                    // so we can read hadRemoteInteraction — true when QR is scanned but not yet
                    // approved. This lets the UI show the 2FA screen while the user confirms.
                    val authService = steamClient.getHandler<SteamUnifiedMessages>()!!
                        .createService<Authentication>()
                    var qrScannedEmitted = false

                    while (isWaitingForQRAuth && authPollResult == null) {
                        try {
                            val request = CAuthentication_PollAuthSessionStatus_Request.newBuilder().apply {
                                clientId = authSession.clientID
                                requestId = com.google.protobuf.ByteString.copyFrom(authSession.requestID)
                            }.build()

                            val result = authService.pollAuthSessionStatus(request).await()

                            if (result.result != EResult.OK) {
                                throw AuthenticationException("Failed to poll status", result.result)
                            }

                            val response = result.body

                            // Replicate handlePollAuthSessionStatusResponse behaviour
                            if (response.newClientId != 0L) {
                                authSession.clientID = response.newClientId
                            }
                            if (response.newChallengeUrl.isNotEmpty()) {
                                val urlEvent = SteamEvent.QrChallengeReceived(response.newChallengeUrl)
                                PluviaApp.events.emit(urlEvent)
                            }

                            // Detect scan before approval
                            if (!qrScannedEmitted && response.hadRemoteInteraction) {
                                qrScannedEmitted = true
                                PluviaApp.events.emit(SteamEvent.QrCodeScanned)
                            }

                            // Check for completion
                            if (response.refreshToken.isNotEmpty()) {
                                authPollResult = AuthPollResult(response)
                            } else {
                                delay(authSession.pollingInterval.toLong() * 1000L)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Poll auth session status error")
                            throw e
                        }
                    }

                    isWaitingForQRAuth = false

                    val event = SteamEvent.QrAuthEnded(authPollResult != null)
                    PluviaApp.events.emit(event)

                    // there is a chance qr got cancelled and there is no authPollResult
                    if (authPollResult == null) {
                        Timber.e("Got no auth poll result")
                        throw Exception("Got no auth poll result")
                    }

                    login(
                        clientId = authSession.clientID,
                        username = authPollResult.accountName,
                        accessToken = authPollResult.accessToken,
                        refreshToken = authPollResult.refreshToken,
                    )
                } ?: run {
                    Timber.e("Could not start QR logon: Failed to connect to Steam")

                    val event = SteamEvent.QrAuthEnded(success = false, message = "No connection to Steam")
                    PluviaApp.events.emit(event)
                }
            } catch (e: Exception) {
                Timber.e(e, "QR failed")

                val message = when (e) {
                    is CancellationException -> "QR Session timed out"
                    is AuthenticationException -> e.result?.name ?: e.message
                    else -> e.message ?: e.javaClass.name
                }

                val event = SteamEvent.QrAuthEnded(success = false, message = message)
                PluviaApp.events.emit(event)
            }
        }

        fun stopLoginWithQr() {
            Timber.i("Stopping QR polling")

            isWaitingForQRAuth = false
        }

        fun start(context: Context) {
            try {
                val intent = Intent(context, SteamService::class.java)
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start SteamService")
            }
        }

        fun stop() {
            instance?.let { steamInstance ->
                steamInstance.scope.launch {
                    steamInstance.stop()
                }
            }
        }

        fun logOut() {
            // Capture username before clearing anything
            val username = PrefManager.username

            // ── Atomic state flip ──
            isLoggingOut = true
            _isLoggedInFlow.value = false
            PrefManager.clearAuthTokens()

            // Cancel background jobs immediately
            instance?.picsGetProductInfoJob?.cancel()
            instance?.picsChangesCheckerJob?.cancel()
            instance?.friendCheckerJob?.cancel()
            instance?.friendsAutoRefreshJob?.cancel()
            instance?.autoUpdateCheckerJob?.cancel()
            instance?.keepaliveJob?.cancel()

            // Emit event synchronously so the UI can react in the same frame
            PluviaApp.events.emit(SteamEvent.LoggedOut(username))

            // Tell Steam we're leaving (best-effort, service will stop in onLoggedOff)
            instance?.let { svc ->
                svc.scope.launch(Dispatchers.Default) {
                    try {
                        clearDatabase()
                        svc._steamUser?.logOff()
                    } catch (e: Exception) {
                        Timber.e(e, "Error during async logOff")
                    }
                }
            }
        }

        fun requestSync() {
            instance?.let { service ->
                service.scope.launch {
                    refreshOwnedGamesFromServer()
                }
            }
        }

        @JvmStatic
        fun hasPendingCloudSync(context: Context, appId: Int): Boolean {
            PrefManager.init(context.applicationContext)
            return PrefManager.hasPendingSteamCloudSyncAppId(appId)
        }

        @JvmStatic
        fun queuePendingCloudSync(context: Context, appId: Int) {
            if (appId <= 0) return
            PrefManager.init(context.applicationContext)
            PrefManager.addPendingSteamCloudSyncAppId(appId)
            Timber.i("Queued Steam Cloud sync for appId=$appId")
        }

        @JvmStatic
        fun clearPendingCloudSync(context: Context, appId: Int) {
            if (appId <= 0) return
            PrefManager.init(context.applicationContext)
            PrefManager.removePendingSteamCloudSyncAppId(appId)
            Timber.i("Cleared pending Steam Cloud sync for appId=$appId")
        }

        @JvmStatic
        fun handleAppExitCloudSync(context: Context, appId: Int) {
            if (appId <= 0) return

            val appContext = context.applicationContext
            PrefManager.init(appContext)
            start(appContext)

            CoroutineScope(Dispatchers.IO).launch {
                if (!isConnected || !isLoggedIn) {
                    queuePendingCloudSync(appContext, appId)
                    return@launch
                }

                val accountId = userSteamId?.accountID?.toLong()
                    ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toLong()
                    ?: 0L
                val prefixToPath: (String) -> String = { prefix ->
                    PathType.from(prefix).toAbsPath(appContext, appId, accountId)
                }

                runCatching {
                    val container = ContainerUtils.getOrCreateContainer(appContext, "STEAM_$appId")
                    ContainerManager(appContext).activateContainer(container)
                }.onFailure { throwable ->
                    Timber.w(throwable, "Failed to activate container before Steam Cloud exit sync for appId=$appId")
                }
                notifyRunningProcesses()

                val syncInfo = runCatching {
                    closeApp(
                        appId = appId,
                        isOffline = false,
                        prefixToPath = prefixToPath,
                    ).await()
                }.getOrElse { throwable ->
                    Timber.e(throwable, "Steam Cloud exit sync failed for appId=$appId")
                    PostSyncInfo(SyncResult.UnknownFail)
                }

                if (syncInfo.syncResult == SyncResult.Success || syncInfo.syncResult == SyncResult.UpToDate) {
                    clearPendingCloudSync(appContext, appId)
                } else {
                    Timber.w("Steam Cloud exit sync incomplete for appId=$appId result=${syncInfo.syncResult}")
                    queuePendingCloudSync(appContext, appId)
                }
            }
        }

        @JvmStatic
        fun processPendingCloudSyncQueue(context: Context) {
            val appContext = context.applicationContext
            PrefManager.init(appContext)

            if (!pendingCloudSyncProcessing.compareAndSet(false, true)) {
                Timber.d("Pending Steam Cloud sync queue is already being processed")
                return
            }

            val service = instance
            if (service == null) {
                pendingCloudSyncProcessing.set(false)
                return
            }

            service.scope.launch(Dispatchers.IO) {
                try {
                    if (!isConnected || !isLoggedIn) {
                        return@launch
                    }

                    val queuedAppIds = PrefManager.getPendingSteamCloudSyncAppIds().toList()
                    if (queuedAppIds.isEmpty()) {
                        return@launch
                    }

                    val accountId = userSteamId?.accountID?.toLong()
                        ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toLong()
                        ?: 0L

                    queuedAppIds.forEach { appId ->
                        runCatching {
                            val container = ContainerUtils.getOrCreateContainer(appContext, "STEAM_$appId")
                            ContainerManager(appContext).activateContainer(container)
                        }.onFailure { throwable ->
                            Timber.w(throwable, "Failed to activate container before queued Steam Cloud sync for appId=$appId")
                        }

                        val prefixToPath: (String) -> String = { prefix ->
                            PathType.from(prefix).toAbsPath(appContext, appId, accountId)
                        }

                        val syncInfo = runCatching {
                            forceSyncUserFiles(
                                appId = appId,
                                prefixToPath = prefixToPath,
                                preferredSave = SaveLocation.Local,
                                parentScope = this,
                            ).await()
                        }.onFailure { throwable ->
                            Timber.e(throwable, "Failed processing queued Steam Cloud sync for appId=$appId")
                        }.getOrNull()

                        if (syncInfo?.syncResult == SyncResult.Success || syncInfo?.syncResult == SyncResult.UpToDate) {
                            clearPendingCloudSync(appContext, appId)
                        } else {
                            Timber.w("Queued Steam Cloud sync still pending for appId=$appId result=${syncInfo?.syncResult}")
                        }
                    }
                } finally {
                    pendingCloudSyncProcessing.set(false)
                }
            }
        }

        private fun clearUserData() {
            PrefManager.clearAuthTokens()

            clearDatabase()
        }

        fun clearDatabase() {
            with(instance!!) {
                scope.launch {
                    db.withTransaction {
                        // We NO LONGER delete apps, change numbers, or file lists here.
                        // This preserves the installed games and shortcuts.
                        // appDao.deleteAll()
                        // We keep app metadata, but cloud sync caches are cleared separately.
                        
                        licenseDao.deleteAll()
                        encryptedAppTicketDao.deleteAll()
                        downloadingAppInfoDao.deleteAll()
                    }
                }
            }
        }

        private fun clearCloudSyncCaches() {
            instance?.let { svc ->
                svc.scope.launch {
                    svc.db.withTransaction {
                        svc.changeNumbersDao.deleteAll()
                        svc.fileChangeListsDao.deleteAll()
                    }
                    Timber.i("Cleared cloud sync caches (change numbers + file lists)")
                }
            }
        }

        private fun performLogOffDuties() {
            val username = PrefManager.username

            clearUserData()
            clearCloudSyncCaches()

            val event = SteamEvent.LoggedOut(username)
            PluviaApp.events.emit(event)

            // Cancel previous continuous jobs or else they will continue to run even after logout
            instance?.picsGetProductInfoJob?.cancel()
            instance?.picsChangesCheckerJob?.cancel()
            instance?.friendCheckerJob?.cancel()
            instance?.friendsAutoRefreshJob?.cancel()
            instance?.autoUpdateCheckerJob?.cancel()
            instance?.keepaliveJob?.cancel()
        }

        suspend fun getOwnedGames(friendID: Long): List<OwnedGames> = withContext(Dispatchers.IO) {
            instance?._unifiedFriends!!.getOwnedGames(friendID)
        }

        // Add helper to detect if any downloads or cloud sync are in progress
        fun hasActiveOperations(): Boolean {
            val anySyncInProgress = syncInProgressApps.values.any { it.get() }
            return anySyncInProgress || downloadJobs.values.any { it.getProgress() < 1f }
        }

        // Should service auto-stop when idle (backgrounded)?
        var autoStopWhenIdle: Boolean = false

        suspend fun isUpdatePending(
            appId: Int,
            branch: String = "public",
        ): Boolean = withContext(Dispatchers.IO) {
            getChangedDepotsForUpdate(appId, branch).isNotEmpty()
        }

        /**
         * Returns a set of depot IDs that have changed (need update).
         * Used for targeted updates instead of re-downloading everything.
         */
        suspend fun getChangedDepotsForUpdate(
            appId: Int,
            branch: String = "public",
        ): Set<Int> = withContext(Dispatchers.IO) {
            if (!isConnected) return@withContext emptySet()

            val steamApps = instance?._steamApps ?: return@withContext emptySet()

            val pics = steamApps.picsGetProductInfo(
                apps = listOf(PICSRequest(id = appId)),
                packages = emptyList(),
            ).await()

            val remoteAppInfo = pics.results
                .firstOrNull()
                ?.apps
                ?.values
                ?.firstOrNull()
                ?: return@withContext emptySet()

            val remoteSteamApp = remoteAppInfo.keyValues.generateSteamApp()
            val localSteamApp = getAppInfoOf(appId) ?: return@withContext getDownloadableDepots(appId).keys.toSet()

            getDownloadableDepots(appId).keys.filter { depotId ->
                val remoteManifest = remoteSteamApp.depots[depotId]?.manifests?.get(branch)
                val localManifest = localSteamApp.depots[depotId]?.manifests?.get(branch)
                if (remoteManifest == null) return@filter false
                remoteManifest?.gid != localManifest?.gid
            }.toSet()
        }

        suspend fun refreshAppMetadataFromSteam(
            appId: Int,
            refreshRelatedDlcs: Boolean = false,
        ): SteamApp? = withContext(Dispatchers.IO) {
            val service = instance ?: return@withContext getAppInfoOf(appId)
            val steamApps = service._steamApps ?: return@withContext getAppInfoOf(appId)
            if (!isConnected) return@withContext getAppInfoOf(appId)
            val allLicenses = service.licenseDao.getAllLicenses()

            suspend fun parseAndPersistApp(
                remoteKeyValues: KeyValue,
                remoteChangeNumber: Int,
                appIdToPersist: Int,
                accessToken: Long = 0L,
            ): SteamApp {
                val existingApp = service.appDao.findApp(appIdToPersist)
                val packageId = existingApp?.packageId?.takeIf { it != INVALID_PKG_ID }
                    ?: allLicenses.firstOrNull { appIdToPersist in it.appIds }?.packageId
                    ?: INVALID_PKG_ID
                val packageFromDb = if (packageId != INVALID_PKG_ID) {
                    service.licenseDao.findLicense(packageId)
                } else {
                    null
                }
                val ownerAccountId = packageFromDb?.ownerAccountId ?: existingApp?.ownerAccountId.orEmpty()
                val existingInstallDir = existingApp?.installDir.orEmpty()
                val preserveInstallDir = existingInstallDir.isNotEmpty() &&
                    (existingInstallDir.startsWith("/") || existingInstallDir.contains(File.separator))

                val generatedApp = remoteKeyValues.generateSteamApp()
                val parsedApp = generatedApp.copy(
                    packageId = packageId,
                    ownerAccountId = ownerAccountId,
                    receivedPICS = true,
                    lastChangeNumber = remoteChangeNumber,
                    licenseFlags = packageFromDb?.licenseFlags ?: existingApp?.licenseFlags ?: EnumSet.noneOf(ELicenseFlags::class.java),
                    installDir = if (preserveInstallDir) existingInstallDir else generatedApp.installDir,
                )

                service.db.withTransaction {
                    service.appDao.insert(parsedApp)
                }

                Timber.i("Refreshed Steam metadata for appId=$appIdToPersist token=${if (accessToken != 0L) "yes" else "no"}")
                return parsedApp
            }

            runCatching {
                val baseCallback = withTimeoutOrNull(10_000L) {
                    steamApps.picsGetProductInfo(
                        apps = listOf(PICSRequest(id = appId)),
                        packages = emptyList(),
                    ).await()
                }

                val remoteBaseApp = baseCallback?.results
                    ?.firstOrNull()
                    ?.apps
                    ?.values
                    ?.firstOrNull()
                    ?: return@runCatching getAppInfoOf(appId)

                val baseApp = parseAndPersistApp(
                    remoteKeyValues = remoteBaseApp.keyValues,
                    remoteChangeNumber = remoteBaseApp.changeNumber,
                    appIdToPersist = appId,
                )

                if (refreshRelatedDlcs) {
                    val relatedDlcIds = baseApp.depots.values
                        .map { it.dlcAppId }
                        .filter { it != INVALID_APP_ID }
                        .toMutableSet()

                    relatedDlcIds.addAll(baseApp.dlcAppIds)
                    relatedDlcIds.addAll(getDownloadableDlcAppsOf(appId).orEmpty().map { it.id })
                    relatedDlcIds.addAll(getHiddenDlcAppsOf(appId).orEmpty().map { it.id })

                    if (relatedDlcIds.isNotEmpty()) {
                        val accessTokens = withTimeoutOrNull(10_000L) {
                            steamApps.picsGetAccessTokens(
                                appIds = relatedDlcIds.toList(),
                                packageIds = emptyList(),
                            ).await()
                        }

                        relatedDlcIds
                            .map { dlcAppId -> PICSRequest(id = dlcAppId, accessToken = accessTokens?.appTokens?.get(dlcAppId) ?: 0L) }
                            .chunked(MAX_PICS_BUFFER)
                            .forEach { chunk ->
                                val dlcCallback = withTimeoutOrNull(10_000L) {
                                    steamApps.picsGetProductInfo(
                                        apps = chunk,
                                        packages = emptyList(),
                                    ).await()
                                }

                                val refreshedDlcs = dlcCallback?.results.orEmpty()
                                    .flatMap { it.apps.values }
                                    .map { remoteDlc ->
                                        val dlcId = remoteDlc.id
                                        val existingDlc = service.appDao.findApp(dlcId)
                                        val packageId = existingDlc?.packageId?.takeIf { it != INVALID_PKG_ID }
                                            ?: allLicenses.firstOrNull { dlcId in it.appIds }?.packageId
                                            ?: INVALID_PKG_ID
                                        val packageFromDb = if (packageId != INVALID_PKG_ID) {
                                            service.licenseDao.findLicense(packageId)
                                        } else {
                                            null
                                        }
                                        val ownerAccountId = packageFromDb?.ownerAccountId ?: existingDlc?.ownerAccountId.orEmpty()

                                        val rawDlcApp = remoteDlc.keyValues.generateSteamApp()
                                        rawDlcApp.copy(
                                            packageId = packageId,
                                            ownerAccountId = ownerAccountId,
                                            receivedPICS = true,
                                            lastChangeNumber = remoteDlc.changeNumber,
                                            licenseFlags = packageFromDb?.licenseFlags ?: existingDlc?.licenseFlags ?: EnumSet.noneOf(ELicenseFlags::class.java),
                                            installDir = existingDlc?.installDir.orEmpty().takeIf {
                                                it.isNotEmpty() && (it.startsWith("/") || it.contains(File.separator))
                                            } ?: rawDlcApp.installDir,
                                            // Force dlcForAppId to the parent app ID — PICS data often omits this field
                                            dlcForAppId = if (rawDlcApp.dlcForAppId == INVALID_APP_ID) appId else rawDlcApp.dlcForAppId,
                                        )
                                    }

                                if (refreshedDlcs.isNotEmpty()) {
                                    service.db.withTransaction {
                                        service.appDao.insertAll(refreshedDlcs)
                                    }
                                }
                            }
                    }
                }

                service.appDao.findApp(appId)
            }.onFailure { error ->
                Timber.e(error, "Failed to refresh Steam metadata for appId=$appId")
            }.getOrElse { getAppInfoOf(appId) }
        }

        suspend fun checkDlcOwnershipViaPICSBatch(dlcAppIds: Set<Int>): Set<Int> {
            if (dlcAppIds.isEmpty()) return emptySet()

            val steamApps = instance?._steamApps ?: return emptySet()

            try {
                // Step 1: Get access tokens for all DLC appIds at once
                val tokens = steamApps.picsGetAccessTokens(
                    appIds = dlcAppIds.toList(),
                    packageIds = emptyList(),
                ).await()

                Timber.d("Access tokens response:")
                Timber.d("  - Granted tokens: ${tokens.appTokens.keys}")
                Timber.d("  - Denied tokens: ${tokens.appTokensDenied}")

                // Step 2: Filter to only appIds that have tokens (we own them)
                val ownedAppIds = tokens.appTokens.keys.filter { it in dlcAppIds }.toSet()

                Timber.d("Owned appIds (from tokens): $ownedAppIds")

                if (ownedAppIds.isEmpty()) {
                    Timber.w("No owned DLCs found via access tokens")
                    return emptySet()
                }

                // Step 3: Create PICSRequests for all owned appIds
                val picsRequests = ownedAppIds.map { appId ->
                    val token = tokens.appTokens[appId] ?: return@map null
                    PICSRequest(id = appId, accessToken = token)
                }.filterNotNull()

                Timber.d("Created ${picsRequests.size} PICS requests")

                if (picsRequests.isEmpty()) return emptySet()

                // Step 4: Query PICS for all apps at once (batch them)
                // Note: Steam has limits, so you might need to chunk if > 100 apps
                val chunkSize = 100
                val allOwnedAppIds = mutableSetOf<Int>()

                picsRequests.chunked(chunkSize).forEach { chunk ->
                    Timber.d("Querying PICS chunk with ${chunk.size} apps")
                    val callback = steamApps.picsGetProductInfo(
                        apps = chunk,
                        packages = emptyList(),
                    ).await()

                    // Collect all appIds that returned results
                    callback.results.forEach { picsCallback ->
                        val returnedAppIds = picsCallback.apps.keys
                        Timber.d("  PICS result: ${returnedAppIds.size} apps returned")
                        allOwnedAppIds.addAll(picsCallback.apps.keys)
                    }
                }

                Timber.i("Final owned DLC appIds: $allOwnedAppIds")
                Timber.i("Total owned: ${allOwnedAppIds.size} out of ${dlcAppIds.size} checked")

                return allOwnedAppIds
            } catch (e: Exception) {
                Timber.e(e, "Failed to check DLC ownership via PICS batch for ${dlcAppIds.size} appIds")
                return emptySet()
            }
        }
    }

    private fun initDatabaseReferences() {
        val database = PluviaDatabase.getInstance(applicationContext)
        db = database
        licenseDao = database.steamLicenseDao()
        appDao = database.steamAppDao()
        changeNumbersDao = database.appChangeNumbersDao()
        appInfoDao = database.appInfoDao()
        fileChangeListsDao = database.appFileChangeListsDao()
        cachedLicenseDao = database.cachedLicenseDao()
        encryptedAppTicketDao = database.encryptedAppTicketDao()
        downloadingAppInfoDao = database.downloadingAppInfoDao()
        chatMessageDao = database.chatMessageDao()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        PrefManager.init(applicationContext)
        PluviaDatabase.init(applicationContext)
        initDatabaseReferences()

        notificationHelper = NotificationHelper(applicationContext)
        val notification = notificationHelper.createForegroundNotification("Steam Service is running")
        startForeground(1, notification)

        _isConnectedFlow.value = steamClient?.isConnected ?: false
        // Login flow is already pre-seeded by initLoginStatus() before start()
        _isLoggedInFlow.value = steamClient?.steamID?.isValid ?: _isLoggedInFlow.value

        // JavaSteam logger CME hot-fix
        runCatching {
            val clazz = Class.forName("in.dragonbra.javasteam.util.log.LogManager")
            val field = clazz.getDeclaredField("LOGGERS").apply { isAccessible = true }
            field.set(
                /* obj = */ null,
                java.util.concurrent.ConcurrentHashMap<Any, Any>(),   // replaces the HashMap
            )
        }

        PluviaApp.events.on<AndroidEvent.EndProcess, Unit>(onEndProcess)

        // pause downloads when WiFi/Ethernet is lost
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                // only pause if no WiFi/LAN remains (avoids false pause on multi-network)
                if (PrefManager.downloadOnWifiOnly && !isWifiConnected) {
                    for ((appId, info) in downloadJobs.entries.toList()) {
                        Timber.d("Cancelling job")
                        info.cancel()
                        PluviaApp.events.emit(AndroidEvent.DownloadPausedDueToConnectivity(appId))
                        removeDownloadJob(appId)
                    }
                    notificationHelper.notify(getString(R.string.downloads_queue_paused_wifi))
                }
            }
        }
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // To view log messages in android logcat properly
        LogManager.addListener(logger)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Notification intents
        when (intent?.action) {
            NotificationHelper.ACTION_EXIT -> {
                Timber.d("Exiting app via notification intent")

                val event = AndroidEvent.EndProcess
                PluviaApp.events.emit(event)

                return START_NOT_STICKY
            }
        }

        if (!isRunning) {
            Timber.i("Using server list path: $serverListPath")
            
            // Ensure parent directory exists
            File(serverListPath).parentFile?.mkdirs()

            val configuration = SteamConfiguration.create {
                it.withProtocolTypes(PROTOCOL_TYPES)
                it.withCellID(PrefManager.cellId)
                it.withServerListProvider(FileServerListProvider(File(serverListPath)))
                it.withConnectionTimeout(60000L)
                it.withHttpClient(
                    OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)   // Time to establish connection
                        .readTimeout(60, TimeUnit.SECONDS)      // Max inactivity between reads
                        .writeTimeout(30, TimeUnit.SECONDS)     // Time for writes
                        .build(),
                )
            }

            // create our steam client instance
            steamClient = SteamClient(configuration).apply {
                // remove callbacks we're not using.
                removeHandler(SteamGameServer::class.java)
                removeHandler(SteamMasterServer::class.java)
                removeHandler(SteamWorkshop::class.java)
                removeHandler(SteamScreenshots::class.java)
            }

            // create the callback manager which will route callbacks to function calls
            callbackManager = CallbackManager(steamClient!!)

            // get the different handlers to be used throughout the service
            _steamUser = steamClient!!.getHandler(SteamUser::class.java)
            _steamApps = steamClient!!.getHandler(SteamApps::class.java)
            _steamFriends = steamClient!!.getHandler(SteamFriends::class.java)
            _steamCloud = steamClient!!.getHandler(SteamCloud::class.java)
            _steamUserStats = steamClient!!.getHandler(SteamUserStats::class.java)

            _unifiedFriends = SteamUnifiedFriends(this)
            _steamFamilyGroups = steamClient!!.getHandler<SteamUnifiedMessages>()!!.createService<FamilyGroups>()

            // subscribe to the callbacks we are interested in
            with(callbackSubscriptions) {
                with(callbackManager!!) {
                    add(subscribe(ConnectedCallback::class.java, ::onConnected))
                    add(subscribe(DisconnectedCallback::class.java, ::onDisconnected))
                    add(subscribe(LoggedOnCallback::class.java, ::onLoggedOn))
                    add(subscribe(LoggedOffCallback::class.java, ::onLoggedOff))
                    add(subscribe(PersonaStateCallback::class.java, ::onPersonaStateReceived))
                    add(subscribe(FriendMsgCallback::class.java, ::onFriendMessage))
                    add(subscribe(LicenseListCallback::class.java, ::onLicenseList))
                    add(subscribe(PlayingSessionStateCallback::class.java, ::onPlayingSessionState))
                }
            }

            isRunning = true

            // we should use Dispatchers.IO here since we are running a sleeping/blocking function
            // "The idea is that the IO dispatcher spends a lot of time waiting (IO blocked),
            // while the Default dispatcher is intended for CPU intensive tasks, where there
            // is little or no sleep."
            // source: https://stackoverflow.com/a/59040920
            scope.launch {
                while (isRunning) {
                    // logD("runWaitCallbacks")

                    try {
                        callbackManager!!.runWaitCallbacks(1000L)
                    } catch (e: Exception) {
                        Timber.e("runWaitCallbacks failed: $e")
                    }
                }
            }

            connectToSteam()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        // Persist download progress for all active downloads
        // This is a safety net for OS kills (unlikely but possible)
        downloadJobs.values.forEach { downloadInfo ->
            downloadInfo.persistProgressSnapshot(force = true)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationHelper.cancel()

        // Unregister Wi-Fi connectivity callback
        connectivityManager.unregisterNetworkCallback(networkCallback)

        scope.launch { stop() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connectToSteam() {
        scope.launch {
            while (isRunning && !isConnected) {
                Timber.d("connectToSteam: attempting connection...")
                try {
                    steamClient!!.connect()
                    
                    // Wait for connection for up to 10 seconds
                    var waitAttempts = 0
                    while (!isConnected && waitAttempts < 10) {
                        delay(1000)
                        waitAttempts++
                    }
                    
                    if (!isConnected) {
                        Timber.w("Failed to connect to Steam, marking endpoint bad and force disconnecting")
                        try {
                            steamClient!!.servers.tryMark(steamClient!!.currentEndpoint, PROTOCOL_TYPES, ServerQuality.BAD)
                        } catch (e: Exception) {}
                        
                        try {
                            steamClient!!.disconnect()
                        } catch (e: Exception) {}
                        
                        // Wait before retrying
                        delay(5000)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error during connectToSteam")
                    delay(5000)
                }
            }
        }
    }

    private suspend fun stop() {
        Timber.i("Stopping Steam service")
        if (steamClient != null && steamClient!!.isConnected) {
            isStopping = true

            steamClient!!.disconnect()

            while (isStopping) {
                delay(200L)
            }

            // the reason we don't clearValues() here is because the onDisconnect
            // callback does it for us
        } else {
            clearValues()
        }
    }

    private fun clearValues() {
        _loginResult = LoginResult.Failed
        isRunning = false
        isConnected = false
        isLoggingOut = false
        isWaitingForQRAuth = false

        steamClient = null
        _steamUser = null
        _steamApps = null
        _steamFriends = null
        _steamCloud = null

        callbackSubscriptions.forEach { it.close() }
        callbackSubscriptions.clear()
        callbackManager = null

        _unifiedFriends?.close()
        _unifiedFriends = null

        isStopping = false
        retryAttempt = 0

        PluviaApp.events.off<AndroidEvent.EndProcess, Unit>(onEndProcess)
        PluviaApp.events.clearAllListenersOf<SteamEvent<Any>>()

        LogManager.removeListener(logger)
    }

    private fun reconnect() {
        notificationHelper.notify("Retrying...")

        isConnected = false

        val event = SteamEvent.Disconnected
        PluviaApp.events.emit(event)

        steamClient!!.disconnect()
    }

    // region [REGION] callbacks
    @Suppress("UNUSED_PARAMETER", "unused")
    private fun onConnected(callback: ConnectedCallback) {
        Timber.i("Connected to Steam")

        retryAttempt = 0
        isConnected = true

        var isAutoLoggingIn = false

        if (PrefManager.username.isNotEmpty() && PrefManager.refreshToken.isNotEmpty()) {
            isAutoLoggingIn = true

            login(
                username = PrefManager.username,
                refreshToken = PrefManager.refreshToken,
                rememberSession = true,
            )
        }

        val event = SteamEvent.Connected(isAutoLoggingIn)
        PluviaApp.events.emit(event)
    }

    private fun onDisconnected(callback: DisconnectedCallback) {
        Timber.i("Disconnected from Steam. User initiated: ${callback.isUserInitiated}")

        isConnected = false

        if (!isStopping && retryAttempt < MAX_RETRY_ATTEMPTS) {
            retryAttempt++

            // Exponential backoff: 5s, 10s, 30s, 60s, 120s, 180s... capped at 300s
            val backoffSeconds = listOf(5, 10, 30, 60, 120, 180, 240, 300).getOrElse(retryAttempt - 1) { 300 }
            Timber.w("Disconnected, reconnecting in ${backoffSeconds}s (retry $retryAttempt/$MAX_RETRY_ATTEMPTS)")

            val event = SteamEvent.RemotelyDisconnected
            PluviaApp.events.emit(event)

            scope.launch {
                delay(backoffSeconds.seconds)
                if (!isStopping) {
                    connectToSteam()
                }
            }
        } else {
            val event = SteamEvent.Disconnected
            PluviaApp.events.emit(event)

            clearValues()

            stopSelf()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun onLoggedOn(callback: LoggedOnCallback) {
        Timber.i("Logged onto Steam: ${callback.result}")
        
        if (callback.result == EResult.OK) {
            isLoggingOut = false
            _isLoggedInFlow.value = true
        } else {
            _isLoggedInFlow.value = false
        }

        if (userSteamId?.isValid == true) {
            if (PrefManager.steamUserAccountId != userSteamId!!.accountID.toInt()) {
                PrefManager.steamUserAccountId = userSteamId!!.accountID.toInt()
                Timber.d("Saving logged in Steam accountID ${userSteamId!!.accountID.toInt()}")
                clearCloudSyncCaches()
            }
            val steamId64 = userSteamId!!.convertToUInt64()
            if (PrefManager.steamUserSteamId64 != steamId64) {
                PrefManager.steamUserSteamId64 = steamId64
                Timber.d("Saving logged in Steam ID64 $steamId64")
            }
        }

        when (callback.result) {
            EResult.TryAnotherCM -> {
                _loginResult = LoginResult.Failed
                reconnect()
            }

            EResult.OK -> {
                // Steam's GeoIP-based cellID is often wrong (e.g. returning Romania for US users).
                // Only log it for debugging; keep cellId at 0 ("Automatic") unless the user
                // manually picks a server in Settings > Stores > Download Server.
                Timber.d("Steam suggested cellID: ${callback.cellID} (not auto-saving, using Automatic unless manually set)")

                // retrieve persona data of logged in user
                scope.launch { requestUserPersona() }

                // Request family share info if we have a familyGroupId.
                if (callback.familyGroupId != 0L) {
                    scope.launch {
                        val request = SteammessagesFamilygroupsSteamclient.CFamilyGroups_GetFamilyGroup_Request.newBuilder().apply {
                            familyGroupid = callback.familyGroupId
                        }.build()

                        _steamFamilyGroups!!.getFamilyGroup(request).await().let {
                            if (it.result != EResult.OK) {
                                Timber.w("An error occurred loading family group info.")
                                return@launch
                            }

                            val response = it.body

                            Timber.i("Found family share: ${response.name}, with ${response.membersCount} members.")

                            response.membersList.forEach { member ->
                                val accountID = SteamID(member.steamid).accountID.toInt()
                                familyGroupMembers.add(accountID)
                            }
                        }
                    }
                }

                picsChangesCheckerJob = continuousPICSChangesChecker()
                picsGetProductInfoJob = continuousPICSGetProductInfo()
                friendsAutoRefreshJob = continuousFriendsChecker()
                autoUpdateCheckerJob = continuousAutoUpdateChecker()
                keepaliveJob = continuousKeepalive()
                continuousCacheCleanup()

                // Tell steam we're online, this allows friends to update.
                _steamFriends?.setPersonaState(EPersonaState.from(PrefManager.personaState) ?: EPersonaState.Online)

                notificationHelper.notify("Connected")
                processPendingCloudSyncQueue(applicationContext)

                // Resume any partial downloads from previous sessions
                try {
                    val pendingAppIds = runBlocking(Dispatchers.IO) {
                        instance?.appInfoDao?.getAllInstalledAppIds() ?: emptyList()
                    }
                    for (appId in pendingAppIds) {
                        val dirPath = getAppDirPath(appId)
                        if (MarkerUtils.hasMarker(dirPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER) ||
                            hasPartialDownloadFiles(dirPath)) {
                            Timber.d("Resuming partial download for appId=$appId")
                            downloadApp(appId)
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to scan for partial downloads on startup")
                }

                _loginResult = LoginResult.Success
            }

            else -> {
                clearUserData()

                _loginResult = LoginResult.Failed

                reconnect()
            }
        }

        val event = SteamEvent.LogonEnded(PrefManager.username, _loginResult)
        PluviaApp.events.emit(event)
    }

    private fun onLoggedOff(callback: LoggedOffCallback) {
        Timber.i("Logged off of Steam: ${callback.result}")

        _isLoggedInFlow.value = false

        notificationHelper.notify("Disconnected...")

        if (isLoggingOut || callback.result == EResult.LogonSessionReplaced) {
            // logOut() already handled cleanup; just stop the service.
            if (!isLoggingOut) performLogOffDuties()

            scope.launch { stop() }
        } else if (callback.result == EResult.LoggedInElsewhere) {
            // received when a client runs an app and wants to forcibly close another
            // client running an app
            val event = SteamEvent.ForceCloseApp
            PluviaApp.events.emit(event)

            reconnect()
        } else {
            reconnect()
        }
    }

    private fun onPlayingSessionState(callback: PlayingSessionStateCallback) {
        Timber.d("onPlayingSessionState called with isPlayingBlocked = " + callback.isPlayingBlocked)
        _isPlayingBlocked.value = callback.isPlayingBlocked
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun onFriendMessage(callback: FriendMsgCallback) {
        val sender = callback.sender ?: return
        if (!sender.isIndividualAccount) return
        val msgText = callback.message ?: return
        if (msgText.isBlank()) return
        val sid64 = sender.convertToUInt64()
        val localSteamClient = steamClient?.steamID
        val isIncoming = localSteamClient == null || sender != localSteamClient
        val message = ChatMessage(
            steamId64 = sid64,
            senderSteamId64 = if (isIncoming) sid64 else (localSteamClient?.convertToUInt64() ?: 0L),
            text = msgText,
            timestamp = System.currentTimeMillis(),
            isIncoming = isIncoming,
        )
        _chatMessages.update { map ->
            val existing = map.toMutableMap()
            val messages = (existing[sid64] ?: emptyList()) + message
            existing[sid64] = messages
            existing
        }
        // Persist to DB
        scope.launch {
            try {
                chatMessageDao.insert(
                    ChatMessageEntity(
                        friendSteamId64 = sid64,
                        senderSteamId64 = message.senderSteamId64,
                        text = message.text,
                        timestamp = message.timestamp,
                        isIncoming = message.isIncoming,
                    )
                )
                chatMessageDao.deleteOldMessages(
                    sid64,
                    PrefManager.chatHistoryKeepCount.coerceIn(100, 2000),
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to persist chat message")
            }
        }
    }

    private fun onPersonaStateReceived(callback: PersonaStateCallback) {
        // Ignore accounts that arent individuals
        if (!callback.friendId.isIndividualAccount) {
            return
        }

        // Ignore states where the name is blank.
        if (callback.playerName.isEmpty()) {
            return
        }

        val localSteamClient = steamClient ?: return

        scope.launch {
            val avatarHash = callback.avatarHash.toHexString()
            val playerName = callback.playerName

            if (callback.friendId == localSteamClient.steamID) {
                Timber.d("Local persona state received: ${callback.playerName}")

                // Update local state flow
                _localPersona.update {
                    it.copy(
                        avatarHash = avatarHash,
                        name = playerName,
                        state = callback.personaState ?: EPersonaState.Offline,
                        gameAppID = callback.gamePlayedAppId,
                        gameName = appDao.findApp(callback.gamePlayedAppId)?.name ?: callback.gameName,
                    )
                }

                // Cache local persona
                PrefManager.steamUserAvatarHash = avatarHash
                PrefManager.steamUserName = playerName

                val event = SteamEvent.PersonaStateReceived(localPersona.value)
                PluviaApp.events.emit(event)
            }

            // Also process other friends (not just self)
            updateFriendInList(
                steamId64 = callback.friendId.convertToUInt64(),
                name = playerName,
                avatarHash = avatarHash,
                state = callback.personaState ?: EPersonaState.Offline,
                gameAppID = callback.gamePlayedAppId,
                gameName = appDao.findApp(callback.gamePlayedAppId)?.name ?: callback.gameName,
            )
        }
    }

    private fun updateFriendInList(
        steamId64: Long,
        name: String,
        avatarHash: String,
        state: EPersonaState,
        gameAppID: Int,
        gameName: String,
    ) {
        _friendsList.update { current ->
            val existing = current.toMutableList()
            val idx = existing.indexOfFirst { it.steamId64 == steamId64 }
            val friend = SteamFriend(
                steamId64 = steamId64,
                avatarHash = avatarHash,
                name = name,
                state = state,
                gameAppID = gameAppID,
                gameName = gameName,
            )
            if (idx >= 0) {
                existing[idx] = friend
            } else {
                existing.add(friend)
            }
            existing
        }
    }

    private fun refreshFriendsList() {
        val friends = _steamFriends ?: return
        val localSteamClient = steamClient ?: return
        val localSteamId = localSteamClient.steamID
        try {
            val count = friends.getFriendCount()
            Timber.d("Friends list: $count friends total")
            val allIds = mutableListOf<SteamID>()
            for (i in 0 until count) {
                val friendId = friends.getFriendByIndex(i) ?: continue
                if (friendId == localSteamId) continue
                if (!friendId.isIndividualAccount) continue
                allIds.add(friendId)
            }
            // Пагинация батчами, чтобы не слать один огромный запрос
            val batchSize = PrefManager.steamFriendsRequestBatchSize.coerceIn(20, 200)
            allIds.chunked(batchSize).forEach { batch ->
                runCatching {
                    friends.requestFriendInfo(batch)
                }.onSuccess {
                    Timber.d("Requested persona info for ${batch.size} friends")
                }.onFailure {
                    Timber.w(it, "requestFriendInfo batch failed (${batch.size} friends)")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to refresh friends list")
        }
        Timber.d("Friends list has ${_friendsList.value.size} friends")
    }

    private fun onLicenseList(callback: LicenseListCallback) {
        if (callback.result != EResult.OK) {
            Timber.w("Failed to get License list")
            return
        }

        Timber.i("Received License List ${callback.result}, size: ${callback.licenseList.size}")

        scope.launch {
            db.withTransaction {
                // Note: I assume with every launch we do, in fact, update the licenses for app the apps if we join or get removed
                //      from family sharing... We really can't test this as there is a 1-year cooldown.
                //      Then 'findStaleLicences' will find these now invalid items to remove.

                // Store raw licenses for DepotDownloader - each license in its own row
                licenses = callback.licenseList
                cachedLicenseDao.deleteAll()
                val cachedLicenses = callback.licenseList.map { license ->
                    CachedLicense(licenseJson = LicenseSerializer.serializeLicense(license))
                }
                cachedLicenseDao.insertAll(cachedLicenses)

                val licensesToAdd = callback.licenseList
                    .groupBy { it.packageID }
                    .map { licensesEntry ->
                        val preferred = licensesEntry.value.firstOrNull {
                            it.ownerAccountID == userSteamId?.accountID?.toInt()
                        } ?: licensesEntry.value.first()
                        SteamLicense(
                            packageId = licensesEntry.key,
                            lastChangeNumber = preferred.lastChangeNumber,
                            timeCreated = preferred.timeCreated,
                            timeNextProcess = preferred.timeNextProcess,
                            minuteLimit = preferred.minuteLimit,
                            minutesUsed = preferred.minutesUsed,
                            paymentMethod = preferred.paymentMethod,
                            licenseFlags = licensesEntry.value
                                .map { it.licenseFlags }
                                .reduceOrNull { first, second ->
                                    val combined = EnumSet.copyOf(first)
                                    combined.addAll(second)
                                    combined
                                } ?: EnumSet.noneOf(ELicenseFlags::class.java),
                            purchaseCode = preferred.purchaseCode,
                            licenseType = preferred.licenseType,
                            territoryCode = preferred.territoryCode,
                            accessToken = preferred.accessToken,
                            ownerAccountId = licensesEntry.value.map { it.ownerAccountID }, // Read note above
                            masterPackageID = preferred.masterPackageID,
                        )
                    }

                if (licensesToAdd.isNotEmpty()) {
                    Timber.i("Adding ${licensesToAdd.size} licenses")
                    licenseDao.insertAll(licensesToAdd)
                }

                val licensesToRemove = licenseDao.findStaleLicences(
                    packageIds = callback.licenseList.map { it.packageID },
                )
                if (licensesToRemove.isNotEmpty()) {
                    Timber.i("Removing ${licensesToRemove.size} (stale) licenses")
                    val packageIds = licensesToRemove.map { it.packageId }
                    licenseDao.deleteStaleLicenses(packageIds)
                }

                // Get PICS information with the current license database.
                licenseDao.getAllLicenses()
                    .map { PICSRequest(it.packageId, it.accessToken) }
                    .chunked(MAX_PICS_BUFFER)
                    .forEach { chunk ->
                        Timber.d("onLicenseList: Queueing ${chunk.size} package(s) for PICS")
                        packagePicsChannel.send(chunk)
                    }
            }
        }
    }

    override fun onChanged(qrAuthSession: QrAuthSession?) {
        qrAuthSession?.let { qr ->
            if (!BuildConfig.DEBUG) {
                Timber.d("QR code changed -> ${qr.challengeUrl}")
            }

            val event = SteamEvent.QrChallengeReceived(qr.challengeUrl)
            PluviaApp.events.emit(event)
        } ?: run { Timber.w("QR challenge url was null") }
    }
    // endregion

    /**
     * Request changes for apps and packages since a given change number.
     * Checks every [PICS_CHANGE_CHECK_DELAY] seconds.
     * Results are returned in a [PICSChangesCallback]
     */
    private fun continuousPICSChangesChecker(): Job = scope.launch {
        while (isActive && isLoggedIn) {
            // Экспоненциальный бэкофф при ошибках: 60с → 120с → 240с → 300с (max 5мин)
            val shift = picsConsecutiveFailures.coerceIn(0, 3)
            delay((60_000L shl shift).coerceAtMost(300_000L))

            PICSChangesCheck()
        }
    }

    private fun continuousFriendsChecker(): Job = scope.launch {
        // Initial delay to let login settle
        delay(30.seconds)
        while (isActive && isLoggedIn) {
            refreshFriendsList()
            delay(120.seconds)
        }
    }

    private fun continuousAutoUpdateChecker(): Job = scope.launch {
        delay(2.minutes)
        while (isActive && isLoggedIn) {
            try {
                checkAndRunAutoUpdates()
            } catch (e: Exception) {
                Timber.w(e, "Auto-update check failed")
            }
            delay(30.minutes)
        }
    }

    /** Send periodic keepalive to prevent NAT/network idle disconnects */
    private fun continuousKeepalive(): Job = scope.launch {
        delay(45.seconds)
        // Экспоненциальный бэкофф при ошибках: 45с → 90с → ... → 300с (max 5мин)
        var backoffMs = 45_000L
        while (isActive && isLoggedIn) {
            try {
                _steamApps?.picsGetChangesSince(lastChangeNumber = PrefManager.lastPICSChangeNumber)
                Timber.v("Keepalive ping sent")
                backoffMs = 45_000L
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("Keepalive failed: ${e.message}")
                backoffMs = (backoffMs * 2).coerceAtMost(300_000L)
            }
            delay(backoffMs)
        }
    }

    /** Periodic cache cleanup - runs weekly to remove stale PICS data without `received_pics` flag */
    private fun continuousCacheCleanup(): Job = scope.launch {
        delay(10.minutes)
        while (isActive && isLoggedIn) {
            try {
                val staleCount = runBlocking(Dispatchers.IO) {
                    val service = instance ?: return@runBlocking 0
                    val allApps = service.appDao.getAllAsList()
                    val stale = allApps.filter { !it.receivedPICS && System.currentTimeMillis() - it.lastChangeNumber * 1000L > 7 * 24 * 3600 * 1000L }
                    if (stale.isNotEmpty()) {
                        Timber.d("Cache cleanup: removing ${stale.size} stale PICS entries")
                        stale.forEach { service.appDao.update(it.copy(receivedPICS = true)) }
                    }
                    stale.size
                }
                if (staleCount > 0) Timber.d("Cache cleanup: refreshed $staleCount stale entries")
            } catch (e: Exception) {
                Timber.w("Cache cleanup failed: ${e.message}")
            }
            delay(7 * 24 * 3600 * 1000L) // Run weekly
        }
    }

    private fun PICSChangesCheck() {
        scope.launch {
            ensureActive()

            try {
                val changesSince = _steamApps!!.picsGetChangesSince(
                    lastChangeNumber = PrefManager.lastPICSChangeNumber,
                    sendAppChangeList = true,
                    sendPackageChangelist = true,
                ).await()

                // Ответ получен — сбрасываем бэкофф PICS-опроса
                picsConsecutiveFailures = 0

                if (PrefManager.lastPICSChangeNumber == changesSince.currentChangeNumber) {
                    Timber.w("Change number was the same as last change number, skipping")
                    return@launch
                }

                // Set our last change number
                PrefManager.lastPICSChangeNumber = changesSince.currentChangeNumber

                Timber.d(
                    "picsGetChangesSince:" +
                            "\n\tlastChangeNumber: ${changesSince.lastChangeNumber}" +
                            "\n\tcurrentChangeNumber: ${changesSince.currentChangeNumber}" +
                            "\n\tisRequiresFullUpdate: ${changesSince.isRequiresFullUpdate}" +
                            "\n\tisRequiresFullAppUpdate: ${changesSince.isRequiresFullAppUpdate}" +
                            "\n\tisRequiresFullPackageUpdate: ${changesSince.isRequiresFullPackageUpdate}" +
                            "\n\tappChangesCount: ${changesSince.appChanges.size}" +
                            "\n\tpkgChangesCount: ${changesSince.packageChanges.size}",

                    )

                // Process any app changes
                launch {
                    changesSince.appChanges.values
                        .filter { changeData ->
                            // only queue PICS requests for apps existing in the db that have changed
                            val app = appDao.findApp(changeData.id) ?: return@filter false
                            changeData.changeNumber != app.lastChangeNumber
                        }
                        .map { PICSRequest(id = it.id) }
                        .chunked(MAX_PICS_BUFFER)
                        .forEach { chunk ->
                            ensureActive()
                            Timber.d("onPicsChanges: Queueing ${chunk.size} app(s) for PICS")
                            appPicsChannel.send(chunk)
                        }
                }

                // Process any package changes
                launch {
                    val pkgsWithChanges = changesSince.packageChanges.values
                        .filter { changeData ->
                            // only queue PICS requests for pkgs existing in the db that have changed
                            val pkg = licenseDao.findLicense(changeData.id) ?: return@filter false
                            changeData.changeNumber != pkg.lastChangeNumber
                        }

                    if (pkgsWithChanges.isNotEmpty()) {
                        val pkgsForAccessTokens = pkgsWithChanges.filter { it.isNeedsToken }.map { it.id }

                        val accessTokens = _steamApps?.picsGetAccessTokens(emptyList(), pkgsForAccessTokens)
                            ?.await()?.packageTokens ?: emptyMap()

                        ensureActive()

                        pkgsWithChanges
                            .map { PICSRequest(it.id, accessTokens[it.id] ?: 0) }
                            .chunked(MAX_PICS_BUFFER)
                            .forEach { chunk ->
                                Timber.d("onPicsChanges: Queueing ${chunk.size} package(s) for PICS")
                                packagePicsChannel.send(chunk)
                            }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NullPointerException) {
                picsConsecutiveFailures++
                Timber.w("No lastPICSChangeNumber, skipping")
            } catch (e: AsyncJobFailedException) {
                picsConsecutiveFailures++
                Timber.w("AsyncJobFailedException, skipping")
            } catch (e: Exception) {
                picsConsecutiveFailures++
                Timber.w(e, "PICS changes check failed")
            }
        }
    }

    /**
     * A buffered flow to parse so many PICS requests in a given moment.
     */
    private fun continuousPICSGetProductInfo(): Job = scope.launch {
        // Launch both coroutines within this parent job
        launch {
            appPicsChannel.receiveAsFlow()
                .filter { it.isNotEmpty() }
                .buffer(capacity = MAX_PICS_BUFFER, onBufferOverflow = BufferOverflow.SUSPEND)
                .collect { appRequests ->
                    Timber.d("Processing ${appRequests.size} app PICS requests")

                    ensureActive()
                    if (!isLoggedIn) return@collect
                    val steamApps = instance?._steamApps ?: return@collect

                    val callback = steamApps.picsGetProductInfo(
                        apps = appRequests,
                        packages = emptyList(),
                    ).await()

                    callback.results.forEachIndexed { index, picsCallback ->
                        Timber.d(
                            "onPicsProduct: ${index + 1} of ${callback.results.size}" +
                                    "\n\tReceived PICS result of ${picsCallback.apps.size} app(s)." +
                                    "\n\tReceived PICS result of ${picsCallback.packages.size} package(s).",
                        )

                        ensureActive()
                        val steamAppsMap = picsCallback.apps.values.mapNotNull { app ->
                            val appFromDb = appDao.findApp(app.id)
                            val packageId = appFromDb?.packageId ?: INVALID_PKG_ID
                            val packageFromDb = if (packageId != INVALID_PKG_ID) licenseDao.findLicense(packageId) else null
                            val ownerAccountId = packageFromDb?.ownerAccountId ?: emptyList()

                            // Apps with -1 for the ownerAccountId should be added.
                            //  This can help with friend game names.

                            // TODO maybe apps with -1 for the ownerAccountId can be stripped with necessities and name.

                            if (app.changeNumber != appFromDb?.lastChangeNumber) {
                                // Preserve custom install path if it exists (full absolute path)
                                val existingInstallDir = appFromDb?.installDir.orEmpty()
                                val preserveInstallDir = existingInstallDir.isNotEmpty() &&
                                    (existingInstallDir.startsWith("/") || existingInstallDir.contains(File.separator))

                                app.keyValues.generateSteamApp().copy(
                                    packageId = packageId,
                                    ownerAccountId = ownerAccountId,
                                    receivedPICS = true,
                                    lastChangeNumber = app.changeNumber,
                                    licenseFlags = packageFromDb?.licenseFlags ?: EnumSet.noneOf(ELicenseFlags::class.java),
                                    installDir = if (preserveInstallDir) existingInstallDir else app.keyValues.generateSteamApp().installDir,
                                )
                            } else {
                                null
                            }
                        }

                        if (steamAppsMap.isNotEmpty()) {
                            Timber.i("Inserting ${steamAppsMap.size} PICS apps to database")
                            db.withTransaction {
                                appDao.insertAll(steamAppsMap)
                            }
                        }
                    }
                }
        }

        launch {
            packagePicsChannel.receiveAsFlow()
                .filter { it.isNotEmpty() }
                .buffer(capacity = MAX_PICS_BUFFER, onBufferOverflow = BufferOverflow.SUSPEND)
                .collect { packageRequests ->
                    Timber.d("Processing ${packageRequests.size} package PICS requests")

                    ensureActive()
                    if (!isLoggedIn) return@collect
                    val steamApps = instance?._steamApps ?: return@collect

                    val callback = steamApps.picsGetProductInfo(
                        apps = emptyList(),
                        packages = packageRequests,
                    ).await()

                    callback.results.forEach { picsCallback ->
                        // Don't race the queue.
                        if (!isLoggedIn) return@collect
                        val queue = Collections.synchronizedList(mutableListOf<Int>())

                        db.withTransaction {
                            picsCallback.packages.values.forEach { pkg ->
                                val appIds = pkg.keyValues["appids"].children.map { it.asInteger() }
                                licenseDao.updateApps(pkg.id, appIds)

                                val depotIds = pkg.keyValues["depotids"].children.map { it.asInteger() }
                                licenseDao.updateDepots(pkg.id, depotIds)

                                // Insert a stub row (or update) of SteamApps to the database.
                                appIds.forEach { appid ->
                                    val steamApp = appDao.findApp(appid)?.copy(packageId = pkg.id)
                                    if (steamApp != null) {
                                        appDao.update(steamApp)
                                    } else {
                                        val stubSteamApp = SteamApp(id = appid, packageId = pkg.id)
                                        appDao.insert(stubSteamApp)
                                    }
                                }

                                queue.addAll(appIds)
                            }
                        }

                        try {
                            // TODO: This could be an issue. (Stalling)
                            steamApps.picsGetAccessTokens(
                                appIds = queue,
                                packageIds = emptyList(),
                            ).await()
                                .appTokens
                                .forEach { (key, value) ->
                                    appTokens[key] = value
                                }

                            // Get PICS information with the app ids.
                            queue
                                .map { PICSRequest(id = it, accessToken = appTokens[it] ?: 0L) }
                                .chunked(MAX_PICS_BUFFER)
                                .forEach { chunk ->
                                    Timber.d("bufferedPICSGetProductInfo: Queueing ${chunk.size} for PICS")
                                    appPicsChannel.send(chunk)
                                }
                        } catch (e: AsyncJobFailedException) {
                            Timber.w("Could not get PICS product info $e")
                        }
                    }
                }
        }
    }

    /**
     * Get encrypted app ticket for an app, with 30-minute caching.
     * Returns the serialized protobuf bytes, or null if unavailable.
     */
    suspend fun getEncryptedAppTicket(appId: Int): ByteArray? {
        return try {
            // Check database for existing ticket less than 30 minutes old
            val cachedTicket = encryptedAppTicketDao.getByAppId(appId)
            val now = System.currentTimeMillis()
            val thirtyMinutes = 30 * 60 * 1000L

            if (cachedTicket != null && (now - cachedTicket.timestamp) < thirtyMinutes) {
                Timber.d("Using cached encrypted app ticket protobuf for app $appId")
                return cachedTicket.encryptedTicket
            }

            // Request new ticket from Steam
            val steamApps = instance?._steamApps ?: null
            val response = try {
                withTimeout(5_000) {
                    steamApps?.requestEncryptedAppTicket(appId)?.await()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to request encrypted app ticket for app $appId")
                return null
            }

            if (response?.result != EResult.OK || response.encryptedAppTicket == null) {
                Timber.w("Failed to get encrypted app ticket for app $appId: ${response?.result}")
                return null
            }

            // Extract all fields from the protobuf message
            val ticketProto = response.encryptedAppTicket
            val ticket = EncryptedAppTicket(
                appId = appId,
                result = response.result.code(),
                ticketVersionNo = ticketProto!!.ticketVersionNo.toInt(),
                crcEncryptedTicket = ticketProto.crcEncryptedticket.toInt(),
                cbEncryptedUserData = ticketProto.cbEncrypteduserdata.toInt(),
                cbEncryptedAppOwnershipTicket = ticketProto.cbEncryptedAppownershipticket.toInt(),
                encryptedTicket = ticketProto.toByteArray(),
                timestamp = now,
            )

            // Store in database
            encryptedAppTicketDao.insert(ticket)
            Timber.d("Stored new encrypted app ticket protobuf for app $appId")

            ticket.encryptedTicket
        } catch (e: Exception) {
            Timber.e(e, "Error getting encrypted app ticket for app $appId")
            null
        }
    }

    /**
     * Get encrypted app ticket as base64 encoded string, with 30-minute caching.
     * Returns the base64 encoded ticket, or null if unavailable.
     */
    suspend fun getEncryptedAppTicketBase64(appId: Int): String? {
        val ticket = getEncryptedAppTicket(appId) ?: return null
        return Base64.encodeToString(ticket, Base64.NO_WRAP)
    }
}
