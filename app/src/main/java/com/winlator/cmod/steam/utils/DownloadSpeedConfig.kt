package com.winlator.cmod.steam.utils

// Лимиты потоков закачки/распаковки по ядрам. Эталон: GameNative DownloadSpeedConfig.
// Тиры 8/16/24/32 — от настройки PrefManager.downloadSpeed.
// Капы занижены под телефон: decompress-поток ест ~8MB ThreadLocal,
// диск/USB-OTG не вывозят 20 параллельных — скорость прыгает в 0 и watchdog рвет сессию.
class DownloadSpeedConfig {
    private data class Limits(
        val maxDownloads: Int,
        val maxDecompress: Int,
    )

    val cpuCores: Int
        get() = Runtime.getRuntime().availableProcessors()

    private val limits: Limits
        get() = when (PrefManager.downloadSpeed) {
            8 -> Limits(
                maxDownloads = (cpuCores * 0.75).toInt().coerceIn(3, 6),
                maxDecompress = (cpuCores * 0.25).toInt().coerceIn(1, 2),
            )
            16 -> Limits(
                maxDownloads = (cpuCores * 1.0).toInt().coerceIn(4, 8),
                maxDecompress = (cpuCores * 0.33).toInt().coerceIn(2, 3),
            )
            24 -> Limits(
                maxDownloads = (cpuCores * 1.25).toInt().coerceIn(6, 10),
                maxDecompress = (cpuCores * 0.4).toInt().coerceIn(2, 4),
            )
            32 -> Limits(
                maxDownloads = (cpuCores * 1.5).toInt().coerceIn(8, 12),
                maxDecompress = (cpuCores * 0.5).toInt().coerceIn(3, 4),
            )
            else -> Limits(
                maxDownloads = (cpuCores / 2).coerceIn(3, 12),
                maxDecompress = (cpuCores / 2).coerceIn(1, 3),
            )
        }

    val maxDownloads: Int
        get() = limits.maxDownloads

    val maxDecompress: Int
        get() = limits.maxDecompress
}
