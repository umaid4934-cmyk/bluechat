package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.entity.ChatMessageEntity
import com.example.ui.theme.IncomingBubbleDark
import com.example.ui.theme.IncomingBubbleLight
import com.example.ui.theme.OutgoingBubbleDark
import com.example.ui.theme.OutgoingBubbleLight
import com.example.util.FileStorageUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessageEntity,
    onLongClick: () -> Unit,
    onFileClick: (String) -> Unit,
    onImagePreview: (String) -> Unit
) {
    val isOutgoing = message.senderAddress == "MY_SELF"
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    val bubbleColor = when {
        isOutgoing -> if (isDark) OutgoingBubbleDark else OutgoingBubbleLight
        else -> if (isDark) IncomingBubbleDark else IncomingBubbleLight
    }

    val textColor = when {
        isOutgoing -> if (isDark) Color.White else Color(0xFF0F172A)
        else -> if (isDark) Color.White else Color(0xFF0F172A)
    }

    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    }

    val formattedTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 12.dp),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            shadowElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = 290.dp)
                .combinedClickable(
                    onClick = {
                        if (!message.fileUri.isNullOrEmpty()) {
                            if (message.fileType == "IMAGE") {
                                onImagePreview(message.fileUri)
                            } else {
                                onFileClick(message.fileUri)
                            }
                        }
                    },
                    onLongClick = onLongClick
                )
        ) {
            Column(
                modifier = Modifier.padding(all = 10.dp)
            ) {
                // File Attachment view if present
                if (!message.fileName.isNullOrEmpty() || message.fileType != null) {
                    when (message.fileType) {
                        "IMAGE" -> {
                            if (!message.fileUri.isNullOrEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(message.fileUri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Shared Image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                        else -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Color.Black.copy(alpha = 0.08f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp)
                            ) {
                                val fileIcon = when (message.fileType) {
                                    "AUDIO" -> Icons.Default.AudioFile
                                    "ZIP" -> Icons.Default.FolderZip
                                    else -> Icons.Default.InsertDriveFile
                                }
                                Icon(
                                    imageVector = fileIcon,
                                    contentDescription = "File icon",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = message.fileName ?: "Shared File",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = textColor,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = FileStorageUtil.formatFileSize(message.fileSize),
                                        fontSize = 11.sp,
                                        color = textColor.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    if (message.status == "RECEIVING" || (message.status == "PENDING" && message.transferProgress < 1.0f)) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Transferring...",
                                    fontSize = 11.sp,
                                    color = textColor.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "${(message.transferProgress * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    color = textColor.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { message.transferProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Message Text
                if (message.messageText.isNotBlank()) {
                    Text(
                        text = message.messageText,
                        color = textColor,
                        fontSize = 14.2.sp,
                        lineHeight = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Time, Star, and Status Tick
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    if (message.isStarred) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Starred",
                            tint = Color(0xFFEAB308),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Text(
                        text = formattedTime,
                        fontSize = 10.5.sp,
                        color = textColor.copy(alpha = 0.65f)
                    )

                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.status) {
                            "PENDING" -> Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Pending",
                                tint = textColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp)
                            )
                            "DELIVERED" -> Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Delivered",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            "FAILED" -> Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Failed",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(12.dp)
                            )
                            else -> Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Sent",
                                tint = textColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
