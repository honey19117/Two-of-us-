package com.us.app.ui.screens

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.us.app.UsViewModel
import com.us.app.ui.screens.onboarding.OnboardingScreen
import com.us.app.ui.screens.canvas.CanvasScreen

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val viewModel: UsViewModel = viewModel()
    val paired by viewModel.paired.collectAsState()

    LaunchedEffect(paired) {
        if (paired) {
            navController.navigate("canvas") { popUpTo(0) }
        }
    }

    NavHost(navController = navController, startDestination = "onboarding") {
        composable("onboarding") { OnboardingScreen(viewModel) }
        composable("canvas") { CanvasScreen(viewModel) }
    }
}
