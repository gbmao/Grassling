package com.grassling.app.local_ai

import com.grassling.app.quest.QuestTrackingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestSelectionTest {
    private val expectedQuests = mapOf(
        1 to Triple("Walk 750 steps", QuestTrackingMode.STEPS, 750),
        2 to Triple("Take a 10-minute walk", QuestTrackingMode.MINUTES, 0),
        3 to Triple("Find something blue outside", QuestTrackingMode.NONE, 0),
        4 to Triple("Walk 1,000 steps to a place you have never visited before", QuestTrackingMode.STEPS, 1_000),
        5 to Triple("Spend 5 minutes outdoors", QuestTrackingMode.MINUTES, 0),
        6 to Triple("Find three different plants", QuestTrackingMode.NONE, 0),
        7 to Triple("Walk 1,000 steps before noon", QuestTrackingMode.STEPS, 1_000),
        8 to Triple("Take a short 500-step walk without using your phone", QuestTrackingMode.STEPS, 500),
        9 to Triple("Find something shaped like a circle", QuestTrackingMode.NONE, 0),
        10 to Triple("Walk 750 steps and look for something interesting", QuestTrackingMode.STEPS, 750),
    )

    @Test
    fun randomQuestNumberAlwaysFallsBetweenOneAndTen() {
        repeat(1_000) {
            assertTrue(PromptBuilder.randomQuestNumber() in 1..10)
        }
    }

    @Test
    fun randomQuestMatchesTheExistingOptionForItsNumber() {
        repeat(1_000) {
            val quest = PromptBuilder.randomQuest()
            val expected = expectedQuests.getValue(quest.number)

            assertEquals(expected.first, quest.description)
            assertEquals(expected.second, quest.trackingMode)
            assertEquals(expected.third, quest.targetSteps)
        }
    }
}
