package com.grassling.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grassling.app.local_ai.ModelSetupState
import com.grassling.app.local_ai.ModelSetupViewModel
import com.grassling.app.ui.screens.HomeScreen
import com.grassling.app.ui.screens.ModelSetupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val setup: ModelSetupViewModel = viewModel()
            val state by setup.state.collectAsStateWithLifecycle()
            MaterialTheme {
                if (state == ModelSetupState.Ready) HomeScreen()
                else ModelSetupScreen(state = state, onRetry = setup::retry)
            }
        }
    }
}
