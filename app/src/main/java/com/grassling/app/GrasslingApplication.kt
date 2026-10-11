package com.grassling.app

import android.app.Application
import com.grassling.app.local_ai.LlamaLocalAI
import com.grassling.app.monster.EnergyManager
import com.grassling.app.utils.StepCounterManager

class GrasslingApplication : Application() {
    // First accessed by ModelSetupViewModel on an IO dispatcher; retained for the process lifetime.
    val localAI: LlamaLocalAI by lazy { LlamaLocalAI(this) }
    val energyManager: EnergyManager by lazy { EnergyManager(this) }
    val stepCounterManager: StepCounterManager by lazy { StepCounterManager(this, energyManager) }
}
