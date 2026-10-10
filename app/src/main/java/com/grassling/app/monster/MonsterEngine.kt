package com.grassling.app.monster

class MonsterEngine {
    fun moodForEnergy(energy: Int): MonsterMood = when (energy.coerceIn(0, 100)) {
        in 0..29 -> MonsterMood.SLEEPING
        in 30..59 -> MonsterMood.NORMAL
        in 60..79 -> MonsterMood.ANXIOUS
        else -> MonsterMood.EXPLODING_WITH_RAGE
    }
}
