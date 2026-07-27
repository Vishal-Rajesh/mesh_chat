package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.MessageEntity
import com.example.mesh.MeshManager
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshAppScreen(viewModel: MeshViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val logs by viewModel.networkLogs.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val simNodes by viewModel.simNodes.collectAsStateWithLifecycle()
    val currentRecipientId by viewModel.currentRecipientId.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()

    val isBleScanning by viewModel.isBleScanning.collectAsStateWithLifecycle()
    val isBleAdvertising by viewModel.isBleAdvertising.collectAsStateWithLifecycle()
    val isWifiP2pSearching by viewModel.isWifiP2pSearching.collectAsStateWithLifecycle()

    var hasPermissions by remember { mutableStateOf(viewModel.checkPermissionsGranted()) }
    var showAddNodeDialog by remember { mutableStateOf(false) }

    // Required Permissions Array
    val permissionsToRequest = remember {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        list.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { resultsMap ->
        val granted = resultsMap.values.all { it }
        hasPermissions = granted
        if (granted) {
            viewModel.meshManager.addLog("PERM", "Bluetooth & Location permissions granted!")
            viewModel.toggleBleScanning()
        } else {
            viewModel.meshManager.addLog("PERM", "Some permissions were denied by user.", isError = true)
        }
    }

    Scaffold(
        topBar = {
            // Header block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BentoBg)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isBleScanning || isBleAdvertising) BentoGreen else BentoPinkDark)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBleScanning) "SCANNING BLE RADIOS" else if (isBleAdvertising) "BEACON ACTIVE" else "OFFLINE MESH COMMUNICATOR",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = BentoTextFaint,
                                letterSpacing = 1.2.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "OffGrid Mesh",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoText,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    Row {
                        IconButton(
                            onClick = { showAddNodeDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(BentoPinkLight)
                        ) {
                            Icon(Icons.Default.PersonAdd, "Pair Friend", tint = BentoPinkDark, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .border(BorderStroke(1.dp, Color(0xFFF5BBB1).copy(alpha = 0.3f)))
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Radar, "Peers") },
                    label = { Text("Mesh Radar", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BentoPinkDark,
                        selectedTextColor = BentoTextFaint,
                        indicatorColor = BentoPink,
                        unselectedIconColor = BentoTextMuted,
                        unselectedTextColor = BentoTextMuted
                    ),
                    modifier = Modifier.testTag("tab_map")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.ChatBubble, "Chat") },
                    label = { Text("Mesh Chat", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BentoPinkDark,
                        selectedTextColor = BentoTextFaint,
                        indicatorColor = BentoPink,
                        unselectedIconColor = BentoTextMuted,
                        unselectedTextColor = BentoTextMuted
                    ),
                    modifier = Modifier.testTag("tab_chat")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Warning, "SOS") },
                    label = { Text("SOS Flood", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BentoPinkDark,
                        selectedTextColor = BentoTextFaint,
                        indicatorColor = BentoPink,
                        unselectedIconColor = BentoTextMuted,
                        unselectedTextColor = BentoTextMuted
                    ),
                    modifier = Modifier.testTag("tab_sos")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Terminal, "Logs") },
                    label = { Text("Radio Logs", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BentoPinkDark,
                        selectedTextColor = BentoTextFaint,
                        indicatorColor = BentoPink,
                        unselectedIconColor = BentoTextMuted,
                        unselectedTextColor = BentoTextMuted
                    ),
                    modifier = Modifier.testTag("tab_logs")
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BentoBg)
        ) {
            // 1. Permissions Banner if missing
            if (!hasPermissions) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BentoWarningBg),
                    border = BorderStroke(1.dp, BentoWarningBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Permission Warning",
                            tint = BentoWarningText,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bluetooth Permissions Required",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoWarningText
                            )
                            Text(
                                text = "Allow Bluetooth & Location to scan and message real nearby devices offline.",
                                fontSize = 11.sp,
                                color = BentoTextMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { permissionLauncher.launch(permissionsToRequest) },
                            colors = ButtonDefaults.buttonColors(containerColor = BentoPinkDark),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. Hardware Radio Toggle Bar
            RadioControlBar(
                isBleScanning = isBleScanning,
                isBleAdvertising = isBleAdvertising,
                isWifiSearching = isWifiP2pSearching,
                onToggleScan = {
                    if (!hasPermissions) {
                        permissionLauncher.launch(permissionsToRequest)
                    } else {
                        viewModel.toggleBleScanning()
                    }
                },
                onToggleAdvertise = {
                    if (!hasPermissions) {
                        permissionLauncher.launch(permissionsToRequest)
                    } else {
                        viewModel.toggleBleAdvertising()
                    }
                },
                onToggleWifi = {
                    if (!hasPermissions) {
                        permissionLauncher.launch(permissionsToRequest)
                    } else {
                        viewModel.toggleWifiDirectSearching()
                    }
                },
                onAddFriend = { showAddNodeDialog = true }
            )

            // Screen Content based on selected tab
            AnimatedContent(
                targetState = selectedTab,
                label = "tab_transition",
                modifier = Modifier.weight(1f)
            ) { tab ->
                when (tab) {
                    0 -> MeshRadarTab(
                        nodes = simNodes,
                        isScanning = isBleScanning,
                        onNodeClick = { nodeId ->
                            viewModel.selectRecipient(nodeId)
                            selectedTab = 1 // Switch to chat
                        },
                        onRequestScan = {
                            if (!hasPermissions) {
                                permissionLauncher.launch(permissionsToRequest)
                            } else {
                                viewModel.toggleBleScanning()
                            }
                        },
                        onEnableAi = { viewModel.enableGeminiAiNode() }
                    )
                    1 -> MeshChatTab(
                        nodes = simNodes,
                        messages = messages,
                        currentRecipientId = currentRecipientId,
                        isSending = isSending,
                        onSelectRecipient = { viewModel.selectRecipient(it) },
                        onSendMessage = { viewModel.sendMessage(it) }
                    )
                    2 -> SosTab(
                        nodes = simNodes,
                        isSending = isSending,
                        onTriggerSos = { viewModel.sendSosBroadcast(it) }
                    )
                    3 -> RadioLogsTab(
                        logs = logs,
                        onClearLogs = { viewModel.clearHistory() }
                    )
                }
            }
        }
    }

    // Manual Node Addition Dialog
    if (showAddNodeDialog) {
        AddNodeDialog(
            onDismiss = { showAddNodeDialog = false },
            onConfirm = { name, id ->
                viewModel.addCustomNode(name, id)
                showAddNodeDialog = false
            }
        )
    }
}

@Composable
fun RadioControlBar(
    isBleScanning: Boolean,
    isBleAdvertising: Boolean,
    isWifiSearching: Boolean,
    onToggleScan: () -> Unit,
    onToggleAdvertise: () -> Unit,
    onToggleWifi: () -> Unit,
    onAddFriend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = isBleScanning,
            onClick = onToggleScan,
            label = { Text(if (isBleScanning) "Scanning BLE" else "Scan BLE", fontSize = 11.sp) },
            leadingIcon = {
                Icon(
                    imageVector = if (isBleScanning) Icons.Default.BluetoothSearching else Icons.Default.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = BentoPink,
                selectedLabelColor = BentoPinkDark
            ),
            shape = RoundedCornerShape(12.dp)
        )

        FilterChip(
            selected = isBleAdvertising,
            onClick = onToggleAdvertise,
            label = { Text(if (isBleAdvertising) "Beaconing" else "Beacon", fontSize = 11.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.CellTower,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = BentoPink,
                selectedLabelColor = BentoPinkDark
            ),
            shape = RoundedCornerShape(12.dp)
        )

        FilterChip(
            selected = isWifiSearching,
            onClick = onToggleWifi,
            label = { Text("Wi-Fi Direct", fontSize = 11.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = BentoPink,
                selectedLabelColor = BentoPinkDark
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun MeshRadarTab(
    nodes: List<MeshManager.SimNode>,
    isScanning: Boolean,
    onNodeClick: (String) -> Unit,
    onRequestScan: () -> Unit,
    onEnableAi: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (nodes.isEmpty()) {
            // Empty State Card
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BentoBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(BentoPinkLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BluetoothSearching,
                                contentDescription = "No Peers",
                                tint = BentoPinkDark,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No Nearby Mesh Nodes Discovered",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoText,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Turn on Bluetooth scanning to find active BLE peers around you, or pair a friend's device manually.",
                            fontSize = 12.sp,
                            color = BentoTextMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onRequestScan,
                                colors = ButtonDefaults.buttonColors(containerColor = BentoPinkDark),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isScanning) "Scanning..." else "Scan Nearby BLE")
                            }

                            OutlinedButton(
                                onClick = onEnableAi,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, BentoPinkDark)
                            ) {
                                Text("+ Mesh AI", color = BentoPinkDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // Radar Grid Canvas & List
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BentoBorder)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        drawCircle(color = BentoBorder, radius = 40.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                        drawCircle(color = BentoBorder, radius = 80.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                        drawCircle(color = BentoBorder, radius = 120.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))

                        drawLine(color = BentoBorder, start = Offset(0f, center.y), end = Offset(size.width, center.y))
                        drawLine(color = BentoBorder, start = Offset(center.x, 0f), end = Offset(center.x, size.height))

                        // Draw "You" node at center
                        drawCircle(color = BentoPinkDark, radius = 8.dp.toPx(), center = center)
                    }

                    Text(
                        text = "YOU (CENTER)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoPinkDark,
                        modifier = Modifier.padding(top = 28.dp)
                    )
                }
            }

            Text(
                text = "DISCOVERED NODES (${nodes.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = BentoTextFaint,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(nodes) { node ->
                    DiscoveredNodeCard(node = node, onClick = { onNodeClick(node.id) })
                }
            }
        }
    }
}

@Composable
fun DiscoveredNodeCard(node: MeshManager.SimNode, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BentoBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (node.isAiAssistant) BentoPurple else BentoPinkLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (node.isAiAssistant) Icons.Default.SmartToy else Icons.Default.BluetoothConnected,
                        contentDescription = node.name,
                        tint = if (node.isAiAssistant) BentoPurpleDark else BentoPinkDark,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = node.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoText
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${node.connectionType} • RSSI: ${node.rssi} dBm",
                            fontSize = 11.sp,
                            color = BentoTextMuted
                        )
                    }
                }
            }

            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = BentoPinkLight),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Chat", fontSize = 12.sp, color = BentoPinkDark, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MeshChatTab(
    nodes: List<MeshManager.SimNode>,
    messages: List<MessageEntity>,
    currentRecipientId: String,
    isSending: Boolean,
    onSelectRecipient: (String) -> Unit,
    onSendMessage: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val filteredMessages = remember(messages, currentRecipientId) {
        if (currentRecipientId == "ALL_NODES") {
            messages
        } else {
            messages.filter { 
                (it.senderId == "you" && it.recipientId == currentRecipientId) ||
                (it.senderId == currentRecipientId && it.recipientId == "you") ||
                (it.recipientId == "ALL_NODES")
            }
        }
    }

    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty()) {
            listState.animateScrollToItem(filteredMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Peer Selector Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = currentRecipientId == "ALL_NODES",
                onClick = { onSelectRecipient("ALL_NODES") },
                label = { Text("Broadcast", fontSize = 11.sp) },
                shape = RoundedCornerShape(12.dp)
            )

            nodes.forEach { node ->
                FilterChip(
                    selected = currentRecipientId == node.id,
                    onClick = { onSelectRecipient(node.id) },
                    label = { Text(node.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Message List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (filteredMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No messages yet. Type a message to broadcast over the mesh.",
                        fontSize = 12.sp,
                        color = BentoTextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredMessages) { msg ->
                        MessageBubble(message = msg)
                    }
                }
            }
        }

        // Input Field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Type mesh message...", fontSize = 13.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("message_input"),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White,
                    unfocusedBorderColor = BentoBorder,
                    focusedBorderColor = BentoPinkDark
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onSendMessage(textInput)
                        textInput = ""
                    }
                },
                enabled = textInput.isNotBlank() && !isSending,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (textInput.isNotBlank()) BentoPinkDark else BentoBorder)
                    .testTag("send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Message",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun MessageBubble(message: MessageEntity) {
    val isMe = message.isOutgoing
    val isSos = message.type == "SOS"

    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = remember(message.timestamp) { dateFormat.format(Date(message.timestamp)) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isSos -> BentoWarningBg
                    isMe -> BentoPink
                    else -> Color.White
                }
            ),
            border = BorderStroke(1.dp, if (isSos) BentoWarningBorder else BentoBorder),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isMe) {
                    Text(
                        text = message.senderName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoPinkDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Text(
                    text = message.content,
                    fontSize = 14.sp,
                    color = BentoText
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.path,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = BentoTextFaint
                    )
                    Text(
                        text = timeStr,
                        fontSize = 9.sp,
                        color = BentoTextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun SosTab(
    nodes: List<MeshManager.SimNode>,
    isSending: Boolean,
    onTriggerSos: (String) -> Unit
) {
    var sosMessage by remember { mutableStateOf("EMERGENCY: Need immediate assistance at coordinates!") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BentoWarningBg),
            border = BorderStroke(1.dp, BentoWarningBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "SOS", tint = BentoWarningText, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Emergency SOS Flood", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BentoWarningText)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Broadcasting an SOS flood message will transmit your emergency status over BLE & Wi-Fi Direct to all nearby mesh nodes.",
                    fontSize = 12.sp,
                    color = BentoTextMuted
                )
            }
        }

        OutlinedTextField(
            value = sosMessage,
            onValueChange = { sosMessage = it },
            label = { Text("SOS Message Content") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )

        Button(
            onClick = { onTriggerSos(sosMessage) },
            enabled = !isSending,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            shape = CircleShape,
            modifier = Modifier
                .size(140.dp)
                .testTag("sos_button")
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.WifiTetheringError, contentDescription = "SOS", tint = Color.White, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("FLOOD SOS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun RadioLogsTab(
    logs: List<MeshManager.LogEntry>,
    onClearLogs: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("RADIO & SYSTEM LOGS", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = BentoTextFaint)
            TextButton(onClick = onClearLogs) {
                Text("Clear Logs", fontSize = 11.sp, color = BentoPinkDark)
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White, RoundedCornerShape(12.dp))
                .border(1.dp, BentoBorder, RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            items(logs) { entry ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "[${dateFormat.format(Date(entry.timestamp))}] ",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = BentoTextMuted
                    )
                    Text(
                        text = "[${entry.tag}] ",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (entry.isError) Color.Red else BentoPinkDark
                    )
                    Text(
                        text = entry.message,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = BentoText
                    )
                }
            }
        }
    }
}

@Composable
fun AddNodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, id: String) -> Unit
) {
    var nameText by remember { mutableStateOf("") }
    var idText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pair Friend / Add Node", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter the name and device identifier or MAC address of your friend's phone to pair directly.", fontSize = 12.sp, color = BentoTextMuted)
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Friend's Name / Device Name") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = idText,
                    onValueChange = { idText = it },
                    label = { Text("Device ID / MAC (Optional)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(nameText, idText) },
                enabled = nameText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BentoPinkDark)
            ) {
                Text("Add Mesh Node")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
