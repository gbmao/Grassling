package com.grassling.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
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
import com.grassling.app.utils.StepCounterForegroundService

class MainActivity : ComponentActivity() {
    private val activityRecognitionPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startStepCounterService()
    }

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
        checkAndRequestActivityRecognitionPermission()
    }

    private fun checkAndRequestActivityRecognitionPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startStepCounterService()
            return
        }

        val permission = Manifest.permission.ACTIVITY_RECOGNITION
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            activityRecognitionPermissionLauncher.launch(permission)
        } else {
            startStepCounterService()
        }
    }

    private fun startStepCounterService() {
        val serviceIntent = Intent(this, StepCounterForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
