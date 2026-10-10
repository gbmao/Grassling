package com.grassling.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grassling.app.local_ai.ModelSetupState

@Composable
fun ModelSetupScreen(state: ModelSetupState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF0F8EC)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Preparing Grassling...", color = Color(0xFF285B35))
        when (state) {
            is ModelSetupState.Downloading -> {
                Text("Downloading AI model: ${state.percent}%")
                LinearProgressIndicator(progress = { state.percent / 100f })
            }
            is ModelSetupState.Error -> {
                Text(state.message, textAlign = TextAlign.Center)
                Button(onClick = onRetry) { Text("Retry") }
            }
            else -> CircularProgressIndicator(color = Color(0xFF285B35))
        }
    }
}
