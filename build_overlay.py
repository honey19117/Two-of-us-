import os

base_dir = r"C:\Users\ThinkPad\.gemini\antigravity\scratch\UsApp\app\src\main"
java_dir = os.path.join(base_dir, r"java\com\us\app")

files = {
    # 1. LoveBrushRepository (Singleton to share state between Service and UI)
    "LoveBrushRepository.kt": """package com.us.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.us.app.data.model.StrokePath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

object LoveBrushRepository {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode
    
    private val _paired = MutableStateFlow(false)
    val paired: StateFlow<Boolean> = _paired
    
    private val _receivedPaths = MutableStateFlow<List<StrokePath>>(emptyList())
    val receivedPaths: StateFlow<List<StrokePath>> = _receivedPaths

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var roomListener: ListenerRegistration? = null
    
    // UI state for overlay
    val showCanvas = MutableStateFlow(false)

    suspend fun initAuth() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    suspend fun createRoom() {
        try {
            initAuth()
            val uid = auth.currentUser?.uid ?: throw Exception("Auth failed")
            val code = "LOVE-" + UUID.randomUUID().toString().substring(0, 4).uppercase()
            db.collection("rooms").document(code).set(mapOf("userA" to uid, "userB" to null)).await()
            _roomCode.value = code
            listenToRoom(code)
        } catch (e: Exception) {
            _errorMessage.value = "Create Room Error: "
        }
    }

    suspend fun joinRoom(code: String) {
        try {
            initAuth()
            val uid = auth.currentUser?.uid ?: throw Exception("Auth failed")
            db.collection("rooms").document(code).update("userB", uid).await()
            _roomCode.value = code
            _paired.value = true
            listenToRoom(code)
        } catch (e: Exception) {
            _errorMessage.value = "Join Room Error: "
        }
    }

    private fun listenToRoom(code: String) {
        roomListener?.remove()
        roomListener = db.collection("rooms").document(code).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val userB = snapshot.getString("userB")
                if (userB != null) _paired.value = true
                
                // Real app would parse the paths from snapshot here and set _receivedPaths
                // and if new paths arrived, set showCanvas.value = true
            }
        }
    }
    
    suspend fun sendDrawing(paths: List<StrokePath>) {
        val code = _roomCode.value ?: return
        // Real app would upload paths here
        showCanvas.value = false
    }
}
""",

    # 2. OverlayService
    "service/LoveBrushOverlayService.kt": """package com.us.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.us.app.LoveBrushRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoveBrushOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        startForegroundService()
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
        
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun startForegroundService() {
        val channelId = "overlay_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Love Brush", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Love Brush Active")
            .setContentText("Tap the floating brush to draw.")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .build()
        startForeground(1, notification)
    }

    private fun showOverlay() {
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LoveBrushOverlayService)
            setViewTreeViewModelStoreOwner(this@LoveBrushOverlayService)
            setViewTreeSavedStateRegistryOwner(this@LoveBrushOverlayService)
            setContent {
                val showCanvas by LoveBrushRepository.showCanvas.collectAsState()
                if (showCanvas) {
                    CanvasOverlay { LoveBrushRepository.showCanvas.value = false }
                } else {
                    FloatingBubble { LoveBrushRepository.showCanvas.value = true }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(composeView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        composeView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun FloatingBubble(onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp)
                .size(60.dp)
                .clip(CircleShape)
                .background(Color.Red)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text("🖌️", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun CanvasOverlay(onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.9f))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = onClose) { Text("Close") }
                Button(onClick = { 
                    CoroutineScope(Dispatchers.IO).launch { LoveBrushRepository.sendDrawing(emptyList()) }
                }) { Text("Send ❤️") }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                detectDragGestures { _, _ -> /* Draw logic here */ }
            })
        }
    }
}
""",

    # 3. Modify MainActivity to request permissions
    "MainActivity.kt": """package com.us.app

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
import com.us.app.service.LoveBrushOverlayService
import com.us.app.ui.theme.UsAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            Text("Room Code: ")
            if (paired) {
                Text("Paired! ❤️")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onStartOverlay) { Text("Launch Floating Brush") }
            } else {
                Text("Waiting for partner...")
            }
        }
    }
}
"""
}

for path, content in files.items():
    full_path = os.path.join(java_dir, path.replace("/", "\\"))
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, "w", encoding="utf-8") as f:
        f.write(content)

print("Generated Floating Architecture")
