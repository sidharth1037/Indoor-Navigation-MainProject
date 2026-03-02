package `in`.project.enroute.feature.pdr.correction

import android.util.Log
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Manages the stairwell transition when a user climbs or descends stairs.
 *
 * State machine:
 * ```
 * IDLE ──(startTransition)──► CLIMBING ──(advanceStep)──► ARRIVED
 *   ▲                                        │               │
 *   │                                        ▼               │
 *   │                                    RETURNING           │
 *   │                                        │               │
 *   │                                        ▼               │
 *   │                                    CANCELLED           │
 *   └─────────────────────(finalize)──────────┴───────────────┘
 * ```
 *
 * ## Arrival & Cancellation Logic
 *
 * ### Arrival (any of these, after progress > [minProgressForArrival]):
 *  1. **Walking counter ≥ [walkingArrivalCount]**: Consecutive "walking"
 *     labels → the user has left the stairs.
 *  2. **Heading turn + off-stairs**: The heading deviates [sharpTurnThreshold]
 *     or more from the recent heading window average AND the latest ML label
 *     is NOT a stair label → the user turned at the landing.
 *  3. **Step cap**: Steps received ≥ [estimatedTotalSteps] AND the latest
 *     label is NOT the same-direction stair label → the animation is full
 *     and the user is no longer on stairs.
 *
 * ### Cancellation (u-turn, ML only):
 *  - **Opposite label counter ≥ [oppositeDirectionCancelCount]**: Consecutive
 *    opposite-direction labels after ≥ [minStepsBeforeCancel] total steps.
 *  - Transitions to RETURNING; the dot animates back to the origin.
 *
 * ### Heading reference
 * A sliding window of the last [headingWindowSize] headings is maintained.
 * Each new heading is compared to the **average** of this window, not to a
 * fixed heading captured at transition start.  This makes the turn detection
 * robust to IMU drift on stairs.
 */
class StairwellTransitionAnimator(
    /**
     * Average horizontal distance (campus units) covered per stair step.
     * Used to estimate total steps for the animation.
     * Default: 15 units ≈ 30 cm at pixelsPerCm=0.5
     */
    private val stairStepUnitLength: Float = 15f,
    /** Minimum estimated steps (avoids instant transitions for short stairwells). */
    private val minEstimatedSteps: Int = 6,
    /** Maximum estimated steps (caps long staircases). */
    private val maxEstimatedSteps: Int = 20,
    /** Heading change threshold (radians) for landing-turn arrival. */
    private val sharpTurnThreshold: Float = 1.05f,  // ~60°
    /** Minimum progress before any arrival/cancel check fires. */
    private val minProgressForArrival: Float = 0.3f,
    /** Consecutive "walking" labels needed to trigger walking-arrival. */
    private val walkingArrivalCount: Int = 2,
    /** Consecutive opposite-direction labels needed to trigger cancel. */
    private val oppositeDirectionCancelCount: Int = 3,
    /** Minimum total steps before cancellation is allowed. */
    private val minStepsBeforeCancel: Int = 3,
    /** Number of headings stored in the sliding window. */
    private val headingWindowSize: Int = 3
) {

    companion object {
        private const val TAG = "StairAnimator"
    }

    // ── Observable state ────────────────────────────────────────────────────

    var state: StairClimbingState = StairClimbingState.IDLE
        private set

    /** 0..1 progress through the stairwell animation. */
    var progress: Float = 0f
        private set

    /** Current interpolated position of the user dot. */
    var currentPosition: Offset = Offset.Zero
        private set

    /** The active transition event, or null when IDLE. */
    var activeEvent: StairTransitionEvent? = null
        private set

    /** How the most recent arrival was triggered. */
    var arrivalReason: ArrivalReason = ArrivalReason.NONE
        private set

    /** Number of pre-climbed steps at the time of detection. */
    var preClimbedStepsAtDetection: Int = 0
        private set

    // ── Internal ────────────────────────────────────────────────────────────

    private var startPosition: Offset = Offset.Zero
    private var endPosition: Offset = Offset.Zero
    private var estimatedTotalSteps: Int = 0
    private var stepsReceived: Int = 0
    /** Travel direction — used to classify "same" vs "opposite" ML labels. */
    private var direction: StairDirection = StairDirection.UP

    // ── Heading sliding window ──────────────────────────────────────────────

    /** Ring buffer of recent headings (stored as radians). */
    private val headingWindow = mutableListOf<Float>()

    /**
     * Returns the circular mean of the headings in [headingWindow].
     * Falls back to [fallback] if the window is empty.
     */
    private fun headingWindowAverage(fallback: Float): Float {
        if (headingWindow.isEmpty()) return fallback
        var sinSum = 0f
        var cosSum = 0f
        for (h in headingWindow) {
            sinSum += sin(h)
            cosSum += cos(h)
        }
        return kotlin.math.atan2(sinSum, cosSum)
    }

    // ── ML label counters ───────────────────────────────────────────────────

    /** Consecutive "walking" labels while climbing. */
    private var consecutiveWalkingCount: Int = 0
    /** Consecutive opposite-direction stair labels while climbing. */
    private var consecutiveOppositeCount: Int = 0

    // ── Return animation state ──────────────────────────────────────────────

    private var returnTotalSteps: Int = 0
    private var returnStepsReceived: Int = 0
    private var progressAtTurnaround: Float = 0f

    // ── Lifecycle ───────────────────────────────────────────────────────────

    /**
     * Begins a stairwell transition animation.
     *
     * @param event   The transition event from the detector.
     * @param heading Current user heading at the moment of entry.
     */
    fun startTransition(event: StairTransitionEvent, heading: Float) {
        activeEvent = event
        startPosition = event.startPosition
        endPosition = event.endPosition
        direction = event.direction
        stepsReceived = 0
        progress = 0f
        currentPosition = startPosition
        arrivalReason = ArrivalReason.NONE
        preClimbedStepsAtDetection = event.preClimbedSteps

        // Seed heading window with the initial heading
        headingWindow.clear()
        headingWindow.add(heading)

        // Reset ML counters
        consecutiveWalkingCount = 0
        consecutiveOppositeCount = 0

        // Estimate total steps from the stairwell drawing length
        val dist = GeometryUtils.distance(startPosition, endPosition)
        estimatedTotalSteps = (dist / stairStepUnitLength).toInt()
            .coerceIn(minEstimatedSteps, maxEstimatedSteps)

        state = StairClimbingState.CLIMBING
        Log.d(TAG, "startTransition: ${event.originFloorId} → ${event.destinationFloorId}, " +
                "dist=${"%.1f".format(dist)}, estSteps=$estimatedTotalSteps")
    }

    /**
     * Called on each real step from the step detector while CLIMBING.
     * Advances the dot along the interpolation line and checks for arrival
     * or cancellation.
     *
     * @param currentHeading The user's heading at this step.
     * @param motionLabel    Latest ML classification label (may be stale).
     * @return The interpolated position to use as the user dot location.
     */
    fun advanceStep(currentHeading: Float, motionLabel: String?): Offset {
        if (state != StairClimbingState.CLIMBING) return currentPosition

        stepsReceived++
        progress = (stepsReceived.toFloat() / estimatedTotalSteps).coerceAtMost(1f)

        // Interpolate position along the stairwell (holds at endPosition
        // once progress reaches 1.0 — does NOT auto-arrive).
        currentPosition = Offset(
            startPosition.x + (endPosition.x - startPosition.x) * progress,
            startPosition.y + (endPosition.y - startPosition.y) * progress
        )

        // ── Classify the ML label relative to travel direction ──────────
        val sameDirectionLabel = when (direction) {
            StairDirection.UP   -> "upstairs"
            StairDirection.DOWN -> "downstairs"
        }
        val oppositeDirectionLabel = when (direction) {
            StairDirection.UP   -> "downstairs"
            StairDirection.DOWN -> "upstairs"
        }
        val isSameDirectionLabel = motionLabel == sameDirectionLabel
        val isOppositeLabel = motionLabel == oppositeDirectionLabel
        val isOffStairsOrUnknown = !isSameDirectionLabel && !isOppositeLabel

        // ── Update consecutive ML counters ──────────────────────────────
        // Walking counter: increments on "walking", resets on anything else
        if (motionLabel == "walking") {
            consecutiveWalkingCount++
        } else {
            consecutiveWalkingCount = 0
        }

        // Opposite counter: increments on opposite stair label, resets otherwise
        if (isOppositeLabel) {
            consecutiveOppositeCount++
        } else {
            consecutiveOppositeCount = 0
        }

        // ── Heading turn check (vs sliding window average) ──────────────
        val windowAvg = headingWindowAverage(currentHeading)
        val headingDelta = abs(GeometryUtils.angleDifference(windowAvg, currentHeading))
        val isSharpTurn = headingDelta > sharpTurnThreshold

        // Update the heading window AFTER comparing to the average, so the
        // current heading doesn't pollute the reference.
        headingWindow.add(currentHeading)
        while (headingWindow.size > headingWindowSize) {
            headingWindow.removeAt(0)
        }

        // ── Arrival conditions ──────────────────────────────────────────
        val pastMinProgress = progress > minProgressForArrival

        val arrivedByWalking = pastMinProgress &&
                consecutiveWalkingCount >= walkingArrivalCount

        val arrivedByTurn = pastMinProgress &&
                isSharpTurn && isOffStairsOrUnknown

        val arrivedByStepCap = stepsReceived >= estimatedTotalSteps &&
                !isSameDirectionLabel

        val arrived = arrivedByWalking || arrivedByTurn || arrivedByStepCap

        // ── Cancellation conditions (u-turn, ML only) ───────────────────
        val cancelled = !arrived &&
                stepsReceived >= minStepsBeforeCancel &&
                consecutiveOppositeCount >= oppositeDirectionCancelCount

        Log.d(TAG, "step #$stepsReceived  prog=${"%.2f".format(progress)}  " +
                "label=$motionLabel  " +
                "walkCnt=$consecutiveWalkingCount  oppCnt=$consecutiveOppositeCount  " +
                "headΔ=${"%.0f".format(Math.toDegrees(headingDelta.toDouble()))}°  " +
                "sharpTurn=$isSharpTurn  " +
                "arrW=$arrivedByWalking arrT=$arrivedByTurn arrS=$arrivedByStepCap  " +
                "cancel=$cancelled")

        when {
            arrived -> {
                val reason = when {
                    arrivedByWalking -> ArrivalReason.WALKING
                    else             -> ArrivalReason.TURN
                }
                forceArrive(reason)
            }
            cancelled -> beginReturn()
        }

        return currentPosition
    }

    /**
     * Forces immediate arrival — snaps to the end position and transitions
     * to ARRIVED state.
     */
    fun forceArrive(reason: ArrivalReason = ArrivalReason.TURN) {
        arrivalReason = reason
        progress = 1f
        currentPosition = endPosition
        state = StairClimbingState.ARRIVED
        Log.d(TAG, "ARRIVED after $stepsReceived steps (reason=$reason)")
    }

    /**
     * Begins the reverse animation back to the origin floor.
     * Transitions to RETURNING state; subsequent [advanceReturnStep] calls
     * will decrement progress until the dot reaches the start position.
     */
    private fun beginReturn() {
        returnTotalSteps = (stepsReceived * progress).toInt()
            .coerceAtLeast(minEstimatedSteps / 2)
        returnStepsReceived = 0
        progressAtTurnaround = progress
        state = StairClimbingState.RETURNING
        Log.d(TAG, "RETURNING from progress=${"%.2f".format(progress)} " +
                "estReturnSteps=$returnTotalSteps")
    }

    /**
     * Called on each step while RETURNING. Moves the dot back toward
     * [startPosition]. Once progress reaches 0 (or "walking" is detected),
     * transitions to CANCELLED so the caller can restore the origin floor.
     *
     * @return The interpolated position for this step.
     */
    fun advanceReturnStep(motionLabel: String?): Offset {
        if (state != StairClimbingState.RETURNING) return currentPosition

        returnStepsReceived++
        val returnProgress = if (returnTotalSteps > 0) {
            (returnStepsReceived.toFloat() / returnTotalSteps).coerceAtMost(1f)
        } else 1f

        // Progress goes from progressAtTurnaround → 0
        progress = progressAtTurnaround * (1f - returnProgress)

        currentPosition = Offset(
            startPosition.x + (endPosition.x - startPosition.x) * progress,
            startPosition.y + (endPosition.y - startPosition.y) * progress
        )

        val arrivedBack = returnProgress >= 1f || motionLabel == "walking"

        Log.d(TAG, "return step #$returnStepsReceived  prog=${"%.2f".format(progress)}  " +
                "returnProg=${"%.2f".format(returnProgress)}  arrivedBack=$arrivedBack")

        if (arrivedBack) {
            progress = 0f
            currentPosition = startPosition
            state = StairClimbingState.CANCELLED
            Log.d(TAG, "CANCELLED (returned to origin) after $returnStepsReceived return steps")
        }

        return currentPosition
    }

    /**
     * Resets the animator back to IDLE after the caller has completed the
     * floor transition (loaded new constraints, reset correction engine, etc.).
     */
    fun finalize() {
        state = StairClimbingState.IDLE
        activeEvent = null
        progress = 0f
        stepsReceived = 0
        estimatedTotalSteps = 0
        returnTotalSteps = 0
        returnStepsReceived = 0
        progressAtTurnaround = 0f
        arrivalReason = ArrivalReason.NONE
        consecutiveWalkingCount = 0
        consecutiveOppositeCount = 0
        headingWindow.clear()
    }

    /** Full reset (e.g. tracking cleared). */
    fun reset() {
        finalize()
        currentPosition = Offset.Zero
        startPosition = Offset.Zero
        endPosition = Offset.Zero
        direction = StairDirection.UP
    }
}
