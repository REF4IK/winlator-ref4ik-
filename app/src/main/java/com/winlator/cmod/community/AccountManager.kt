package com.winlator.cmod.community

import android.content.Context
import android.os.Environment
import android.util.Base64
import android.util.Log
import com.winlator.cmod.BuildConfig
import com.winlator.cmod.core.DohOkHttp
import com.winlator.cmod.core.HttpUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

/**
 * Аккаунты сообщества: регистрация/логин/сброс пароля/аватар.
 * Аккаунт НУЖЕН только чтобы комментировать и шарить конфиги под своим именем
 * + восстанавливать свои загрузки на новом устройстве. Без логина всё
 * анонимное работает как раньше.
 *
 * Сеть — блокирующие вызовы к своему воркеру (только с фонового потока),
 * любая ошибка = AccountResult.Error, исключений наружу нет. Пароль летит
 * только по HTTPS, локально хранится лишь сессия + recovery key.
 */
object AccountManager {

    private const val TAG = "CommunityConfigs"
    private const val PREFS = "cmod_account"
    private const val BACKUP = "my-account.json"

    val BASE: String = BuildConfig.CLOUDFLARE_WORKER_URL

    sealed class AccountResult<out T> {
        data class Success<T>(val data: T) : AccountResult<T>()
        data class Error(val code: String, val httpCode: Int = -1) : AccountResult<Nothing>()
    }

    data class CreateData(
        val userId: String,
        val username: String,
        val session: String,
        val recoveryKey: String,
        val admin: Boolean = false,
    )

    data class LoginData(
        val userId: String,
        val username: String,
        val session: String,
        val avatarUrl: String?,
        val uploads: List<AccountUpload>,
        val admin: Boolean = false,
    )

    data class AccountUpload(
        val sha: String,
        val game: String,
        val filename: String,
        val token: String,
        val ts: Long,
        val kind: String = "config",
    )

    data class ResetData(val session: String)

    data class Account(
        val userId: String,
        val username: String,
        val session: String,
        val avatarUrl: String?,
        val avatarVersion: Long = 0L,
        val admin: Boolean = false,
    ) {
        val displayAvatarUrl: String?
            get() = avatarUrl?.let { url ->
                if (avatarVersion <= 0L) url
                else url + (if (url.contains("?")) "&" else "?") + "t=" + avatarVersion
            }
    }

    data class RecoveryBackup(
        val username: String,
        val userId: String,
        val recoveryKey: String,
    )

    /** Полный URL аватара: воркер отдаёт относительный путь. */
    fun absUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val u = url.trim()
        return if (u.startsWith("http")) u else BASE + u
    }

    fun createAccount(context: Context, username: String, password: String): AccountResult<CreateData> {
        val body = JSONObject().put("username", username).put("password", password).toString()
        val resp = post("$BASE/api/account/create", body)
        val json = parseOk(resp) ?: return errorFrom(resp)
        val data = CreateData(
            userId = json.optString("user_id", "").trim(),
            username = json.optString("username", username).trim(),
            session = json.optString("session", "").trim(),
            recoveryKey = json.optString("recovery_key", "").trim(),
            admin = json.optBoolean("admin", false),
        )
        if (data.session.isBlank()) return AccountResult.Error("network")
        saveNewAccount(context, data.userId, data.username, data.session, data.recoveryKey, data.admin)
        return AccountResult.Success(data)
    }

    fun login(context: Context, username: String, password: String): AccountResult<LoginData> {
        val body = JSONObject().put("username", username).put("password", password).toString()
        val resp = post("$BASE/api/account/login", body)
        val json = parseOk(resp) ?: return errorFrom(resp)
        val avatar = absUrl(json.optString("avatarUrl", "").trim().ifBlank { null })
        val uploads = ArrayList<AccountUpload>()
        json.optJSONArray("uploads")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val sha = obj.optString("sha", "").trim()
                if (sha.isEmpty()) continue
                uploads.add(
                    AccountUpload(
                        sha = sha,
                        game = obj.optString("game", "").trim(),
                        filename = obj.optString("filename", "").trim(),
                        token = obj.optString("token", "").trim(),
                        ts = obj.optLong("ts", 0L),
                        kind = obj.optString("kind", "config").trim().ifBlank { "config" },
                    )
                )
            }
        }
        val data = LoginData(
            userId = json.optString("user_id", "").trim(),
            username = json.optString("username", username).trim(),
            session = json.optString("session", "").trim(),
            avatarUrl = avatar,
            uploads = uploads,
            admin = json.optBoolean("admin", false),
        )
        if (data.session.isBlank()) return AccountResult.Error("network")
        saveLogin(context, data.userId, data.username, data.session, data.avatarUrl, data.admin)
        saveUploadsCache(context, uploads)
        if (data.avatarUrl != null) bumpAvatarVersion(context)
        return AccountResult.Success(data)
    }

    fun resetPassword(
        context: Context,
        username: String,
        recoveryKey: String,
        newPassword: String,
    ): AccountResult<ResetData> {
        val body = JSONObject()
            .put("username", username)
            .put("recovery_key", recoveryKey)
            .put("new_password", newPassword)
            .toString()
        val resp = post("$BASE/api/account/reset", body)
        val json = parseOk(resp) ?: return errorFrom(resp)
        val session = json.optString("session", "").trim()
        if (session.isBlank()) return AccountResult.Error("network")
        val backupUserId = recoveryBackup(context)?.userId.orEmpty()
        val keepAdmin = current(context)?.takeIf { it.username.equals(username, ignoreCase = true) }?.admin == true
        saveLogin(context, backupUserId, username, session, null, keepAdmin)
        return AccountResult.Success(ResetData(session))
    }

    fun uploadAvatar(context: Context, bytes: ByteArray, contentType: String): AccountResult<String> {
        val session = session(context) ?: return AccountResult.Error("not_signed_in")
        val body = JSONObject()
            .put("session", session)
            .put("image", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .put("content_type", contentType)
            .toString()
        val resp = post("$BASE/api/account/avatar", body)
        val json = parseOk(resp) ?: return errorFrom(resp)
        val url = absUrl(json.optString("avatarUrl", "").trim())
        if (url.isNullOrBlank()) return AccountResult.Error("network")
        bumpAvatarVersion(context)
        current(context)?.let { saveLogin(context, it.userId, it.username, it.session, url, it.admin) }
        return AccountResult.Success(url)
    }

    fun current(context: Context): Account? {
        val sp = prefs(context)
        if (!sp.getBoolean(K_LOGGED_IN, false)) return null
        val session = sp.getString(K_SESSION, null)?.trim().orEmpty()
        val username = sp.getString(K_USERNAME, null)?.trim().orEmpty()
        if (session.isBlank() || username.isBlank()) return null
        return Account(
            userId = sp.getString(K_USER_ID, null)?.trim().orEmpty(),
            username = username,
            session = session,
            avatarUrl = sp.getString(K_AVATAR, null)?.trim()?.ifBlank { null },
            avatarVersion = sp.getLong(K_AVATAR_VERSION, 0L),
            admin = sp.getBoolean(K_ADMIN, false),
        )
    }

    fun isLoggedIn(context: Context): Boolean = current(context) != null

    fun isAdmin(context: Context): Boolean = current(context)?.admin == true

    fun session(context: Context): String? = current(context)?.session

    fun saveLogin(context: Context, userId: String, username: String, session: String, avatarUrl: String?, admin: Boolean = false) {
        prefs(context).edit()
            .putBoolean(K_LOGGED_IN, true)
            .putString(K_USER_ID, userId)
            .putString(K_USERNAME, username)
            .putString(K_SESSION, session)
            .putString(K_AVATAR, avatarUrl)
            .putBoolean(K_ADMIN, admin)
            .apply()
    }

    fun saveNewAccount(context: Context, userId: String, username: String, session: String, recoveryKey: String, admin: Boolean = false) {
        saveLogin(context, userId, username, session, null, admin)
        writeBackup(RecoveryBackup(username = username, userId = userId, recoveryKey = recoveryKey))
    }

    fun logout(context: Context) {
        prefs(context).edit()
            .remove(K_LOGGED_IN)
            .remove(K_USER_ID)
            .remove(K_USERNAME)
            .remove(K_SESSION)
            .remove(K_AVATAR)
            .remove(K_AVATAR_VERSION)
            .remove(K_UPLOADS)
            .remove(K_ADMIN)
            .apply()
    }

    fun avatarVersion(context: Context): Long = prefs(context).getLong(K_AVATAR_VERSION, 0L)

    fun bumpAvatarVersion(context: Context): Long {
        val v = System.currentTimeMillis()
        prefs(context).edit().putLong(K_AVATAR_VERSION, v).apply()
        return v
    }

    fun recoveryKey(context: Context): String? = recoveryBackup(context)?.recoveryKey

    fun recoveryBackup(context: Context): RecoveryBackup? =
        try {
            val file = backupFile()
            if (file == null || !file.exists()) null
            else {
                val o = JSONObject(file.readText())
                val key = o.optString("recovery_key", "").trim()
                val user = o.optString("username", "").trim()
                if (key.isBlank() || user.isBlank()) null
                else RecoveryBackup(
                    username = user,
                    userId = o.optString("user_id", "").trim(),
                    recoveryKey = key,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Account backup read failed", e)
            null
        }

    /** Кеш реестра своих загрузок с последнего логина (для экрана профиля). */
    fun cachedUploads(context: Context): List<AccountUpload> {
        return try {
            val raw = prefs(context).getString(K_UPLOADS, null) ?: return emptyList()
            val arr = org.json.JSONArray(raw)
            val out = ArrayList<AccountUpload>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val sha = o.optString("sha", "").trim()
                if (sha.isEmpty()) continue
                out.add(
                    AccountUpload(
                        sha = sha,
                        game = o.optString("game", "").trim(),
                        filename = o.optString("filename", "").trim(),
                        token = o.optString("token", "").trim(),
                        ts = o.optLong("ts", 0L),
                        kind = o.optString("kind", "config").trim().ifBlank { "config" },
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun removeCachedUpload(context: Context, sha: String) {
        saveUploadsCache(context, cachedUploads(context).filterNot { it.sha == sha })
    }

    private fun saveUploadsCache(context: Context, uploads: List<AccountUpload>) {
        try {
            val arr = org.json.JSONArray()
            for (u in uploads) {
                arr.put(
                    JSONObject()
                        .put("sha", u.sha)
                        .put("game", u.game)
                        .put("filename", u.filename)
                        .put("token", u.token)
                        .put("ts", u.ts)
                        .put("kind", u.kind)
                )
            }
            prefs(context).edit().putString(K_UPLOADS, arr.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Uploads cache write failed", e)
        }
    }

    private const val K_LOGGED_IN = "loggedIn"
    private const val K_USER_ID = "user_id"
    private const val K_USERNAME = "username"
    private const val K_SESSION = "session"
    private const val K_AVATAR = "avatarUrl"
    private const val K_AVATAR_VERSION = "avatar_version"
    private const val K_UPLOADS = "uploads_cache"
    private const val K_ADMIN = "admin"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun parseOk(resp: HttpUtils.HttpResponse?): JSONObject? {
        if (resp == null || resp.code !in 200..299 || resp.body.isNullOrBlank()) return null
        return try {
            val o = JSONObject(resp.body)
            if (o.optBoolean("success", false)) o else null
        } catch (e: Exception) {
            null
        }
    }

    private fun errorFrom(resp: HttpUtils.HttpResponse?): AccountResult.Error {
        if (resp == null || resp.body.isNullOrBlank()) return AccountResult.Error("network", resp?.code ?: 0)
        return try {
            val code = JSONObject(resp.body).optString("error", "").trim()
            AccountResult.Error(code.ifBlank { "network" }, resp.code)
        } catch (e: Exception) {
            AccountResult.Error("network", resp.code)
        }
    }

    private fun writeBackup(backup: RecoveryBackup) {
        try {
            val file = backupFile() ?: return
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            file.writeText(
                JSONObject()
                    .put("username", backup.username)
                    .put("user_id", backup.userId)
                    .put("recovery_key", backup.recoveryKey)
                    .toString(2)
            )
            file.setReadable(true, false)
        } catch (e: Exception) {
            Log.w(TAG, "Account backup write failed", e)
        }
    }

    private fun backupFile(): File? =
        try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(File(downloads, "winlator-cmod/game-configs"), BACKUP)
        } catch (e: Exception) {
            null
        }

    /**
     * Синхронный POST через DohOkHttp (тот же DoH-клиент, что у всех запросов
     * к воркеру). Вызывать только с фонового потока.
     */
    private fun post(url: String, jsonBody: String): HttpUtils.HttpResponse? {
        return try {
            val reqBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url(url).post(reqBody)
                .header("User-Agent", "Winlator.CMOD").build()
            DohOkHttp.get().newCall(req).execute().use { resp ->
                val body = try { resp.body?.string() } catch (_: Exception) { null }
                HttpUtils.HttpResponse(resp.code, body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Account POST failed: $url", e)
            HttpUtils.HttpResponse(0, null)
        }
    }
}
