package com.grassling.app.monster

data class Monster(
    val id: String = "",
    val name: String = "Grassling",
    val state: MonsterState = MonsterState.IDLE,
    val mood: MonsterMood = MonsterMood.NORMAL,
)
