package com.grassling.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grassling.app.data.database.entity.MonsterEntity

@Dao
interface MonsterDao {
    @Query("SELECT * FROM monsters WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MonsterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(monster: MonsterEntity)
}
