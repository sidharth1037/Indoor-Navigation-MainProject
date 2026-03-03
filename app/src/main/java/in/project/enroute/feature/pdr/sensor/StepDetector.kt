package `in`.project.enroute.feature.pdr.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import `in`.project.enroute.feature.pdr.data.model.StepDetectionConfig
import kotlin.math.abs

/**
 * Detects steps from the z-axis accelerometer using four layered techniques
 * to minimise false positives from random shakes and bumps:
 *
 * A) **High-pass filter** — removes gravity (DC offset) so the signal
 *    oscillates around zero; only dynamic acceleration remains.
 *    Formula: filtered = α · (filtered_prev + z − z_prev)
 *
 * B) **Peak prominence** — the peak must rise at least [StepDetectionConfig.minProminence]
 *    above the preceding valley. A sudden single spike with no real trough
 *    before it is rejected.
 *
 * C) **Cadence rhythm gate** — after [RHYTHM_SEED_STEPS] steps are collected,
 *    each new candidate is rejected if it falls outside
 *    [avgInterval × toleranceLow, avgInterval × toleranceHigh].
 *    A shake that breaks the walking rhythm is discarded.
 *
 * D) **Floor requirement** — between each accepted step the filtered signal
 *    must drop below [StepDetectionConfig.floorThreshold]. This ensures a
 *    complete oscillation cycle before the next step can register.
 */
class StepDetector(private val sensorManager: SensorManager) : SensorEventListener {

    var onStepDetected: ((stepIntervalMs: Long) -> Unit)? = null
    var onAccelerometerData: ((x: Float, y: Float, z: Float) -> Unit)? = null

    private var config = StepDetectionConfig()
    private var isRunning = false

    // ── Plan A: High-pass filter state ──────────────────────────────────────
    private var prevRawZ = 0f
    private var filteredZ = 0f
    private var filterWarmup = 0

    // ── Peak detection state ─────────────────────────────────────────────────
    private val magnitudeWindow = mutableListOf<Float>()
    private var lastMagnitude = 0f
    private var isRising = true

    // ── Plan B: Valley tracking ──────────────────────────────────────────────
    private var lastValley = 0f

    // ── Plan C: Rhythm gate ──────────────────────────────────────────────────
    private val stepIntervalHistory = mutableListOf<Long>()
    private var lastStepTime = 0L

    // ── Plan D: Floor requirement ────────────────────────────────────────────
    private var belowFloorSincePeak = true

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
        magnitudeWindow.clear()
        lastMagnitude = 0f
        isRising = true
        prevRawZ = 0f
        filteredZ = 0f
        filterWarmup = 0
        lastValley = 0f
        stepIntervalHistory.clear()
        lastStepTime = 0L
        belowFloorSincePeak = true
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        onAccelerometerData?.invoke(x, y, z)

        // ── A: High-pass filter ──────────────────────────────────────────────
        // Removes gravity.  filteredZ ≈ 0 at rest; ±2–4 m/s² during a step.
        filteredZ = config.highPassAlpha * (filteredZ + z - prevRawZ)
        prevRawZ = z
        filterWarmup = (filterWarmup + 1).coerceAtMost(WARMUP_SAMPLES)

        val magnitude = abs(filteredZ)

        // ── D: Track floor ───────────────────────────────────────────────────
        if (magnitude < config.floorThreshold) belowFloorSincePeak = true

        // Maintain sliding window
        magnitudeWindow.add(magnitude)
        while (magnitudeWindow.size > config.windowSize) magnitudeWindow.removeAt(0)

        // Wait for window and filter to fill
        if (magnitudeWindow.size < config.windowSize || filterWarmup < WARMUP_SAMPLES) {
            lastMagnitude = magnitude
            return
        }

        val currentlyRising = magnitude > lastMagnitude

        // ── B: Track valley (minimum in the current descending phase) ────────
        if (!isRising && magnitude < lastValley) lastValley = magnitude

        // ── Peak detection ───────────────────────────────────────────────────
        if (isRising && !currentlyRising && magnitude > config.threshold) {
            val peak = lastMagnitude  // sample just before descent = true peak

            // B: Prominence check — peak must rise sufficiently above last valley
            val prominence = peak - lastValley
            val prominenceOk = prominence >= config.minProminence

            // D: Floor check — signal must have returned to rest since last step
            val floorOk = lastStepTime == 0L || belowFloorSincePeak

            if (prominenceOk && floorOk) {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastStep = currentTime - lastStepTime

                if (lastStepTime == 0L || timeSinceLastStep >= config.debounceMs) {
                    // C: Rhythm gate — only active after enough seed steps.
                    // Auto-reset if the user paused (no accepted step for > RHYTHM_TIMEOUT_MS).
                    if (stepIntervalHistory.size >= RHYTHM_SEED_STEPS &&
                        timeSinceLastStep > RHYTHM_TIMEOUT_MS
                    ) {
                        stepIntervalHistory.clear()
                    }

                    val rhythmOk = if (lastStepTime != 0L &&
                        stepIntervalHistory.size >= RHYTHM_SEED_STEPS
                    ) {
                        val avgInterval = stepIntervalHistory
                            .takeLast(RHYTHM_SEED_STEPS).average()
                        val lo = (avgInterval * config.rhythmToleranceLow).toLong()
                        val hi = (avgInterval * config.rhythmToleranceHigh).toLong()
                        val inRhythm = timeSinceLastStep in lo..hi

                        // Even if rejected by rhythm, update history if the interval is a
                        // plausible walking pace — allows the gate to adapt to pace changes.
                        if (!inRhythm && timeSinceLastStep in ABS_STEP_MIN_MS..ABS_STEP_MAX_MS) {
                            stepIntervalHistory.add(timeSinceLastStep)
                            if (stepIntervalHistory.size > INTERVAL_HISTORY_SIZE) stepIntervalHistory.removeAt(0)
                        }
                        inRhythm
                    } else true

                    if (rhythmOk) {
                        val effectiveInterval = if (lastStepTime == 0L) 500L else timeSinceLastStep
                        onStepDetected?.invoke(effectiveInterval)

                        if (lastStepTime != 0L) {
                            stepIntervalHistory.add(timeSinceLastStep)
                            if (stepIntervalHistory.size > INTERVAL_HISTORY_SIZE) {
                                stepIntervalHistory.removeAt(0)
                            }
                        }
                        lastStepTime = currentTime
                        belowFloorSincePeak = false  // D: arm floor requirement
                    }
                }
            }

            // Reset valley tracking for the next trough regardless of acceptance
            lastValley = magnitude
        }

        isRising = currentlyRising
        lastMagnitude = magnitude
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        /** Filter samples to skip while the high-pass filter stabilises. */
        private const val WARMUP_SAMPLES = 20
        /** Minimum accepted steps before rhythm gate becomes active. */
        private const val RHYTHM_SEED_STEPS = 3
        /** Number of recent step intervals kept for rhythm averaging. */
        private const val INTERVAL_HISTORY_SIZE = 8
        /** If no step is accepted for this long, rhythm history is cleared and re-seeded. */
        private const val RHYTHM_TIMEOUT_MS = 2000L
        /** Absolute plausible walking interval range (used for adaptive history update). */
        private const val ABS_STEP_MIN_MS = 300L
        private const val ABS_STEP_MAX_MS = 2000L
    }
}
