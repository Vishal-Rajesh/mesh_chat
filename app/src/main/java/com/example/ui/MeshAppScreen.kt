package com.example.ui

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
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
    var selectedTab by remember { mutableIntStateOf(0) }
    val logs by viewModel.networkLogs.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val simNodes by viewModel.simNodes.collectAsStateWithLifecycle()
    val currentRecipientId by viewModel.currentRecipientId.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            // Elegant Bento Style Header block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BentoBg)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (viewModel.meshManager.isBleAdvertising) "HYBRID RADIO ACTIVE" else "OFFLINE COMMUNICATOR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = BentoTextFaint,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "OffGrid Mesh",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoText,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    // Avatar Person Icon matching Bento template
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(BentoPink)
                            .border(1.dp, BentoPinkBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User Avatar",
                            tint = BentoPinkDark,
                            modifier = Modifier.size(24.dp)
                        )
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
                    icon = { Icon(Icons.Default.Map, "Map") },
                    label = { Text("Mesh Map", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
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
                    icon = { Icon(Icons.Default.Dns, "Logs") },
                    label = { Text("Diagnostics", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
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
        },
        containerColor = BentoBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BentoBg)
        ) {
            when (selectedTab) {
                0 -> MapTab(viewModel, simNodes)
                1 -> ChatTab(viewModel, messages, simNodes, currentRecipientId, isSending)
                2 -> SosTab(viewModel, messages, simNodes, isSending)
                3 -> LogsTab(viewModel, logs)
            }
        }
    }
}

// ----------------------------------------------------
// TAB 1: INTERACTIVE SPATIAL MESH MAP & SIMULATOR
// ----------------------------------------------------
@Composable
fun MapTab(viewModel: MeshViewModel, simNodes: List<MeshManager.SimNode>) {
    var draggingNodeId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // High-fidelity Bento Grid Map Container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BentoPinkBorder.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header inside the map box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Interactive Mesh Nodes",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoText
                        )
                        Text(
                            text = "Drag nodes closer or further to dynamically route hops",
                            fontSize = 11.sp,
                            color = BentoTextMuted
                        )
                    }
                    IconButton(
                        onClick = { viewModel.meshManager.recalculateRoutes() },
                        modifier = Modifier
                            .size(36.dp)
                            .background(BentoPink, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recalculate Routes",
                            tint = BentoPinkDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Interactive Radar Space
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(BentoBg.copy(alpha = 0.5f))
                        .border(BorderStroke(1.dp, BentoPinkBorder.copy(alpha = 0.2f)))
                ) {
                    val widthPx = constraints.maxWidth.toFloat()
                    val heightPx = constraints.maxHeight.toFloat()

                    val scaleX = widthPx / 200f
                    val scaleY = heightPx / 200f

                    // Canvas ranges and links
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val userOffset = Offset(100f * scaleX, 100f * scaleY)

                        // BLE Range
                        drawCircle(
                            color = BentoYellow.copy(alpha = 0.15f),
                            radius = 80f * scaleX,
                            center = userOffset
                        )
                        // WiFi-Direct Range
                        drawCircle(
                            color = BentoBlue.copy(alpha = 0.12f),
                            radius = 140f * scaleX,
                            center = userOffset
                        )

                        // Draw hops lines
                        simNodes.forEach { node ->
                            val nodeOffset = Offset(node.x * scaleX, node.y * scaleY)
                            val isConnected = node.hops != -1

                            if (isConnected) {
                                val path = node.path
                                if (path.size == 1) {
                                    drawLine(
                                        color = if (node.connectionType == "WIFI_DIRECT") BentoBlueText else BentoTextFaint,
                                        start = userOffset,
                                        end = nodeOffset,
                                        strokeWidth = if (node.connectionType == "WIFI_DIRECT") 5f else 3f,
                                        pathEffect = if (node.connectionType == "BLE") PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f) else null
                                    )
                                } else {
                                    var previousOffset = userOffset
                                    path.forEach { pathName ->
                                        val intermediateNode = simNodes.find { it.name == pathName }
                                        if (intermediateNode != null) {
                                            val currentOffset = Offset(intermediateNode.x * scaleX, intermediateNode.y * scaleY)
                                            drawLine(
                                                color = if (intermediateNode.connectionType == "WIFI_DIRECT") BentoBlueText else BentoPurpleText,
                                                start = previousOffset,
                                                end = currentOffset,
                                                strokeWidth = 3f,
                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                                            )
                                            previousOffset = currentOffset
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Render You in Center
                    val youX = 100f * scaleX
                    val youY = 100f * scaleY
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(youX.roundToInt() - 18.dp.value.roundToInt(), youY.roundToInt() - 18.dp.value.roundToInt()) }
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(BentoPink, BentoPinkBorder)))
                            .border(2.dp, BentoPinkDark, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("YOU", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = BentoPinkDark)
                    }

                    // Render Peers
                    simNodes.forEach { node ->
                        val nodeX = node.x * scaleX
                        val nodeY = node.y * scaleY

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(nodeX.roundToInt() - 22.dp.value.roundToInt(), nodeY.roundToInt() - 22.dp.value.roundToInt()) }
                                .size(44.dp)
                                .pointerInput(node.id) {
                                    detectDragGestures(
                                        onDragStart = { draggingNodeId = node.id },
                                        onDragEnd = { draggingNodeId = null },
                                        onDragCancel = { draggingNodeId = null },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val deltaX = dragAmount.x / scaleX
                                            val deltaY = dragAmount.y / scaleY
                                            val clampX = (node.x + deltaX).coerceIn(5f, 195f)
                                            val clampY = (node.y + deltaY).coerceIn(5f, 195f)
                                            viewModel.updateNodePosition(node.id, clampX, clampY)
                                        }
                                    )
                                }
                                .clip(CircleShape)
                                .background(
                                    if (node.status == "ACTIVE") {
                                        when (node.id) {
                                            "ai_advisor" -> BentoGreen
                                            "drone3" -> BentoBlue
                                            else -> BentoPurple
                                        }
                                    } else Color.White
                                )
                                .border(
                                    width = if (node.id == draggingNodeId) 2.dp else 1.dp,
                                    color = if (node.hops != -1) {
                                        if (node.connectionType == "WIFI_DIRECT") BentoBlueText else BentoPurpleText
                                    } else {
                                        if (node.isSOS) BentoTextFaint else Color.LightGray
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (node.id) {
                                    "alice" -> Icons.Default.DirectionsWalk
                                    "drone3" -> Icons.Default.Flight
                                    "rescue_base" -> Icons.Default.Cabin
                                    "wilderness_cabin" -> Icons.Default.Warning
                                    "ai_advisor" -> Icons.Default.Psychology
                                    else -> Icons.Default.Person
                                },
                                contentDescription = node.name,
                                tint = if (node.hops != -1) {
                                    when (node.id) {
                                        "ai_advisor" -> BentoGreenText
                                        "drone3" -> BentoBlueText
                                        else -> BentoPurpleText
                                    }
                                } else {
                                    if (node.isSOS) BentoTextFaint else BentoTextMuted
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bento Grid Details Blocks: Layout inside rows/columns for Bento visual look
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left Bento block (Pantry-inspired)
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(115.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BentoPurple),
                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.04f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Router, null, tint = BentoPurpleText, modifier = Modifier.size(16.dp))
                    }
                    Column {
                        Text("ROUTER", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BentoPurpleText)
                        Text(
                            "${simNodes.count { it.hops != -1 }} Peers\nActive Hop Path",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoText,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // Right Bento block (Daily Goal-inspired)
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(115.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BentoBlue),
                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.04f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.WifiTethering, null, tint = BentoBlueText, modifier = Modifier.size(16.dp))
                    }
                    Column {
                        Text("SIGNAL RANGE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BentoBlueText)
                        Text(
                            "80m BLE\n140m Wi-Fi P2P",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoText,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 2: ROBUST MULTI-HOP OFFLINE CHAT CLIENT
// ----------------------------------------------------
@Composable
fun ChatTab(
    viewModel: MeshViewModel,
    messages: List<MessageEntity>,
    simNodes: List<MeshManager.SimNode>,
    currentRecipientId: String,
    isSending: Boolean
) {
    var chatInput by remember { mutableStateOf("") }
    val currentRecipient = simNodes.find { it.id == currentRecipientId }
    val filteredMessages = messages.filter {
        (it.recipientId == currentRecipientId && it.isOutgoing) ||
                (it.senderId == currentRecipientId && !it.isOutgoing)
    }

    val lazyListState = rememberLazyListState()
    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty()) {
            lazyListState.animateScrollToItem(filteredMessages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Conversation Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BentoPinkBorder.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var showDropdown by remember { mutableStateOf(false) }
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(BentoPink.copy(alpha = 0.5f))
                            .border(1.dp, BentoPinkBorder, RoundedCornerShape(12.dp))
                            .clickable { showDropdown = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (currentRecipientId) {
                                "ai_advisor" -> Icons.Default.Psychology
                                "rescue_base" -> Icons.Default.Cabin
                                "drone3" -> Icons.Default.Flight
                                else -> Icons.Default.Person
                            },
                            contentDescription = "Contact",
                            tint = BentoTextFaint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentRecipient?.name ?: "Select Contact",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoText
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, null, tint = BentoTextMuted, modifier = Modifier.size(16.dp))
                    }

                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        simNodes.forEach { node ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (node.hops != -1) BentoGreenText else BentoTextFaint)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(node.name, color = BentoText)
                                        if (node.hops != -1) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("(${node.hops}h)", fontSize = 10.sp, color = BentoTextFaint)
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.selectRecipient(node.id)
                                    showDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (currentRecipient != null && currentRecipient.hops != -1) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "CHANNEL SECURED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoGreenText,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Hops: ${currentRecipient.hops} (${currentRecipient.connectionType})",
                            fontSize = 11.sp,
                            color = BentoTextMuted
                        )
                    }
                } else {
                    Text(
                        text = "UNREACHABLE",
                        fontSize = 11.sp,
                        color = BentoTextFaint,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Messages List Frame
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            if (filteredMessages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = BentoTextMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ECDH Multi-Hop Connected",
                        color = BentoText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Messages sent are fully encrypted with automatic hop forwarding routing.",
                        color = BentoTextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredMessages) { msg ->
                        MessageBubble(msg)
                    }
                }
            }
        }

        // Send Input Area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BentoPinkBorder.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    placeholder = { Text("Write offgrid message...", color = BentoTextMuted.copy(alpha = 0.6f), fontSize = 13.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        cursorColor = BentoTextFaint,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = BentoText,
                        unfocusedTextColor = BentoText
                    ),
                    maxLines = 3,
                    enabled = !isSending
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (chatInput.isNotBlank()) {
                            viewModel.sendMessage(chatInput)
                            chatInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(if (chatInput.isNotBlank() && !isSending) BentoPink else BentoBg, CircleShape)
                        .testTag("chat_send_button"),
                    enabled = chatInput.isNotBlank() && !isSending
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = BentoPinkDark, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Send, "Send", tint = BentoPinkDark, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: MessageEntity) {
    val alignRight = msg.isOutgoing
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    val formattedTime = sdf.format(Date(msg.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = if (alignRight) Arrangement.End else Arrangement.Start
        ) {
            Column(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (alignRight) 16.dp else 4.dp,
                            bottomEnd = if (alignRight) 4.dp else 16.dp
                        )
                    )
                    .background(if (alignRight) BentoPink else BentoPurple)
                    .padding(12.dp)
            ) {
                if (!alignRight) {
                    Text(
                        text = msg.senderName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoPurpleText,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Text(
                    text = msg.content,
                    fontSize = 14.sp,
                    color = BentoText
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (msg.hops > 0) "${msg.hops} Hops: ${msg.path}" else "Direct",
                        fontSize = 9.sp,
                        color = BentoTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formattedTime,
                        fontSize = 9.sp,
                        color = BentoTextMuted
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 3: EMERGENCY SOS BROADCAST INTERFACE
// ----------------------------------------------------
@Composable
fun SosTab(
    viewModel: MeshViewModel,
    messages: List<MessageEntity>,
    simNodes: List<MeshManager.SimNode>,
    isSending: Boolean
) {
    var sosInput by remember { mutableStateOf("INJURY: Sprained ankle at cabin coordinates. Need dispatch/support.") }
    val sosMessages = messages.filter { it.type == "SOS" || it.recipientId == "ALL_NODES" || it.senderId == "you (SOS)" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Highly Styled Bento Emergency Alert Board
            Card(
                colors = CardDefaults.cardColors(containerColor = BentoPink),
                border = BorderStroke(1.dp, BentoPinkBorder),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, "SOS Alert", tint = BentoTextFaint, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EMERGENCY FLOOD MODE",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextFaint,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "SOS bypasses all normal handshakes and floods the mesh. Any nearby nodes will automatically forward and relay this distress signature.",
                        fontSize = 11.sp,
                        color = BentoPinkDark,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BentoPinkBorder.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Distress Broadcast Message",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoText,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    TextField(
                        value = sosInput,
                        onValueChange = { sosInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .testTag("sos_input_field"),
                        maxLines = 4,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = BentoBg,
                            unfocusedContainerColor = BentoBg,
                            focusedTextColor = BentoText,
                            unfocusedTextColor = BentoText,
                            cursorColor = BentoTextFaint
                        ),
                        enabled = !isSending
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (sosInput.isNotBlank()) {
                                viewModel.sendSosBroadcast(sosInput)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BentoTextFaint),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sos_broadcast_button"),
                        enabled = !isSending && sosInput.isNotBlank()
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.WifiTethering, "SOS Flood", tint = Color.White)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("LAUNCH SOS FLOOD", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        if (sosMessages.isNotEmpty()) {
            item {
                Text(
                    text = "Active Mesh Distress Packets",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = BentoTextFaint,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            items(sosMessages.reversed()) { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BentoPinkBorder.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "SOS_ID: ${msg.id.take(8).uppercase()}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = BentoTextFaint,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(msg.timestamp)),
                                fontSize = 10.sp,
                                color = BentoTextMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Source: ${msg.senderName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = msg.content,
                            fontSize = 13.sp,
                            color = BentoText
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 4: RADIO DIAGNOSTICS & PACKET LOG TERMINAL
// ----------------------------------------------------
@Composable
fun LogsTab(viewModel: MeshViewModel, logs: List<MeshManager.LogEntry>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Switch controllers styled side-by-side inside beautiful grid cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // BLE Beacon Card (Lavender Purple)
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BentoPurple),
                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.04f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("BLE BEACON", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BentoPurpleText)
                        Switch(
                            checked = viewModel.meshManager.isBleAdvertising,
                            onCheckedChange = { viewModel.toggleBleAdvertising() },
                            enabled = viewModel.meshManager.isBleSupported,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BentoPurpleText
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                    Column {
                        Text(
                            if (viewModel.meshManager.isBleAdvertising) "Beacon active" else "Beacon idle",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoText
                        )
                        Text(
                            "BLE advertising peripheral role status",
                            fontSize = 9.sp,
                            color = BentoTextMuted,
                            lineHeight = 11.sp
                        )
                    }
                }
            }

            // Wi-Fi Direct Card (Pale Blue)
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BentoBlue),
                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.04f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("WI-FI DIRECT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BentoBlueText)
                        Switch(
                            checked = viewModel.meshManager.isWifiP2pSearching,
                            onCheckedChange = { viewModel.toggleWifiDirectSearching() },
                            enabled = viewModel.meshManager.isWifiDirectSupported,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BentoBlueText
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                    Column {
                        Text(
                            if (viewModel.meshManager.isWifiP2pSearching) "Scanning peers" else "Scanning idle",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoText
                        )
                        Text(
                            "WifiP2p peer search & discovery status",
                            fontSize = 9.sp,
                            color = BentoTextMuted,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
        }

        // Rolling Network Packet Terminal Log
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BentoPinkBorder.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header of Logs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BentoPink.copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(BentoTextFaint)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DIAGNOSTIC STREAM",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = BentoText
                        )
                    }
                    Text(
                        text = "RESET DB & LOGS",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = BentoTextFaint,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { viewModel.clearHistory() }
                            .padding(4.dp)
                    )
                }

                // Scrolling Terminal rows
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFFAFAFA))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (logs.isEmpty()) {
                        item {
                            Text(
                                text = "System listening. Toggle BLE/WiFi controllers to trigger logs...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = BentoTextMuted
                            )
                        }
                    } else {
                        items(logs) { entry ->
                            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timestamp))
                            val tagColor = when (entry.tag) {
                                "BLE" -> BentoYellowText
                                "WIFI" -> BentoBlueText
                                "SOS" -> BentoTextFaint
                                "SEC" -> BentoPurpleText
                                else -> BentoTextMuted
                            }

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "[$timeStr] ",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                    color = BentoTextMuted.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "${entry.tag}: ",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tagColor
                                )
                                Text(
                                    text = entry.message,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                    color = if (entry.isError) BentoTextFaint else BentoText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
