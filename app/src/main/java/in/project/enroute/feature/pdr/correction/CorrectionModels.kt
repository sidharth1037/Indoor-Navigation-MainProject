package `in`.project.enroute.feature.pdr.correction

import androidx.compose.ui.geometry.Offset
import `in`.project.enroute.data.model.FloorPlanData
import `in`.project.enroute.feature.pdr.data.model.PathPoint

// ── Raw pipeline types ──────────────────────────────────────────────────────

/**
 * A single step before it has been committed through the correction pipeline.
 */
data class RawStep(
    val position: Offset,
    val heading: Float,
    val strideLengthUnits: Float,
    val timestamp: Long = System.currentTimeMillis()
)

// ── Wall constraint ─────────────────────────────────────────────────────────

/**
 * A wall segment already transformed to campus-wide coordinates.
 */
data class CampusWall(
    val start: Offset,
    val end: Offset
)

/**
 * Result of constraining a movement vector against walls.
 */
data class WallConstraintResult(
    /** Position after wall clamping + sliding. */
    val constrainedPosition: Offset,
    /** `true` if any wall blocked the original movement. */
    val wasConstrained: Boolean,
    /** Suggested heading correction (rad) from wall sliding, to apply on future steps. */
    val headingCorrection: Float = 0f
)

// ── Entrance snapping ───────────────────────────────────────────────────────

/**
 * An entrance point already transformed to campus-wide coordinates.
 */
data class CampusEntrance(
    val id: Int,
    val position: Offset,
    val name: String? = null
)

/**
 * Result of attempting to snap a position to a nearby entrance.
 */
data class SnapResult(
    val snappedPosition: Offset,
    val wasSnapped: Boolean,
    /** World-space delta applied to reach the entrance. */
    val correctionDelta: Offset = Offset.Zero,
    /** Stride calibration factor derived from the correction
     *  (>1 → stride was too short, <1 → too long). */
    val strideCalibrationFactor: Float = 1f
)

// ── Turn detection ──────────────────────────────────────────────────────────

/**
 * Describes a detected turn within the step buffer.
 */
data class TurnEvent(
    /** Index inside the raw buffer where the turn occurred. */
    val bufferIndex: Int,
    val preHeading: Float,
    val postHeading: Float,
    val headingDelta: Float,
    val approximatePosition: Offset
)

// ── Engine output ───────────────────────────────────────────────────────────

/**
 * Returned by [StepCorrectionEngine.processStep] after running
 * the full correction pipeline for one incoming step.
 */
data class CorrectionResult(
    /** Newly committed path points (empty while the buffer is filling). */
    val newCommittedPoints: List<PathPoint>,
    /** Latest position (may still be in the buffer, not yet committed). */
    val correctedCurrentPosition: Offset,
    /** Accumulated heading correction (rad) that the engine is applying. */
    val headingCorrection: Float,
    /** Current stride calibration factor. */
    val strideCalibrationFactor: Float
)

// ── Floor data bundle ───────────────────────────────────────────────────────

/**
 * Bundles everything needed to initialise the correction engine for a floor.
 * Passed from the UI / FloorPlanViewModel to PdrViewModel.
 */
data class FloorConstraintData(
    val floorPlanData: FloorPlanData,
    val scale: Float,
    val rotationDegrees: Float,
    val offsetX: Float,
    val offsetY: Float
)
