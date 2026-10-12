package com.grassling.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.grassling.app.GrasslingApplication
import com.grassling.app.data.QuestStorage
import com.grassling.app.local_ai.GrasslingConversationPrompt
import com.grassling.app.local_ai.GameSnapshot
import com.grassling.app.local_ai.QuestOption
import com.grassling.app.monster.EnergyManager
import com.grassling.app.monster.Monster
import com.grassling.app.monster.MonsterEngine
import com.grassling.app.quest.QuestTrackingMode
import com.grassling.app.ui.components.MonsterView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

@Composable
fun HomeScreen() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val monsterEngine = remember { MonsterEngine() }
    val context = LocalContext.current
    val application = remember(context) { context.applicationContext as? GrasslingApplication }
    val energyManager = remember(context, application) {
        application?.energyManager ?: EnergyManager(context)
    }
    val energy by energyManager.energy.collectAsState()
    val stepCounter = application?.stepCounterManager
    val lifecycleOwner = LocalLifecycleOwner.current
    val conversationPrompt = remember { GrasslingConversationPrompt() }
    val latestEnergy by rememberUpdatedState(energy)
    var speechBubble by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var showQuestDialog by remember { mutableStateOf(false) }
    var isGeneratingQuest by remember { mutableStateOf(false) }
    var selectedQuest by remember { mutableStateOf<QuestOption?>(null) }
    var questError by remember { mutableStateOf<String?>(null) }
    var activeQuest by remember { mutableStateOf<QuestStorage.StoredQuest?>(null) }
    var isCheckingActiveQuest by remember { mutableStateOf(true) }
    var isSavingQuest by remember { mutableStateOf(false) }
    var questStorageUnavailable by remember { mutableStateOf(false) }
    var pendingStepQuest by remember { mutableStateOf<QuestOption?>(null) }
    var currentQuestStepCount by remember { mutableStateOf(0) }
    var questHistoryRefreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(lifecycleOwner, energyManager) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (currentCoroutineContext().isActive) {
                energyManager.refreshEnergy()
                delay(ENERGY_REFRESH_INTERVAL_MS)
            }
        }
    }

    LaunchedEffect(context) {
        try {
            activeQuest = withContext(Dispatchers.IO) {
                QuestStorage(context.applicationContext).getActiveQuest()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            questStorageUnavailable = true
        } finally {
            isCheckingActiveQuest = false
        }
    }

    LaunchedEffect(lifecycleOwner, activeQuest?.creationDate, activeQuest?.trackingMode) {
        val quest = activeQuest
        if (quest?.trackingMode != QuestTrackingMode.STEPS) {
            currentQuestStepCount = 0
            return@LaunchedEffect
        }
        val counter = stepCounter ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (!counter.isRunning()) counter.start()
            while (currentCoroutineContext().isActive) {
                currentQuestStepCount = quest.stepsAtAcceptance?.let { stepsAtAcceptance ->
                    (withContext(Dispatchers.IO) { counter.getTotalSteps() } - stepsAtAcceptance)
                        .coerceAtLeast(0)
                } ?: counter.getSteps()
                delay(STEP_PROGRESS_REFRESH_MS)
            }
        }
    }

    fun persistAcceptedQuest(quest: QuestOption) {
        isSavingQuest = true
        questError = null
        coroutineScope.launch {
            try {
                val (wasAdded, currentQuest) = withContext(Dispatchers.IO) {
                    val storage = QuestStorage(context.applicationContext)
                    storage.addActiveQuest(
                        QuestStorage.StoredQuest(
                            description = quest.description,
                            targetSteps = quest.targetSteps,
                            targetMinutes = quest.targetMinutes,
                            trackingMode = quest.trackingMode,
                            stepsAtAcceptance = if (quest.trackingMode == QuestTrackingMode.STEPS) {
                                stepCounter?.getTotalSteps() ?: 0
                            } else {
                                null
                            },
                        ),
                    ) to storage.getActiveQuest()
                }
                activeQuest = currentQuest
                if (wasAdded || currentQuest != null) {
                    selectedQuest = null
                    showQuestDialog = false
                    if (wasAdded) {
                        val localAI = application?.localAI
                        if (localAI != null) {
                            val reaction = try {
                                conversationPrompt.generateAcceptedQuestReaction(
                                    mood = monsterEngine.moodForEnergy(latestEnergy).name,
                                    questDescription = currentQuest?.description ?: quest.description,
                                    generateText = localAI::generateConversation,
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                null
                            }
                            if (reaction != null) {
                                speechBubble = reaction
                                coroutineScope.launch {
                                    delay(MAX_BUBBLE_DURATION_MS)
                                    if (speechBubble == reaction) speechBubble = null
                                }
                            }
                        }
                    }
                } else {
                    questError = "Grassling couldn't save this quest. Please try again."
                    if (quest.trackingMode == QuestTrackingMode.STEPS) stepCounter?.stop()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                questError = "Grassling couldn't save this quest. Please try again."
                if (quest.trackingMode == QuestTrackingMode.STEPS) stepCounter?.stop()
            } finally {
                isSavingQuest = false
            }
        }
    }

    fun startQuestTracking(quest: QuestOption) {
        if (quest.trackingMode == QuestTrackingMode.STEPS) {
            val counter = stepCounter
            counter?.start()
            if (counter?.isRunning() != true) {
                isSavingQuest = false
                questError = "Step counting isn't available. Check the activity permission and step sensor."
                return
            }
            currentQuestStepCount = counter.getSteps()
        }
        persistAcceptedQuest(quest)
    }

    val stepPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val quest = pendingStepQuest
        pendingStepQuest = null
        if (quest == null) return@rememberLauncherForActivityResult
        if (granted) {
            startQuestTracking(quest)
        } else {
            isSavingQuest = false
            questError = "Allow activity recognition so Grassling can count your steps."
        }
    }

    fun requestWalkingQuest() {
        if (
            isCheckingActiveQuest || questStorageUnavailable || activeQuest != null ||
            isGeneratingQuest || isSavingQuest || showQuestDialog
        ) return
        showQuestDialog = true
        isGeneratingQuest = true
        selectedQuest = null
        questError = null
        val currentEnergy = energy
        coroutineScope.launch {
            try {
                val localAI = application?.localAI ?: error("The AI model is not ready yet.")
                selectedQuest = localAI.chooseQuest(currentEnergy).getOrThrow()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                questError = "Grassling couldn't choose a quest. Close this and tap Grassling to try again."
            } finally {
                isGeneratingQuest = false
            }
        }
    }

    fun acceptSelectedQuest() {
        val quest = selectedQuest ?: return
        if (isSavingQuest || activeQuest != null) return
        questError = null
        if (
            quest.trackingMode == QuestTrackingMode.STEPS &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingStepQuest = quest
            isSavingQuest = true
            stepPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            return
        }
        if (quest.trackingMode == QuestTrackingMode.STEPS) {
            isSavingQuest = true
        }
        startQuestTracking(quest)
    }

    fun finishQuest(creationDate: String) {
        if (isSavingQuest) return
        isSavingQuest = true
        coroutineScope.launch {
            try {
                val completedQuest = withContext(Dispatchers.IO) {
                    QuestStorage(context.applicationContext).finishActiveQuestIfReady(
                        creationDate = creationDate,
                        currentTotalSteps = stepCounter?.getTotalSteps() ?: 0,
                        currentSessionSteps = stepCounter?.getSteps() ?: 0,
                    )
                }
                if (completedQuest != null) {
                    energyManager.rewardQuestCompletion()
                    activeQuest = null
                    currentQuestStepCount = 0
                    questHistoryRefreshKey += 1
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                questError = "Grassling couldn't finish this quest. Please try again."
            } finally {
                isSavingQuest = false
            }
        }
    }

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
                    var response: String? = null
                    repeat(3) {
                        if (response == null) response = generateSpeech()
                    }
                    val spokenResponse = response ?: "Hum..."

                    val shownAt = SystemClock.elapsedRealtime()
                    if (nextCallAt <= shownAt) nextCallAt = shownAt + periodMs
                    speechBubble = spokenResponse
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
                QuestScreen(
                    isVisible = drawerState.isOpen,
                    currentStepCount = currentQuestStepCount,
                    refreshKey = questHistoryRefreshKey,
                    onFinishQuest = ::finishQuest,
                )
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
                        modifier = Modifier.size(240.dp).clickable(
                            enabled = !isCheckingActiveQuest && !questStorageUnavailable && activeQuest == null &&
                                !isSavingQuest && !showQuestDialog,
                            onClick = ::requestWalkingQuest,
                        ),
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
                EnergyMeter(energy = energy)
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

    if (showQuestDialog) {
        AlertDialog(
            onDismissRequest = { showQuestDialog = false },
            title = {
                Column {
                    Text(
                        text = "A quest for you",
                        color = Color(0xFF285B35),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Grassling picked a little outdoor adventure.",
                        color = Color(0xFF718071),
                        fontSize = 14.sp,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        isGeneratingQuest -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF4F8A54),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Choosing a quest...")
                        }
                        isSavingQuest -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF4F8A54),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Saving your quest...")
                        }
                        questError != null -> Text(
                            text = questError.orEmpty(),
                            color = Color(0xFF8A4C32),
                            fontSize = 14.sp,
                        )
                        selectedQuest != null -> Surface(
                            color = Color(0xFFEAF4E5),
                            contentColor = Color(0xFF285B35),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    color = Color.White,
                                    contentColor = Color(0xFF4F8A54),
                                    shape = RoundedCornerShape(14.dp),
                                    shadowElevation = 1.dp,
                                ) {
                                    Text(
                                        text = "✦",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        fontSize = 20.sp,
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = selectedQuest?.description.orEmpty(),
                                    fontSize = 17.sp,
                                    lineHeight = 23.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { showQuestDialog = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Decline")
                    }
                    Button(
                        onClick = ::acceptSelectedQuest,
                        modifier = Modifier.weight(1f),
                        enabled = selectedQuest != null && !isGeneratingQuest && !isSavingQuest,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F7B47)),
                    ) {
                        Text("Accept")
                    }
                }
            },
        )
    }
}

@Composable
private fun EnergyMeter(energy: Int) {
    val normalizedEnergy = energy.coerceIn(0, 100)
    val energyColor = when (normalizedEnergy) {
        in 0..59 -> Color(0xFF4F8A54)
        in 60..79 -> Color(0xFFE0A23B)
        else -> Color(0xFFD95F55)
    }

    Surface(
        modifier = Modifier.width(280.dp),
        color = Color.White.copy(alpha = 0.94f),
        contentColor = Color(0xFF285B35),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Energy",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$normalizedEnergy / 100",
                    color = energyColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFE7EEE4)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(normalizedEnergy / 100f)
                        .height(12.dp)
                        .background(energyColor, RoundedCornerShape(50)),
                )
            }
        }
    }
}

private const val CONVERSATION_INTERVAL_MS = 10_000L
private const val MAX_BUBBLE_DURATION_MS = 5_000L
private const val STEP_PROGRESS_REFRESH_MS = 500L
private const val ENERGY_REFRESH_INTERVAL_MS = 60_000L

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreen()
}
