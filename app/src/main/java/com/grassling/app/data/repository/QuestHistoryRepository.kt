package com.grassling.app.data.repository

import com.grassling.app.data.database.dao.QuestDao
import com.grassling.app.data.database.entity.QuestEntity

class QuestHistoryRepository(private val questDao: QuestDao) {
    suspend fun getHistory(): List<QuestEntity> = questDao.getHistory()

    suspend fun saveQuest(quest: QuestEntity) = questDao.save(quest)
}
