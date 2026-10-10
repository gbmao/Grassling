package com.grassling.app.local_ai

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

interface LocalAI : Closeable {
    suspend fun loadModel(): Result<Unit>
    suspend fun generatePersonality(snapshot: GameSnapshot): Result<String>
    suspend fun generateQuest(snapshot: GameSnapshot, recentQuests: List<String> = emptyList()): Result<String>
}

class LlamaLocalAI(context: Context, private val config: AIConfig = AIConfig()) : LocalAI {
    private val engine = LlamaEngine(config)
    private val modelManager = ModelManager(context)

    override suspend fun loadModel(): Result<Unit> = withContext(Dispatchers.IO) {
        result {
            if (!engine.isLoaded) {
                check(modelManager.isModelInstalled()) { "The AI model is not installed or failed verification." }
                engine.loadModel(modelManager.getModelFile().absolutePath)
            }
        }
    }

    override suspend fun generatePersonality(snapshot: GameSnapshot): Result<String> =
        generate(
            PromptBuilder.buildPersonalityPrompt(snapshot),
            PromptBuilder.buildPersonalityFallbackPrompt(snapshot),
        )

    /** Generates a standalone conversation turn using a caller-built Grassling prompt. */
    suspend fun generateConversation(prompt: String): Result<String> = generate(prompt, prompt)

    override suspend fun generateQuest(snapshot: GameSnapshot, recentQuests: List<String>): Result<String> =
        generate(
            PromptBuilder.buildQuestPrompt(snapshot, recentQuests),
            PromptBuilder.buildQuestFallbackPrompt(snapshot),
            requireWalkQuest = true,
        )

    private suspend fun generate(
        prompt: String,
        fallbackPrompt: String,
        requireWalkQuest: Boolean = false,
    ): Result<String> = withContext(Dispatchers.Default) {
        result {
            var response = ""
            repeat(3) { attempt ->
                val needsRetry = response.isBlank() ||
                    (requireWalkQuest && !WALK_QUEST_PATTERN.containsMatchIn(response))
                if (needsRetry) {
                    val attemptPrompt = if (attempt == 0) prompt else fallbackPrompt
                    response = engine.generate(attemptPrompt).trim()
                }
            }
            response
        }
    }

    // The Application owns this instance. Call close off the UI thread for explicit teardown/tests.
    override fun close() = engine.releaseModel()

    private inline fun <T> result(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private companion object {
        val WALK_QUEST_PATTERN = Regex("\\b(walk|walking|stroll|steps?|minutes?)\\b", RegexOption.IGNORE_CASE)
    }
}
