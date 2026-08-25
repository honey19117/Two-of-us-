import os

base_dir = r"C:\Users\ThinkPad\.gemini\antigravity\scratch\UsApp\app\src\main\java\com\us\app"

files = {
    "MainActivity.kt": """package com.us.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.us.app.ui.theme.UsAppTheme
import com.us.app.ui.screens.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UsAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}
""",
    "ui/screens/MainScreen.kt": """package com.us.app.ui.screens

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
""",
    "ui/screens/onboarding/OnboardingScreen.kt": """package com.us.app.ui.screens.onboarding

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
"""
}

for path, content in files.items():
    full_path = os.path.join(base_dir, path.replace("/", "\\"))
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, "w", encoding="utf-8") as f:
        f.write(content)

print("Kotlin files generated.")
