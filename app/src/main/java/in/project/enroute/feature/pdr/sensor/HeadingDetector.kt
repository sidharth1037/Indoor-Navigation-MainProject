package `in`.project.enroute.feature.pdr.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Detects the device's heading (compass direction) using the rotation vector sensor.
 * Emits heading via [heading] StateFlow. Supports two modes:
 * - **Compass mode** (default): slower sensor rate + larger dead-band for idle display
 * - **Tracking mode**: faster sensor rate + smaller dead-band for smooth following rotation
 */
class HeadingDetector(private val sensorManager: SensorManager) : SensorEventListener {

    /**
     * Current heading in radians (azimuth, -π to π).
     * Only emits when the heading changes by more than the active dead-band.
     */
    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading.asStateFlow()

    private var isRunning = false
    private var isTrackingMode = false

    // Reusable arrays to avoid allocation in onSensorChanged
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    /**
     * Starts listening in compass mode (low frequency, suitable for idle compass display).
     */
    fun start() {
        startInternal(trackingMode = false)
    }

    /**
     * Switches to tracking mode with higher sensor rate and tighter dead-band
     * for smooth canvas rotation during following mode.
     */
    fun setTrackingMode(enabled: Boolean) {
        if (isTrackingMode == enabled) return
        if (!isRunning) {
            // Just record the desired mode; will be applied on next start()
            isTrackingMode = enabled
            return
        }
        // Re-register with new rate
        sensorManager.unregisterListener(this)
        isRunning = false
        startInternal(trackingMode = enabled)
    }

    private fun startInternal(trackingMode: Boolean) {
        if (isRunning) return
        isTrackingMode = trackingMode

        val rate = if (trackingMode) SensorManager.SENSOR_DELAY_GAME
                   else SensorManager.SENSOR_DELAY_UI

        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        rotationSensor?.let {
            sensorManager.registerListener(this, it, rate)
            isRunning = true
        }
    }

    /**
     * Stops listening for rotation vector sensor events.
     */
    fun stop() {
        if (!isRunning) return
        
        sensorManager.unregisterListener(this)
        isRunning = false
    }

    /**
     * Called when rotation vector data is available.
     * Applies dead-band filtering whose threshold depends on the active mode.
     */
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val newHeading = orientationAngles[0]
        val deadBand = if (isTrackingMode) DEAD_BAND_TRACKING else DEAD_BAND_COMPASS

        if (abs(newHeading - _heading.value) >= deadBand) {
            _heading.value = newHeading
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    companion object {
        /** ~1° — sufficient for compass icon rotation */
        private const val DEAD_BAND_COMPASS = 0.0175f
        /** ~0.25° — smooth enough for canvas rotation in following mode */
        private const val DEAD_BAND_TRACKING = 0.0044f
    }
}
