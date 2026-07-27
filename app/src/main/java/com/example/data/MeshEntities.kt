package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val senderName: String,
    val recipientId: String,
    val content: String,
    val timestamp: Long,
    val type: String, // "TEXT", "FILE", "SOS"
    val isOutgoing: Boolean,
    val hops: Int,
    val path: String, // Comma-separated path, e.g., "A -> B -> C"
    val attachmentPath: String? = null
)

@Entity(tableName = "nodes")
data class NodeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val status: String, // "ACTIVE", "IDLE", "DISCONNECTED"
    val connectionType: String, // "BLE", "WIFI_DIRECT", "SIMULATED"
    val hopsAway: Int,
    val lastSeen: Long,
    val signalStrength: Int, // in dBm
    val xPos: Float = 0.0f, // 2D position for spatial simulation grid
    val yPos: Float = 0.0f
)
