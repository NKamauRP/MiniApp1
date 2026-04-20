package com.example.miniapp1.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.miniapp1.ui.screens.ChatScreen
import com.example.miniapp1.ui.screens.SettingsScreen
import com.example.miniapp1.ui.viewmodel.ChatViewModel
import com.example.miniapp1.ui.viewmodel.DownloadViewModel

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object Settings : Screen("settings")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    downloadViewModel: DownloadViewModel,
    chatViewModel: ChatViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route
    ) {
        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                viewModel = chatViewModel
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                viewModel = downloadViewModel
            )
        }
    }
}
