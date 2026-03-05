package `in`.project.enroute.feature.pdr.correction

import androidx.compose.ui.geometry.Offset

// ── Stairwell zone (boundary-based, pre-computed) ───────────────────────────

/**
 * A stairwell polygon transformed to campus-wide coordinates, with its
 * "top" and "bottom" edges identified.
 *
 * Built from Stairwell geometry (which in turn comes from grouped StairLines).
 * The detector uses polygon containment and boundary-crossing instead of
 * entrance proximity, giving much better position accuracy for wide stairwells.
 *
 * @param polygonId     Matches the original Stairwell.polygonId.
 * @param boundary      Ordered polygon vertices in campus coords.
 * @param topEdge       The edge marked "top" (line segment, campus coords).
 *                      Pair.first / Pair.second are the two endpoints.
 * @param bottomEdge    The edge marked "bottom" (campus coords).
 * @param floorsConnected Numeric floors this stairwell links (e.g. [1.0, 1.5]).
 * @param floorId       Floor ID where this geometry was loaded from.
 * @param isSameFloor   True when the stairwell doesn't cross major floors
 *                      (e.g. 0.3→0.6 within the same floor plan). In this
 *                      case the PDR should NOT switch floors/constraints.
 * @param bottomFloorId Resolved floor ID for the lower floor (e.g. "floor_1").
 * @param topFloorId    Resolved floor ID for the upper floor (e.g. "floor_1.5").
 * @param bottomFloorNumber Numeric floor value for the lower end.
 * @param topFloorNumber    Numeric floor value for the upper end.
 */
data class StairwellZone(
    val polygonId: Int,
    val boundary: List<Offset>,
    val topEdge: Pair<Offset, Offset>,
    val bottomEdge: Pair<Offset, Offset>,
    val floorsConnected: List<Float>,
    val floorId: String,
    val isSameFloor: Boolean,
    val bottomFloorId: String,
    val topFloorId: String,
    val bottomFloorNumber: Float,
    val topFloorNumber: Float
) {
    // Pre-computed AABB for fast rejection (computed once at construction)
    val boundsMinX: Float = boundary.minOfOrNull { it.x } ?: 0f
    val boundsMinY: Float = boundary.minOfOrNull { it.y } ?: 0f
    val boundsMaxX: Float = boundary.maxOfOrNull { it.x } ?: 0f
    val boundsMaxY: Float = boundary.maxOfOrNull { it.y } ?: 0f
}

// ── Transition event (fired by the detector) ────────────────────────────────

enum class StairDirection { UP, DOWN }

/**
 * Emitted by [StairwellTransitionDetector] when the user is confirmed to be
 * entering a stairwell.
 */
data class StairTransitionEvent(
    val zone: StairwellZone,
    val direction: StairDirection,
    /** Campus-wide position where climbing starts (projected onto entry edge). */
    val startPosition: Offset,
    /** Campus-wide position where climbing ends (estimated on exit edge). */
    val endPosition: Offset,
    /** Floor ID the user is leaving. */
    val originFloorId: String,
    /** Floor ID the user is going to. */
    val destinationFloorId: String,
    /**
     * True when this transition stays within the same floor plan
     * (e.g. sub-levels 0.3↔0.6).  No floor-switch or constraint reload needed.
     */
    val isSameFloor: Boolean = false,
    /**
     * Number of steps the user took between entering the stairwell
     * and ML confirmation.  The animation should account for these
     * "already climbed" steps so it doesn't lag behind reality.
     */
    val preClimbedSteps: Int = 0
)

// ── Animator state ──────────────────────────────────────────────────────────

enum class StairClimbingState {
    /** Normal PDR walking. */
    IDLE,
    /** User is actively climbing/descending stairs. */
    CLIMBING,
    /** User turned around — dot is animating back to the origin floor. */
    RETURNING,
    /** Climbing finished — waiting for PDR to resume on the new floor. */
    ARRIVED,
    /** Return animation finished — waiting to restore origin floor. */
    CANCELLED
}

/** How the stairwell arrival was triggered — determines whether
 *  compensation steps should be replayed on the new floor. */
enum class ArrivalReason {
    /** Not arrived yet. */
    NONE,
    /** Arrived because ML said "walking" — user already walked past top. */
    WALKING,
    /** Arrived because a sharp heading turn was detected at the landing. */
    TURN
}
