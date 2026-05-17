package com.winlator.cmod.steam.db.converters

import androidx.room.TypeConverter
import com.winlator.cmod.steam.enums.PathType

class PathTypeConverter {
    @TypeConverter
    fun fromPathType(pathType: PathType): String = pathType.name

    @TypeConverter
    fun toPathType(value: String): PathType = PathType.from(value)
}
