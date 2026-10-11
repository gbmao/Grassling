package com.grassling.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import com.grassling.app.data.StepStorage
import com.grassling.app.monster.EnergyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class StepCounterManager(context: Context, private val energyManager: EnergyManager) {
    private val appContext = context.applicationContext
    private val stepStorage = StepStorage(appContext)
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val lock = Any()
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sensorReadings = Channel<Int>(Channel.UNLIMITED)

    private var running = false
    private var initialSensorSteps: Int? = null
    private var relativeSteps = 0

    init {
        persistenceScope.launch {
            for (sensorSteps in sensorReadings) {
                val totalSteps = runCatching { stepStorage.recordSensorSteps(sensorSteps) }
                    .getOrNull()
                    ?: continue
                energyManager.recordCumulativeSteps(totalSteps)
            }
        }
    }

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
            val sensorSteps = event.values.firstOrNull()?.toInt()?.coerceAtLeast(0) ?: return

            val shouldPersist = synchronized(lock) {
                if (!running) return@synchronized false
                val initialSteps = initialSensorSteps
                if (initialSteps == null || sensorSteps < initialSteps) {
                    initialSensorSteps = sensorSteps
                    relativeSteps = 0
                } else {
                    relativeSteps = sensorSteps - initialSteps
                }
                true
            }
            if (shouldPersist) sensorReadings.trySend(sensorSteps)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        synchronized(lock) {
            sensorManager.unregisterListener(listener)
            running = false
            initialSensorSteps = null
            relativeSteps = 0

            val sensor = stepCounterSensor ?: return
            if (!hasRequiredPermission()) return

            running = try {
                sensorManager.registerListener(
                    listener,
                    sensor,
                    SensorManager.SENSOR_DELAY_NORMAL,
                )
            } catch (_: SecurityException) {
                false
            }
        }
    }

    fun isRunning(): Boolean = synchronized(lock) { running }

    fun getSteps(): Int = synchronized(lock) { relativeSteps }

    fun getTotalSteps(): Int = stepStorage.loadSteps().totalSteps

    fun stop() {
        synchronized(lock) {
            sensorManager.unregisterListener(listener)
            running = false
        }
    }

    private fun hasRequiredPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            appContext.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
}
