package com.example.bluetooth

data class BluetoothDeviceModel(
    val name: String,
    val address: String,
    val isPaired: Boolean = false,
    val isConnected: Boolean = false,
    val rssi: Int? = null
)
