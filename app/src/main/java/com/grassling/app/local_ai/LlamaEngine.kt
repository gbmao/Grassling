package com.grassling.app.local_ai

class LlamaEngine(
    private val config: AIConfig,
) {
    private val native = NativeLlamaBridge()
    private var handle = 0L

    val isLoaded: Boolean
        @Synchronized get() = handle != 0L

    @Synchronized
    fun loadModel(path: String) {
        releaseModel()
        handle = native.nativeLoadModel(
            path = path,
            contextSize = config.contextSize,
            threadCount = config.inferenceThreads,
        )
        check(handle != 0L) { "llama.cpp não retornou um modelo carregado." }
    }

    @Synchronized
    fun generate(prompt: String): String {
        check(handle != 0L) { "O modelo local não foi carregado." }
        return native.nativeGenerate(
            handle = handle,
            prompt = prompt,
            maxTokens = config.maxOutputTokens,
            temperature = config.temperature,
            topP = config.topP,
            topK = config.topK,
            presencePenalty = config.presencePenalty,
        )
    }

    @Synchronized
    fun releaseModel() {
        if (handle != 0L) {
            native.nativeReleaseModel(handle)
            handle = 0L
        }
    }

}

internal class NativeLlamaBridge {
    external fun nativeLoadModel(path: String, contextSize: Int, threadCount: Int): Long

    external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        presencePenalty: Float,
    ): String

    external fun nativeReleaseModel(handle: Long)

    companion object {
        init {
            System.loadLibrary("grassling_llama")
        }
    }
}
