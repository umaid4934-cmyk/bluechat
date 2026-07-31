package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BluetoothConnectionState
import com.example.bluetooth.BluetoothController
import com.example.bluetooth.BluetoothDeviceModel
import com.example.data.local.entity.ChatConversationEntity
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.repository.ChatRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothController = BluetoothController(application)
    val repository = ChatRepository(application, bluetoothController)

    val userProfile: StateFlow<UserProfileEntity?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val conversations: StateFlow<List<ChatConversationEntity>> = repository.conversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val starredMessages: StateFlow<List<ChatMessageEntity>> = repository.starredMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalStorageUsed: StateFlow<Long?> = repository.totalStorageUsed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val connectionState: StateFlow<BluetoothConnectionState> = repository.connectionState
    val scannedDevices: StateFlow<List<BluetoothDeviceModel>> = repository.scannedDevices
    val pairedDevices: StateFlow<List<BluetoothDeviceModel>> = repository.pairedDevices
    val isBluetoothEnabled: StateFlow<Boolean> = bluetoothController.isBluetoothEnabled

    fun saveUserProfile(name: String, profilePicUri: String?, isFirstLaunchCompleted: Boolean) {
        viewModelScope.launch {
            repository.saveUserProfile(name, profilePicUri, isFirstLaunchCompleted)
        }
    }

    fun updateThemeMode(mode: String) {
        viewModelScope.launch {
            repository.updateThemeMode(mode)
        }
    }

    fun startDiscovery() {
        bluetoothController.startDiscovery()
    }

    fun stopDiscovery() {
        bluetoothController.stopDiscovery()
    }

    fun connectToDevice(device: BluetoothDeviceModel) {
        val myName = userProfile.value?.userName ?: "BlueChat User"
        bluetoothController.connectToDevice(device, myName)
    }

    fun disconnect() {
        bluetoothController.closeConnection()
    }

    fun startServer() {
        val myName = userProfile.value?.userName ?: "BlueChat User"
        bluetoothController.startServer(myName)
    }

    fun deleteConversation(deviceAddress: String) {
        viewModelScope.launch {
            repository.deleteConversation(deviceAddress)
        }
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothController.unregister()
    }
}
