package com.grassling.app.local_ai

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grassling.app.data.QuestStorage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class GrasslingConversationLogTest {
    @Test
    fun logsOneHundredRealGemmaConversationTriggers() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runId = UUID.randomUUID().toString()
        val localAI = LlamaLocalAI(context)
        val promptBuilder = GrasslingConversationPrompt()
        val states = listOf(
            "SLEEPING" to 15,
            "NORMAL" to 45,
            "ANXIOUS" to 70,
            "EXPLODING_WITH_RAGE" to 90,
            "NORMAL" to 52,
        )
        val activeQuestDescriptions = listOf(
            "Walk 500 steps",
            "Take a short outdoor walk",
            "Walk for five minutes",
            "Find something green while walking",
            "Walk 300 steps at your own pace",
        )
        val completedQuestDescriptions = listOf(
            "Walked 250 steps",
            "Took a gentle stroll",
            "Walked to see the morning sky",
            "Completed a ten-minute walk",
            "Found a new leafy friend",
        )
        val runLog = File(context.cacheDir, "grassling-conversation-100-$runId.log")
        var loggedTriggers = 0
        var nonEmptyResponses = 0

        runLog.bufferedWriter().use { writer ->
            val begin = "BEGIN run_id=$runId target_triggers=$TRIGGER_COUNT " +
                "model=${ModelManager.FILE_NAME} device=${Build.MANUFACTURER} ${Build.MODEL} " +
                "android_api=${Build.VERSION.SDK_INT} threads=${AIConfig().inferenceThreads}"
            writer.appendLine(begin)
            Log.i(TAG, begin)
            try {
                localAI.loadModel().getOrThrow()
                repeat(TRIGGER_COUNT) { index ->
                    val (mood, energy) = states[index % states.size]
                    val active = listOf(
                        QuestStorage.StoredQuest(
                            activeQuestDescriptions[index % activeQuestDescriptions.size],
                            targetSteps = 300 + (index % 5) * 100,
                        ),
                    )
                    val completed = listOf(
                        QuestStorage.StoredQuest(
                            description = completedQuestDescriptions[index % completedQuestDescriptions.size],
                            targetSteps = 200 + (index % 4) * 100,
                            completed = true,
                            completedAt = "2026-10-10T%02d:%02d:%02d.000Z".format(
                                index / 3600 % 24,
                                index / 60 % 60,
                                index % 60,
                            ),
                        ),
                    )
                    var line: String? = null
                    val elapsedMs = measureTimeMillis {
                        line = promptBuilder.generateLine(
                            mood = mood,
                            energy = energy,
                            currentQuests = active,
                            completedQuests = completed,
                            generateText = localAI::generateConversation,
                        )
                    }
                    val entry = JSONObject()
                        .put("run_id", runId)
                        .put("trigger", index + 1)
                        .put("mood", mood)
                        .put("energy", energy)
                        .put("current_quest", active.first().description)
                        .put("completed_quest", completed.first().description)
                        .put("duration_ms", elapsedMs)
                        .put("speech", line ?: "")
                        .put("non_empty", !line.isNullOrBlank())
                    writer.appendLine(entry.toString())
                    loggedTriggers += 1
                    if (!line.isNullOrBlank()) nonEmptyResponses += 1
                    Log.i(TAG, entry.toString())
                }
                val end = "END run_id=$runId triggers=$loggedTriggers non_empty=$nonEmptyResponses"
                writer.appendLine(end)
                Log.i(TAG, end)
                assertEquals("The test must execute all conversation triggers", TRIGGER_COUNT, loggedTriggers)
            } finally {
                localAI.close()
            }
        }
    }

    private companion object {
        const val TAG = "GrasslingConv100"
        const val TRIGGER_COUNT = 100
    }
}
