package `in`.project.enroute.feature.pdr.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Detects the device's heading (compass direction) using the rotation vector sensor.
 * Reports heading via callback. Pure sensor class with no UI dependencies.
 */
class HeadingDetector(private val sensorManager: SensorManager) : SensorEventListener {

    /**
     * Callback for heading updates (in radians, azimuth from -π to π).
     */
    var onHeadingChanged: ((heading: Float) -> Unit)? = null

    private var isRunning = false

    /**
     * Starts listening for rotation vector sensor events.
     */
    fun start() {
        if (isRunning) return
        
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
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
     * Calculates and reports the heading (azimuth).
     */
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        val orientationAngles = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // Report heading (azimuth in radians) via callback
        onHeadingChanged?.invoke(orientationAngles[0])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }
}
