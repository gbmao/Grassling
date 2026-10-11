package com.grassling.app.local_ai

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

interface LocalAI : Closeable {
    suspend fun loadModel(): Result<Unit>
    suspend fun generatePersonality(snapshot: GameSnapshot): Result<String>
    suspend fun chooseQuest(energy: Int): Result<QuestOption>
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

    override suspend fun chooseQuest(energy: Int): Result<QuestOption> = withContext(Dispatchers.Default) {
        result {
            var selection: QuestOption? = null
            repeat(3) { attempt ->
                if (selection == null) {
                    val prompt = if (attempt == 0) {
                        PromptBuilder.buildQuestPrompt(energy)
                    } else {
                        PromptBuilder.buildQuestFallbackPrompt(energy)
                    }
                    selection = PromptBuilder.resolveQuestSelection(engine.generate(prompt))
                }
            }
            checkNotNull(selection) { "Grassling couldn't choose a quest. Please try again." }
        }
    }

    private suspend fun generate(
        prompt: String,
        fallbackPrompt: String,
    ): Result<String> = withContext(Dispatchers.Default) {
        result {
            var response = ""
            repeat(3) { attempt ->
                if (response.isBlank()) {
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

}
