package com.example.data

import kotlinx.coroutines.flow.Flow

class MeshRepository(private val meshDao: MeshDao) {
    val allMessages: Flow<List<MessageEntity>> = meshDao.getAllMessages()
    val allNodes: Flow<List<NodeEntity>> = meshDao.getAllNodes()

    suspend fun insertMessage(message: MessageEntity) {
        meshDao.insertMessage(message)
    }

    suspend fun insertNode(node: NodeEntity) {
        meshDao.insertNode(node)
    }

    suspend fun insertNodes(nodes: List<NodeEntity>) {
        meshDao.insertNodes(nodes)
    }

    suspend fun updateNode(node: NodeEntity) {
        meshDao.updateNode(node)
    }

    suspend fun clearAll() {
        meshDao.clearAllMessages()
        meshDao.clearAllNodes()
    }
}
