package com.grassling.app.monster

import android.content.Context
import com.grassling.app.data.StepStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.floor
import kotlin.math.min

/** Persists Grassling's energy and restores 100 points over a configurable duration. */
class EnergyManager(
    context: Context,
    initialEnergy: Int = 0,
    private val timeMillis: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val lock = Any()
    private var energyValue: Double
    private var lastUpdatedAt: Long
    private val mutableEnergy: MutableStateFlow<Int>
    val energy: StateFlow<Int>

    init {
        val now = timeMillis()
        val resetForCurrentRules = preferences.getInt(KEY_ENERGY_RULE_VERSION, 0) < ENERGY_RULE_VERSION
        energyValue = if (resetForCurrentRules) {
            initialEnergy.coerceIn(0, MAX_ENERGY).toDouble()
        } else {
            preferences.getString(KEY_ENERGY, null)
                ?.toDoubleOrNull()
                ?.coerceIn(0.0, MAX_ENERGY.toDouble())
                ?: initialEnergy.coerceIn(0, MAX_ENERGY).toDouble()
        }
        lastUpdatedAt = if (resetForCurrentRules) {
            now
        } else {
            preferences.getLong(KEY_LAST_UPDATED_AT, now).coerceAtMost(now)
        }
        mutableEnergy = MutableStateFlow(energyValue.toInt())
        energy = mutableEnergy.asStateFlow()
        if (resetForCurrentRules) {
            preferences.edit().putInt(KEY_ENERGY_RULE_VERSION, ENERGY_RULE_VERSION).apply()
        }
        refreshEnergy(now)
        resetStepRemainderForCurrentRule()
        initializeStepCheckpoint(context.applicationContext)
    }

    /** Applies time-based energy gain and returns the current whole-number value. */
    fun refreshEnergy(): Int = refreshEnergy(timeMillis())

    /** Sets energy directly, for controlled game actions and development controls. */
    fun setEnergy(value: Int): Int = synchronized(lock) {
        refreshEnergyLocked(timeMillis())
        energyValue = value.coerceIn(0, MAX_ENERGY).toDouble()
        lastUpdatedAt = timeMillis()
        publishAndSave()
    }

    /** Adds a game reward, capped at 100 energy. */
    fun addEnergy(amount: Int): Int = synchronized(lock) {
        refreshEnergyLocked(timeMillis())
        energyValue = min(MAX_ENERGY.toDouble(), energyValue + amount.coerceAtLeast(0))
        publishAndSave()
    }

    /** Removes energy immediately, in addition to the passive 24-hour decay. */
    fun removeEnergy(amount: Int): Int = synchronized(lock) {
        refreshEnergyLocked(timeMillis())
        energyValue = (energyValue - amount.coerceAtLeast(0)).coerceAtLeast(0.0)
        publishAndSave()
    }

    /** Consumes a cumulative step total and removes one energy for every 11 new steps. */
    fun recordCumulativeSteps(cumulativeSteps: Int): Int = synchronized(lock) {
        refreshEnergyLocked(timeMillis())
        val safeTotal = cumulativeSteps.coerceAtLeast(0)
        val previousTotal = preferences.getInt(KEY_LAST_STEP_TOTAL, safeTotal).coerceAtLeast(0)
        val newSteps = (safeTotal - previousTotal).coerceAtLeast(0)
        val totalPendingSteps = preferences.getInt(KEY_PENDING_STEP_REMAINDER, 0)
            .coerceAtLeast(0)
            .toLong() + newSteps
        val energyToRemove = (totalPendingSteps / STEPS_PER_ENERGY_POINT).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val pendingSteps = (totalPendingSteps % STEPS_PER_ENERGY_POINT).toInt()
        preferences.edit()
            .putInt(KEY_LAST_STEP_TOTAL, safeTotal)
            .putInt(KEY_PENDING_STEP_REMAINDER, pendingSteps)
            .apply()
        energyValue = (energyValue - energyToRemove).coerceAtLeast(0.0)
        publishAndSave()
    }

    /** Grants the fixed reward for completing a quest. */
    fun rewardQuestCompletion(): Int = addEnergy(QUEST_COMPLETION_REWARD)

    private fun refreshEnergy(now: Long): Int = synchronized(lock) {
        refreshEnergyLocked(now)
        publishAndSave()
    }

    private fun refreshEnergyLocked(now: Long) {
        val elapsedMillis = (now - lastUpdatedAt).coerceAtLeast(0L)
        energyValue = (
            energyValue + elapsedMillis * ENERGY_GAIN_PER_MILLISECOND
        ).coerceAtMost(MAX_ENERGY.toDouble())
        lastUpdatedAt = maxOf(lastUpdatedAt, now)
    }

    private fun publishAndSave(): Int {
        val wholeEnergy = floor(energyValue).toInt().coerceIn(0, MAX_ENERGY)
        mutableEnergy.value = wholeEnergy
        preferences.edit()
            .putString(KEY_ENERGY, energyValue.coerceIn(0.0, MAX_ENERGY.toDouble()).toString())
            .putLong(KEY_LAST_UPDATED_AT, lastUpdatedAt)
            .apply()
        return wholeEnergy
    }

    private fun initializeStepCheckpoint(context: Context) {
        if (!preferences.contains(KEY_LAST_STEP_TOTAL)) {
            val existingSteps = StepStorage(context).loadSteps().totalSteps
            preferences.edit().putInt(KEY_LAST_STEP_TOTAL, existingSteps).apply()
        }
    }

    private fun resetStepRemainderForCurrentRule() {
        if (preferences.getInt(KEY_STEP_ENERGY_RULE_VERSION, 0) < STEP_ENERGY_RULE_VERSION) {
            preferences.edit()
                .putInt(KEY_STEP_ENERGY_RULE_VERSION, STEP_ENERGY_RULE_VERSION)
                .putInt(KEY_PENDING_STEP_REMAINDER, 0)
                .apply()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "grassling_energy"
        const val KEY_ENERGY_RULE_VERSION = "energy_rule_version"
        const val KEY_ENERGY = "energy"
        const val KEY_LAST_UPDATED_AT = "last_updated_at"
        const val KEY_LAST_STEP_TOTAL = "last_step_total"
        const val KEY_PENDING_STEP_REMAINDER = "pending_step_remainder"
        const val KEY_STEP_ENERGY_RULE_VERSION = "step_energy_rule_version"
        const val MAX_ENERGY = 100
        const val ENERGY_RULE_VERSION = 3
        const val STEP_ENERGY_RULE_VERSION = 2
        const val STEPS_PER_ENERGY_POINT = 11
        const val QUEST_COMPLETION_REWARD = 10
        const val ENERGY_DURATION_MILLIS = 24 * 60 * 60 * 1_000L
        val ENERGY_GAIN_PER_MILLISECOND = MAX_ENERGY.toDouble() / ENERGY_DURATION_MILLIS
    }
}
