package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ChatConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatConversationDao {

    @Query("SELECT * FROM chat_conversations ORDER BY lastTimestamp DESC")
    fun getAllConversations(): Flow<List<ChatConversationEntity>>

    @Query("SELECT * FROM chat_conversations WHERE deviceAddress = :deviceAddress LIMIT 1")
    suspend fun getConversationByAddress(deviceAddress: String): ChatConversationEntity?

    @Query("SELECT * FROM chat_conversations WHERE deviceAddress = :deviceAddress LIMIT 1")
    fun getConversationFlow(deviceAddress: String): Flow<ChatConversationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConversation(conversation: ChatConversationEntity)

    @Query("UPDATE chat_conversations SET unreadCount = 0 WHERE deviceAddress = :deviceAddress")
    suspend fun clearUnreadCount(deviceAddress: String)

    @Query("UPDATE chat_conversations SET isConnected = :isConnected WHERE deviceAddress = :deviceAddress")
    suspend fun updateConnectionStatus(deviceAddress: String, isConnected: Boolean)

    @Query("DELETE FROM chat_conversations WHERE deviceAddress = :deviceAddress")
    suspend fun deleteConversation(deviceAddress: String)
}
