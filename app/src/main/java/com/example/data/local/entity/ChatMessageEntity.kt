package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val chatAddress: String, // Bluetooth MAC address of peer
    val senderAddress: String, // "MY_SELF" or peer Bluetooth MAC address
    val senderName: String,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "DELIVERED", // "PENDING", "DELIVERED", "FAILED"
    val isStarred: Boolean = false,
    val fileUri: String? = null,
    val fileName: String? = null,
    val fileType: String? = null, // "IMAGE", "VIDEO", "AUDIO", "DOCUMENT", "APK", "ZIP", "OTHER"
    val fileSize: Long = 0L,
    val transferProgress: Float = 1.0f // 0.0 to 1.0
)
