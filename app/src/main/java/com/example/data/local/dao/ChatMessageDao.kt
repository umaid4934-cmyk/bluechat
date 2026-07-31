package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE chatAddress = :chatAddress ORDER BY timestamp ASC")
    fun getMessagesForChat(chatAddress: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE isStarred = 1 ORDER BY timestamp DESC")
    fun getStarredMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE (messageText LIKE '%' || :query || '%' OR fileName LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchMessages(query: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("DELETE FROM chat_messages WHERE chatAddress = :chatAddress")
    suspend fun clearChatMessages(chatAddress: String)

    @Query("UPDATE chat_messages SET isStarred = :isStarred WHERE id = :messageId")
    suspend fun setStarred(messageId: String, isStarred: Boolean)

    @Query("UPDATE chat_messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("UPDATE chat_messages SET transferProgress = :progress, status = :status WHERE id = :messageId")
    suspend fun updateTransferProgress(messageId: String, progress: Float, status: String)

    @Query("SELECT SUM(fileSize) FROM chat_messages WHERE fileUri IS NOT NULL")
    fun getTotalStorageUsed(): Flow<Long?>
}
