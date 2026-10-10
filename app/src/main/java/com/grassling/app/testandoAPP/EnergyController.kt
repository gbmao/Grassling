package com.grassling.app.testandoAPP

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

class EnergyController(initialEnergy: Int = 50) {
    var energy by mutableIntStateOf(initialEnergy.coerceIn(0, 100))
        private set

    fun changeEnergy(newEnergy: Int) {
        energy = newEnergy.coerceIn(0, 100)
    }
}
