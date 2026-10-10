package com.grassling.app.local_ai

/** Gemma IT has user/model roles; fixed instructions belong in the first user turn. */
internal object PromptBuilder {
    fun buildQuestPrompt(snapshot: GameSnapshot, recentQuests: List<String> = emptyList()): String = buildString {
        append("<start_of_turn>user\n")
        appendLine("Give your owner one short outdoor walking quest.")
        appendLine("Mood: ${plainText(snapshot.mood)}")
        appendLine("Energy: ${snapshot.energy.coerceIn(0, 100)}/100")
        appendLine("Owner steps today: ${snapshot.stepsToday.coerceAtLeast(0)}")
        if (recentQuests.isNotEmpty()) {
            appendLine("Recent quests:")
            recentQuests.takeLast(3).forEach { appendLine("- ${plainText(it).take(120)}") }
        }
        appendLine("Speak as Grassling in English. Use one short sentence and no title.")
        append("<end_of_turn>\n<start_of_turn>model\n")
    }

    fun buildPersonalityPrompt(snapshot: GameSnapshot): String =
        build(snapshot, emptyList(), "Say how you feel now in one short sentence.")

    fun buildQuestFallbackPrompt(snapshot: GameSnapshot): String = buildString {
        append("<start_of_turn>user\n")
        appendLine("Ask your owner to take one short outdoor walk.")
        appendLine("Mood: ${plainText(snapshot.mood)}")
        appendLine("Energy: ${snapshot.energy.coerceIn(0, 100)}/100")
        appendLine("Owner steps today: ${snapshot.stepsToday.coerceAtLeast(0)}")
        appendLine("Reply as Grassling in English, in one short sentence.")
        append("<end_of_turn>\n<start_of_turn>model\n")
    }

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
