package com.grassling.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.grassling.app.data.database.dao.MonsterDao
import com.grassling.app.data.database.dao.QuestDao
import com.grassling.app.data.database.entity.MonsterEntity
import com.grassling.app.data.database.entity.QuestEntity

@Database(
    entities = [MonsterEntity::class, QuestEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class GrasslingDatabase : RoomDatabase() {
    abstract fun monsterDao(): MonsterDao

    abstract fun questDao(): QuestDao
}
