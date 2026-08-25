package com.us.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun HomeScreen(onNavigateToCanvas: () -> Unit, onNavigateToSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Connected with your person ❤️")
        Button(onClick = onNavigateToCanvas) {
            Text("Open Love Brush 🖌️")
        }
        Button(onClick = onNavigateToSettings) {
            Text("Settings")
        }
    }
}
