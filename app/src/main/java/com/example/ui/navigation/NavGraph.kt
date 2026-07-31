package com.example.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ui.screens.BluetoothScanScreen
import com.example.ui.screens.ChatInfoScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.FirstLaunchScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StarredMessagesScreen
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.MainViewModel

object Routes {
    const val FIRST_LAUNCH = "first_launch"
    const val HOME = "home"
    const val SCAN = "scan"
    const val CHAT = "chat/{address}/{name}"
    const val SETTINGS = "settings"
    const val STARRED = "starred"
    const val CHAT_INFO = "chat_info/{address}/{name}"

    fun buildChatRoute(address: String, name: String): String =
        "chat/${Uri.encode(address)}/${Uri.encode(name)}"

    fun buildChatInfoRoute(address: String, name: String): String =
        "chat_info/${Uri.encode(address)}/${Uri.encode(name)}"
}

@Composable
fun BlueChatNavGraph(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    chatViewModel: ChatViewModel
) {
    val userProfile by mainViewModel.userProfile.collectAsState()

    val startDestination = if (userProfile?.isFirstLaunchCompleted == true) {
        Routes.HOME
    } else {
        Routes.FIRST_LAUNCH
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.FIRST_LAUNCH) {
            FirstLaunchScreen(
                onSetupComplete = { name, profilePicUri ->
                    mainViewModel.saveUserProfile(name, profilePicUri, isFirstLaunchCompleted = true)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.FIRST_LAUNCH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                viewModel = mainViewModel,
                onNavigateToChat = { address, name ->
                    navController.navigate(Routes.buildChatRoute(address, name))
                },
                onNavigateToScan = {
                    navController.navigate(Routes.SCAN)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToStarred = {
                    navController.navigate(Routes.STARRED)
                }
            )
        }

        composable(Routes.SCAN) {
            BluetoothScanScreen(
                viewModel = mainViewModel,
                onNavigateBack = { navController.popBackStack() },
                onConnectedToDevice = { address, name ->
                    navController.navigate(Routes.buildChatRoute(address, name)) {
                        popUpTo(Routes.SCAN) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("address") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val address = Uri.decode(backStackEntry.arguments?.getString("address") ?: "")
            val name = Uri.decode(backStackEntry.arguments?.getString("name") ?: "")

            ChatScreen(
                chatViewModel = chatViewModel,
                chatAddress = address,
                peerName = name,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToInfo = {
                    navController.navigate(Routes.buildChatInfoRoute(address, name))
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = mainViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.STARRED) {
            StarredMessagesScreen(
                mainViewModel = mainViewModel,
                chatViewModel = chatViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.CHAT_INFO,
            arguments = listOf(
                navArgument("address") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val address = Uri.decode(backStackEntry.arguments?.getString("address") ?: "")
            val name = Uri.decode(backStackEntry.arguments?.getString("name") ?: "")

            ChatInfoScreen(
                chatAddress = address,
                peerName = name,
                mainViewModel = mainViewModel,
                chatViewModel = chatViewModel,
                onNavigateBack = { navController.popBackStack() },
                onDeleteConversationCompleted = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }
    }
}
