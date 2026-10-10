package com.grassling.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.grassling.app.GrasslingApplication
import com.grassling.app.data.QuestStorage
import com.grassling.app.local_ai.GrasslingConversationPrompt
import com.grassling.app.monster.Monster
import com.grassling.app.monster.MonsterEngine
import com.grassling.app.testandoAPP.EnergyController
import com.grassling.app.ui.components.MonsterView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import android.os.SystemClock
import kotlin.math.roundToInt
import kotlin.math.min

@Composable
fun HomeScreen() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val energyController = remember { EnergyController() }
    val monsterEngine = remember { MonsterEngine() }
    val energy = energyController.energy
    val context = LocalContext.current
    val application = remember(context) { context.applicationContext as? GrasslingApplication }
    val lifecycleOwner = LocalLifecycleOwner.current
    val conversationPrompt = remember { GrasslingConversationPrompt() }
    val latestEnergy by rememberUpdatedState(energy)
    var speechBubble by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lifecycleOwner, application, conversationPrompt) {
        val grasslingApplication = application ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val periodMs = CONVERSATION_INTERVAL_MS
            var nextCallAt = SystemClock.elapsedRealtime() + periodMs
            try {
                while (currentCoroutineContext().isActive) {
                    delay((nextCallAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
                    nextCallAt += periodMs

                    val currentEnergy = latestEnergy
                    val currentMood = monsterEngine.moodForEnergy(currentEnergy)
                    val quests = try {
                        withContext(Dispatchers.IO) {
                            QuestStorage(context.applicationContext).loadQuests()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val currentQuests = quests.filterNot { it.completed }
                    val completedQuests = quests.filter { it.completed }
                        .sortedByDescending { it.completedAt.orEmpty() }
                    suspend fun generateSpeech(): String? = conversationPrompt.generateLine(
                        mood = currentMood.name,
                        energy = currentEnergy,
                        currentQuests = currentQuests,
                        completedQuests = completedQuests,
                        generateText = grasslingApplication.localAI::generateConversation,
                    )
                    var response = generateSpeech()
                    while (response == null) {
                        response = generateSpeech()
                    }

                    val shownAt = SystemClock.elapsedRealtime()
                    if (nextCallAt <= shownAt) nextCallAt = shownAt + periodMs
                    speechBubble = response
                    val visibleUntilNextCall = (nextCallAt - shownAt).coerceAtLeast(0L)
                    delay(min(MAX_BUBBLE_DURATION_MS, visibleUntilNextCall))
                    speechBubble = null
                    val remainingUntilNextCall = (nextCallAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                    if (remainingUntilNextCall > 0L) delay(remainingUntilNextCall)
                }
            } finally {
                speechBubble = null
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet {
                QuestScreen(isVisible = drawerState.isOpen)
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFF0F8EC), Color(0xFFE3F1DD), Color(0xFFD5EACF)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(240.dp)) {
                    MonsterView(
                        monster = Monster(mood = monsterEngine.moodForEnergy(energy)),
                        modifier = Modifier.size(240.dp),
                    )
                    speechBubble?.let { message ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-46).dp)
                                .widthIn(max = 260.dp),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 1.dp)
                                    .size(12.dp)
                                    .rotate(45f)
                                    .background(Color.White),
                            )
                            Surface(
                                color = Color.White,
                                contentColor = Color(0xFF285B35),
                                shadowElevation = 4.dp,
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text(
                                    text = message,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Energy: $energy / 100",
                    color = Color(0xFF285B35),
                )
                Slider(
                    value = energy.toFloat(),
                    onValueChange = { energyController.changeEnergy(it.roundToInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.width(260.dp),
                )
            }
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(24.dp),
            ) {
                val strokeWidth = 2.dp.toPx()
                val centerY = size.height / 2f
                val tipX = size.width - 3.dp.toPx()
                val headStartX = tipX - 7.dp.toPx()
                val headHalfHeight = 6.dp.toPx()
                val arrowColor = Color(0xFF285B35)

                drawLine(
                    color = arrowColor,
                    start = Offset(2.dp.toPx(), centerY),
                    end = Offset(tipX, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = arrowColor,
                    start = Offset(headStartX, centerY - headHalfHeight),
                    end = Offset(tipX, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = arrowColor,
                    start = Offset(tipX, centerY),
                    end = Offset(headStartX, centerY + headHalfHeight),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private const val CONVERSATION_INTERVAL_MS = 10_000L
private const val MAX_BUBBLE_DURATION_MS = 5_000L

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreen()
}
