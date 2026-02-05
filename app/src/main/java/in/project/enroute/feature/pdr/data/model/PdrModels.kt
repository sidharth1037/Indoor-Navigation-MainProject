package `in`.project.enroute.feature.pdr.data.model

import androidx.compose.ui.geometry.Offset

/**
 * Represents a single step event in PDR (Pedestrian Dead Reckoning).
 * Emitted when a step is detected and processed.
 *
 * @param strideLengthCm The calculated stride length in centimeters
 * @param cadence Steps per second
 * @param position The new coordinate after this step
 * @param heading The heading direction in radians
 * @param timestamp When this step occurred
 */
data class StepEvent(
    val strideLengthCm: Float,
    val cadence: Float,
    val position: Offset,
    val heading: Float,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Current state of cadence and stride calculations.
 * Used by UI to display real-time statistics.
 *
 * @param averageCadence The rolling average of cadence over recent steps
 * @param lastStrideLengthCm The most recent stride calculation
 * @param stepCount Total number of steps since origin was set
 */
data class CadenceState(
    val averageCadence: Float = 0f,
    val lastStrideLengthCm: Float = 0f,
    val stepCount: Int = 0
)

/**
 * Configuration parameters for step detection.
 *
 * @param threshold Acceleration threshold for step detection
 * @param debounceMs Minimum time between steps in milliseconds
 * @param windowSize Window size for peak detection
 */
data class StepDetectionConfig(
    val threshold: Float = 12f,
    val debounceMs: Long = 300L,
    val windowSize: Int = 6
)

/**
 * Parameters for stride length calculation.
 *
 * @param heightCm User's height in centimeters
 * @param kValue Stride calculation constant K
 * @param cValue Stride calculation constant C
 * @param cadenceAverageSize Number of recent cadences to average
 */
data class StrideConfig(
    val heightCm: Float = 170f,
    val kValue: Float = 0.37f,
    val cValue: Float = 0.15f,
    val cadenceAverageSize: Int = 5
)

/**
 * Represents a single point in the PDR path with its heading.
 * Each step has the heading direction at the time it was taken.
 *
 * @param position The coordinate of this step
 * @param heading The heading direction in radians at this step
 */
data class PathPoint(
    val position: Offset,
    val heading: Float
)

/**
 * The overall PDR tracking state.
 *
 * @param isTracking Whether PDR tracking is currently active
 * @param origin The starting point for the current tracking session
 * @param currentPosition The current calculated position
 * @param heading Current heading in radians
 * @param path List of all path points since origin was set (with heading at each step)
 */
data class PdrState(
    val isTracking: Boolean = false,
    val origin: Offset? = null,
    val currentPosition: Offset? = null,
    val heading: Float = 0f,
    val path: List<PathPoint> = emptyList(),
    val cadenceState: CadenceState = CadenceState()
)
