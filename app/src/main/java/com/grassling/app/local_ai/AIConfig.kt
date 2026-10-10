package com.grassling.app.local_ai

data class AIConfig(
    val modelFileName: String = "Qwen3-0.6B.Q4_K_M.gguf",
    val contextSize: Int = 2048,
    val maxOutputTokens: Int = 96,
    val maxRecentMessages: Int = 8,
    val temperature: Float = 0.7f,
    val topP: Float = 0.8f,
    val topK: Int = 20,
    val presencePenalty: Float = 1.5f,
    val inferenceThreads: Int = 4,
)

enum class ChatRole(val promptRole: String) {
    USER("user"),
    GRASSLING("assistant"),
}

data class ChatMessage(
    val role: ChatRole,
    val content: String,
)

data class GameSnapshot(
    val mood: String? = null,
    val energy: Int? = null,
    val level: Int? = null,
    val currentQuest: String? = null,
)
