package com.example.bluetooth

import org.json.JSONObject

sealed class BluetoothTransferPacket {
    data class Text(
        val msgId: String,
        val text: String,
        val senderName: String,
        val timestamp: Long
    ) : BluetoothTransferPacket()

    data class FileHeader(
        val msgId: String,
        val fileName: String,
        val fileSize: Long,
        val fileType: String,
        val totalChunks: Int,
        val senderName: String,
        val timestamp: Long
    ) : BluetoothTransferPacket()

    data class FileChunk(
        val msgId: String,
        val chunkIndex: Int,
        val totalChunks: Int,
        val dataBase64: String
    ) : BluetoothTransferPacket()

    data class Ack(
        val msgId: String,
        val status: String = "DELIVERED"
    ) : BluetoothTransferPacket()

    data class Typing(
        val isTyping: Boolean
    ) : BluetoothTransferPacket()

    data class Handshake(
        val senderName: String
    ) : BluetoothTransferPacket()

    fun toJsonString(): String {
        val json = JSONObject()
        when (this) {
            is Text -> {
                json.put("type", "TEXT")
                json.put("msgId", msgId)
                json.put("text", text)
                json.put("senderName", senderName)
                json.put("timestamp", timestamp)
            }
            is FileHeader -> {
                json.put("type", "FILE_HEADER")
                json.put("msgId", msgId)
                json.put("fileName", fileName)
                json.put("fileSize", fileSize)
                json.put("fileType", fileType)
                json.put("totalChunks", totalChunks)
                json.put("senderName", senderName)
                json.put("timestamp", timestamp)
            }
            is FileChunk -> {
                json.put("type", "FILE_CHUNK")
                json.put("msgId", msgId)
                json.put("chunkIndex", chunkIndex)
                json.put("totalChunks", totalChunks)
                json.put("dataBase64", dataBase64)
            }
            is Ack -> {
                json.put("type", "ACK")
                json.put("msgId", msgId)
                json.put("status", status)
            }
            is Typing -> {
                json.put("type", "TYPING")
                json.put("isTyping", isTyping)
            }
            is Handshake -> {
                json.put("type", "HANDSHAKE")
                json.put("senderName", senderName)
            }
        }
        return json.toString()
    }

    companion object {
        fun parseJson(jsonString: String): BluetoothTransferPacket? {
            return try {
                val json = JSONObject(jsonString)
                when (json.optString("type")) {
                    "TEXT" -> Text(
                        msgId = json.getString("msgId"),
                        text = json.getString("text"),
                        senderName = json.getString("senderName"),
                        timestamp = json.getLong("timestamp")
                    )
                    "FILE_HEADER" -> FileHeader(
                        msgId = json.getString("msgId"),
                        fileName = json.getString("fileName"),
                        fileSize = json.getLong("fileSize"),
                        fileType = json.getString("fileType"),
                        totalChunks = json.getInt("totalChunks"),
                        senderName = json.getString("senderName"),
                        timestamp = json.getLong("timestamp")
                    )
                    "FILE_CHUNK" -> FileChunk(
                        msgId = json.getString("msgId"),
                        chunkIndex = json.getInt("chunkIndex"),
                        totalChunks = json.getInt("totalChunks"),
                        dataBase64 = json.getString("dataBase64")
                    )
                    "ACK" -> Ack(
                        msgId = json.getString("msgId"),
                        status = json.optString("status", "DELIVERED")
                    )
                    "TYPING" -> Typing(
                        isTyping = json.getBoolean("isTyping")
                    )
                    "HANDSHAKE" -> Handshake(
                        senderName = json.getString("senderName")
                    )
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
