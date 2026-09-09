package com.winlator.cmod.community

import android.content.Context
import android.util.Log
import com.winlator.cmod.core.DohOkHttp
import com.winlator.cmod.core.HttpUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Модерация для админа. Все вызовы блокирующие — только с фонового потока.
 * Сервер проверяет админа по сессии, клиентский флаг ни на что не влияет.
 */
object AdminManager {

    private const val TAG = "CommunityConfigs"

    data class Votes(val up: Int, val down: Int)

    /** Удалить чужой конфиг/бандл. kind: "bundle" или "config". Нужны game+filename для config. */
    fun deleteConfig(context: Context, sha: String, kind: String, game: String, filename: String): Boolean {
        val session = AccountManager.session(context) ?: return false
        val body = JSONObject()
            .put("session", session)
            .put("sha", sha)
            .put("kind", kind)
            .put("game", game)
            .put("filename", filename)
            .toString()
        val resp = post("${AccountManager.BASE}/api/admin/delete", body) ?: return false
        return try {
            resp.code in 200..299 && JSONObject(resp.body ?: "").optBoolean("success", false)
        } catch (e: Exception) {
            false
        }
    }

    /** Удалить коммент по индексу в треде. Возвращает новое число комментов или null. */
    fun deleteComment(context: Context, sha: String, index: Int): Int? {
        val session = AccountManager.session(context) ?: return null
        val body = JSONObject()
            .put("session", session)
            .put("sha", sha)
            .put("index", index)
            .toString()
        val resp = post("${AccountManager.BASE}/api/admin/comment/delete", body) ?: return null
        return try {
            val o = JSONObject(resp.body ?: "")
            if (resp.code in 200..299 && o.optBoolean("success", false)) o.optInt("count", -1).takeIf { it >= 0 } else null
        } catch (e: Exception) {
            null
        }
    }

    /** Выставить голоса (0/0 = сброс). */
    fun setVotes(context: Context, sha: String, up: Int, down: Int): Votes? {
        val session = AccountManager.session(context) ?: return null
        val body = JSONObject()
            .put("session", session)
            .put("sha", sha)
            .put("up", up)
            .put("down", down)
            .toString()
        val resp = post("${AccountManager.BASE}/api/admin/votes", body) ?: return null
        return try {
            val o = JSONObject(resp.body ?: "")
            if (resp.code in 200..299 && o.optBoolean("success", false)) {
                Votes(o.optInt("votes_up", up), o.optInt("votes_down", down))
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /** Правка описания конфига. */
    fun setDescription(context: Context, sha: String, kind: String, game: String, filename: String, text: String): Boolean {
        val session = AccountManager.session(context) ?: return false
        val body = JSONObject()
            .put("session", session)
            .put("sha", sha)
            .put("kind", kind)
            .put("game", game)
            .put("filename", filename)
            .put("text", text)
            .toString()
        val resp = post("${AccountManager.BASE}/api/admin/desc", body) ?: return false
        return try {
            resp.code in 200..299 && JSONObject(resp.body ?: "").optBoolean("success", false)
        } catch (e: Exception) {
            false
        }
    }

    /** Бан / разбан по нику. Возвращает ошибку сервера или null при успехе. */
    fun setBan(context: Context, username: String, ban: Boolean): String? {        val session = AccountManager.session(context) ?: return "not_signed_in"
        val body = JSONObject()
            .put("session", session)
            .put("username", username)
            .put("ban", ban)
            .toString()
        val resp = post("${AccountManager.BASE}/api/admin/ban", body) ?: return "network"
        return try {
            val o = JSONObject(resp.body ?: "")
            if (resp.code in 200..299 && o.optBoolean("success", false)) null
            else o.optString("error", "network").ifBlank { "network" }
        } catch (e: Exception) {
            "network"
        }
    }

    data class AdminUser(
        val username: String,
        val userId: String,
        val num: Long,
        val created: Long,
        val admin: Boolean,
        val banned: Boolean,
    ) {
        val displayNum: String
            get() = if (num < 0) "—" else String.format(java.util.Locale.US, "%07d", num)
    }

    /** Список всех аккаунтов (только админ). */
    fun users(context: Context): List<AdminUser>? {
        val session = AccountManager.session(context) ?: return null
        val body = JSONObject().put("session", session).toString()
        val resp = post("${AccountManager.BASE}/api/admin/users", body) ?: return null
        return try {
            val o = JSONObject(resp.body ?: "")
            if (resp.code !in 200..299 || !o.optBoolean("success", false)) return null
            val arr = o.optJSONArray("users") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val u = arr.optJSONObject(i) ?: return@mapNotNull null
                AdminUser(
                    username = u.optString("username", ""),
                    userId = u.optString("user_id", ""),
                    num = u.optLong("num", -1),
                    created = u.optLong("created", 0L),
                    admin = u.optBoolean("admin", false),
                    banned = u.optBoolean("banned", false),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Удалить аккаунт + его сессии (конфиги/комменты остаются). */
    fun userDelete(context: Context, username: String): Boolean {
        val session = AccountManager.session(context) ?: return false
        val body = JSONObject().put("session", session).put("username", username).toString()
        val resp = post("${AccountManager.BASE}/api/admin/user/delete", body) ?: return false
        return try {
            resp.code in 200..299 && JSONObject(resp.body ?: "").optBoolean("success", false)
        } catch (e: Exception) {
            false
        }
    }

    /** Правка num/created. Возвращает код ошибки или null при успехе. */
    fun setProfile(context: Context, username: String, num: Long?, createdSec: Long?): String? {
        val session = AccountManager.session(context) ?: return "not_signed_in"
        val body = JSONObject().put("session", session).put("username", username)
        if (num != null) body.put("num", num)
        if (createdSec != null) body.put("created", createdSec)
        val resp = post("${AccountManager.BASE}/api/admin/profile", body.toString()) ?: return "network"
        return try {
            val o = JSONObject(resp.body ?: "")
            if (resp.code in 200..299 && o.optBoolean("success", false)) null
            else o.optString("error", "network").ifBlank { "network" }
        } catch (e: Exception) {
            "network"
        }
    }

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
            Log.w(TAG, "Admin POST failed: $url", e)
            HttpUtils.HttpResponse(0, null)
        }
    }
}
