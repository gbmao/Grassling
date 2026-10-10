package com.grassling.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.grassling.app.local_ai.LlamaLocalAI
import com.grassling.app.ui.screens.HomeScreen

class MainActivity : ComponentActivity() {
    private lateinit var localAI: LlamaLocalAI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localAI = LlamaLocalAI(applicationContext)
        setContent {
            MaterialTheme {
                HomeScreen(localAI)
            }
        }
    }

    override fun onDestroy() {
        if (::localAI.isInitialized) localAI.close()
        super.onDestroy()
    }
}
