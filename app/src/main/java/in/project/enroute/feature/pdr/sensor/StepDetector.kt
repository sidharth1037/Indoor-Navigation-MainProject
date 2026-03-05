package `in`.project.enroute.feature.pdr.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import `in`.project.enroute.feature.pdr.data.model.StepDetectionConfig
import kotlin.math.abs

/**
 * Detects accelerometer peaks that correspond to footsteps.
 *
 * A) **High-pass filter** — removes gravity (DC offset) so the signal
 *    oscillates around zero; only dynamic acceleration remains.
 *    Formula: filtered = α · (filtered_prev + z − z_prev)
 *
 * B) **Peak detection** — a peak is registered when |filteredZ| crosses
 *    the threshold on a rising→falling transition, subject to a minimum
 *    debounce interval.
 *
 * False-positive filtering is handled externally by the ML motion model
 * in PdrViewModel — this detector simply emits every valid peak.
 */
class StepDetector(private val sensorManager: SensorManager) : SensorEventListener {

    var onStepDetected: ((stepIntervalMs: Long) -> Unit)? = null
    var onAccelerometerData: ((x: Float, y: Float, z: Float) -> Unit)? = null

    private var config = StepDetectionConfig()
    private var isRunning = false

    // ── High-pass filter state ──────────────────────────────────────────────
    private var prevRawZ = 0f
    private var filteredZ = 0f
    private var filterWarmup = 0

    // ── Peak detection state ────────────────────────────────────────────────
    private var lastMagnitude = 0f
    private var isRising = true
    private var lastStepTime = 0L

    fun updateConfig(newConfig: StepDetectionConfig) {
        config = newConfig
    }

    fun start() {
        if (isRunning) return
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            isRunning = true
            resetState()
        }
    }

    fun stop() {
        if (!isRunning) return
        sensorManager.unregisterListener(this)
        isRunning = false
        resetState()
    }

    private fun resetState() {
        lastMagnitude = 0f
        isRising = true
        prevRawZ = 0f
        filteredZ = 0f
        filterWarmup = 0
        lastStepTime = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        onAccelerometerData?.invoke(x, y, z)

        // ── High-pass filter ─────────────────────────────────────────────────
        filteredZ = config.highPassAlpha * (filteredZ + z - prevRawZ)
        prevRawZ = z
        filterWarmup = (filterWarmup + 1).coerceAtMost(WARMUP_SAMPLES)

        if (filterWarmup < WARMUP_SAMPLES) {
            lastMagnitude = abs(filteredZ)
            return
        }

        val magnitude = abs(filteredZ)
        val currentlyRising = magnitude > lastMagnitude

        // ── Peak detection (rising → falling above threshold + debounce) ─────
        if (isRising && !currentlyRising && magnitude > config.threshold) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastStepTime

            if (lastStepTime == 0L || elapsed >= config.debounceMs) {
                val interval = if (lastStepTime == 0L) DEFAULT_FIRST_INTERVAL else elapsed
                lastStepTime = now
                onStepDetected?.invoke(interval)
            }
        }

        isRising = currentlyRising
        lastMagnitude = magnitude
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val WARMUP_SAMPLES = 20
        private const val DEFAULT_FIRST_INTERVAL = 500L
    }
}
