package com.winlator.cmod.steam.data

import com.winlator.cmod.steam.enums.OS
import com.winlator.cmod.steam.enums.OSArch
import com.winlator.cmod.steam.db.serializers.OsEnumSetSerializer
import java.util.EnumSet
import kotlinx.serialization.Serializable

@Serializable
data class LaunchInfo(
    val executable: String,
    val workingDir: String,
    val description: String,
    val type: String,
    @Serializable(with = OsEnumSetSerializer::class)
    val configOS: java.util.EnumSet<OS>,
    val configArch: OSArch,
)
