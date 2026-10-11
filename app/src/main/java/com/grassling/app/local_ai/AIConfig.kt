package com.grassling.app.local_ai

data class AIConfig(
    val contextSize: Int = 2048,
    val maxOutputTokens: Int = 32,
    val temperature: Float = 0.7f,
    val topP: Float = 0.8f,
    val topK: Int = 20,
    val presencePenalty: Float = 0.0f,
    val inferenceThreads: Int = 4,
)

data class GameSnapshot(
    val mood: String = "NORMAL",
    val energy: Int = 50,
    val stepsToday: Int = 0,
)
