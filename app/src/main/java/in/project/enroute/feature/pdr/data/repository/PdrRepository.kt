package `in`.project.enroute.feature.pdr.data.repository

import androidx.compose.ui.geometry.Offset
import `in`.project.enroute.feature.pdr.data.model.CadenceState
import `in`.project.enroute.feature.pdr.data.model.PathPoint
import `in`.project.enroute.feature.pdr.data.model.PdrState
import `in`.project.enroute.feature.pdr.data.model.StepEvent
import `in`.project.enroute.feature.pdr.data.model.StrideConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.sin

/**
 * Repository for PDR (Pedestrian Dead Reckoning) path calculation logic.
 * Handles stride calculation, path tracking, and state management.
 * Uses StateFlow for reactive data emission.
 */
class PdrRepository {

    private var strideConfig = StrideConfig()
    private val recentCadences = mutableListOf<Float>()

    // Conversion factor: pixels per centimeter (can be calibrated)
    private val pixelsPerCm = 0.5f

    // Current position tracking
    private var currentX = 0f
    private var currentY = 0f
    private var stepCount = 0

    /**
     * The overall PDR tracking state.
     */
    private val _pdrState = MutableStateFlow(PdrState())
    val pdrState: StateFlow<PdrState> = _pdrState.asStateFlow()

    /**
     * Emits individual step events for observers that need per-step updates.
     */
    private val _stepEvents = MutableStateFlow<StepEvent?>(null)
    val stepEvents: StateFlow<StepEvent?> = _stepEvents.asStateFlow()

    /**
     * Updates the stride calculation configuration.
     */
    fun updateStrideConfig(config: StrideConfig) {
        strideConfig = config
    }

    /**
     * Sets the origin point and starts tracking.
     * This is the starting point for PDR path calculation.
     *
     * @param origin The starting coordinate in canvas/world space
     */
    fun setOrigin(origin: Offset) {
        currentX = origin.x
        currentY = origin.y
        stepCount = 0
        recentCadences.clear()

        // Origin point with initial heading
        val originPoint = PathPoint(position = origin, heading = _pdrState.value.heading)
        val path = listOf(originPoint)

        _pdrState.value = PdrState(
            isTracking = true,
            origin = origin,
            currentPosition = origin,
            heading = _pdrState.value.heading,
            path = path,
            cadenceState = CadenceState()
        )
    }

    /**
     * Processes a detected step and calculates the new position.
     * Only works if tracking is active (origin has been set).
     *
     * @param stepIntervalMs Time since last step in milliseconds
     * @param heading Current heading in radians
     * @return The new position, or null if not tracking
     */
    fun processStep(stepIntervalMs: Long, heading: Float): Offset? {
        val currentState = _pdrState.value
        if (!currentState.isTracking || currentState.origin == null) {
            return null
        }

        // Calculate cadence (steps per second)
        val cadence = if (stepIntervalMs > 0) 1000f / stepIntervalMs else 0f

        // Update cadence average
        recentCadences.add(cadence)
        while (recentCadences.size > strideConfig.cadenceAverageSize) {
            recentCadences.removeAt(0)
        }
        val averageCadence = if (recentCadences.isNotEmpty()) {
            recentCadences.average().toFloat()
        } else {
            0f
        }

        // Calculate dynamic stride length based on cadence and height
        val strideLengthCm = calculateStrideLength(cadence,averageCadence)
        val strideInPixels = strideLengthCm * pixelsPerCm

        // Calculate new position using heading
        // heading is in radians: 0 = North, positive = clockwise
        stepCount++

        // First step stays at origin
        val newPosition = if (stepCount == 1) {
            Offset(currentX, currentY)
        } else {
            val newX = currentX + strideInPixels * sin(heading)
            val newY = currentY - strideInPixels * cos(heading)
            currentX = newX
            currentY = newY
            Offset(newX, newY)
        }

        // Update path with PathPoint including heading at this step
        val newPathPoint = PathPoint(position = newPosition, heading = heading)
        val updatedPath = currentState.path + newPathPoint

        // Update cadence state
        val cadenceState = CadenceState(
            averageCadence = averageCadence,
            lastStrideLengthCm = strideLengthCm,
            stepCount = stepCount
        )

        // Emit step event
        _stepEvents.value = StepEvent(
            strideLengthCm = strideLengthCm,
            cadence = cadence,
            position = newPosition,
            heading = heading
        )

        // Update PDR state
        _pdrState.value = currentState.copy(
            currentPosition = newPosition,
            heading = heading,
            path = updatedPath,
            cadenceState = cadenceState
        )

        return newPosition
    }

    /**
     * Updates the current heading without processing a step.
     * Used for continuous heading updates from the sensor.
     */
    fun updateHeading(heading: Float) {
        _pdrState.value = _pdrState.value.copy(heading = heading)
    }

    /**
     * Clears the path and stops tracking.
     * Resets all internal state.
     */
    fun clearAndStopTracking() {
        currentX = 0f
        currentY = 0f
        stepCount = 0
        recentCadences.clear()

        _pdrState.value = PdrState(
            isTracking = false,
            origin = null,
            currentPosition = null,
            heading = 0f,
            path = emptyList(),
            cadenceState = CadenceState()
        )

        _stepEvents.value = null
    }

    /**
     * Calculates stride length dynamically based on cadence and user height.
     * Uses the formula: stride = height * (k * cadence + c)
     *
     * @param cadence Steps per second
     * @return Stride length in centimeters
     */
    private fun calculateStrideLength(instantCadence: Float, averageCadence: Float): Float {
        val heightInMeters = strideConfig.heightCm / 100f

        // SMOOTHING: Blending instant cadence (30%) with history (70%)
        // to prevent jittery movement on the map.
        val smoothedCadence = (instantCadence * 0.3f) + (averageCadence * 0.7f)

        // DYNAMIC K: Standard gait research suggests k is ~0.157 for walking.
        // If the user is moving fast (cadence > 2.0), we slightly increase K.
        val adjustedK = if (smoothedCadence > 2.0f) strideConfig.kValue * 1.15f else strideConfig.kValue

        val stride = heightInMeters * (adjustedK * smoothedCadence + strideConfig.cValue)

        // Convert to cm and clamp to human limits (30cm - 120cm)
        return (stride * 100f).coerceIn(30f, 120f)
    }
}

//first calculation with fixed k
//private fun calculateStrideLength(cadence: Float): Float {
//    // Formula: stride = height * (k * cadence + c)
//    // This gives reasonable stride lengths based on walking speed
//    val heightInMeters = strideConfig.heightCm / 100f
//    val stride = heightInMeters * (strideConfig.kValue * cadence + strideConfig.cValue)
//
//    // Convert to cm and clamp to reasonable range (30-120 cm)
//    return (stride * 100f).coerceIn(30f, 120f)
//}
