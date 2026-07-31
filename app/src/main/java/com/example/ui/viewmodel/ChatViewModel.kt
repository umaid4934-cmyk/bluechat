package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BluetoothConnectionState
import com.example.data.local.entity.ChatMessageEntity
import com.example.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    application: Application,
    private val repository: ChatRepository
) : AndroidViewModel(application) {

    private val _currentChatAddress = MutableStateFlow("")
    val currentChatAddress: StateFlow<String> = _currentChatAddress.asStateFlow()

    private val _peerName = MutableStateFlow("Bluetooth Peer")
    val peerName: StateFlow<String> = _peerName.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val connectionState: StateFlow<BluetoothConnectionState> = repository.connectionState

    val messages: StateFlow<List<ChatMessageEntity>> = _currentChatAddress
        .flatMapLatest { address ->
            if (address.isBlank()) MutableStateFlow(emptyList())
            else repository.getMessagesForChat(address)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchResults: StateFlow<List<ChatMessageEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) MutableStateFlow(emptyList())
            else repository.searchMessages(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun initChat(address: String, name: String) {
        _currentChatAddress.value = address
        _peerName.value = name
        viewModelScope.launch {
            repository.clearUnreadCount(address)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank() || _currentChatAddress.value.isBlank()) return
        viewModelScope.launch {
            repository.sendTextMessage(_currentChatAddress.value, _peerName.value, text.trim())
        }
    }

    fun sendFileMessage(uri: Uri) {
        if (_currentChatAddress.value.isBlank()) return
        viewModelScope.launch {
            repository.sendFileMessage(_currentChatAddress.value, _peerName.value, uri)
        }
    }

    fun toggleStarMessage(message: ChatMessageEntity) {
        viewModelScope.launch {
            repository.toggleStarMessage(message.id, message.isStarred)
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    fun clearChat() {
        if (_currentChatAddress.value.isBlank()) return
        viewModelScope.launch {
            repository.clearChat(_currentChatAddress.value)
        }
    }
}
