package `in`.project.enroute.feature.pdr.correction

import androidx.compose.ui.geometry.Offset

// ── Stair pair (pre-computed in FloorPlanViewModel) ─────────────────────────

/**
 * A matched pair of stairwell entrances already transformed to campus-wide
 * coordinates.  Pre-computed so the PDR pipeline only needs proximity checks.
 *
 * Each pair links a "bottom" entrance to the **nearest** "top" entrance on
 * the same stairwell (identified by spatial proximity, since multiple
 * stairwells may lead to the same destination floor).
 *
 * @param bottomPosition Campus-coordinate position of the bottom entrance.
 * @param topPosition    Campus-coordinate position of the top entrance.
 * @param bottomFloorId  Floor ID of the floor the bottom entrance lives on (e.g. "floor_1").
 * @param topFloorId     Floor ID of the destination floor (e.g. "floor_2").
 * @param bottomFloorNumber Numeric floor value for the bottom (e.g. 1f).
 * @param topFloorNumber    Numeric floor value for the top (e.g. 2f).
 */
data class StairPair(
    val bottomPosition: Offset,
    val topPosition: Offset,
    val bottomFloorId: String,
    val topFloorId: String,
    val bottomFloorNumber: Float,
    val topFloorNumber: Float
)

// ── Transition event (fired by the detector) ────────────────────────────────

enum class StairDirection { UP, DOWN }

/**
 * Emitted by [StairwellTransitionDetector] when the user is confirmed to be
 * entering a stairwell.
 */
data class StairTransitionEvent(
    val stairPair: StairPair,
    val direction: StairDirection,
    /** Campus-wide position where climbing starts (bottom for UP, top for DOWN). */
    val startPosition: Offset,
    /** Campus-wide position where climbing ends (top for UP, bottom for DOWN). */
    val endPosition: Offset,
    /** Floor ID the user is leaving. */
    val originFloorId: String,
    /** Floor ID the user is going to. */
    val destinationFloorId: String,
    /**
     * Number of steps the user took between entering the stairwell proximity
     * (candidate latch) and ML confirmation.  The animation should account
     * for these "already climbed" steps so it doesn't lag behind reality.
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
