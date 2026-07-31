package com.example.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.bluetooth.BluetoothConnectionState
import com.example.bluetooth.BluetoothController
import com.example.bluetooth.BluetoothDeviceModel
import com.example.bluetooth.BluetoothTransferPacket
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ChatConversationEntity
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.notification.NotificationHelper
import com.example.util.FileStorageUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.UUID

class ChatRepository(
    private val context: Context,
    val bluetoothController: BluetoothController
) {
    private val db = AppDatabase.getInstance(context)
    private val messageDao = db.chatMessageDao()
    private val conversationDao = db.chatConversationDao()
    private val userProfileDao = db.userProfileDao()
    private val notificationHelper = NotificationHelper(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    // Flow properties
    val userProfile: Flow<UserProfileEntity?> = userProfileDao.getUserProfile()
    val conversations: Flow<List<ChatConversationEntity>> = conversationDao.getAllConversations()
    val starredMessages: Flow<List<ChatMessageEntity>> = messageDao.getStarredMessages()
    val totalStorageUsed: Flow<Long?> = messageDao.getTotalStorageUsed()

    val connectionState: StateFlow<BluetoothConnectionState> = bluetoothController.connectionState
    val scannedDevices: StateFlow<List<BluetoothDeviceModel>> = bluetoothController.scannedDevices
    val pairedDevices: StateFlow<List<BluetoothDeviceModel>> = bluetoothController.pairedDevices

    private val incomingChunksMap = mutableMapOf<String, ByteArrayOutputStreamList>()

    private class ByteArrayOutputStreamList {
        val bytes = java.io.ByteArrayOutputStream()
        var receivedCount = 0
    }

    init {
        listenToIncomingPackets()
        initDefaultUserProfile()
    }

    private fun initDefaultUserProfile() {
        scope.launch {
            val existing = userProfileDao.getUserProfileDirect()
            if (existing == null) {
                userProfileDao.saveUserProfile(
                    UserProfileEntity(
                        id = 1,
                        userName = "User",
                        themeMode = "SYSTEM",
                        isFirstLaunchCompleted = false
                    )
                )
            }
        }
    }

    private fun listenToIncomingPackets() {
        scope.launch {
            bluetoothController.incomingPackets.collect { packet ->
                handlePacket(packet)
            }
        }
    }

    private suspend fun handlePacket(packet: BluetoothTransferPacket) {
        val activeConn = connectionState.value
        val peerAddress = if (activeConn is BluetoothConnectionState.Connected) activeConn.address else "00:11:22:33:44:55"
        val userProf = userProfileDao.getUserProfileDirect()

        when (packet) {
            is BluetoothTransferPacket.Text -> {
                val entity = ChatMessageEntity(
                    id = packet.msgId,
                    chatAddress = peerAddress,
                    senderAddress = peerAddress,
                    senderName = packet.senderName,
                    messageText = packet.text,
                    timestamp = packet.timestamp,
                    status = "DELIVERED"
                )
                messageDao.insertMessage(entity)

                updateConversationRecord(
                    deviceAddress = peerAddress,
                    deviceName = packet.senderName,
                    lastMsg = packet.text,
                    timestamp = packet.timestamp,
                    incrementUnread = true
                )

                notificationHelper.showMessageNotification(
                    senderName = packet.senderName,
                    messageText = packet.text,
                    chatAddress = peerAddress
                )

                // Send back ACK
                bluetoothController.sendPacket(BluetoothTransferPacket.Ack(packet.msgId))
            }

            is BluetoothTransferPacket.FileHeader -> {
                val entity = ChatMessageEntity(
                    id = packet.msgId,
                    chatAddress = peerAddress,
                    senderAddress = peerAddress,
                    senderName = packet.senderName,
                    messageText = "📁 Attached File: ${packet.fileName}",
                    timestamp = packet.timestamp,
                    status = "RECEIVING",
                    fileName = packet.fileName,
                    fileSize = packet.fileSize,
                    fileType = packet.fileType,
                    transferProgress = 0.0f
                )
                messageDao.insertMessage(entity)

                updateConversationRecord(
                    deviceAddress = peerAddress,
                    deviceName = packet.senderName,
                    lastMsg = "📁 ${packet.fileName}",
                    timestamp = packet.timestamp,
                    incrementUnread = true
                )

                incomingChunksMap[packet.msgId] = ByteArrayOutputStreamList()
            }

            is BluetoothTransferPacket.FileChunk -> {
                val buffer = incomingChunksMap[packet.msgId]
                if (buffer != null) {
                    val decodedBytes = Base64.decode(packet.dataBase64, Base64.NO_WRAP)
                    buffer.bytes.write(decodedBytes)
                    buffer.receivedCount++

                    val progress = buffer.receivedCount.toFloat() / packet.totalChunks.toFloat()
                    messageDao.updateTransferProgress(packet.msgId, progress, "RECEIVING")

                    if (buffer.receivedCount >= packet.totalChunks) {
                        // Complete file assembly
                        val completeBytes = buffer.bytes.toByteArray()
                        val savedFile = FileStorageUtil.saveByteArrayToFile(
                            context,
                            "received_${packet.msgId}_file",
                            completeBytes
                        )
                        if (savedFile != null) {
                            messageDao.updateMessage(
                                ChatMessageEntity(
                                    id = packet.msgId,
                                    chatAddress = peerAddress,
                                    senderAddress = peerAddress,
                                    senderName = "Peer",
                                    messageText = "📁 Attached File",
                                    fileUri = savedFile.absolutePath,
                                    transferProgress = 1.0f,
                                    status = "DELIVERED"
                                )
                            )
                        } else {
                            messageDao.updateTransferProgress(packet.msgId, 1.0f, "FAILED")
                        }
                        incomingChunksMap.remove(packet.msgId)
                    }
                }
            }

            is BluetoothTransferPacket.Ack -> {
                messageDao.updateMessageStatus(packet.msgId, "DELIVERED")
            }

            is BluetoothTransferPacket.Handshake -> {
                updateConversationRecord(
                    deviceAddress = peerAddress,
                    deviceName = packet.senderName,
                    lastMsg = "Connected via Bluetooth",
                    timestamp = System.currentTimeMillis(),
                    incrementUnread = false
                )
            }

            is BluetoothTransferPacket.Typing -> {
                // Handled in ViewModel if needed
            }
        }
    }

    private suspend fun updateConversationRecord(
        deviceAddress: String,
        deviceName: String,
        lastMsg: String,
        timestamp: Long,
        incrementUnread: Boolean
    ) {
        val existing = conversationDao.getConversationByAddress(deviceAddress)
        val unread = if (incrementUnread) (existing?.unreadCount ?: 0) + 1 else (existing?.unreadCount ?: 0)
        val updated = ChatConversationEntity(
            deviceAddress = deviceAddress,
            deviceName = deviceName.ifBlank { existing?.deviceName ?: "Bluetooth Device" },
            profilePicUri = existing?.profilePicUri,
            lastMessage = lastMsg,
            lastTimestamp = timestamp,
            unreadCount = unread,
            isConnected = true
        )
        conversationDao.insertOrUpdateConversation(updated)
    }

    fun getMessagesForChat(chatAddress: String): Flow<List<ChatMessageEntity>> =
        messageDao.getMessagesForChat(chatAddress)

    fun searchMessages(query: String): Flow<List<ChatMessageEntity>> =
        messageDao.searchMessages(query)

    suspend fun saveUserProfile(name: String, profilePicUri: String?, isFirstLaunchCompleted: Boolean) {
        val current = userProfileDao.getUserProfileDirect() ?: UserProfileEntity()
        userProfileDao.saveUserProfile(
            current.copy(
                userName = name,
                profilePicUri = profilePicUri,
                isFirstLaunchCompleted = isFirstLaunchCompleted
            )
        )
    }

    suspend fun updateThemeMode(themeMode: String) {
        userProfileDao.updateThemeMode(themeMode)
    }

    suspend fun clearUnreadCount(deviceAddress: String) {
        conversationDao.clearUnreadCount(deviceAddress)
    }

    suspend fun sendTextMessage(chatAddress: String, peerName: String, text: String) {
        val userProf = userProfileDao.getUserProfileDirect()
        val myName = userProf?.userName ?: "Me"
        val msgId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val entity = ChatMessageEntity(
            id = msgId,
            chatAddress = chatAddress,
            senderAddress = "MY_SELF",
            senderName = myName,
            messageText = text,
            timestamp = timestamp,
            status = "PENDING"
        )
        messageDao.insertMessage(entity)

        updateConversationRecord(
            deviceAddress = chatAddress,
            deviceName = peerName,
            lastMsg = text,
            timestamp = timestamp,
            incrementUnread = false
        )

        val packet = BluetoothTransferPacket.Text(
            msgId = msgId,
            text = text,
            senderName = myName,
            timestamp = timestamp
        )

        val success = bluetoothController.sendPacket(packet)
        if (success) {
            messageDao.updateMessageStatus(msgId, "DELIVERED")
        } else {
            messageDao.updateMessageStatus(msgId, "FAILED")
        }
    }

    suspend fun sendFileMessage(chatAddress: String, peerName: String, uri: Uri) {
        withContext(Dispatchers.IO) {
            val userProf = userProfileDao.getUserProfileDirect()
            val myName = userProf?.userName ?: "Me"
            val localFile = FileStorageUtil.copyUriToLocalStorage(context, uri) ?: return@withContext

            val fileName = FileStorageUtil.getFileName(context, uri)
            val extension = localFile.extension
            val fileType = FileStorageUtil.getFileTypeFromExtension(extension)
            val fileSize = localFile.length()

            val msgId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()

            val entity = ChatMessageEntity(
                id = msgId,
                chatAddress = chatAddress,
                senderAddress = "MY_SELF",
                senderName = myName,
                messageText = "📁 $fileName",
                timestamp = timestamp,
                status = "PENDING",
                fileUri = localFile.absolutePath,
                fileName = fileName,
                fileType = fileType,
                fileSize = fileSize,
                transferProgress = 0.0f
            )
            messageDao.insertMessage(entity)

            updateConversationRecord(
                deviceAddress = chatAddress,
                deviceName = peerName,
                lastMsg = "📁 $fileName",
                timestamp = timestamp,
                incrementUnread = false
            )

            // Read file in 16KB chunks
            val chunkSize = 16384
            val totalChunks = Math.ceil(fileSize.toDouble() / chunkSize.toDouble()).toInt().coerceAtLeast(1)

            val headerPacket = BluetoothTransferPacket.FileHeader(
                msgId = msgId,
                fileName = fileName,
                fileSize = fileSize,
                fileType = fileType,
                totalChunks = totalChunks,
                senderName = myName,
                timestamp = timestamp
            )

            val headerSent = bluetoothController.sendPacket(headerPacket)
            if (!headerSent) {
                messageDao.updateTransferProgress(msgId, 0.0f, "FAILED")
                return@withContext
            }

            val fileInputStream = FileInputStream(localFile)
            val buffer = ByteArray(chunkSize)
            var bytesRead: Int
            var chunkIndex = 0

            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                val chunkBytes = if (bytesRead == chunkSize) buffer else buffer.copyOf(bytesRead)
                val base64Data = Base64.encodeToString(chunkBytes, Base64.NO_WRAP)

                val chunkPacket = BluetoothTransferPacket.FileChunk(
                    msgId = msgId,
                    chunkIndex = chunkIndex,
                    totalChunks = totalChunks,
                    dataBase64 = base64Data
                )

                val sent = bluetoothController.sendPacket(chunkPacket)
                if (!sent) {
                    messageDao.updateTransferProgress(msgId, chunkIndex.toFloat() / totalChunks.toFloat(), "FAILED")
                    fileInputStream.close()
                    return@withContext
                }

                chunkIndex++
                val progress = chunkIndex.toFloat() / totalChunks.toFloat()
                messageDao.updateTransferProgress(msgId, progress, if (chunkIndex == totalChunks) "DELIVERED" else "PENDING")
            }
            fileInputStream.close()
        }
    }

    suspend fun toggleStarMessage(messageId: String, currentStarred: Boolean) {
        messageDao.setStarred(messageId, !currentStarred)
    }

    suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessageById(messageId)
    }

    suspend fun clearChat(chatAddress: String) {
        messageDao.clearChatMessages(chatAddress)
    }

    suspend fun deleteConversation(chatAddress: String) {
        messageDao.clearChatMessages(chatAddress)
        conversationDao.deleteConversation(chatAddress)
    }
}
