package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object FileStorageUtil {

    fun getFileTypeFromExtension(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> "IMAGE"
            "mp4", "mkv", "webm", "avi", "3gp", "mov" -> "VIDEO"
            "mp3", "wav", "aac", "ogg", "m4a", "flac" -> "AUDIO"
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt" -> "DOCUMENT"
            "apk" -> "APK"
            "zip", "rar", "7z", "tar", "gz" -> "ZIP"
            else -> "OTHER"
        }
    }

    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex("_display_name")
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "file_${System.currentTimeMillis()}"
    }

    fun copyUriToLocalStorage(context: Context, uri: Uri): File? {
        return try {
            val fileName = getFileName(context, uri)
            val storageDir = File(context.filesDir, "bluechat_files")
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            val destinationFile = File(storageDir, "${UUID.randomUUID()}_$fileName")
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(destinationFile)
            if (inputStream != null) {
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()
                destinationFile
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveByteArrayToFile(context: Context, fileName: String, bytes: ByteArray): File? {
        return try {
            val storageDir = File(context.filesDir, "bluechat_received")
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            val destinationFile = File(storageDir, fileName)
            val outputStream = FileOutputStream(destinationFile, true) // append mode
            outputStream.write(bytes)
            outputStream.close()
            destinationFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun openFile(context: Context, fileUriString: String) {
        try {
            val uri = Uri.parse(fileUriString)
            val file = File(uri.path ?: return)
            if (!file.exists()) return

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val extension = file.extension
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", sizeInBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
