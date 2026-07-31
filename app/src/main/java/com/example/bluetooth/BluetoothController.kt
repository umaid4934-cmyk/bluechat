package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

class BluetoothController(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _scannedDevices = MutableStateFlow<List<BluetoothDeviceModel>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDeviceModel>> = _scannedDevices.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceModel>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDeviceModel>> = _pairedDevices.asStateFlow()

    private val _connectionState =
        MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)
    val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<BluetoothTransferPacket>(extraBufferCapacity = 64)
    val incomingPackets: SharedFlow<BluetoothTransferPacket> = _incomingPackets.asSharedFlow()

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private var activeSocket: BluetoothSocket? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var readJob: Job? = null
    private var acceptJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "BluetoothController"
        private val APP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val SERVICE_NAME = "BlueChatService"
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
                    if (state == BluetoothAdapter.STATE_ON) {
                        loadPairedDevices()
                    }
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    device?.let {
                        val name = try { it.name ?: "Unknown Device" } catch (e: SecurityException) { "Unknown Device" }
                        val model = BluetoothDeviceModel(
                            name = name,
                            address = it.address,
                            isPaired = false,
                            rssi = rssi
                        )
                        val currentList = _scannedDevices.value.toMutableList()
                        if (currentList.none { d -> d.address == model.address }) {
                            currentList.add(model)
                            _scannedDevices.value = currentList
                        }
                    }
                }
            }
        }
    }

    init {
        registerReceivers()
        loadPairedDevices()
    }

    private fun registerReceivers() {
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_FOUND)
            }
            context.registerReceiver(bluetoothReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register broadcast receiver", e)
        }
    }

    fun unregister() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver", e)
        }
        stopServer()
        closeConnection()
    }

    @SuppressLint("MissingPermission")
    fun loadPairedDevices() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            // Emulator or BT disabled fallback list
            _pairedDevices.value = listOf(
                BluetoothDeviceModel("Demo Device (BlueChat)", "00:11:22:33:44:55", isPaired = true)
            )
            return
        }
        try {
            val bonded = bluetoothAdapter.bondedDevices ?: emptySet()
            _pairedDevices.value = bonded.map {
                BluetoothDeviceModel(
                    name = it.name ?: "Paired Device",
                    address = it.address,
                    isPaired = true
                )
            }.ifEmpty {
                listOf(BluetoothDeviceModel("Demo Device (BlueChat)", "00:11:22:33:44:55", isPaired = true))
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BT permission to load paired devices", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            // Fallback for emulator simulation
            _scannedDevices.value = listOf(
                BluetoothDeviceModel("Nearby BlueChat Phone", "11:22:33:44:55:66", isPaired = false, rssi = -65),
                BluetoothDeviceModel("Tablet (Offline)", "AA:BB:CC:DD:EE:FF", isPaired = false, rssi = -78)
            )
            return
        }
        try {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            _scannedDevices.value = emptyList()
            bluetoothAdapter.startDiscovery()
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission to discover", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission to cancel discovery", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startServer(myDeviceName: String) {
        stopServer()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return

        acceptJob = scope.launch {
            try {
                serverSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                    SERVICE_NAME, APP_UUID
                )
                while (acceptJob?.isActive == true) {
                    val socket = serverSocket?.accept() ?: break
                    val remoteDevice = socket.remoteDevice
                    val deviceName = try { remoteDevice?.name ?: "Remote Peer" } catch (e: SecurityException) { "Remote Peer" }
                    val address = remoteDevice?.address ?: "00:00:00:00:00:00"

                    withContext(Dispatchers.Main) {
                        _connectionState.value = BluetoothConnectionState.Connected(deviceName, address)
                    }
                    activeSocket = socket
                    startReading(socket)
                    sendPacket(BluetoothTransferPacket.Handshake(myDeviceName))
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket accept failed", e)
            }
        }
    }

    fun stopServer() {
        acceptJob?.cancel()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
        serverSocket = null
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDeviceModel, myDeviceName: String) {
        scope.launch {
            _connectionState.value = BluetoothConnectionState.Connecting(device.name, device.address)
            stopDiscovery()

            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled || device.address == "00:11:22:33:44:55" || device.address == "11:22:33:44:55:66") {
                // Simulation mode for emulator / test devices
                kotlinx.coroutines.delay(1000)
                _connectionState.value = BluetoothConnectionState.Connected(device.name, device.address)
                return@launch
            }

            try {
                val btDevice = bluetoothAdapter.getRemoteDevice(device.address)
                val socket = btDevice.createInsecureRfcommSocketToServiceRecord(APP_UUID)
                socket.connect()

                activeSocket = socket
                _connectionState.value = BluetoothConnectionState.Connected(device.name, device.address)
                startReading(socket)
                sendPacket(BluetoothTransferPacket.Handshake(myDeviceName))
            } catch (e: Exception) {
                Log.e(TAG, "Connection to ${device.address} failed", e)
                _connectionState.value = BluetoothConnectionState.Error("Failed to connect: ${e.localizedMessage}")
                closeConnection()
            }
        }
    }

    private fun startReading(socket: BluetoothSocket) {
        readJob?.cancel()
        readJob = scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                while (readJob?.isActive == true) {
                    val line = reader.readLine() ?: break
                    val packet = BluetoothTransferPacket.parseJson(line)
                    if (packet != null) {
                        _incomingPackets.emit(packet)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Read thread interrupted/disconnected", e)
            } finally {
                withContext(Dispatchers.Main) {
                    _connectionState.value = BluetoothConnectionState.Disconnected
                }
            }
        }
    }

    suspend fun sendPacket(packet: BluetoothTransferPacket): Boolean {
        return withContext(Dispatchers.IO) {
            val socket = activeSocket
            if (socket != null && socket.isConnected) {
                try {
                    val outputStream: OutputStream = socket.outputStream
                    val data = (packet.toJsonString() + "\n").toByteArray(Charsets.UTF_8)
                    outputStream.write(data)
                    outputStream.flush()
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send packet", e)
                    false
                }
            } else {
                // Simulated echo/ack for emulator
                if (_connectionState.value is BluetoothConnectionState.Connected) {
                    if (packet is BluetoothTransferPacket.Text) {
                        scope.launch {
                            kotlinx.coroutines.delay(300)
                            _incomingPackets.emit(BluetoothTransferPacket.Ack(packet.msgId))
                        }
                    }
                    true
                } else {
                    false
                }
            }
        }
    }

    fun closeConnection() {
        readJob?.cancel()
        try {
            activeSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket", e)
        }
        activeSocket = null
        _connectionState.value = BluetoothConnectionState.Disconnected
    }
}
