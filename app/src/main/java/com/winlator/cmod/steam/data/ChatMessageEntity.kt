package com.winlator.cmod.steam.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity("chat_message", indices = [Index("friend_steam_id64")])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo("friend_steam_id64") val friendSteamId64: Long,
    @ColumnInfo("sender_steam_id64") val senderSteamId64: Long,
    @ColumnInfo("text") val text: String,
    @ColumnInfo("timestamp") val timestamp: Long,
    @ColumnInfo("is_incoming") val isIncoming: Boolean,
)
