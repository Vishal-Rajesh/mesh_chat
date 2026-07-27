package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.MessageEntity
import com.example.data.MeshRepository
import com.example.mesh.MeshManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeshViewModel(
    private val repository: MeshRepository,
    val meshManager: MeshManager
) : ViewModel() {

    // Messages flow from Room SQLite DB
    val messages: StateFlow<List<MessageEntity>> = repository.allMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Discovered Nodes from Manager
    val simNodes = meshManager.simNodes

    // Logs from Manager
    val networkLogs = meshManager.networkLogs

    // Hardware Radio States
    val isBleScanning = meshManager.isBleScanningState
    val isBleAdvertising = meshManager.isBleAdvertisingState
    val isWifiP2pSearching = meshManager.isWifiP2pSearchingState

    // Current conversation partner ID ("ALL_NODES" for broadcast)
    private val _currentRecipientId = MutableStateFlow("ALL_NODES")
    val currentRecipientId = _currentRecipientId.asStateFlow()

    // Loading/sending states
    private val _isSending = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()

    fun selectRecipient(nodeId: String) {
        _currentRecipientId.value = nodeId
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        _isSending.value = true
        meshManager.sendSimulatedMessage(_currentRecipientId.value, content) {
            _isSending.value = false
        }
    }

    fun sendSosBroadcast(content: String) {
        if (content.isBlank()) return
        _isSending.value = true
        meshManager.triggerSosBroadcast(content) {
            _isSending.value = false
        }
    }

    fun toggleBleScanning() {
        meshManager.toggleBleScanning()
    }

    fun toggleBleAdvertising() {
        meshManager.toggleBleAdvertising()
    }

    fun toggleWifiDirectSearching() {
        meshManager.toggleWifiP2pDiscovery()
    }

    fun addCustomNode(name: String, id: String) {
        meshManager.addCustomNode(name, id)
    }

    fun enableGeminiAiNode() {
        meshManager.enableGeminiAiNode()
    }

    fun updateNodePosition(nodeId: String, x: Float, y: Float) {
        meshManager.updateNodePosition(nodeId, x, y)
    }

    fun checkPermissionsGranted(): Boolean {
        return meshManager.checkPermissionsGranted()
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
            meshManager.cleanAllLogs()
        }
    }
}

class MeshViewModelFactory(
    private val repository: MeshRepository,
    private val meshManager: MeshManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MeshViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MeshViewModel(repository, meshManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
