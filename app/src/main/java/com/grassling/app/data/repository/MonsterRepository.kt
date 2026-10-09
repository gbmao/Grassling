package com.grassling.app.data.repository

import com.grassling.app.data.database.dao.MonsterDao
import com.grassling.app.data.database.entity.MonsterEntity

class MonsterRepository(private val monsterDao: MonsterDao) {
    suspend fun getMonster(id: String): MonsterEntity? = monsterDao.getById(id)

    suspend fun saveMonster(monster: MonsterEntity) = monsterDao.save(monster)
}
