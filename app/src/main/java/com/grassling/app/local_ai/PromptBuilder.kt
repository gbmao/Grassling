package com.grassling.app.local_ai

data class QuestOption(
    val number: Int,
    val description: String,
)

/** Gemma IT has user/model roles; fixed instructions belong in the first user turn. */
internal object PromptBuilder {
    private val questOptions = listOf(
        QuestOption(1, "Walk 750 steps"),
        QuestOption(2, "Take a 10-minute walk"),
        QuestOption(3, "Find something blue outside"),
        QuestOption(4, "Walk to a place you have never visited before"),
        QuestOption(5, "Spend 5 minutes outdoors"),
        QuestOption(6, "Find three different plants"),
        QuestOption(7, "Walk 1,000 steps before noon"),
        QuestOption(8, "Take a short walk without using your phone"),
        QuestOption(9, "Find something shaped like a circle"),
        QuestOption(10, "Walk until you discover something interesting"),
    )

    fun buildQuestPrompt(energy: Int): String = buildQuestSelectionPrompt(energy, isRetry = false)

    fun buildQuestFallbackPrompt(energy: Int): String = buildQuestSelectionPrompt(energy, isRetry = true)

    fun resolveQuestSelection(response: String): QuestOption? {
        val number = Regex("^(?:quest|option)?\\s*(10|[1-9])\\.?$", RegexOption.IGNORE_CASE)
            .matchEntire(response.trim())
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: return null
        return questOptions.firstOrNull { it.number == number }
    }

    private fun buildQuestSelectionPrompt(energy: Int, isRetry: Boolean): String = buildString {
        append("<start_of_turn>user\n")
        appendLine("You are Grassling. Choose which existing quest to ask for based on your current energy.")
        appendLine("Higher energy means choose a longer walk or more steps. Lower energy means choose a shorter, easier quest.")
        appendLine("Current energy: ${energy.coerceIn(0, 100)}/100")
        appendLine("Available quests:")
        questOptions.forEach { appendLine("${it.number}. ${it.description}") }
        if (isRetry) appendLine("Select one number from 1 to 10. Output nothing else.")
        appendLine("Do not create, rewrite, or describe a quest. Reply with only the chosen quest number.")
        append("<end_of_turn>\n<start_of_turn>model\n")
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
