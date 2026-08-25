package com.us.app.ui.screens.onboarding

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
