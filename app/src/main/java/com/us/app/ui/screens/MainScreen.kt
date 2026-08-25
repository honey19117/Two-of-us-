package com.us.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.us.app.ui.screens.onboarding.OnboardingScreen
import com.us.app.ui.screens.home.HomeScreen
import com.us.app.ui.screens.canvas.CanvasScreen
import com.us.app.ui.screens.settings.SettingsScreen

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    // Simple routing
    NavHost(navController = navController, startDestination = "onboarding") {
        composable("onboarding") { OnboardingScreen(navController) }
        composable("home") { HomeScreen(navController) }
        composable("canvas") { CanvasScreen(navController) }
        composable("settings") { SettingsScreen(navController) }
    }
}
