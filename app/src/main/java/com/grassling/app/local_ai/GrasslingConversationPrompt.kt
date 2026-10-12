package com.grassling.app.local_ai

import com.grassling.app.data.QuestStorage
import com.grassling.app.quest.QuestTrackingMode

/** Builds a standalone Gemma turn for Grassling's periodic, context-aware dialogue. */
class GrasslingConversationPrompt {
    fun buildAcceptedQuestReactionPrompt(mood: String, questDescription: String): String = buildString {
        append("<start_of_turn>user\n")
        appendLine("You are Grassling, a small virtual creature.")
        appendLine()
        appendLine("You feel ${moodBehavior(mood)}.")
        appendLine()
        appendLine("The user just accepted this quest:")
        appendLine("\"${plainText(questDescription).take(MAX_DESCRIPTION_LENGTH)}\"")
        appendLine()
        appendLine("React naturally in one short sentence.")
        appendLine()
        appendLine("Reply only with Grassling's sentence.")
        append("<end_of_turn>\n<start_of_turn>model\nGrassling:")
    }

    fun build(
        mood: String,
        energy: Int,
        currentQuests: List<QuestStorage.StoredQuest>,
        completedQuests: List<QuestStorage.StoredQuest>,
    ): String = buildString {
        append("<start_of_turn>user\n")
        appendLine("You are Grassling.")
        appendLine()
        appendLine("Speak as Grassling in ONE short sentence.")
        appendLine()
        appendLine("Mood: ${plainText(mood)}")
        appendLine("Mood meaning: ${moodBehavior(mood)}")
        appendLine("Energy: ${energy.coerceIn(0, 100)}/100")
        appendLine()
        appendLine("Current quest: ${currentQuests.firstOrNull()?.let(::questDescription) ?: "None"}")
        appendLine("Recently completed: ${completedQuests.firstOrNull()?.let(::questDescription) ?: "None"}")
        appendLine()
        appendLine("Speak naturally and clearly show your current mood.")
        appendLine("Do not repeat the information above.")
        appendLine("Do not write labels such as \"Mood:\", \"Energy:\", or \"Current quest:\".")
        appendLine("Do not explain anything.")
        appendLine("Do not invent events, numbers, actions, or facts.")
        append("<end_of_turn>\n<start_of_turn>model\nGrassling:")
    }

    private fun moodBehavior(mood: String): String = when (mood.uppercase()) {
        "SLEEPING" -> "sleepy, drowsy, quiet, low-energy"
        "NORMAL" -> "calm, casual, neutral, mildly positive"
        "ANXIOUS" -> "nervous, uncertain, uneasy, worried"
        "EXPLODING_WITH_RAGE" -> "furious, irritated, intense, angry"
        else -> "calm, casual, neutral, mildly positive"
    }

    suspend fun generateLine(
        mood: String,
        energy: Int,
        currentQuests: List<QuestStorage.StoredQuest>,
        completedQuests: List<QuestStorage.StoredQuest>,
        generateText: suspend (String) -> Result<String>,
    ): String? {
        val response = generateText(build(mood, energy, currentQuests, completedQuests)).getOrNull()
        return response
            ?.let(::limitToTwoSentences)
            ?.takeIf(String::isNotBlank)
    }

    suspend fun generateAcceptedQuestReaction(
        mood: String,
        questDescription: String,
        generateText: suspend (String) -> Result<String>,
    ): String? {
        val response = generateText(buildAcceptedQuestReactionPrompt(mood, questDescription)).getOrNull()
        return response
            ?.let(::limitToOneSentence)
            ?.takeIf(String::isNotBlank)
    }

    fun limitToOneSentence(response: String): String = limitToSentences(response, 1)

    /** Keeps generated text within the UI's two-sentence limit even if the model ignores it. */
    fun limitToTwoSentences(response: String): String = limitToSentences(response, 2)

    private fun limitToSentences(response: String, maximum: Int): String {
        val clean = response
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('"', '\'', '“', '”')
        return clean.split(Regex("(?<=[.!?])\\s+"))
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .take(maximum)
            .joinToString(" ")
    }

    private fun questDescription(quest: QuestStorage.StoredQuest): String {
        val progressContext = when (quest.trackingMode) {
            QuestTrackingMode.STEPS -> quest.targetSteps.takeIf { it > 0 }?.let { " ($it steps)" }.orEmpty()
            QuestTrackingMode.MINUTES -> quest.targetMinutes.takeIf { it > 0 }?.let { " ($it minutes)" }.orEmpty()
            QuestTrackingMode.NONE -> ""
        }
        return plainText(quest.description).take(MAX_DESCRIPTION_LENGTH) + progressContext
    }

    private fun plainText(value: String): String =
        value.replace(Regex("<[^>]*>|[\\r\\n]"), " ").replace(Regex("\\s+"), " ").trim()

    private companion object {
        const val MAX_DESCRIPTION_LENGTH = 120
    }
}
