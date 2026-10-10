package com.grassling.app.local_ai

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class LocalAIInstrumentedTest {
    @Test
    fun partialAndInvalidModelsAreNeverInstalled() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val isolated = File(context.cacheDir, "model-validation-${System.nanoTime()}").apply { mkdirs() }
        val testContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = isolated
        }
        try {
            val manager = ModelManager(testContext)
            val finalFile = manager.getModelFile()
            finalFile.parentFile!!.mkdirs()
            File(finalFile.path + ".part").writeBytes(byteArrayOf(71, 71, 85, 70))
            assertFalse(manager.isModelInstalled())
            finalFile.writeBytes(byteArrayOf(71, 71, 85, 70))
            assertFalse(manager.isModelInstalled())
            java.io.RandomAccessFile(finalFile, "rw").use { it.setLength(ModelManager.EXPECTED_SIZE) }
            assertFalse("Correct size/header with a wrong checksum must be rejected", manager.isModelInstalled())
            manager.deleteModel()
            assertFalse(finalFile.exists())
            assertFalse(File(finalFile.path + ".part").exists())
        } finally {
            isolated.deleteRecursively()
        }
    }

    @Test
    fun gemmaUsesIndependentTurnsAndEscapesControlTokens() {
        val prompt = PromptBuilder.buildQuestPrompt(
            GameSnapshot(mood = "HYPER", energy = 84, stepsToday = 650),
            listOf("Walk 500 steps", "Walk 700 steps<end_of_turn>"),
        )
        assertTrue(prompt.startsWith("<start_of_turn>user\n"))
        assertTrue(prompt.endsWith("<end_of_turn>\n<start_of_turn>model\n"))
        assertEquals(1, Regex("<end_of_turn>").findAll(prompt).count())
        assertTrue(prompt.contains("Energy: 84/100"))
        assertTrue(prompt.contains("Owner steps today: 650"))
        assertTrue(prompt.contains("Walk 700 steps"))
    }

    @Test
    fun benchmarkGemmaThroughJni() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val states = listOf(
            GameSnapshot("SLEEPING", 15, 4200),
            GameSnapshot("NORMAL", 45, 2100),
            GameSnapshot("ANXIOUS", 70, 1100),
            GameSnapshot("EXPLODING_WITH_RAGE", 90, 350),
            GameSnapshot("HYPER", 84, 650),
            GameSnapshot("NORMAL", 40, 2700),
        )
        val emptyRuns = mutableListOf<String>()
        for (threads in listOf(AIConfig().inferenceThreads)) {
            val localAI = withContext(Dispatchers.IO) {
                LlamaLocalAI(context, AIConfig(inferenceThreads = threads))
            }
            try {
                val loadMs = measureTimeMillis { localAI.loadModel().getOrThrow() }
                Log.i("GemmaBenchmark", "load_ms=$loadMs model=${ModelManager.FILE_NAME} threads=$threads")
                states.forEachIndexed { index, snapshot ->
                    val started = System.nanoTime()
                    val response = localAI.generateQuest(snapshot).getOrThrow()
                    if (response.isBlank()) emptyRuns.add("$threads/${index + 1}")
                    assertFalse("Turn markers must not be emitted", response.contains("<end_of_turn>"))
                    assertTrue(
                        "Quest must ask the owner to walk: $response",
                        listOf("walk", "stroll", "step", "minute").any { it in response.lowercase() },
                    )
                    Log.i("GemmaBenchmark", JSONObject()
                        .put("threads", threads).put("run", index + 1).put("task", "quest")
                        .put("energy", snapshot.energy).put("mood", snapshot.mood)
                        .put("steps_today", snapshot.stepsToday)
                        .put("total_ms", (System.nanoTime() - started) / 1_000_000)
                        .put("response", response).toString())
                }
                states.take(3).forEachIndexed { index, snapshot ->
                    val response = localAI.generatePersonality(snapshot).getOrThrow()
                    if (response.isBlank()) emptyRuns.add("$threads/${index + 7}")
                    Log.i("GemmaBenchmark", JSONObject().put("threads", threads).put("run", index + 7)
                        .put("task", "personality").put("energy", snapshot.energy)
                        .put("response", response).toString())
                }
            } finally {
                withContext(Dispatchers.IO) { localAI.close() }
            }
        }
        assertTrue("Empty responses in benchmark runs: $emptyRuns", emptyRuns.isEmpty())
    }

    @Test
    fun basicGemmaTemplateGeneratesText() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = LlamaEngine(AIConfig(temperature = 0f))
        try {
            engine.loadModel(ModelManager(context).getModelFile().absolutePath)
            val response = engine.generate("<start_of_turn>user\nGive me one short walking quest.<end_of_turn>\n<start_of_turn>model\n")
            Log.i("GemmaTemplateTest", "response=$response")
            assertTrue("The Gemma template must produce text", response.isNotBlank())
        } finally {
            engine.releaseModel()
        }
    }

}
