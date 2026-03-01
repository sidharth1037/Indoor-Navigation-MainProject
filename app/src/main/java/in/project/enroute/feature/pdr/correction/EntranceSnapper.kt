package `in`.project.enroute.feature.pdr.correction

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

/**
 * When a turn is detected, snaps the turn-point to the nearest
 * plausible entrance and derives a stride calibration factor from the
 * correction distance.
 */
class EntranceSnapper(private val config: CorrectionConfig = CorrectionConfig()) {

    /**
     * Attempts to snap [turnPosition] to a nearby entrance.
     *
     * @param turnPosition       Approximate position where the turn was detected.
     * @param preHeading         Heading **before** the turn (used to filter
     *                           entrances by directional plausibility).
     * @param strideLengthUnits  Average stride in coordinate units (for
     *                           calibration factor derivation).
     * @param constraintProvider Spatial data source.
     * @return A [SnapResult] — check [SnapResult.wasSnapped].
     */
    fun snap(
        turnPosition: Offset,
        preHeading: Float,
        strideLengthUnits: Float,
        constraintProvider: FloorConstraintProvider
    ): SnapResult {

        val candidates = constraintProvider.getEntrancesNear(
            position = turnPosition,
            radius = config.entranceSnapRadius,
            heading = preHeading,
            headingTolerance = config.entranceDirectionTolerance
        )

        if (candidates.isEmpty()) {
            return SnapResult(snappedPosition = turnPosition, wasSnapped = false)
        }

        // Pick the closest entrance
        val closest = candidates.minByOrNull { entrance ->
            GeometryUtils.distance(turnPosition, entrance.position)
        } ?: return SnapResult(snappedPosition = turnPosition, wasSnapped = false)

        val snapDistance = GeometryUtils.distance(turnPosition, closest.position)

        // Reject snaps that are too far — likely a wrong entrance match.
        // Cap at half the snap radius to avoid jumping the user forward.
        if (snapDistance > config.entranceSnapRadius * 0.5f) {
            return SnapResult(snappedPosition = turnPosition, wasSnapped = false)
        }

        val correctionDelta = Offset(
            closest.position.x - turnPosition.x,
            closest.position.y - turnPosition.y
        )

        // ── Stride calibration ──────────────────────────────────────────────
        // Positive dot-product with the walking direction means the entrance
        // is farther ahead → stride was too short → factor > 1.
        val strideCalibrationFactor = if (strideLengthUnits > 0f) {
            val dotProduct = correctionDelta.x * sin(preHeading) +
                    correctionDelta.y * (-cos(preHeading))
            val adjustment = (dotProduct / strideLengthUnits)
                .coerceIn(-config.maxStrideAdjustment, config.maxStrideAdjustment)
            1f + adjustment
        } else {
            1f
        }

        return SnapResult(
            snappedPosition = closest.position,
            wasSnapped = true,
            correctionDelta = correctionDelta,
            strideCalibrationFactor = strideCalibrationFactor
        )
    }
}
