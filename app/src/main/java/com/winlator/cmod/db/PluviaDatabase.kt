package com.winlator.cmod.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.winlator.cmod.steam.data.AppInfo
import com.winlator.cmod.steam.data.CachedLicense
import com.winlator.cmod.steam.data.ChangeNumbers
import com.winlator.cmod.steam.data.DownloadingAppInfo
import com.winlator.cmod.steam.data.EncryptedAppTicket
import com.winlator.cmod.steam.data.FileChangeLists
import com.winlator.cmod.steam.data.ChatMessageEntity
import com.winlator.cmod.steam.data.SteamApp
import com.winlator.cmod.steam.data.SteamLicense
import com.winlator.cmod.steam.db.converters.AppConverter
import com.winlator.cmod.steam.db.converters.ByteArrayConverter
import com.winlator.cmod.steam.db.converters.FriendConverter
import com.winlator.cmod.steam.db.converters.LicenseConverter
import com.winlator.cmod.steam.db.converters.PathTypeConverter
import com.winlator.cmod.steam.db.converters.UserFileInfoListConverter
import com.winlator.cmod.steam.db.dao.AppInfoDao
import com.winlator.cmod.steam.db.dao.CachedLicenseDao
import com.winlator.cmod.steam.db.dao.ChatMessageDao
import com.winlator.cmod.steam.db.dao.ChangeNumbersDao
import com.winlator.cmod.steam.db.dao.DownloadingAppInfoDao
import com.winlator.cmod.steam.db.dao.EncryptedAppTicketDao
import com.winlator.cmod.steam.db.dao.FileChangeListsDao
import com.winlator.cmod.steam.db.dao.SteamAppDao
import com.winlator.cmod.steam.db.dao.SteamLicenseDao

const val DATABASE_NAME = "pluvia_database"

@Database(
    entities = [
        AppInfo::class,
        CachedLicense::class,
        ChangeNumbers::class,
        ChatMessageEntity::class,
        DownloadingAppInfo::class,
        EncryptedAppTicket::class,
        FileChangeLists::class,
        SteamApp::class,
        SteamLicense::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(
    AppConverter::class,
    ByteArrayConverter::class,
    FriendConverter::class,
    LicenseConverter::class,
    PathTypeConverter::class,
    UserFileInfoListConverter::class,
)
abstract class PluviaDatabase : RoomDatabase() {
    abstract fun steamLicenseDao(): SteamLicenseDao

    abstract fun steamAppDao(): SteamAppDao

    abstract fun appChangeNumbersDao(): ChangeNumbersDao

    abstract fun appFileChangeListsDao(): FileChangeListsDao

    abstract fun appInfoDao(): AppInfoDao

    abstract fun cachedLicenseDao(): CachedLicenseDao

    abstract fun encryptedAppTicketDao(): EncryptedAppTicketDao

    abstract fun downloadingAppInfoDao(): DownloadingAppInfoDao

    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var instance: PluviaDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `chat_message` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `friend_steam_id64` INTEGER NOT NULL, `sender_steam_id64` INTEGER NOT NULL, `text` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `is_incoming` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_steam_app_dlc_for_app_id ON steam_app (dlc_for_app_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_steam_app_package_id ON steam_app (package_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_steam_app_type ON steam_app (type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_message_friend_steam_id64 ON chat_message (friend_steam_id64)")
            }
        }

        fun init(context: android.content.Context): PluviaDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PluviaDatabase::class.java,
                    DATABASE_NAME,
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }

        fun getInstance(context: android.content.Context): PluviaDatabase = init(context)

        fun getInstance(): PluviaDatabase {
            return instance ?: throw IllegalStateException("PluviaDatabase not initialized")
        }
    }
}
