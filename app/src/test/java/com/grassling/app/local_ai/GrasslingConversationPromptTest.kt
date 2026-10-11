package com.grassling.app.local_ai

import com.grassling.app.data.QuestStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GrasslingConversationPromptTest {
    private val prompts = GrasslingConversationPrompt()

    @Test
    fun generateLineCallsTheConversationGeneratorWithCurrentGameContext() = runBlocking {
        val active = listOf(
            QuestStorage.StoredQuest("Walk 500 steps", targetSteps = 500),
            QuestStorage.StoredQuest("A second active walk", targetSteps = 750),
        )
        val completed = listOf(
            QuestStorage.StoredQuest(
                description = "Walk around the block",
                targetSteps = 300,
                completed = true,
                completedAt = "2026-10-10T10:00:00.000Z",
            ),
            QuestStorage.StoredQuest(
                description = "An older completed walk",
                targetSteps = 100,
                completed = true,
                completedAt = "2026-10-09T10:00:00.000Z",
            ),
        )
        var calls = 0
        var receivedPrompt = ""

        val line = prompts.generateLine(
            mood = "ANXIOUS",
            energy = 72,
            currentQuests = active,
            completedQuests = completed,
        ) { prompt ->
            calls += 1
            receivedPrompt = prompt
            Result.success("I feel restless. Let's walk soon.")
        }

        assertEquals(1, calls)
        assertEquals("I feel restless. Let's walk soon.", line)
        val expectedPrompt = """
            <start_of_turn>user
            You are Grassling.

            Speak as Grassling in ONE short sentence.

            Mood: ANXIOUS
            Mood meaning: nervous, uncertain, uneasy, worried
            Energy: 72/100

            Current quest: Walk 500 steps (500 steps)
            Recently completed: Walk around the block (300 steps)

            Speak naturally and clearly show your current mood.
            Do not repeat the information above.
            Do not write labels such as "Mood:", "Energy:", or "Current quest:".
            Do not explain anything.
            Do not invent events, numbers, actions, or facts.
            <end_of_turn>
            <start_of_turn>model
            Grassling:
        """.trimIndent()
        assertEquals(expectedPrompt, receivedPrompt)
        assertFalse(receivedPrompt.contains("A second active walk"))
        assertFalse(receivedPrompt.contains("An older completed walk"))
        assertFalse(receivedPrompt.contains("Example:"))
        assertFalse(receivedPrompt.contains("Something feels wrong"))
        assertFalse(receivedPrompt.contains("Grrr! Everything is getting on my nerves"))
        assertTrue(receivedPrompt.endsWith("Grassling:"))
    }

    @Test
    fun promptIncludesOnlyTheCurrentMoodMeaningAndNoExamples() {
        val expected = mapOf(
            "SLEEPING" to "sleepy, drowsy, quiet, low-energy",
            "NORMAL" to "calm, casual, neutral, mildly positive",
            "ANXIOUS" to "nervous, uncertain, uneasy, worried",
            "EXPLODING_WITH_RAGE" to "furious, irritated, intense, angry",
        )

        expected.forEach { (mood, behavior) ->
            val prompt = prompts.build(mood, 50, emptyList(), emptyList())
            assertTrue("Missing mood for $mood", prompt.contains("Mood: $mood\n"))
            assertTrue("Missing mood meaning for $mood", prompt.contains("Mood meaning: $behavior\n"))
            expected.values.filterNot { it == behavior }.forEach { otherBehavior ->
                assertFalse("Prompt for $mood includes another mood meaning", prompt.contains(otherBehavior))
            }
            assertFalse(prompt.contains("Example:"))
            assertTrue(prompt.endsWith("Grassling:"))
        }
    }

    @Test
    fun generateLineLimitsSpeechToTwoSentences() = runBlocking {
        val line = prompts.generateLine(
            mood = "NORMAL",
            energy = 45,
            currentQuests = emptyList(),
            completedQuests = emptyList(),
        ) { Result.success("I feel good. Let's go outside! I also found a leaf.") }

        assertEquals("I feel good. Let's go outside!", line)
    }

    @Test
    fun generateLineDoesNotReturnBlankOrFailedResponses() = runBlocking {
        val inputs = emptyList<QuestStorage.StoredQuest>()
        val blank = prompts.generateLine("SLEEPING", 12, inputs, inputs) {
            Result.success("  <end_of_turn>  ")
        }
        val failed = prompts.generateLine("SLEEPING", 12, inputs, inputs) {
            Result.failure(IOException("test failure"))
        }

        assertNull(blank)
        assertNull(failed)
        assertFalse(prompts.limitToTwoSentences("One. Two. Three.").contains("Three"))
    }
}
