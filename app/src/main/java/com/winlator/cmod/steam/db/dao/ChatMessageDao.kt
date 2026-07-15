package com.winlator.cmod.steam.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.winlator.cmod.steam.data.ChatMessageEntity

@Dao
interface ChatMessageDao {

    @Insert
    suspend fun insert(message: ChatMessageEntity)

    @Insert
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Query("SELECT * FROM chat_message WHERE friend_steam_id64 = :friendSteamId64 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getMessages(friendSteamId64: Long, limit: Int = 200): List<ChatMessageEntity>

    @Query("DELETE FROM chat_message WHERE friend_steam_id64 = :friendSteamId64 AND id NOT IN (SELECT id FROM chat_message WHERE friend_steam_id64 = :friendSteamId64 ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun deleteOldMessages(friendSteamId64: Long, keepCount: Int = 200)

    @Query("DELETE FROM chat_message WHERE friend_steam_id64 = :friendSteamId64")
    suspend fun deleteAllForFriend(friendSteamId64: Long)
}
