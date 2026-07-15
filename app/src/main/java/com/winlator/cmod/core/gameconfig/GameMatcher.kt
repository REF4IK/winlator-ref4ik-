package com.winlator.cmod.core.gameconfig

import org.json.JSONObject

data class GameCandidate(
    val key: String,
    val name: String,
    val folders: List<String>,
    val configCount: Int,
    val score: Int,
)

class GameMatcher {

    fun search(query: String, index: JSONObject?, limit: Int = 50): List<GameCandidate> {
        if (query.isBlank() || index == null) return emptyList()

        val q = query.lowercase().trim()
        if (q.length < 2) return emptyList()

        val results = mutableListOf<GameCandidate>()

        for (key in index.keys()) {
            val entry = index.optJSONObject(key) ?: continue
            val name = entry.optString("name", "").lowercase()
            val foldArr = entry.optJSONArray("folders")
            val folders = mutableListOf<String>()
            if (foldArr != null) {
                for (i in 0 until foldArr.length()) {
                    folders.add(foldArr.optString(i, "").lowercase())
                }
            }
            val count = entry.optInt("config_count", 0)
            val originalName = entry.optString("name", key)

            var score = 0
            if (name == q) score = 100
            else if (name.startsWith(q)) score = 80
            else if (name.contains(q)) score = 60
            else if (folders.any { it.contains(q) }) score = 40
            else if (folders.any { it.startsWith(q) }) score = 30
            else {
                val tokens = q.split(Regex("[\\s_]+"))
                val nameTokens = name.split(Regex("[\\s_]+"))
                val overlap = tokens.count { t -> nameTokens.any { nt -> nt.startsWith(t) || nt.contains(t) } }
                if (overlap > 0) score = 20 + overlap * 5
            }

            if (score > 0) {
                results.add(GameCandidate(key, originalName, folders, count, score))
            }
        }

        results.sortByDescending { it.score }
        return results.take(limit)
    }

    fun matchShortcut(shortcutName: String, index: JSONObject?): GameCandidate? {
        if (index == null) return null

        val name = shortcutName.lowercase().trim()
        val cleanName = name.replace(Regex("[^a-zA-Z0-9]"), "")

        var best: GameCandidate? = null
        var bestScore = 0

        for (key in index.keys()) {
            val entry = index.optJSONObject(key) ?: continue
            val gameName = entry.optString("name", "").lowercase()
            val foldArr = entry.optJSONArray("folders")
            val folders = mutableListOf<String>()
            if (foldArr != null) {
                for (i in 0 until foldArr.length()) {
                    folders.add(foldArr.optString(i, "").lowercase())
                }
            }
            val count = entry.optInt("config_count", 0)

            var score = 0
            val cleanGameName = gameName.replace(Regex("[^a-zA-Z0-9]"), "")

            if (gameName == name || cleanGameName == cleanName) score = 100
            else if (gameName.contains(name) || name.contains(gameName)) score = 80
            else if (folders.any { it == name || it.contains(name) || name.contains(it) }) score = 60
            else if (folders.any { cleanName.contains(it.replace(Regex("[^a-zA-Z0-9]"), "")) }) score = 40
            else if (gameName.startsWith(name.substring(0, minOf(4, name.length)))) score = 30

            if (score > bestScore) {
                bestScore = score
                best = GameCandidate(key, entry.optString("name", ""), folders, count, score)
            }
        }

        return best
    }
}
