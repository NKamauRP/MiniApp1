package com.example.miniapp1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.miniapp1.ui.navigation.NavGraph
import com.example.miniapp1.ui.theme.MiniApp1Theme

import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.miniapp1.ui.viewmodel.ChatViewModel
import com.example.miniapp1.ui.viewmodel.DownloadViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable Edge-to-Edge
        enableEdgeToEdge()
        
        setContent {
            MiniApp1Theme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val downloadViewModel: DownloadViewModel = viewModel()
                    val chatViewModel: ChatViewModel = viewModel()
                    NavGraph(
                        navController = navController, 
                        downloadViewModel = downloadViewModel,
                        chatViewModel = chatViewModel
                    )
                }
            }
        }
    }
}
