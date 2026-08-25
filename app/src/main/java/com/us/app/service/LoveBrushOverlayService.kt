package com.us.app.service

import android.app.Service
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
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
        
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
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
    Box(
        modifier = Modifier
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

@Composable
fun CanvasOverlay(onClose: () -> Unit) {
    val receivedPaths by LoveBrushRepository.receivedPaths.collectAsState()
    var currentPaths by remember { mutableStateOf<List<com.us.app.data.model.StrokePath>>(emptyList()) }
    var currentPath by remember { mutableStateOf<com.us.app.data.model.StrokePath?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.8f))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = onClose) { Text("Hide") }
                Button(onClick = { currentPaths = emptyList() }) { Text("Clear") }
                Button(onClick = { 
                    scope.launch { 
                        LoveBrushRepository.sendDrawing(currentPaths)
                        currentPaths = emptyList()
                    }
                }) { Text("Send ❤️") }
            }
            
            androidx.compose.foundation.Canvas(
                modifier = Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset: androidx.compose.ui.geometry.Offset -> 
                            currentPath = com.us.app.data.model.StrokePath(points = listOf(com.us.app.data.model.Point(offset.x, offset.y))) 
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
                        drawPath(path = path, color = Color.Red, style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePath.strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    }
                }
                
                // Draw current stroke
                currentPath?.let { strokePath ->
                    val path = androidx.compose.ui.graphics.Path()
                    if (strokePath.points.isNotEmpty()) {
                        path.moveTo(strokePath.points.first().x, strokePath.points.first().y)
                        strokePath.points.drop(1).forEach { pt -> path.lineTo(pt.x, pt.y) }
                        drawPath(path = path, color = Color.Red, style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePath.strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    }
                }
            }
        }
    }
}
