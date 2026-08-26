package com.us.app.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
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
    private lateinit var params: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        LoveBrushRepository.initialize(this) // VERY IMPORTANT: Restores Room Code if process died
        
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
            val channel = android.app.NotificationChannel(channelId, "Love Brush", android.app.NotificationManager.IMPORTANCE_LOW)
            getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("Love Brush")
            .setContentText("Listening for drawings...")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .build()
        startForeground(1, notification)
    }

    private fun showOverlay() {
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LoveBrushOverlayService)
            setViewTreeViewModelStoreOwner(this@LoveBrushOverlayService)
            setViewTreeSavedStateRegistryOwner(this@LoveBrushOverlayService)
            setContent {
                val showCanvas by LoveBrushRepository.showCanvas.collectAsState()
                
                LaunchedEffect(showCanvas) {
                    if (showCanvas) {
                        params.width = WindowManager.LayoutParams.MATCH_PARENT
                        params.height = WindowManager.LayoutParams.MATCH_PARENT
                    } else {
                        params.width = WindowManager.LayoutParams.WRAP_CONTENT
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT
                    }
                    windowManager.updateViewLayout(composeView, params)
                }
                
                if (showCanvas) {
                    CanvasOverlay { LoveBrushRepository.showCanvas.value = false }
                } else {
                    FloatingBubble { LoveBrushRepository.showCanvas.value = true }
                }
            }
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
    val hasNewMessage by LoveBrushRepository.hasNewMessage.collectAsState()
    
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (hasNewMessage) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartbeat"
    )

    Box(
        modifier = Modifier
            .padding(16.dp)
            .size(65.dp)
            .scale(scale)
            .shadow(12.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = if (hasNewMessage) listOf(Color(0xFFFF8A80), Color(0xFFFF1744)) else listOf(Color(0xFFFF5252), Color(0xFFD50000))
                )
            )
            .clickable { 
                LoveBrushRepository.hasNewMessage.value = false
                onClick() 
            },
        contentAlignment = Alignment.Center
    ) {
        Text(if (hasNewMessage) "💖" else "🖌️", fontSize = 28.sp)
    }
}

@Composable
fun CanvasOverlay(onClose: () -> Unit) {
    val receivedPaths by LoveBrushRepository.receivedPaths.collectAsState()
    var currentPaths by remember { mutableStateOf<List<com.us.app.data.model.StrokePath>>(emptyList()) }
    var currentPath by remember { mutableStateOf<com.us.app.data.model.StrokePath?>(null) }
    
    val colors = listOf(Color.Red, Color(0xFFFF4081), Color(0xFF9C27B0), Color(0xFF3F51B5), Color.Black)
    var selectedColor by remember { mutableStateOf(colors[0]) }
    
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.90f))) {
        
        // Canvas for drawing
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset: androidx.compose.ui.geometry.Offset -> 
                        currentPath = com.us.app.data.model.StrokePath(
                            points = listOf(com.us.app.data.model.Point(offset.x, offset.y)),
                            color = selectedColor.toArgb()
                        ) 
                    },
                    onDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, _: androidx.compose.ui.geometry.Offset -> 
                        currentPath = currentPath?.copy(points = currentPath!!.points + com.us.app.data.model.Point(change.position.x, change.position.y)) 
                    },
                    onDragEnd = { 
                        currentPath?.let { currentPaths = currentPaths + it }
                        currentPath = null 
                    }
                )
            }
        ) {
            // Draw received paths
            receivedPaths.forEach { strokePath ->
                val path = androidx.compose.ui.graphics.Path()
                if (strokePath.points.isNotEmpty()) {
                    path.moveTo(strokePath.points.first().x, strokePath.points.first().y)
                    strokePath.points.drop(1).forEach { pt -> path.lineTo(pt.x, pt.y) }
                    drawPath(path = path, color = Color(strokePath.color), style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePath.strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }

            // Draw my paths
            currentPaths.forEach { strokePath ->
                val path = androidx.compose.ui.graphics.Path()
                if (strokePath.points.isNotEmpty()) {
                    path.moveTo(strokePath.points.first().x, strokePath.points.first().y)
                    strokePath.points.drop(1).forEach { pt -> path.lineTo(pt.x, pt.y) }
                    drawPath(path = path, color = Color(strokePath.color), style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePath.strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
            
            // Draw current stroke
            currentPath?.let { strokePath ->
                val path = androidx.compose.ui.graphics.Path()
                if (strokePath.points.isNotEmpty()) {
                    path.moveTo(strokePath.points.first().x, strokePath.points.first().y)
                    strokePath.points.drop(1).forEach { pt -> path.lineTo(pt.x, pt.y) }
                    drawPath(path = path, color = Color(strokePath.color), style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePath.strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
        }

        // Toolbar Top (Clear & Close)
        Row(
            modifier = Modifier.fillMaxWidth().padding(32.dp), 
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape, 
                color = Color.White, 
                shadowElevation = 8.dp,
                modifier = Modifier.clickable { onClose() }
            ) {
                Text("✖", modifier = Modifier.padding(16.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            }
            
            Surface(
                shape = RoundedCornerShape(50), 
                color = Color.White, 
                shadowElevation = 8.dp,
                modifier = Modifier.clickable { 
                    currentPaths = emptyList() 
                    LoveBrushRepository.clearCanvasLocally()
                }
            ) {
                Text("🧹 Clear Canvas", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), fontWeight = FontWeight.Bold, color = Color.Red)
            }
        }

        // Toolbar Bottom (Colors & Send)
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Color Palette
            Surface(
                shape = RoundedCornerShape(50), 
                color = Color.White, 
                shadowElevation = 12.dp,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    colors.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(width = if (selectedColor == c) 3.dp else 0.dp, color = if (selectedColor == c) Color.Gray else Color.Transparent, shape = CircleShape)
                                .clickable { selectedColor = c }
                        )
                    }
                }
            }
            
            // Send Button
            Button(
                onClick = { 
                    scope.launch { 
                        LoveBrushRepository.sendDrawing(currentPaths)
                        currentPaths = emptyList()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp).shadow(12.dp, RoundedCornerShape(50)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFF4081), Color(0xFFE91E63), Color(0xFFC2185B))))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Send to Partner 💌", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
