package com.winlator.cmod.steam

import android.content.Context
import android.util.Log
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.TarCompressorUtils
import com.winlator.cmod.xenvironment.ImageFs
import java.io.File
import java.io.FileOutputStream

object SteamClientManager {
    private const val TAG = "SteamClientManager"

    @JvmStatic
    fun isColdClientInstalled(context: Context): Boolean {
        val imageFs = ImageFs.find(context)
        val loaderExe = File(imageFs.rootDir, "${ImageFs.WINEPREFIX}/drive_c/Program Files (x86)/Steam/steamclient_loader_x64.exe")
        val extraDll = File(imageFs.rootDir, "${ImageFs.WINEPREFIX}/drive_c/Program Files (x86)/Steam/extra_dlls/steamclient_extra_x64.dll")
        return loaderExe.exists() && loaderExe.length() > 0 && extraDll.exists() && extraDll.length() > 0
    }

    @JvmStatic
    fun ensureColdClientSupportReady(context: Context): Boolean {
        if (isColdClientInstalled(context)) return true

        val imageFs = ImageFs.find(context)
        val archiveFile = File(context.filesDir, "experimental-drm.tzst")
        if (!archiveFile.exists()) {
            try {
                context.assets.open("experimental-drm.tzst").use { input ->
                    FileOutputStream(archiveFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied bundled experimental-drm.tzst to filesDir")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy bundled experimental-drm.tzst", e)
                return false
            }
        }

        return try {
            TarCompressorUtils.extract(
                TarCompressorUtils.Type.ZSTD,
                archiveFile,
                imageFs.rootDir,
                null,
            )
            val steamDir = File(imageFs.rootDir, "${ImageFs.WINEPREFIX}/drive_c/Program Files (x86)/Steam")
            FileUtils.chmod(steamDir, 0x1ED)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract ColdClient support archive", e)
            false
        }
    }
}
