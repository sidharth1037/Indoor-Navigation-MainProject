package `in`.project.enroute.feature.pdr.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import `in`.project.enroute.feature.pdr.data.model.StepDetectionConfig
import kotlin.math.sqrt

/**
 * Detects steps using the accelerometer sensor.
 * Uses peak detection algorithm with debouncing.
 * 
 * Step detection algorithm:
 * 1. Calculate acceleration magnitude from x, y, z components
 * 2. Maintain a sliding window of recent magnitudes
 * 3. Detect peaks that exceed the threshold
 * 4. Apply debouncing to prevent double-counting
 */
class StepDetector(private val sensorManager: SensorManager) : SensorEventListener {

    /**
     * Callback when a step is detected.
     * Returns the time interval since last step (for cadence calculation).
     */
    var onStepDetected: ((stepIntervalMs: Long) -> Unit)? = null

    private var config = StepDetectionConfig()
    private var isRunning = false

    // Peak detection state
    private val magnitudeWindow = mutableListOf<Float>()
    private var lastStepTime = 0L
    private var lastMagnitude = 0f
    private var isRising = true

    /**
     * Updates the step detection configuration.
     */
    fun updateConfig(newConfig: StepDetectionConfig) {
        config = newConfig
    }

    /**
     * Starts listening for accelerometer events.
     */
    fun start() {
        if (isRunning) return
        
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            isRunning = true
            resetState()
        }
    }

    /**
     * Stops listening for accelerometer events.
     */
    fun stop() {
        if (!isRunning) return
        
        sensorManager.unregisterListener(this)
        isRunning = false
        resetState()
    }

    /**
     * Resets the internal detection state.
     */
    private fun resetState() {
        magnitudeWindow.clear()
        lastStepTime = 0L
        lastMagnitude = 0f
        isRising = true
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        // Calculate acceleration magnitude (without gravity would require high-pass filter)
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)

        // Add to sliding window
        magnitudeWindow.add(magnitude)
        while (magnitudeWindow.size > config.windowSize) {
            magnitudeWindow.removeAt(0)
        }

        // Need enough samples for peak detection
        if (magnitudeWindow.size < config.windowSize) {
            lastMagnitude = magnitude
            return
        }

        // Detect peak: was rising, now falling, and above threshold
        val currentlyRising = magnitude > lastMagnitude
        
        if (isRising && !currentlyRising && magnitude > config.threshold) {
            // Found a peak - check debounce
            val currentTime = System.currentTimeMillis()
            val timeSinceLastStep = currentTime - lastStepTime
            
            if (lastStepTime == 0L || timeSinceLastStep >= config.debounceMs) {
                // Valid step detected
                onStepDetected?.invoke(timeSinceLastStep)
                lastStepTime = currentTime
            }
        }

        isRising = currentlyRising
        lastMagnitude = magnitude
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }
}
