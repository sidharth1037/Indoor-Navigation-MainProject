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
 *
 * Heading is stored in a **separate** StateFlow so that high-frequency
 * compass updates don't trigger copies of the path list inside [PdrState].
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
     * The overall PDR tracking state (path, origin, cadence, etc.).
     * Does NOT include heading — see [heading] for that.
     */
    private val _pdrState = MutableStateFlow(PdrState())
    val pdrState: StateFlow<PdrState> = _pdrState.asStateFlow()

    /**
     * Current heading in radians, updated independently from [pdrState]
     * so compass-only changes don't trigger path-related recompositions.
     */
    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading.asStateFlow()

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
     * All positions are stored in campus-wide coordinates.
     *
     * @param origin The starting coordinate in campus-wide space
     * @param currentFloor The floor the user is on (e.g. "floor_1")
     */
    fun setOrigin(origin: Offset, currentFloor: String? = null) {
        currentX = origin.x
        currentY = origin.y
        stepCount = 0
        recentCadences.clear()

        // Origin point with initial heading
        val originPoint = PathPoint(position = origin, heading = _heading.value)
        val path = listOf(originPoint)

        _pdrState.value = PdrState(
            isTracking = true,
            origin = origin,
            currentPosition = origin,
            path = path,
            cadenceState = CadenceState(),
            currentFloor = currentFloor
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

        val newX = currentX + strideInPixels * sin(heading)
        val newY = currentY - strideInPixels * cos(heading)
        currentX = newX
        currentY = newY
        val newPosition = Offset(newX, newY)

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

        // Update PDR state (heading is stored separately)
        _pdrState.value = currentState.copy(
            currentPosition = newPosition,
            path = updatedPath,
            cadenceState = cadenceState
        )

        return newPosition
    }

    /**
     * Updates the current heading without touching PdrState.
     * This avoids copying the entire path list on every compass tick.
     */
    fun updateHeading(heading: Float) {
        _heading.value = heading
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
            path = emptyList(),
            cadenceState = CadenceState(),
            currentFloor = null
        )
        _heading.value = 0f

        _stepEvents.value = null
    }

    /**
     * Calculates stride length using a normalized linear gait model.
     * Formula: Stride = Height * (k * Cadence + c)
     * * This version is tuned to prevent "short-stepping" on the map.
     */
    private fun calculateStrideLength(instantCadence: Float, averageCadence: Float): Float {
        val heightCm = strideConfig.heightCm ?: return 0f
        val heightInMeters = heightCm / 100f

        // 1. SMOOTHING: High weight on average cadence to prevent erratic jumps,
        // but enough instant cadence to feel responsive.
        val smoothedCadence = (instantCadence * 0.35f) + (averageCadence * 0.65f)

        // 2. DYNAMIC GAIT SCALING:
        // If cadence is very low (< 1 step/sec), the user is likely hesitant.
        // If cadence is high (> 2.2 steps/sec), they are running.
        val k = strideConfig.kValue
        val c = strideConfig.cValue

        // General Equation: Stride = H * (k * f + c) where f is frequency (cadence)
        var stride = heightInMeters * (k * smoothedCadence + c)

        // 3. CALIBRATION OFFSET:
        // If the user is shorter (like 165cm), the proportional stride often
        // underestimates real-world movement due to hip flexibility.
        if (heightCm < 170f) {
            stride *= 1.05f // 5% boost for shorter users to compensate for sensor lag
        }

        // 4. BIOLOGICAL CLAMPING:
        // Minimum: 0.4m (40cm) - Anything less is a shuffle, not a step.
        // Maximum: 0.85 * Height - Standard limit for human leg extension.
        val minStride = 40f
        val maxStride = heightCm * 0.85f

        return (stride * 100f).coerceIn(minStride, maxStride)
    }

    /* // first calculation with fixed k (kept as requested)
    private fun calculateStrideLength(cadence: Float): Float {
        val heightInMeters = strideConfig.heightCm / 100f
        val stride = heightInMeters * (strideConfig.kValue * cadence + strideConfig.cValue)
        return (stride * 100f).coerceIn(30f, 120f)
    }
    */
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
