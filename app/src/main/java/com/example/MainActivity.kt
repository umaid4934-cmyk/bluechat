package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.example.ui.navigation.BlueChatNavGraph
import com.example.ui.theme.BlueChatTheme
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ChatViewModelFactory
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(application, mainViewModel.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val userProfile by mainViewModel.userProfile.collectAsState()
            val themeMode = userProfile?.themeMode ?: "SYSTEM"

            val darkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            BlueChatTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                BlueChatNavGraph(
                    navController = navController,
                    mainViewModel = mainViewModel,
                    chatViewModel = chatViewModel
                )
            }
        }
    }
}
