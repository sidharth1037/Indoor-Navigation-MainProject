package `in`.project.enroute.feature.navigation.guidance

import kotlin.math.roundToInt

/**
 * ETA estimation utilities for room panel metadata.
 *
 * Distances are in meters and speed is in m/s.
 */
object TripEtaEstimator {
    const val DEFAULT_WALK_SPEED_MPS: Float = 1.35f
    const val FEW_SECONDS_THRESHOLD_SEC: Int = 12
    private const val DEFAULT_STAIR_SPEED_RATIO: Float = 0.58f
    private const val STAIR_TRANSITION_EQUIVALENT_METERS: Float = 9f

    fun estimateEtaSeconds(distanceMeters: Int?, speedMps: Float): Int? {
        val distance = distanceMeters ?: return null
        if (distance <= 0) return 0
        val safeSpeed = speedMps.coerceAtLeast(0.3f)
        return (distance / safeSpeed).roundToInt().coerceAtLeast(1)
    }

    fun estimateEtaSecondsWithStairs(
        distanceMeters: Int?,
        speedMps: Float,
        stairTransitions: Int
    ): Int? {
        val distance = distanceMeters ?: return null
        if (distance <= 0 && stairTransitions <= 0) return 0

        val safeFlatSpeed = speedMps.coerceAtLeast(0.3f)
        val stairSpeed = (safeFlatSpeed * DEFAULT_STAIR_SPEED_RATIO).coerceAtLeast(0.2f)

        val flatSeconds = (distance / safeFlatSpeed)
        val stairSeconds = stairTransitions.coerceAtLeast(0) *
            (STAIR_TRANSITION_EQUIVALENT_METERS / stairSpeed)

        return (flatSeconds + stairSeconds).roundToInt().coerceAtLeast(1)
    }

    fun formatEta(seconds: Int?): String? {
        val s = seconds ?: return null
        if (s < FEW_SECONDS_THRESHOLD_SEC) return "Few seconds"

        val minutes = s / 60
        val remainSec = s % 60
        return when {
            minutes <= 0 -> "${remainSec} sec"
            remainSec == 0 -> "${minutes} min"
            else -> "${minutes} min ${remainSec} sec"
        }
    }
}
