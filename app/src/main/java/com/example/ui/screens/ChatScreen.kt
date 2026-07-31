package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth.BluetoothConnectionState
import com.example.data.local.entity.ChatMessageEntity
import com.example.ui.components.AttachmentPickerBottomSheet
import com.example.ui.components.AvatarView
import com.example.ui.components.ImagePreviewDialog
import com.example.ui.components.MessageBubble
import com.example.ui.viewmodel.ChatViewModel
import com.example.util.FileStorageUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel,
    chatAddress: String,
    peerName: String,
    onNavigateBack: () -> Unit,
    onNavigateToInfo: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val messages by chatViewModel.messages.collectAsState()
    val connectionState by chatViewModel.connectionState.collectAsState()

    var messageText by remember { mutableStateOf("") }
    var showAttachmentPicker by remember { mutableStateOf(false) }
    var selectedMessageForMenu by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var previewImagePath by remember { mutableStateOf<String?>(null) }
    var showMenuDropdown by remember { mutableStateOf(false) }

    val isConnected = connectionState is BluetoothConnectionState.Connected &&
            (connectionState as BluetoothConnectionState.Connected).address == chatAddress

    LaunchedEffect(chatAddress) {
        chatViewModel.initChat(chatAddress, peerName)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { chatViewModel.sendFileMessage(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(onClick = onNavigateToInfo)
                            .padding(vertical = 4.dp)
                    ) {
                        AvatarView(
                            name = peerName,
                            imageUri = null,
                            size = 38.dp,
                            showOnlineStatus = true,
                            isOnline = isConnected
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = peerName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (isConnected) "Online via Bluetooth" else "Offline",
                                fontSize = 11.5.sp,
                                color = if (isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToInfo) {
                        Icon(Icons.Default.Info, contentDescription = "Chat Info")
                    }
                    IconButton(onClick = { showMenuDropdown = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenuDropdown,
                        onDismissRequest = { showMenuDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Chat Info") },
                            onClick = {
                                showMenuDropdown = false
                                onNavigateToInfo()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear Chat") },
                            onClick = {
                                showMenuDropdown = false
                                chatViewModel.clearChat()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Messages List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "🔒 Bluetooth Encrypted Offline Chat\nSay Hello to start messaging!",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp, bottom = 8.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            MessageBubble(
                                message = msg,
                                onLongClick = { selectedMessageForMenu = msg },
                                onFileClick = { uriStr ->
                                    FileStorageUtil.openFile(context, uriStr)
                                },
                                onImagePreview = { uriStr ->
                                    previewImagePath = uriStr
                                }
                            )
                        }
                    }
                }
            }

            // Quick Emoji row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf("👍", "❤️", "😂", "🔥", "🤝", "🎉", "⚡").forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .clickable { messageText += emoji }
                            .padding( horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Chat Input Bar
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = { showAttachmentPicker = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach File",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Type a message...") },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        ),
                        maxLines = 4,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                chatViewModel.sendTextMessage(messageText)
                                messageText = ""
                            }
                        },
                        modifier = Modifier
                            .background(
                                if (messageText.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                CircleShape
                            )
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (messageText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Attachment Picker Bottom Sheet
    if (showAttachmentPicker) {
        AttachmentPickerBottomSheet(
            onDismissRequest = { showAttachmentPicker = false },
            onPickFileType = { mimeType ->
                showAttachmentPicker = false
                filePickerLauncher.launch(mimeType)
            }
        )
    }

    // Full screen image preview dialog
    previewImagePath?.let { path ->
        ImagePreviewDialog(
            imagePath = path,
            onDismissRequest = { previewImagePath = null }
        )
    }

    // Long Press Action Dialog
    selectedMessageForMenu?.let { msg ->
        AlertDialog(
            onDismissRequest = { selectedMessageForMenu = null },
            title = { Text("Message Options") },
            text = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                chatViewModel.toggleStarMessage(msg)
                                selectedMessageForMenu = null
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = if (msg.isStarred) Icons.Default.StarOutline else Icons.Default.Star,
                            contentDescription = "Star",
                            tint = Color(0xFFEAB308)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(if (msg.isStarred) "Unstar Message" else "Star Message")
                    }

                    if (msg.messageText.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Copied Text", msg.messageText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    selectedMessageForMenu = null
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Copy Text")
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, msg.messageText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Message"))
                                selectedMessageForMenu = null
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Share")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                chatViewModel.deleteMessage(msg.id)
                                selectedMessageForMenu = null
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Delete Message", color = Color(0xFFEF4444))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMessageForMenu = null }) {
                    Text("Close")
                }
            }
        )
    }
}
