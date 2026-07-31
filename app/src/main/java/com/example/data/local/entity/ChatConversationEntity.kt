package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_conversations")
data class ChatConversationEntity(
    @PrimaryKey
    val deviceAddress: String, // Bluetooth MAC Address or unique ID
    val deviceName: String,
    val profilePicUri: String? = null,
    val lastMessage: String = "",
    val lastTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val isConnected: Boolean = false
)
