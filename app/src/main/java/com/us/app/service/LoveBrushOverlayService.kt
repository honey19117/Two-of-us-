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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.drawscope.scale
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            x = 0
            y = 100
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
                        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    } else {
                        params.width = WindowManager.LayoutParams.WRAP_CONTENT
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT
                        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    }
                    windowManager.updateViewLayout(composeView, params)
                }
                
                if (showCanvas) {
                    MainAppOverlay { LoveBrushRepository.showCanvas.value = false }
                } else {
                    FloatingBubble(
                        onClick = { LoveBrushRepository.showCanvas.value = true },
                        onDrag = { dx, dy ->
                            params.x += dx.toInt()
                            params.y += dy.toInt()
                            windowManager.updateViewLayout(composeView, params)
                        }
                    )
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
fun FloatingBubble(onClick: () -> Unit, onDrag: (Float, Float) -> Unit) {
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
            .pointerInput(Unit) {
                detectTapGestures(onTap = { 
                    LoveBrushRepository.hasNewMessage.value = false
                    onClick() 
                })
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(if (hasNewMessage) "💖" else "🖌️", fontSize = 28.sp)
    }
}

@Composable
fun MainAppOverlay(onClose: () -> Unit) {
    val hasNewMessage by LoveBrushRepository.hasNewMessage.collectAsState()
    var selectedTab by remember { mutableStateOf(if (hasNewMessage) "INBOX" else "DRAW") }

    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.95f))) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Top Navigation Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape, 
                    color = Color.White, 
                    shadowElevation = 8.dp,
                    modifier = Modifier.clickable { onClose() }
                ) {
                    Text("✖", modifier = Modifier.padding(12.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                }
                
                // Tabs
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFF5F5F5),
                    shadowElevation = 4.dp
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        TabButton("🖌️ Draw", selectedTab == "DRAW") { selectedTab = "DRAW" }
                        TabButton(if (hasNewMessage) "💌 Love Letters 🔴" else "💌 Love Letters", selectedTab == "INBOX") { 
                            selectedTab = "INBOX" 
                        }
                    }
                }
            }

            // Content Area
            Box(modifier = Modifier.weight(1f)) {
                if (selectedTab == "DRAW") {
                    ComposeDrawView()
                } else {
                    InboxGalleryView { selectedTab = "DRAW" }
                }
            }
        }
    }
}

@Composable
fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor = if (isSelected) Color(0xFFFF4081) else Color.Transparent
    val textColor = if (isSelected) Color.White else Color.Gray

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(text, color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun ComposeDrawView() {
    val receivedPaths by LoveBrushRepository.receivedPaths.collectAsState()
    
    var currentPaths by remember { mutableStateOf<List<com.us.app.data.model.StrokePath>>(emptyList()) }
    var undonePaths by remember { mutableStateOf<List<com.us.app.data.model.StrokePath>>(emptyList()) }
    var currentPath by remember { mutableStateOf<com.us.app.data.model.StrokePath?>(null) }
    
    // A massive expanded color palette!
    val allColors = listOf(
        Color.Black, Color.DarkGray, Color.Gray,
        Color(0xFFD50000), Color.Red, Color(0xFFFF5252), // Reds
        Color(0xFFC51162), Color(0xFFFF4081), Color(0xFFFF80AB), // Pinks
        Color(0xFFAA00FF), Color(0xFFE040FB), Color(0xFFEA80FC), // Purples
        Color(0xFF6200EA), Color(0xFF7C4DFF), Color(0xFFB388FF), // Deep Purples
        Color(0xFF2962FF), Color(0xFF448AFF), Color(0xFF82B1FF), // Blues
        Color(0xFF00BFA5), Color(0xFF64FFDA), // Teals
        Color(0xFF00C853), Color(0xFF69F0AE), // Greens
        Color(0xFFFFD600), Color(0xFFFFFF00), // Yellows
        Color(0xFFFF6D00), Color(0xFFFFAB40)  // Oranges
    )
    var selectedColor by remember { mutableStateOf(allColors[4]) } // Default Red
    
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        
        // The Drawing Canvas
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
                        currentPath?.let { 
                            currentPaths = currentPaths + it
                            undonePaths = emptyList() // Clear redo stack on new draw
                        }
                        currentPath = null 
                    }
                )
            }
        ) {
            // Draw Partner's Paths
            receivedPaths.forEach { strokePath ->
                val path = androidx.compose.ui.graphics.Path()
                if (strokePath.points.isNotEmpty()) {
                    path.moveTo(strokePath.points.first().x, strokePath.points.first().y)
                    strokePath.points.drop(1).forEach { pt -> path.lineTo(pt.x, pt.y) }
                    drawPath(path = path, color = Color(strokePath.color), style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePath.strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }

            // Draw My Paths
            currentPaths.forEach { strokePath ->
                val path = androidx.compose.ui.graphics.Path()
                if (strokePath.points.isNotEmpty()) {
                    path.moveTo(strokePath.points.first().x, strokePath.points.first().y)
                    strokePath.points.drop(1).forEach { pt -> path.lineTo(pt.x, pt.y) }
                    drawPath(path = path, color = Color(strokePath.color), style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePath.strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
            
            // Draw Current Stroke
            currentPath?.let { strokePath ->
                val path = androidx.compose.ui.graphics.Path()
                if (strokePath.points.isNotEmpty()) {
                    path.moveTo(strokePath.points.first().x, strokePath.points.first().y)
                    strokePath.points.drop(1).forEach { pt -> path.lineTo(pt.x, pt.y) }
                    drawPath(path = path, color = Color(strokePath.color), style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePath.strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
        }

        // Action Buttons Row (Undo/Redo/Clear)
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Undo Button
            Surface(
                shape = CircleShape, 
                color = Color.White, 
                shadowElevation = 8.dp,
                modifier = Modifier.clickable(enabled = currentPaths.isNotEmpty()) { 
                    if (currentPaths.isNotEmpty()) {
                        undonePaths = undonePaths + currentPaths.last()
                        currentPaths = currentPaths.dropLast(1)
                    }
                }
            ) {
                Text("↩️", modifier = Modifier.padding(12.dp), fontSize = 16.sp, color = if (currentPaths.isNotEmpty()) Color.Black else Color.LightGray)
            }
            
            // Redo Button
            Surface(
                shape = CircleShape, 
                color = Color.White, 
                shadowElevation = 8.dp,
                modifier = Modifier.clickable(enabled = undonePaths.isNotEmpty()) { 
                    if (undonePaths.isNotEmpty()) {
                        currentPaths = currentPaths + undonePaths.last()
                        undonePaths = undonePaths.dropLast(1)
                    }
                }
            ) {
                Text("↪️", modifier = Modifier.padding(12.dp), fontSize = 16.sp, color = if (undonePaths.isNotEmpty()) Color.Black else Color.LightGray)
            }
            
            // Clear Button
            Surface(
                shape = CircleShape, 
                color = Color.White, 
                shadowElevation = 8.dp,
                modifier = Modifier.clickable { 
                    currentPaths = emptyList() 
                    undonePaths = emptyList()
                    LoveBrushRepository.clearCanvasLocally()
                }
            ) {
                Text("🧹", modifier = Modifier.padding(12.dp), fontSize = 16.sp)
            }
        }

        // Toolbar Bottom
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Scrollable Extended Color Palette
            Surface(
                shape = RoundedCornerShape(50), 
                color = Color.White, 
                shadowElevation = 12.dp,
                modifier = Modifier.padding(bottom = 24.dp).fillMaxWidth()
            ) {
                LazyRow(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(allColors) { c ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(width = if (selectedColor == c) 4.dp else 0.dp, color = if (selectedColor == c) Color.Black else Color.Transparent, shape = CircleShape)
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
                        undonePaths = emptyList()
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

@Composable
fun InboxGalleryView(onReply: () -> Unit) {
    val inbox by LoveBrushRepository.inbox.collectAsState()
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        if (inbox.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No drawings yet...", color = Color.Gray, fontSize = 18.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(inbox) { message ->
                    val dateString = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault()).format(Date(message.timestamp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = if (message.seen) Color.White else Color(0xFFFFF0F5)),
                        modifier = Modifier
                            .height(200.dp)
                            .clickable {
                                scope.launch { LoveBrushRepository.markAsRead(message.messageId) }
                                LoveBrushRepository.viewMessage(message)
                                onReply() // Switch back to draw tab so they can see the message full screen
                            }
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Mini Canvas Preview
                            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.White).padding(8.dp)) {
                                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                    scale(0.3f, 0.3f) {
                                        message.paths.forEach { strokePath ->
                                            val path = androidx.compose.ui.graphics.Path()
                                            if (strokePath.points.isNotEmpty()) {
                                                path.moveTo(strokePath.points.first().x, strokePath.points.first().y)
                                                strokePath.points.drop(1).forEach { pt -> path.lineTo(pt.x, pt.y) }
                                                drawPath(path = path, color = Color(strokePath.color), style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePath.strokeWidth * 3, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                                            }
                                        }
                                    }
                                }
                            }
                            // Timestamp Footer
                            Row(
                                modifier = Modifier.fillMaxWidth().background(if (message.seen) Color(0xFFF5F5F5) else Color(0xFFFFB6C1)).padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(dateString, fontSize = 12.sp, color = if (message.seen) Color.Gray else Color.White, fontWeight = FontWeight.Bold)
                                if (!message.seen) {
                                    Text("NEW", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
