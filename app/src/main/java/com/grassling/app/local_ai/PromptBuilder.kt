package com.grassling.app.local_ai

import com.grassling.app.quest.QuestTrackingMode

data class QuestOption(
    val number: Int,
    val description: String,
    val trackingMode: QuestTrackingMode = QuestTrackingMode.NONE,
    val targetSteps: Int = 0,
    val targetMinutes: Int = 0,
)

/** Gemma IT has user/model roles; fixed instructions belong in the first user turn. */
internal object PromptBuilder {
    private val questOptions = listOf(
        QuestOption(1, "Walk 750 steps", QuestTrackingMode.STEPS, targetSteps = 750),
        QuestOption(2, "Take a 10-minute walk", QuestTrackingMode.MINUTES, targetMinutes = 10),
        QuestOption(3, "Find something blue outside"),
        QuestOption(
            4,
            "Walk 1,000 steps to a place you have never visited before",
            QuestTrackingMode.STEPS,
            targetSteps = 1_000,
        ),
        QuestOption(5, "Spend 5 minutes outdoors", QuestTrackingMode.MINUTES, targetMinutes = 5),
        QuestOption(6, "Find three different plants"),
        QuestOption(7, "Walk 1,000 steps before noon", QuestTrackingMode.STEPS, targetSteps = 1_000),
        QuestOption(
            8,
            "Take a short 500-step walk without using your phone",
            QuestTrackingMode.STEPS,
            targetSteps = 500,
        ),
        QuestOption(9, "Find something shaped like a circle"),
        QuestOption(
            10,
            "Walk 750 steps and look for something interesting",
            QuestTrackingMode.STEPS,
            targetSteps = 750,
        ),
    )

    fun randomQuestNumber(): Int = (1..10).random()

    fun randomQuest(): QuestOption {
        val number = randomQuestNumber()
        return questOptions.first { it.number == number }
    }

    fun buildPersonalityPrompt(snapshot: GameSnapshot): String =
        build(snapshot, emptyList(), "Say how you feel now in one short sentence.")

    fun buildPersonalityFallbackPrompt(snapshot: GameSnapshot): String = buildString {
        append("<start_of_turn>user\n")
        appendLine("You are Grassling. Mood: ${plainText(snapshot.mood)}.")
        appendLine("Energy: ${snapshot.energy.coerceIn(0, 100)}/100. Owner steps: ${snapshot.stepsToday.coerceAtLeast(0)}.")
        appendLine("Say how you feel in one short English sentence.")
        append("<end_of_turn>\n<start_of_turn>model\n")
    }

    private fun build(snapshot: GameSnapshot, recentQuests: List<String>, instruction: String): String = buildString {
        // BOS is added exactly once by llama_tokenize(add_special=true).
        append("<start_of_turn>user\n")
        appendLine("You are Grassling, a small virtual monster. Speak in English. " +
            "The owner's walks lower your energy. Lower energy is calmer; match your mood.")
        appendLine("Mood: ${plainText(snapshot.mood)}")
        appendLine("Energy: ${snapshot.energy.coerceIn(0, 100)}/100")
        appendLine("Owner steps today: ${snapshot.stepsToday.coerceAtLeast(0)}")
        if (recentQuests.isNotEmpty()) {
            appendLine("Do not repeat these completed quests:")
            recentQuests.takeLast(5).forEach { appendLine("- ${plainText(it).take(160)}") }
        }
        append(instruction)
        append("<end_of_turn>\n<start_of_turn>model\n")
    }

    private fun plainText(value: String): String = value.replace(Regex("<[^>]*>|[\\r\\n]"), " ").trim()
}
