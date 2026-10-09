package com.grassling.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quests")
data class QuestEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String,
    val completed: Boolean,
    val createdAt: Long,
)
