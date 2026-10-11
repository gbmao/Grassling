package com.grassling.app.data

import android.content.Context
import org.json.JSONObject
import java.io.File

private const val STEPS_FILE_NAME = "steps.json"

class StepStorage(context: Context) {
    data class StoredSteps(
        val totalSteps: Int = 0,
        val lastSensorSteps: Int? = null,
    )

    private val stepsFile = File(context.applicationContext.filesDir, STEPS_FILE_NAME)

    init {
        ensureFileExists()
    }

    @Synchronized
    fun loadSteps(): StoredSteps {
        ensureFileExists()
        val json = runCatching { JSONObject(stepsFile.readText(Charsets.UTF_8)) }
            .getOrDefault(JSONObject())
        val lastSensorSteps = if (json.isNull("lastSensorSteps")) {
            null
        } else {
            json.optInt("lastSensorSteps", -1).takeIf { it >= 0 }
        }
        return StoredSteps(
            totalSteps = json.optInt("totalSteps", 0).coerceAtLeast(0),
            lastSensorSteps = lastSensorSteps,
        )
    }

    @Synchronized
    fun saveSteps(steps: StoredSteps) {
        ensureFileExists()
        writeSteps(
            steps.copy(
                totalSteps = steps.totalSteps.coerceAtLeast(0),
                lastSensorSteps = steps.lastSensorSteps?.coerceAtLeast(0),
            ),
        )
    }

    @Synchronized
    fun addSteps(steps: Int): Int {
        val current = loadSteps()
        val total = (current.totalSteps.toLong() + steps.coerceAtLeast(0))
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        saveSteps(current.copy(totalSteps = total))
        return total
    }

    /** Adds newly observed sensor steps without double counting across starts or app restarts. */
    @Synchronized
    fun recordSensorSteps(sensorSteps: Int): Int {
        val current = loadSteps()
        val sensorValue = sensorSteps.coerceAtLeast(0)
        val newSteps = current.lastSensorSteps
            ?.takeIf { sensorValue >= it }
            ?.let { sensorValue - it }
            ?: 0
        val total = (current.totalSteps.toLong() + newSteps)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        saveSteps(current.copy(totalSteps = total, lastSensorSteps = sensorValue))
        return total
    }

    private fun writeSteps(steps: StoredSteps) {
        stepsFile.writeText(
            JSONObject()
                .put("totalSteps", steps.totalSteps)
                .put("lastSensorSteps", steps.lastSensorSteps ?: JSONObject.NULL)
                .toString(2),
            Charsets.UTF_8,
        )
    }

    private fun ensureFileExists() {
        stepsFile.parentFile?.mkdirs()
        if (!stepsFile.exists()) {
            writeSteps(StoredSteps())
        }
    }
}
