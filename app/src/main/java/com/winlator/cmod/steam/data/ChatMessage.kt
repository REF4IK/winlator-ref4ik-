package com.winlator.cmod.steam.data

data class ChatMessage(
    val steamId64: Long,
    val senderSteamId64: Long,
    val text: String,
    val timestamp: Long,
    val isIncoming: Boolean,
)
