package com.us.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // Background Gradient
    val brush = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFF0F5), Color(0xFFFFB6C1), Color(0xFFFF69B4))
    )
    
    // Bouncing animation for title
    val infiniteTransition = rememberInfiniteTransition()
    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Box(modifier = Modifier.fillMaxSize().background(brush)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Two of Us ❤️", 
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.offset(y = bounceOffset.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Stay connected, always.", 
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
            
            Spacer(modifier = Modifier.height(48.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)),
                        shape = RoundedCornerShape(50)
                    ) { 
                        Text("1. Allow Floating Brush", fontWeight = FontWeight.Bold) 
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    errorMessage?.let { 
                        Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) 
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    if (roomCode == null) {
                        Button(
                            onClick = { scope.launch { LoveBrushRepository.createRoom() } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                            shape = RoundedCornerShape(50)
                        ) { Text("Create a Room", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("OR", color = Color.Gray, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = joinCode, 
                            onValueChange = { joinCode = it.uppercase() }, 
                            label = { Text("Enter Partner's Code") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFE91E63),
                                focusedLabelColor = Color(0xFFE91E63)
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = { scope.launch { LoveBrushRepository.joinRoom(joinCode) } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2185B)),
                            shape = RoundedCornerShape(50)
                        ) { Text("Join Room", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    } else {
                        Text("Your Secret Room Code:", color = Color.Gray, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFCE4EC),
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Text(
                                text = roomCode ?: "", 
                                style = MaterialTheme.typography.displaySmall, 
                                color = Color(0xFFE91E63),
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        if (paired) {
                            Text("💖 Connected to Partner 💖", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE91E63), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onStartOverlay,
                                modifier = Modifier.fillMaxWidth().height(60.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                                shape = RoundedCornerShape(50)
                            ) { 
                                Text("Launch Floating Brush 🖌️", fontSize = 18.sp, fontWeight = FontWeight.Bold) 
                            }
                        } else {
                            CircularProgressIndicator(color = Color(0xFFE91E63))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Waiting for your partner to join...", color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        }
                    }
                }
            }
        }
    }
}
