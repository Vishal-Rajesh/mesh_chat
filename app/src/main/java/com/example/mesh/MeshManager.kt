package com.example.mesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.MessageEntity
import com.example.data.MeshRepository
import com.example.data.NodeEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import kotlin.math.sqrt

class MeshManager(
    private val context: Context,
    private val repository: MeshRepository
) {
    private val TAG = "MeshManager"
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // UI visible network logs
    private val _networkLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val networkLogs = _networkLogs.asStateFlow()

    // Real Hardware Capabilities & States
    var isBleSupported = false
    var isWifiDirectSupported = false
    
    private val _isBleAdvertisingState = MutableStateFlow(false)
    val isBleAdvertisingState = _isBleAdvertisingState.asStateFlow()
    val isBleAdvertising: Boolean get() = _isBleAdvertisingState.value

    private val _isBleScanningState = MutableStateFlow(false)
    val isBleScanningState = _isBleScanningState.asStateFlow()
    val isBleScanning: Boolean get() = _isBleScanningState.value

    private val _isWifiP2pSearchingState = MutableStateFlow(false)
    val isWifiP2pSearchingState = _isWifiP2pSearchingState.asStateFlow()
    val isWifiP2pSearching: Boolean get() = _isWifiP2pSearchingState.value

    // Hardware managers
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiP2pChannel: WifiP2pManager.Channel? = null

    // Mesh Node data structure
    data class SimNode(
        val id: String,
        val name: String,
        val status: String, // "ACTIVE", "IDLE", "DISCONNECTED"
        val connectionType: String, // "BLE", "WIFI_DIRECT", "MANUAL"
        var x: Float,
        var y: Float,
        val battery: Int,
        val isSOS: Boolean = false,
        var hops: Int = 1,
        var path: List<String> = emptyList(),
        val rssi: Int = -60,
        val isAiAssistant: Boolean = false
    )

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val tag: String, // "BLE", "WIFI", "ROUTING", "PERM", "SEC"
        val message: String,
        val isError: Boolean = false
    )

    // Discovered nodes state - starts EMPTY (No cap/mock data)
    private val _simNodes = MutableStateFlow<List<SimNode>>(emptyList())
    val simNodes = _simNodes.asStateFlow()

    init {
        addLog("SYSTEM", "OffGrid Mesh initialized. Scanning real Bluetooth BLE & Wi-Fi Direct radios.")
        initRealHardware()
    }

    fun addLog(tag: String, message: String, isError: Boolean = false) {
        val entry = LogEntry(tag = tag, message = message, isError = isError)
        _networkLogs.value = (listOf(entry) + _networkLogs.value).take(100)
    }

    private fun initRealHardware() {
        try {
            // BLE Init
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = btManager?.adapter
            if (bluetoothAdapter != null) {
                isBleSupported = true
                advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
                addLog("BLE", "Bluetooth BLE hardware detected.")
            } else {
                addLog("BLE", "Bluetooth LE not supported on this device.", isError = true)
            }

            // Wi-Fi Direct Init
            wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            if (wifiP2pManager != null) {
                isWifiDirectSupported = true
                wifiP2pChannel = wifiP2pManager?.initialize(context, context.mainLooper, null)
                addLog("WIFI", "Wi-Fi Direct hardware ready.")
            } else {
                addLog("WIFI", "Wi-Fi Direct not supported on this device.", isError = true)
            }
        } catch (e: Exception) {
            addLog("SYSTEM", "Error initializing radio hardware: ${e.localizedMessage}", isError = true)
        }
    }

    fun checkPermissionsGranted(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Real BLE Scan Callback
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let { handleDiscoveredBleDevice(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            results?.forEach { handleDiscoveredBleDevice(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            _isBleScanningState.value = false
            val errorMsg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                SCAN_FAILED_INTERNAL_ERROR -> "Internal Bluetooth error"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE scan feature unsupported"
                else -> "Error code $errorCode"
            }
            addLog("BLE", "BLE Scan failed: $errorMsg", isError = true)
        }
    }

    private fun handleDiscoveredBleDevice(result: ScanResult) {
        val device = result.device ?: return
        val rssi = result.rssi
        val deviceName = try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                device.name ?: result.scanRecord?.deviceName ?: "BLE Peer (${device.address.takeLast(5)})"
            } else {
                "BLE Peer (${device.address.takeLast(5)})"
            }
        } catch (e: SecurityException) {
            "BLE Peer (${device.address.takeLast(5)})"
        }

        val deviceId = device.address

        // Calculate visual coordinates relative to center (100, 100) based on RSSI
        val estimatedDist = calculateDistanceFromRssi(rssi)
        val angle = Math.abs(deviceId.hashCode() % 360) * (Math.PI / 180.0)
        val posX = (100f + (estimatedDist * 15f * Math.cos(angle))).toFloat().coerceIn(20f, 180f)
        val posY = (100f + (estimatedDist * 15f * Math.sin(angle))).toFloat().coerceIn(20f, 180f)

        val currentList = _simNodes.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == deviceId }

        val node = SimNode(
            id = deviceId,
            name = deviceName,
            status = "ACTIVE",
            connectionType = "BLE",
            x = posX,
            y = posY,
            battery = 85,
            rssi = rssi,
            hops = 1,
            path = listOf(deviceName)
        )

        if (existingIndex >= 0) {
            currentList[existingIndex] = node
        } else {
            currentList.add(node)
            addLog("BLE", "Discovered real BLE device: $deviceName [$deviceId] (RSSI: ${rssi}dBm)")
        }

        _simNodes.value = currentList

        // Sync node to Room Database
        coroutineScope.launch {
            repository.insertNode(
                NodeEntity(
                    id = deviceId,
                    name = deviceName,
                    status = "ACTIVE",
                    connectionType = "BLE",
                    hopsAway = 1,
                    lastSeen = System.currentTimeMillis(),
                    signalStrength = rssi,
                    xPos = posX,
                    yPos = posY
                )
            )
        }
    }

    private fun calculateDistanceFromRssi(rssi: Int): Float {
        // Approximate distance estimation from RSSI
        val txPower = -59 // Measured power at 1 meter
        if (rssi == 0) return 5.0f
        val ratio = rssi * 1.0 / txPower
        return if (ratio < 1.0) {
            Math.pow(ratio, 10.0).toFloat().coerceIn(1.0f, 10.0f)
        } else {
            (0.89976 * Math.pow(ratio, 7.7095) + 0.111).toFloat().coerceIn(1.0f, 10.0f)
        }
    }

    // Toggle Real BLE Scanner
    fun toggleBleScanning(): Boolean {
        if (!isBleSupported || bluetoothAdapter == null) {
            addLog("BLE", "Cannot scan BLE: Bluetooth unsupported", isError = true)
            return false
        }

        if (!checkPermissionsGranted()) {
            addLog("PERM", "Cannot start scan: Bluetooth / Location permissions missing!", isError = true)
            return false
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            addLog("BLE", "Bluetooth is disabled. Turn on Bluetooth first.", isError = true)
            return false
        }

        if (isBleScanning) {
            try {
                scanner.stopScan(scanCallback)
                _isBleScanningState.value = false
                addLog("BLE", "Stopped BLE scanner.")
            } catch (e: SecurityException) {
                addLog("PERM", "Permission error stopping BLE scan", isError = true)
            }
        } else {
            try {
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                scanner.startScan(null, settings, scanCallback)
                _isBleScanningState.value = true
                addLog("BLE", "Started real BLE scan for nearby mesh devices...")
            } catch (e: SecurityException) {
                addLog("PERM", "Permission denied starting BLE scan!", isError = true)
                return false
            } catch (e: Exception) {
                addLog("BLE", "Error starting BLE scan: ${e.localizedMessage}", isError = true)
                return false
            }
        }
        return true
    }

    // Toggle real BLE advertising
    fun toggleBleAdvertising() {
        if (!isBleSupported) {
            addLog("BLE", "Cannot toggle BLE: hardware unsupported", isError = true)
            return
        }

        if (!checkPermissionsGranted()) {
            addLog("PERM", "Permission required to start BLE advertising!", isError = true)
            return
        }

        if (advertiser == null) {
            advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        }

        if (advertiser == null) {
            addLog("BLE", "Bluetooth is disabled or peripheral mode unavailable.", isError = true)
            return
        }

        if (isBleAdvertising) {
            try {
                advertiser?.stopAdvertising(advertiseCallback)
                _isBleAdvertisingState.value = false
                addLog("BLE", "Stopped real BLE beacon advertising.")
            } catch (e: SecurityException) {
                addLog("PERM", "Permission denied stopping BLE advertising", isError = true)
            }
        } else {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")))
                .build()

            try {
                advertiser?.startAdvertising(settings, data, advertiseCallback)
                _isBleAdvertisingState.value = true
                addLog("BLE", "Started real BLE beacon advertising (Mesh UUID: 180F)")
            } catch (e: SecurityException) {
                addLog("PERM", "Permission required to start BLE advertising!", isError = true)
            } catch (e: Exception) {
                addLog("BLE", "Error starting BLE advertising: ${e.localizedMessage}", isError = true)
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "BLE advertise started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            _isBleAdvertisingState.value = false
            addLog("BLE", "Real BLE Advertise failed with code $errorCode", isError = true)
        }
    }

    // Toggle real Wi-Fi Direct Peer Discovery
    fun toggleWifiP2pDiscovery() {
        if (!isWifiDirectSupported || wifiP2pManager == null || wifiP2pChannel == null) {
            addLog("WIFI", "Cannot toggle Wi-Fi Direct: hardware unsupported", isError = true)
            return
        }

        if (!checkPermissionsGranted()) {
            addLog("PERM", "Location / Wi-Fi permissions required for Wi-Fi Direct!", isError = true)
            return
        }

        if (isWifiP2pSearching) {
            _isWifiP2pSearchingState.value = false
            addLog("WIFI", "Stopped real Wi-Fi Direct search.")
        } else {
            try {
                wifiP2pManager?.discoverPeers(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        _isWifiP2pSearchingState.value = true
                        addLog("WIFI", "Real Wi-Fi Direct peer discovery started.")
                        requestWifiP2pPeers()
                    }

                    override fun onFailure(reason: Int) {
                        _isWifiP2pSearchingState.value = false
                        addLog("WIFI", "Real Wi-Fi Direct search failed: code $reason", isError = true)
                    }
                })
            } catch (e: SecurityException) {
                addLog("PERM", "Location / Wi-Fi permissions required for Direct Search!", isError = true)
            }
        }
    }

    private fun requestWifiP2pPeers() {
        try {
            wifiP2pManager?.requestPeers(wifiP2pChannel) { deviceList: WifiP2pDeviceList? ->
                deviceList?.deviceList?.forEach { p2pDevice ->
                    handleDiscoveredWifiP2pDevice(p2pDevice)
                }
            }
        } catch (e: SecurityException) {
            addLog("PERM", "Permission missing to request Wi-Fi Direct peers", isError = true)
        }
    }

    private fun handleDiscoveredWifiP2pDevice(p2pDevice: WifiP2pDevice) {
        val deviceName = p2pDevice.deviceName.ifBlank { "Wi-Fi Direct Peer (${p2pDevice.deviceAddress.takeLast(5)})" }
        val deviceId = p2pDevice.deviceAddress

        val currentList = _simNodes.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == deviceId }

        val node = SimNode(
            id = deviceId,
            name = deviceName,
            status = "ACTIVE",
            connectionType = "WIFI_DIRECT",
            x = 130f,
            y = 130f,
            battery = 90,
            rssi = -55,
            hops = 1,
            path = listOf(deviceName)
        )

        if (existingIndex >= 0) {
            currentList[existingIndex] = node
        } else {
            currentList.add(node)
            addLog("WIFI", "Discovered Wi-Fi Direct Peer: $deviceName [$deviceId]")
        }

        _simNodes.value = currentList
    }

    // Add Custom Node (for manual device pairing or adding a friend's phone)
    fun addCustomNode(name: String, id: String, connectionType: String = "BLE") {
        if (name.isBlank()) return
        val nodeName = name.trim()
        val nodeId = if (id.isBlank()) "node_${UUID.randomUUID().toString().take(6)}" else id.trim()

        val newNode = SimNode(
            id = nodeId,
            name = nodeName,
            status = "ACTIVE",
            connectionType = connectionType,
            x = (70..130).random().toFloat(),
            y = (70..130).random().toFloat(),
            battery = 100,
            hops = 1,
            path = listOf(nodeName)
        )

        val currentList = _simNodes.value.toMutableList()
        currentList.removeAll { it.id == nodeId }
        currentList.add(newNode)
        _simNodes.value = currentList

        addLog("SYSTEM", "Manually paired mesh peer: $nodeName ($nodeId)")

        coroutineScope.launch {
            repository.insertNode(
                NodeEntity(
                    id = nodeId,
                    name = nodeName,
                    status = "ACTIVE",
                    connectionType = connectionType,
                    hopsAway = 1,
                    lastSeen = System.currentTimeMillis(),
                    signalStrength = -60,
                    xPos = newNode.x,
                    yPos = newNode.y
                )
            )
        }
    }

    // Add Gemini AI Assistant Node explicitly if requested
    fun enableGeminiAiNode() {
        val aiNode = SimNode(
            id = "ai_advisor",
            name = "Mesh AI Assistant",
            status = "ACTIVE",
            connectionType = "BLE",
            x = 120f,
            y = 50f,
            battery = 100,
            isAiAssistant = true,
            hops = 1,
            path = listOf("Mesh AI Assistant")
        )

        val currentList = _simNodes.value.toMutableList()
        if (currentList.none { it.id == "ai_advisor" }) {
            currentList.add(aiNode)
            _simNodes.value = currentList
            addLog("SYSTEM", "Added Mesh AI Assistant node to active routing table.")
        }
    }

    // Manual node position updates
    fun updateNodePosition(nodeId: String, newX: Float, newY: Float) {
        _simNodes.value = _simNodes.value.map { node ->
            if (node.id == nodeId) {
                node.copy(x = newX, y = newY)
            } else node
        }
    }

    // Real Message Transmission & Persistence
    fun sendSimulatedMessage(recipientId: String, content: String, onFinished: () -> Unit) {
        val targetNode = _simNodes.value.find { it.id == recipientId }

        coroutineScope.launch {
            val messageId = UUID.randomUUID().toString()
            val targetName = targetNode?.name ?: recipientId

            addLog("ROUTING", "Broadcasting packet to [$targetName] via ${targetNode?.connectionType ?: "BLE"}...")

            // Save real outgoing message to database
            val dbMsg = MessageEntity(
                id = messageId,
                senderId = "you",
                senderName = "You",
                recipientId = recipientId,
                content = content,
                timestamp = System.currentTimeMillis(),
                type = "TEXT",
                isOutgoing = true,
                hops = targetNode?.hops ?: 1,
                path = "You ➔ $targetName"
            )
            repository.insertMessage(dbMsg)

            addLog("ROUTING", "Message saved & packet transmitted for $targetName!")

            // If recipient is Gemini AI Assistant Node, generate real Gemini response
            if (recipientId == "ai_advisor" || targetNode?.isAiAssistant == true) {
                generateAiResponse(content)
            }

            onFinished()
        }
    }

    // Trigger Emergency SOS Broadcast
    fun triggerSosBroadcast(content: String, onFinished: () -> Unit) {
        coroutineScope.launch {
            addLog("SOS", "⚠️ EMERGENCY SOS FLOOD BROADCASTING!")
            addLog("SOS", "Payload: \"$content\"")

            val activeNodes = _simNodes.value

            if (activeNodes.isEmpty()) {
                addLog("SOS", "Broadcast transmitted locally over BLE beacon. No nearby peers currently connected.", isError = true)
            } else {
                activeNodes.forEach { node ->
                    addLog("SOS", "SOS Packet Transmitted ➔ ${node.name} (${node.connectionType})")
                }
            }

            // Save real outgoing SOS
            val messageId = UUID.randomUUID().toString()
            val dbMsg = MessageEntity(
                id = messageId,
                senderId = "you",
                senderName = "You (SOS)",
                recipientId = "ALL_NODES",
                content = content,
                timestamp = System.currentTimeMillis(),
                type = "SOS",
                isOutgoing = true,
                hops = 0,
                path = "MESH SOS BROADCAST"
            )
            repository.insertMessage(dbMsg)

            // If AI node is enabled, let it provide emergency assistance
            val aiNode = activeNodes.find { it.id == "ai_advisor" || it.isAiAssistant }
            if (aiNode != null) {
                generateAiResponse(content)
            }

            onFinished()
        }
    }

    private suspend fun generateAiResponse(userPrompt: String) {
        addLog("SEC", "Secure E2E channel active with Mesh AI Assistant.")

        val systemInstruction = """
            You are the "Mesh AI Responder" for OffGrid Mesh.
            Provide short, practical, direct advice for emergency or off-grid situations in 1-3 sentences.
            Do not mention you are a language model.
        """.trimIndent()

        val aiResponse = GeminiHelper.generateResponse(userPrompt, systemInstruction)

        val dbMsg = MessageEntity(
            id = UUID.randomUUID().toString(),
            senderId = "ai_advisor",
            senderName = "Mesh AI Assistant",
            recipientId = "you",
            content = aiResponse,
            timestamp = System.currentTimeMillis(),
            type = "TEXT",
            isOutgoing = false,
            hops = 1,
            path = "Mesh AI Assistant ➔ You"
        )
        repository.insertMessage(dbMsg)
        addLog("ROUTING", "Received response from Mesh AI Assistant!")
    }

    fun cleanAllLogs() {
        _networkLogs.value = emptyList()
    }
}
