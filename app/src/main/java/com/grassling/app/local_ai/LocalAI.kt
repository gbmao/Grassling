package com.grassling.app.local_ai

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

interface LocalAI : Closeable {
    val modelFile: File
    val isModelInstalled: Boolean

    suspend fun loadModel(): Result<Unit>

    suspend fun importModel(uri: Uri): Result<Unit>

    suspend fun respond(
        message: String,
        gameSnapshot: GameSnapshot = GameSnapshot(),
        recentConversation: List<ChatMessage> = emptyList(),
    ): Result<String>
}

class LlamaLocalAI(
    context: Context,
    private val config: AIConfig = AIConfig(),
) : LocalAI {
    private val engine = LlamaEngine(config)
    private val modelManager = ModelManager(context, config, engine)

    override val modelFile: File
        get() = modelManager.modelFile

    override val isModelInstalled: Boolean
        get() = modelManager.modelExists()

    override suspend fun loadModel(): Result<Unit> = runCatching {
        modelManager.loadModel()
    }

    override suspend fun importModel(uri: Uri): Result<Unit> = runCatching {
        modelManager.installModel(uri)
        modelManager.loadModel()
    }

    override suspend fun respond(
        message: String,
        gameSnapshot: GameSnapshot,
        recentConversation: List<ChatMessage>,
    ): Result<String> = runCatching {
        require(message.isNotBlank()) { "Escreva uma mensagem antes de conversar." }
        check(engine.isLoaded) { "Carregue o modelo GGUF antes de conversar." }

        val prompt = PromptBuilder.build(
            userMessage = message,
            gameSnapshot = gameSnapshot,
            recentConversation = recentConversation,
            config = config,
        )
        withContext(Dispatchers.Default) {
            engine.generate(prompt)
        }.trim()
    }

    override fun close() {
        modelManager.close()
    }
}
