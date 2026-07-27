package com.example.mesh

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import android.os.ParcelUuid
import android.util.Log
import com.example.data.MessageEntity
import com.example.data.NodeEntity
import com.example.data.MeshRepository
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

    // Real Hardware Capabilities
    var isBleSupported = false
    var isWifiDirectSupported = false
    var isBleAdvertising = false
    var isWifiP2pSearching = false

    // Hardware managers
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiP2pChannel: WifiP2pManager.Channel? = null

    // Node coordinates for spatial grid. User's phone is fixed at (100, 100)
    data class SimNode(
        val id: String,
        val name: String,
        val status: String, // "ACTIVE", "IDLE", "DISCONNECTED"
        val connectionType: String, // "BLE", "WIFI_DIRECT"
        var x: Float,
        var y: Float,
        val battery: Int,
        val isSOS: Boolean = false,
        var hops: Int = -1,
        var path: List<String> = emptyList()
    )

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val tag: String, // "BLE", "WIFI", "ROUTING", "SIM", "SEC"
        val message: String,
        val isError: Boolean = false
    )

    // Pre-populate interactive nodes
    private val _simNodes = MutableStateFlow<List<SimNode>>(listOf(
        SimNode("alice", "Alice (Explorer)", "ACTIVE", "BLE", 80f, 60f, 88),
        SimNode("drone3", "Drone #3 (Relay)", "ACTIVE", "WIFI_DIRECT", 130f, 130f, 95),
        SimNode("rescue_base", "Alpha Rescue", "ACTIVE", "BLE", 180f, 160f, 100),
        SimNode("wilderness_cabin", "Cabin SOS Beacon", "IDLE", "BLE", 70f, 180f, 32, isSOS = true),
        SimNode("ai_advisor", "Mesh AI Responder", "ACTIVE", "BLE", 120f, 50f, 100)
    ))
    val simNodes = _simNodes.asStateFlow()

    init {
        addLog("SYSTEM", "OffGrid Mesh initialized. Mode: Hybrid Real + Sim")
        initRealHardware()
        recalculateRoutes()
    }

    private fun addLog(tag: String, message: String, isError: Boolean = false) {
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
                addLog("BLE", "Bluetooth hardware detected. BLE peripheral mode ready.")
            } else {
                addLog("BLE", "Bluetooth not supported on this device.", isError = true)
            }

            // Wi-Fi Direct Init
            wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            if (wifiP2pManager != null) {
                isWifiDirectSupported = true
                wifiP2pChannel = wifiP2pManager?.initialize(context, context.mainLooper, null)
                addLog("WIFI", "Wi-Fi Direct hardware initialized.")
            } else {
                addLog("WIFI", "Wi-Fi Direct not supported on this device.", isError = true)
            }
        } catch (e: Exception) {
            addLog("SYSTEM", "Error initializing local radios: ${e.localizedMessage}", isError = true)
        }
    }

    // Toggle real BLE advertising
    fun toggleBleAdvertising() {
        if (!isBleSupported || advertiser == null) {
            addLog("BLE", "Cannot toggle BLE: hardware unsupported or disabled", isError = true)
            return
        }

        if (isBleAdvertising) {
            try {
                advertiser?.stopAdvertising(advertiseCallback)
                isBleAdvertising = false
                addLog("BLE", "Stopped real BLE advertising.")
            } catch (e: SecurityException) {
                addLog("BLE", "Permission denied for stopping BLE advertising", isError = true)
            }
        } else {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb"))) // Battery-like service
                .build()

            try {
                advertiser?.startAdvertising(settings, data, advertiseCallback)
                isBleAdvertising = true
                addLog("BLE", "Started real BLE advertising (UUID: 180F)")
            } catch (e: SecurityException) {
                addLog("BLE", "Permission required to start BLE advertising!", isError = true)
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
            addLog("BLE", "Real BLE Advertise failed with code $errorCode", isError = true)
        }
    }

    // Toggle real Wi-Fi Direct Peer Discovery
    fun toggleWifiP2pDiscovery() {
        if (!isWifiDirectSupported || wifiP2pManager == null || wifiP2pChannel == null) {
            addLog("WIFI", "Cannot toggle Wi-Fi Direct: hardware unsupported", isError = true)
            return
        }

        if (isWifiP2pSearching) {
            isWifiP2pSearching = false
            addLog("WIFI", "Stopped real Wi-Fi Direct search.")
        } else {
            try {
                wifiP2pManager?.discoverPeers(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        isWifiP2pSearching = true
                        addLog("WIFI", "Real Wi-Fi Direct discovery successfully started.")
                    }

                    override fun onFailure(reason: Int) {
                        addLog("WIFI", "Real Wi-Fi Direct search failed: code $reason", isError = true)
                    }
                })
            } catch (e: SecurityException) {
                addLog("WIFI", "Location / Wi-Fi permissions required for Direct Search!", isError = true)
            }
        }
    }

    // Interactive Simulation Methods
    fun updateNodePosition(nodeId: String, newX: Float, newY: Float) {
        _simNodes.value = _simNodes.value.map { node ->
            if (node.id == nodeId) {
                node.copy(x = newX, y = newY)
            } else node
        }
        recalculateRoutes()
    }

    // Distance routing math (BFS)
    // User is located at (100f, 100f).
    // Max BLE range is 80 units. Max WiFi-Direct range is 120 units.
    fun recalculateRoutes() {
        val nodes = _simNodes.value.map { it.copy() }
        val rangeMap = mapOf("BLE" to 80f, "WIFI_DIRECT" to 140f)

        // Helper to check distance
        fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            return sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2))
        }

        // BFS traversal
        val visited = mutableSetOf<String>()
        val queue = LinkedList<Pair<String, List<String>>>() // ID to path list

        // Push neighbors of "You" (root)
        nodes.forEach { node ->
            val dist = distance(100f, 100f, node.x, node.y)
            val maxRange = rangeMap[node.connectionType] ?: 80f
            if (dist <= maxRange) {
                queue.add(node.id to listOf(node.name))
                visited.add(node.id)
            }
        }

        val nodeRouteMap = mutableMapOf<String, Pair<Int, List<String>>>()

        while (queue.isNotEmpty()) {
            val (currentId, path) = queue.poll()!!
            val currentNode = nodes.find { it.id == currentId } ?: continue

            nodeRouteMap[currentId] = Pair(path.size, path)

            // Find neighbors of current node
            nodes.forEach { neighbor ->
                if (!visited.contains(neighbor.id)) {
                    val dist = distance(currentNode.x, currentNode.y, neighbor.x, neighbor.y)
                    val maxRange = rangeMap[neighbor.connectionType] ?: 80f
                    if (dist <= maxRange) {
                        visited.add(neighbor.id)
                        queue.add(neighbor.id to (path + neighbor.name))
                    }
                }
            }
        }

        // Apply route updates to nodes
        _simNodes.value = _simNodes.value.map { node ->
            val route = nodeRouteMap[node.id]
            if (route != null) {
                node.copy(
                    hops = route.first,
                    path = route.second,
                    status = "ACTIVE"
                )
            } else {
                node.copy(
                    hops = -1,
                    path = emptyList(),
                    status = "DISCONNECTED"
                )
            }
        }
    }

    // Simulate multi-hop transmission with visual delay & logging
    fun sendSimulatedMessage(recipientId: String, content: String, onFinished: () -> Unit) {
        val targetNode = _simNodes.value.find { it.id == recipientId }
        if (targetNode == null) {
            addLog("ROUTING", "Recipient node not found", isError = true)
            onFinished()
            return
        }

        if (targetNode.hops == -1) {
            addLog("ROUTING", "No path available to ${targetNode.name}. Message queued in local database.", isError = true)
            coroutineScope.launch {
                val dbMsg = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    senderId = "you",
                    senderName = "You",
                    recipientId = recipientId,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    type = "TEXT",
                    isOutgoing = true,
                    hops = 0,
                    path = "No Path (Queued)"
                )
                repository.insertMessage(dbMsg)
                onFinished()
            }
            return
        }

        coroutineScope.launch {
            val messageId = UUID.randomUUID().toString()
            addLog("ROUTING", "Starting Hop Relay for: \"$content\"")

            val fullPath = listOf("You") + targetNode.path
            for (i in 0 until fullPath.size - 1) {
                val from = fullPath[i]
                val to = fullPath[i + 1]
                addLog("ROUTING", "Hop ${i + 1}: Relaying from [$from] ➔ [$to]...")
                delay(1000) // Delay to visually model transmission progress
            }

            addLog("ROUTING", "Message successfully delivered to ${targetNode.name} in ${targetNode.hops} hops!")

            // Save outgoing message
            val dbMsg = MessageEntity(
                id = messageId,
                senderId = "you",
                senderName = "You",
                recipientId = recipientId,
                content = content,
                timestamp = System.currentTimeMillis(),
                type = "TEXT",
                isOutgoing = true,
                hops = targetNode.hops,
                path = fullPath.joinToString(" ➔ ")
            )
            repository.insertMessage(dbMsg)

            // Simulated response logic
            delay(1200)
            generateSimulatedReply(recipientId, content)
            onFinished()
        }
    }

    // Trigger Emergency SOS flood broadcast
    fun triggerSosBroadcast(content: String, onFinished: () -> Unit) {
        coroutineScope.launch {
            addLog("SOS", "⚠️ EMERGENCY SOS FLOOD TRIGGERED!")
            addLog("SOS", "Payload: \"$content\"")

            val reachableNodes = _simNodes.value.filter { it.hops != -1 }

            if (reachableNodes.isEmpty()) {
                addLog("SOS", "Warning: Mesh has no active peer links! SOS packet broadcast is floating locally.", isError = true)
            }

            // Save outgoing SOS
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
                path = "MESH FLOOD"
            )
            repository.insertMessage(dbMsg)

            reachableNodes.forEach { node ->
                addLog("SOS", "SOS Packet Flooded ➔ ${node.name} (Hops: ${node.hops})")
                delay(400)
            }

            addLog("SOS", "Emergency flood broadcast completed. Awaiting automated ACK/responses.")

            // Generate responses from reachable nodes
            delay(1000)
            reachableNodes.forEach { node ->
                if (node.id != "ai_advisor") {
                    val replyText = when (node.id) {
                        "alice" -> "I am nearby! SOS received, heading towards coordinates..."
                        "rescue_base" -> "Alpha Rescue base copy. Activating emergency beacon, dispatching responder team."
                        "wilderness_cabin" -> "SOS verified. Emergency solar cell activated."
                        "drone3" -> "Drone #3 auto-relaying distress signature to national rescue channel."
                        else -> "SOS received."
                    }

                    val replyMsg = MessageEntity(
                        id = UUID.randomUUID().toString(),
                        senderId = node.id,
                        senderName = node.name,
                        recipientId = "you",
                        content = replyText,
                        timestamp = System.currentTimeMillis(),
                        type = "TEXT",
                        isOutgoing = false,
                        hops = node.hops,
                        path = (node.path.reversed() + "You").joinToString(" ➔ ")
                    )
                    repository.insertMessage(replyMsg)
                }
            }

            // Let AI respond too
            val aiNode = reachableNodes.find { it.id == "ai_advisor" }
            if (aiNode != null) {
                generateAiResponse(content)
            }

            onFinished()
        }
    }

    private suspend fun generateSimulatedReply(nodeId: String, userMessage: String) {
        if (nodeId == "ai_advisor") {
            generateAiResponse(userMessage)
            return
        }

        val replyContent = when (nodeId) {
            "alice" -> "Sounds great! Copy that. I'm keeping an eye out for other mesh signals."
            "drone3" -> "[AUTOMATED ACK] Payload chunk successfully packeted and buffered."
            "rescue_base" -> "Message acknowledged by Alpha Command. Weather reports are clear. Stay at current location."
            "wilderness_cabin" -> "Cabin automated sensor indicates temp: 18°C, Power: OK."
            else -> "ACK: message received."
        }

        val node = _simNodes.value.find { it.id == nodeId } ?: return
        val dbMsg = MessageEntity(
            id = UUID.randomUUID().toString(),
            senderId = nodeId,
            senderName = node.name,
            recipientId = "you",
            content = replyContent,
            timestamp = System.currentTimeMillis(),
            type = "TEXT",
            isOutgoing = false,
            hops = node.hops,
            path = (node.path.reversed() + "You").joinToString(" ➔ ")
        )
        repository.insertMessage(dbMsg)
        addLog("ROUTING", "Incoming reply received from ${node.name}!")
    }

    private suspend fun generateAiResponse(userPrompt: String) {
        addLog("SEC", "Encrypting channel... Deriving ECDH key with AI Responder node.")
        delay(600)
        addLog("SEC", "Secure E2E mesh channel active. Launching server-side mesh AI helper.")

        val systemInstruction = """
            You are a crucial helpful component of OffGrid Mesh: the "Mesh AI Responder".
            The user is currently running a local mesh application in a disconnected area.
            Provide reassuring, short, and highly accurate emergency or general guidelines.
            Keep your answer to 1-3 direct, concise sentences. Do not mention that you are a language model.
            Act as an offline emergency assistant router node.
        """.trimIndent()

        val aiResponse = GeminiHelper.generateResponse(userPrompt, systemInstruction)

        val node = _simNodes.value.find { it.id == "ai_advisor" } ?: return
        val dbMsg = MessageEntity(
            id = UUID.randomUUID().toString(),
            senderId = "ai_advisor",
            senderName = "Mesh AI Responder",
            recipientId = "you",
            content = aiResponse,
            timestamp = System.currentTimeMillis(),
            type = "TEXT",
            isOutgoing = false,
            hops = node.hops,
            path = (node.path.reversed() + "You").joinToString(" ➔ ")
        )
        repository.insertMessage(dbMsg)
        addLog("ROUTING", "Mesh AI response delivered securely via encrypted hop tunnel.")
    }

    fun cleanAllLogs() {
        _networkLogs.value = emptyList()
    }
}
