package com.us.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.us.app.service.LoveBrushOverlayService
import com.us.app.ui.theme.UsAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LoveBrushRepository.initialize(this)
        setContent {
            UsAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SetupScreen(
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))
                                startActivity(intent)
                            }
                        },
                        onStartOverlay = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                                val intent = Intent(this, LoveBrushOverlayService::class.java)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    startForegroundService(intent)
                                } else {
                                    startService(intent)
                                }
                                finish() // Close the app, overlay takes over
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SetupScreen(onRequestPermission: () -> Unit, onStartOverlay: () -> Unit) {
    val roomCode by LoveBrushRepository.roomCode.collectAsState()
    val paired by LoveBrushRepository.paired.collectAsState()
    val errorMessage by LoveBrushRepository.errorMessage.collectAsState()
    val scope = rememberCoroutineScope()
    var joinCode by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Love Brush Setup", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = onRequestPermission) { Text("1. Grant Overlay Permission") }
        Spacer(modifier = Modifier.height(32.dp))
        
        errorMessage?.let { Text(it, color = Color.Red) }
        
        if (roomCode == null) {
            Button(onClick = { scope.launch { LoveBrushRepository.createRoom() } }) { Text("Create Room") }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = joinCode, onValueChange = { joinCode = it }, label = { Text("Room Code") })
            Button(onClick = { scope.launch { LoveBrushRepository.joinRoom(joinCode) } }) { Text("Join Room") }
        } else {
            Text("Share this Room Code with your partner:", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(roomCode ?: "", style = MaterialTheme.typography.displayLarge, color = Color.Red)
            Spacer(modifier = Modifier.height(24.dp))
            if (paired) {
                Text("Paired! ❤️", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onStartOverlay) { Text("Launch Floating Brush") }
            } else {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Waiting for partner to join...", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
