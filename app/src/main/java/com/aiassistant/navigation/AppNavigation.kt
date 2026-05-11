package com.aiassistant.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aiassistant.data.EncryptedPrefsManager
import com.aiassistant.ui.main.MainScreen

@Composable
fun AppNavigation(encryptedPrefsManager: EncryptedPrefsManager) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(encryptedPrefsManager = encryptedPrefsManager)
        }
    }
}
