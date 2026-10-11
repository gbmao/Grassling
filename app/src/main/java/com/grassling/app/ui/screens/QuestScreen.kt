package com.grassling.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grassling.app.data.QuestStorage
import com.grassling.app.quest.QuestTrackingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val ForestGreen = Color(0xFF285B35)
private val SoftGreen = Color(0xFFE6F2E1)
private val QuestBackground = Color(0xFFF5F8F2)

@Composable
fun QuestScreen(isVisible: Boolean, currentStepCount: Int) {
    val context = LocalContext.current
    var activeQuest by remember { mutableStateOf<QuestStorage.StoredQuest?>(null) }
    var completedQuests by remember { mutableStateOf(emptyList<QuestStorage.StoredQuest>()) }
    var isLoading by remember { mutableStateOf(false) }
    var elapsedQuestSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(isVisible, context) {
        if (isVisible) {
            isLoading = true
            val quests = withContext(Dispatchers.IO) {
                runCatching {
                    QuestStorage(context.applicationContext)
                        .loadQuests()
                }.getOrDefault(emptyList())
            }
            activeQuest = quests.lastOrNull { !it.completed }
            completedQuests = quests
                .filter { it.completed }
                .sortedByDescending { it.completedAt.orEmpty() }
                .take(5)
            isLoading = false
        }
    }

    LaunchedEffect(isVisible, activeQuest?.creationDate, activeQuest?.targetMinutes) {
        val quest = activeQuest
        if (!isVisible || quest?.trackingMode != QuestTrackingMode.MINUTES || quest.targetMinutes <= 0) {
            elapsedQuestSeconds = 0L
            return@LaunchedEffect
        }
        while (currentCoroutineContext().isActive) {
            elapsedQuestSeconds = elapsedSecondsSince(quest.creationDate)
            delay(1_000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(QuestBackground)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quest log",
                    color = ForestGreen,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Your active quest and recent wins",
                    color = Color(0xFF718071),
                    fontSize = 14.sp,
                )
            }
            if (completedQuests.isNotEmpty()) {
                Surface(
                    shape = CircleShape,
                    color = SoftGreen,
                ) {
                    Text(
                        text = "${completedQuests.size}/5",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = ForestGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = ForestGreen)
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    activeQuest?.let { quest ->
                        item(key = "active-${quest.creationDate}") {
                            InProgressSection(
                                quest = quest,
                                currentStepCount = currentStepCount,
                                elapsedSeconds = elapsedQuestSeconds,
                            )
                        }
                    }
                    item(key = "completed-header") {
                        Text(
                            text = "Completed",
                            color = ForestGreen,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (completedQuests.isEmpty()) {
                        item(key = "empty-history") {
                            EmptyQuestHistory(modifier = Modifier.height(180.dp))
                        }
                    } else {
                        itemsIndexed(
                            items = completedQuests,
                            key = { index, quest -> "${quest.completedAt}-${quest.creationDate}-$index" },
                        ) { _, quest ->
                            CompletedQuestCard(quest = quest)
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EmptyQuestHistory(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(SoftGreen),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✓",
                    color = ForestGreen,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "No completed quests yet",
                color = ForestGreen,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = "Complete a quest and it will show up here.",
                color = Color(0xFF718071),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun InProgressSection(
    quest: QuestStorage.StoredQuest,
    currentStepCount: Int,
    elapsedSeconds: Long,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "In progress",
            color = ForestGreen,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4E5)),
            border = BorderStroke(1.dp, Color(0xFFD5E7CF)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "➜",
                            color = ForestGreen,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "In progress",
                        color = ForestGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = quest.description,
                    color = Color(0xFF26372A),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                when (quest.trackingMode) {
                    QuestTrackingMode.STEPS -> {
                        val progress = currentStepCount.coerceAtLeast(0)
                        val text = if (quest.targetSteps > 0) {
                            "$progress / ${quest.targetSteps} steps"
                        } else {
                            "$progress steps"
                        }
                        QuestProgressBadge(text)
                    }
                    QuestTrackingMode.MINUTES -> if (quest.targetMinutes > 0) {
                        QuestProgressBadge(
                            "${formatDuration(elapsedSeconds)} / ${formatDuration(quest.targetMinutes * 60L)}",
                        )
                    }
                    QuestTrackingMode.NONE -> Unit
                }
            }
        }
    }
}

@Composable
private fun CompletedQuestCard(
    quest: QuestStorage.StoredQuest,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE5ECE2)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(SoftGreen),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✓",
                    color = ForestGreen,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quest.description,
                    color = Color(0xFF26372A),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        quest.targetSteps > 0 -> {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = SoftGreen,
                            ) {
                                Text(
                                    text = "${quest.targetSteps} steps",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    color = ForestGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        quest.targetMinutes > 0 -> {
                            Surface(shape = RoundedCornerShape(50), color = SoftGreen) {
                                Text(
                                    text = "${quest.targetMinutes} min",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    color = ForestGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    formatCompletionDate(quest.completedAt)?.let { date ->
                        Text(
                            text = date,
                            color = Color(0xFF718071),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestProgressBadge(text: String) {
    Surface(shape = RoundedCornerShape(50), color = Color.White) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = ForestGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun elapsedSecondsSince(timestamp: String): Long {
    val startTime = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }.parse(timestamp)?.time
    }.getOrNull() ?: System.currentTimeMillis()
    return ((System.currentTimeMillis() - startTime) / 1_000L).coerceAtLeast(0L)
}

private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds.coerceAtLeast(0L) / 60L
    val seconds = totalSeconds.coerceAtLeast(0L) % 60L
    return "%02d:%02d".format(Locale.US, minutes, seconds)
}

private fun formatCompletionDate(timestamp: String?): String? {
    if (timestamp.isNullOrBlank()) return null
    val datePart = timestamp.substringBefore('T')
    val parsedDate = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(datePart)
    }.getOrNull() ?: return null
    return SimpleDateFormat("MMM d", Locale.US).format(parsedDate)
}
