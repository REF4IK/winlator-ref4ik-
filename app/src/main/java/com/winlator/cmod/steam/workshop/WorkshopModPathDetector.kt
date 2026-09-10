package com.winlator.cmod.steam.workshop

import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream

/**
 * Как моды воркшопа подсовывать игре (упрощённый порт эталона GameNative).
 *
 * Standard — игра читает моды через ISteamUGC (gbe_fork steam_settings/mods/).
 * SymlinkIntoDir / CopyIntoDir — игра сканирует свои папки (addons/workshop/
 * content/mods и т.п.), кладём туда каждый айтем с fan-out по [FanOutPolicy].
 */
sealed class WorkshopModPathStrategy {

    enum class FanOutPolicy {
        /** Только targetDirs[0] — самый уверенный каталог. */
        PRIMARY_ONLY,
        /** Во все каталоги из targetDirs. */
        ALL_DIRS,
    }

    data object Standard : WorkshopModPathStrategy()

    data class SymlinkIntoDir(
        val targetDirs: List<File>,
        val fanOut: FanOutPolicy = FanOutPolicy.PRIMARY_ONLY,
    ) : WorkshopModPathStrategy() {
        constructor(targetDir: File) : this(listOf(targetDir), FanOutPolicy.PRIMARY_ONLY)

        val effectiveDirs: List<File> get() = when (fanOut) {
            FanOutPolicy.PRIMARY_ONLY -> listOf(targetDirs.first())
            FanOutPolicy.ALL_DIRS -> targetDirs
        }
    }

    data class CopyIntoDir(
        val targetDirs: List<File>,
        val fanOut: FanOutPolicy = FanOutPolicy.PRIMARY_ONLY,
    ) : WorkshopModPathStrategy() {
        constructor(targetDir: File) : this(listOf(targetDir), FanOutPolicy.PRIMARY_ONLY)

        val effectiveDirs: List<File> get() = when (fanOut) {
            FanOutPolicy.PRIMARY_ONLY -> listOf(targetDirs.first())
            FanOutPolicy.ALL_DIRS -> targetDirs
        }
    }
}

/**
 * Детект папок модов статическим анализом установки игры (без запуска).
 * Эвристики: скан бинарников, поиск известных имён в установке,
 * скан конфигов (.ini/.cfg/.json), workshop/content + addons-пути.
 */
class WorkshopModPathDetector {

    enum class Confidence { LOW, MEDIUM, HIGH }

    data class DetectionResult(
        val strategy: WorkshopModPathStrategy,
        val confidence: Confidence,
        val reason: String,
        val stdSeen: Boolean = false,
    )

    companion object {
        const val TAG = "WorkshopModPathDetector"
        const val MAX_BINARY_SCAN_BYTES = 64L * 1024 * 1024
        const val MIN_STRING_LEN = 5
        const val MAX_CONFIG_BYTES = 1L * 1024 * 1024
        const val INSTALL_SCAN_DEPTH = 2

        val HIGH_CONFIDENCE_NAMES = setOf(
            "mods", "mod", "addons", "addon", "plugins", "plugin",
            "workshop_mods", "usermods", "user_mods",
            "modules", "module", "ugc",
            "resourcepacks", "resource_packs",
        )
        val MEDIUM_CONFIDENCE_NAMES = setOf(
            "levels", "level",
            "scenarios", "scenario", "missions", "mission",
            "workshop", "override", "gamedata",
            "maps", "content",
            "packs", "outfits", "skins", "characters",
            "textures", "sounds", "audio",
        )
        val LOW_CONFIDENCE_NAMES = setOf(
            "custom", "usercontent", "user_content", "community",
            "packages", "package", "downloads", "download", "downloaded",
            "extras", "expansion", "expansions",
            "assets", "data",
        )
        val ALL_MOD_DIR_NAMES = HIGH_CONFIDENCE_NAMES + MEDIUM_CONFIDENCE_NAMES + LOW_CONFIDENCE_NAMES

        val BINARY_STANDARD_SIGNALS = listOf(
            "GetItemInstallInfo", "ISteamUGC", "SteamUGC()",
            "workshop\\content", "workshop/content",
        )
        val CONFIG_EXTENSIONS = setOf("ini", "cfg", "conf", "json", "jsonc", "xml", "txt", "yaml", "yml", "toml")
        val CONFIG_MOD_KEY_FRAGMENTS = listOf(
            "modpath", "mod_path", "modfolder", "mod_folder", "moddir", "mod_dir",
            "moddirectory", "mod_directory",
            "workshoppath", "workshop_path", "addonspath", "addons_path",
            "pluginpath", "plugin_path",
            "contentpath", "content_path",
            "localmods", "local_mods", "usermods", "user_mods",
        )
        val INSTALL_SKIP_DIRS = setOf(
            ".depotdownloader", "steam_settings", ".git", ".svn",
            "_commonredist", "__macosx", "directx",
            "engine", "binaries", "help",
        )
    }

    private data class CandidateDir(val dir: File, val confidence: Confidence, val source: String)

    fun detect(
        gameInstallDir: File,
        winePrefix: File = File(""),
        gameName: String = "",
    ): DetectionResult {
        if (!gameInstallDir.exists() || !gameInstallDir.isDirectory) {
            return DetectionResult(WorkshopModPathStrategy.Standard, Confidence.LOW, "Install directory does not exist")
        }

        val found = LinkedHashMap<String, CandidateDir>()
        fun add(c: CandidateDir) {
            val key = runCatching { c.dir.canonicalPath }.getOrElse { c.dir.absolutePath }
            val existing = found[key]
            if (existing == null || c.confidence > existing.confidence) found[key] = c
        }

        val binaryResult = collectFromBinaries(gameInstallDir)
        binaryResult.candidates.forEach { add(it) }
        collectModsDirectories(gameInstallDir, 0).forEach { add(it) }
        collectFromConfigFiles(gameInstallDir).forEach { add(it) }
        collectWorkshopContentDirs(gameInstallDir).forEach { add(it) }

        if (found.isEmpty()) {
            return if (binaryResult.stdSeen) {
                DetectionResult(
                    WorkshopModPathStrategy.Standard, Confidence.MEDIUM,
                    "Standard Steam Workshop API detected, no local mod dirs found",
                    stdSeen = true,
                )
            } else {
                DetectionResult(WorkshopModPathStrategy.Standard, Confidence.LOW, "No mod path signals found")
            }
        }

        val sorted = found.values.sortedByDescending { it.confidence }
        val reason = sorted.joinToString("; ") { "${it.dir.name}[${it.confidence}](${it.source})" }
        Timber.tag(TAG).i("[WS-DETECT] game='$gameName' candidates=${sorted.size}: $reason")

        // Fan-out только при сильных corroborating-сигналах из разных семейств
        val strongFamilies = sorted
            .filter { it.confidence >= Confidence.MEDIUM }
            .mapNotNull {
                when {
                    it.source.startsWith("binary") -> "binary"
                    it.source.startsWith("config") -> "config"
                    else -> null
                }
            }.toSet()
        val fanOut = if (strongFamilies.size >= 2) {
            WorkshopModPathStrategy.FanOutPolicy.ALL_DIRS
        } else {
            WorkshopModPathStrategy.FanOutPolicy.PRIMARY_ONLY
        }

        return DetectionResult(
            WorkshopModPathStrategy.SymlinkIntoDir(sorted.map { it.dir }, fanOut),
            sorted.first().confidence,
            reason,
            binaryResult.stdSeen,
        )
    }

    // ── Эвристика 1: скан бинарников ──

    private data class BinaryResult(val candidates: List<CandidateDir>, val stdSeen: Boolean)

    private fun collectFromBinaries(installDir: File): BinaryResult {
        val exes = (installDir.listFiles { f ->
            f.isFile && f.extension.lowercase() == "exe" && f.length() > 512 * 1024 &&
                !f.name.lowercase().let { n ->
                    n.contains("unins") || n.contains("crash") || n.contains("report") ||
                        n.contains("setup") || n.contains("install") || n.contains("redist") ||
                        n.contains("vcredist")
                }
        } ?: emptyArray()).sortedByDescending { it.length() }.take(2)
        if (exes.isEmpty()) return BinaryResult(emptyList(), false)

        var stdSeen = false
        val installPaths = mutableListOf<String>()
        exes.forEach { exe ->
            val (std, paths) = scanOneBinary(exe)
            if (std) stdSeen = true
            installPaths += paths
        }

        val results = mutableListOf<CandidateDir>()
        installPaths.distinct().forEach { raw ->
            val resolved = resolveInstallPath(raw, installDir) ?: return@forEach
            if (!resolved.isDirectory) return@forEach
            val conf = when (resolved.name.lowercase()) {
                in HIGH_CONFIDENCE_NAMES -> Confidence.HIGH
                in MEDIUM_CONFIDENCE_NAMES -> Confidence.MEDIUM
                else -> Confidence.LOW
            }
            results.add(CandidateDir(resolved, conf, "binary(install)"))
        }
        return BinaryResult(results, stdSeen)
    }

    private fun scanOneBinary(file: File): Pair<Boolean, List<String>> {
        var stdFound = false
        val paths = mutableListOf<String>()
        val buf = ByteArray(65_536)
        val sb = StringBuilder(512)
        var total = 0L

        fun flush() {
            val s = sb.toString().trimEnd()
            sb.clear()
            if (s.length < MIN_STRING_LEN) return
            val lo = s.lowercase()
            if (!stdFound && BINARY_STANDARD_SIGNALS.any { lo.contains(it.lowercase()) }) stdFound = true
            if ((s.contains('\\') || s.contains('/')) && ALL_MOD_DIR_NAMES.any { m ->
                    lo.contains("\\$m\\") || lo.contains("/$m/") ||
                        lo.endsWith("\\$m") || lo.endsWith("/$m")
                }) paths.add(s)
        }

        try {
            BufferedInputStream(FileInputStream(file), buf.size).use { st ->
                var r: Int
                while (st.read(buf).also { r = it } != -1) {
                    for (i in 0 until r) {
                        val b = buf[i].toInt() and 0xFF
                        if (b in 0x20..0x7E) sb.append(b.toChar()) else flush()
                    }
                    total += r
                    if (total >= MAX_BINARY_SCAN_BYTES) break
                }
                flush()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w("Binary scan error ${file.name}: ${e.message}")
        }
        return stdFound to paths
    }

    private fun resolveInstallPath(raw: String, installDir: File): File? {
        val segs = raw.replace('\\', '/').trimEnd('/').split('/').filter { it.isNotBlank() }
        val idx = segs.indexOfFirst { it.lowercase() in ALL_MOD_DIR_NAMES }
        if (idx < 0) return null
        return File(installDir, segs[idx])
    }

    // ── Эвристика 2: скан установки ──

    private fun collectModsDirectories(dir: File, depth: Int): List<CandidateDir> {
        if (depth > INSTALL_SCAN_DEPTH) return emptyList()
        val results = mutableListOf<CandidateDir>()
        dir.listFiles()?.forEach { child ->
            if (!child.isDirectory) return@forEach
            val lo = child.name.lowercase()
            if (lo in INSTALL_SKIP_DIRS || child.name.startsWith(".")) return@forEach
            if (lo.endsWith("_data") && child.listFiles()?.any {
                    it.name.equals("Managed", ignoreCase = true) ||
                        it.name.equals("Resources", ignoreCase = true)
                } == true) return@forEach
            val populated = (child.listFiles()?.size ?: 0) > 0
            val conf: Confidence? = when {
                lo in HIGH_CONFIDENCE_NAMES -> Confidence.HIGH
                lo in MEDIUM_CONFIDENCE_NAMES && populated -> Confidence.MEDIUM
                lo in MEDIUM_CONFIDENCE_NAMES && depth == 0 -> Confidence.LOW
                else -> null
            }
            if (conf != null) {
                results.add(
                    CandidateDir(
                        child, conf,
                        if (populated) "install-dir(pop,d$depth)" else "install-dir(empty,d$depth)",
                    )
                )
            }
            if (lo !in HIGH_CONFIDENCE_NAMES && lo !in MEDIUM_CONFIDENCE_NAMES && lo !in INSTALL_SKIP_DIRS) {
                results += collectModsDirectories(child, depth + 1)
            }
        }
        return results
    }

    // ── Эвристика 3: конфиги ──

    private fun collectFromConfigFiles(root: File): List<CandidateDir> {
        val candidates = mutableListOf<File>()
        collectConfigCandidates(root, 0, 3, candidates)
        return candidates.flatMap { parseConfigFile(it, root) }
    }

    private fun collectConfigCandidates(dir: File, depth: Int, max: Int, out: MutableList<File>) {
        if (depth > max) return
        dir.listFiles()?.forEach { entry ->
            if (entry.isFile && entry.extension.lowercase() in CONFIG_EXTENSIONS &&
                entry.length() in 1..MAX_CONFIG_BYTES
            ) {
                out.add(entry)
            } else if (entry.isDirectory && depth < max) {
                collectConfigCandidates(entry, depth + 1, max, out)
            }
        }
    }

    private fun parseConfigFile(file: File, scanRoot: File): List<CandidateDir> {
        val text = runCatching { file.readText(Charsets.UTF_8) }
            .recoverCatching { file.readText(Charsets.ISO_8859_1) }
            .getOrNull() ?: return emptyList()
        val results = mutableListOf<CandidateDir>()
        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.startsWith("#") || line.startsWith(";") || line.startsWith("//")) continue
            if (!CONFIG_MOD_KEY_FRAGMENTS.any { line.lowercase().contains(it) }) continue
            val di = line.indexOfFirst { it == '=' || it == ':' }.takeIf { it >= 0 } ?: continue
            val rv = line.substring(di + 1).trim().trim('"', '\'', ',').ifBlank { null } ?: continue
            val cleaned = rv.replace('\\', '/')
            val resolved = File(file.parentFile, cleaned).takeIf { it.isDirectory }
                ?: File(scanRoot, cleaned.substringAfterLast('/')).takeIf { it.isDirectory }
                ?: continue
            results.add(CandidateDir(resolved, Confidence.MEDIUM, "config:${file.name}"))
        }
        return results
    }

    // ── Эвристика 4: workshop/content + addons-пути ──

    private fun collectWorkshopContentDirs(installDir: File): List<CandidateDir> {
        val results = mutableListOf<CandidateDir>()
        installDir.walkTopDown().maxDepth(4).forEach { entry ->
            if (!entry.isDirectory) return@forEach
            val lo = entry.name.lowercase()
            if (lo == "workshop" || lo == "content" || lo == "addons" || lo == "addon") {
                val populated = (entry.listFiles()?.size ?: 0) > 0
                if (populated || entry.name.lowercase() in HIGH_CONFIDENCE_NAMES) {
                    results.add(
                        CandidateDir(
                            entry,
                            if (entry.name.lowercase() in HIGH_CONFIDENCE_NAMES) Confidence.HIGH else Confidence.MEDIUM,
                            "workshop-tree",
                        )
                    )
                }
            }
        }
        return results
    }
}
