import os

base_dir = r"C:\Users\ThinkPad\.gemini\antigravity\scratch\UsApp\app\src\main\java\com\us\app"

files = {
    "UsViewModel.kt": """package com.us.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.us.app.data.model.StrokePath
import com.us.app.data.model.Point
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class UsViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode
    
    private val _paired = MutableStateFlow(false)
    val paired: StateFlow<Boolean> = _paired
    
    private val _receivedPaths = MutableStateFlow<List<StrokePath>>(emptyList())
    val receivedPaths: StateFlow<List<StrokePath>> = _receivedPaths

    private var roomListener: ListenerRegistration? = null

    init {
        viewModelScope.launch {
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
        }
    }

    fun createRoom() {
        viewModelScope.launch {
            val code = "LOVE-" + UUID.randomUUID().toString().substring(0, 4).uppercase()
            val uid = auth.currentUser?.uid ?: return@launch
            db.collection("rooms").document(code).set(
                mapOf("userA" to uid, "userB" to null)
            ).await()
            _roomCode.value = code
            listenToRoom(code)
        }
    }

    fun joinRoom(code: String) {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            db.collection("rooms").document(code).update("userB", uid).await()
            _roomCode.value = code
            _paired.value = true
            listenToRoom(code)
        }
    }

    private fun listenToRoom(code: String) {
        roomListener?.remove()
        roomListener = db.collection("rooms").document(code)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val userB = snapshot.getString("userB")
                    if (userB != null) {
                        _paired.value = true
                    }
                }
            }
    }
    
    fun sendDrawing(paths: List<StrokePath>) {
        // Simplified for prototype
    }
}
""",
    "ui/screens/MainScreen.kt": """package com.us.app.ui.screens

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
""",
    "ui/screens/onboarding/OnboardingScreen.kt": """package com.us.app.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.us.app.UsViewModel

@Composable
fun OnboardingScreen(viewModel: UsViewModel) {
    var joinCode by remember { mutableStateOf("") }
    val roomCode by viewModel.roomCode.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Just you. Just me. Always connected.", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(48.dp))
        
        if (roomCode == null) {
            Button(onClick = { viewModel.createRoom() }, modifier = Modifier.fillMaxWidth()) { Text("Create a Room") }
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(value = joinCode, onValueChange = { joinCode = it }, label = { Text("Room Code") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.joinRoom(joinCode) }, modifier = Modifier.fillMaxWidth()) { Text("Join Room") }
        } else {
            Text("Your Room Code:", style = MaterialTheme.typography.bodyLarge)
            Text(roomCode ?: "", style = MaterialTheme.typography.displayMedium)
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Waiting for your partner to join...")
        }
    }
}
""",
    "ui/screens/canvas/CanvasScreen.kt": """package com.us.app.ui.screens.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.us.app.UsViewModel
import com.us.app.data.model.StrokePath
import com.us.app.data.model.Point

@Composable
fun CanvasScreen(viewModel: UsViewModel) {
    var paths by remember { mutableStateOf(listOf<StrokePath>()) }
    var currentPath by remember { mutableStateOf<StrokePath?>(null) }
    
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { paths = paths.dropLast(1) }) { Text("Undo") }
            Button(onClick = { paths = emptyList() }) { Text("Clear") }
        }

        Canvas(
            modifier = Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> currentPath = StrokePath(points = listOf(Point(offset.x, offset.y))) },
                    onDrag = { change, _ -> currentPath = currentPath?.copy(points = currentPath!!.points + Point(change.position.x, change.position.y)) },
                    onDragEnd = { currentPath?.let { paths = paths + it }; currentPath = null }
                )
            }
        ) {
            paths.forEach { strokePath ->
                val path = Path()
                if (strokePath.points.isNotEmpty()) {
                    path.moveTo(strokePath.points.first().x, strokePath.points.first().y)
                    strokePath.points.drop(1).forEach { pt -> path.lineTo(pt.x, pt.y) }
                    drawPath(path = path, color = Color(strokePath.color), style = Stroke(width = strokePath.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            currentPath?.let { strokePath ->
                val path = Path()
                if (strokePath.points.isNotEmpty()) {
                    path.moveTo(strokePath.points.first().x, strokePath.points.first().y)
                    strokePath.points.drop(1).forEach { pt -> path.lineTo(pt.x, pt.y) }
                    drawPath(path = path, color = Color(strokePath.color), style = Stroke(width = strokePath.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }

        Button(onClick = { viewModel.sendDrawing(paths) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Send ❤️") }
    }
}
"""
}

for path, content in files.items():
    full_path = os.path.join(base_dir, path.replace("/", "\\"))
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, "w", encoding="utf-8") as f:
        f.write(content)

print("Full App UI Generated.")
