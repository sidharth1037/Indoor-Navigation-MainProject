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
    val heightCm: Float? = null, // Default to average height
    // kValue: Sensitivity to speed changes.
    // Research (e.g., Bylemans et al.) suggests ~0.15 - 0.17 for height-normalized gait.
    val kValue: Float = 0.16f,
    // cValue: The base stride constant.
    // Increasing this ensures the 'minimum' step reaches the destination.
    val cValue: Float = 0.25f,
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
 * Heading is stored separately in PdrUiState to avoid copying the path
 * list on every compass tick.
 *
 * All positions (origin, currentPosition, path) are in **campus-wide** coordinates
 * (metadata-transformed + building relativePosition offset), matching the canvas
 * drawing space. This eliminates per-building offset juggling in overlays and
 * camera-centering code.
 *
 * @param isTracking Whether PDR tracking is currently active
 * @param origin The starting point in campus-wide coordinates
 * @param currentPosition The current calculated position in campus-wide coordinates
 * @param path List of all path points since origin was set (with heading at each step)
 */
data class PdrState(
    val isTracking: Boolean = false,
    val origin: Offset? = null,
    val currentPosition: Offset? = null,
    val path: List<PathPoint> = emptyList(),
    val cadenceState: CadenceState = CadenceState(),
    /** The floor the user is currently on (e.g. "floor_1"), or null if outdoors. */
    val currentFloor: String? = null,
    /** The building the user is currently inside (e.g. "main_block"), or null if outdoors. */
    val currentBuilding: String? = null,
    // ── Stairwell transition state ──────────────────────────────────────
    /** True while the user is climbing/descending stairs. */
    val isOnStairs: Boolean = false,
    /** 0..1 progress through the stairwell animation. */
    val stairTransitionProgress: Float = 0f,
    /** "floor_1" → "floor_2" — the floor the user is heading toward (null when not on stairs). */
    val stairDestinationFloor: String? = null,
    /** Campus-wide position of the bottom entrance (for rendering). */
    val stairBottomPosition: Offset? = null,
    /** Campus-wide position of the top entrance (for rendering). */
    val stairTopPosition: Offset? = null
)
