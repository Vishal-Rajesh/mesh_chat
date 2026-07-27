package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.MeshDatabase
import com.example.data.MeshRepository
import com.example.mesh.MeshManager
import com.example.ui.MeshAppScreen
import com.example.ui.MeshViewModel
import com.example.ui.MeshViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize SQLite local Room database
        val database = MeshDatabase.getDatabase(this)
        val repository = MeshRepository(database.meshDao())

        // 2. Initialize our core mesh radio coordinator
        val meshManager = MeshManager(applicationContext, repository)

        // 3. Setup Mesh Jetpack ViewModel
        val viewModel = ViewModelProvider(
            this,
            MeshViewModelFactory(repository, meshManager)
        )[MeshViewModel::class.java]

        setContent {
            MyApplicationTheme {
                MeshAppScreen(viewModel = viewModel)
            }
        }
    }
}
