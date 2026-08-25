package com.us.app.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun OnboardingScreen(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Just you. Just me. Always connected.", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { /* TODO: Create Room */ navController.navigate("home") }) {
            Text("Create a Room")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { /* TODO: Join Room */ navController.navigate("home") }) {
            Text("Join a Room")
        }
    }
}
