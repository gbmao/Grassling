package com.grassling.app.quest

interface QuestRepository {
    suspend fun getAvailableQuests(): List<Quest>
}
