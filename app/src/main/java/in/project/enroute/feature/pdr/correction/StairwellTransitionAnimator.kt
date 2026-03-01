package `in`.project.enroute.feature.pdr.correction

import android.util.Log
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/**
 * Manages the stairwell transition when a user climbs or descends stairs.
 *
 * State machine:
 * ```
 * IDLE ──(startTransition)──► CLIMBING ──(advanceStep)──► ARRIVED
 *   ▲                                        │               │
 *   │                                        ▼               │
 *   │                                    CANCELLED           │
 *   └─────────────────────(finalize)──────────┴───────────────┘
 * ```
 *
 * ## Design
 *
 * During **CLIMBING** the user dot is linearly interpolated from
 * [startPosition] to [endPosition] based on [progress].  Each real step
 * from the step detector advances the progress.  No footstep markers are
 * rendered — only the blue dot slides along the stairwell.
 *
 * ### Head-start
 *
 * By the time the ML model confirms stair climbing, the user has already
 * taken several steps.  [startTransition] accepts a `preClimbedSteps` count
 * and seeds the animation progress so the dot doesn't start from 0%.
 *
 * ### Arrival vs Cancellation
 *
 * A **sharp heading change** during climbing triggers either arrival or
 * cancellation, depending on the ML label at that moment:
 *
 *  - **Arrival** (turn at the landing):
 *    - ML label = "walking" (user left the stairs), OR
 *    - ML label matches the travel direction ("upstairs" when going UP,
 *      "downstairs" when going DOWN), OR
 *    - ML label is null / unknown — benefit of the doubt → arrival.
 *    - 100% progress does **not** auto-arrive; the dot holds at the end
 *      point until walking or a turn is detected.
 *
 *  - **Cancellation** (mid-stairwell turnaround):
 *    - Sharp turn + ML label is the **opposite** direction ("downstairs"
 *      when going UP, "upstairs" when going DOWN) → user reversed.
 *    - Also triggers if ML firmly shows the opposite direction even
 *      without a sharp turn (after minimum progress).
 *
 * When arrival is triggered by a turn, the caller should **replay** that
 * step as a normal PDR step on the new floor so it produces a footprint.
 *
 * Once ARRIVED, the caller must [finalize] to move back to IDLE and resume
 * normal PDR on the new floor.
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
    /** Heading change threshold (radians) that triggers forced arrival. */
    private val sharpTurnThreshold: Float = 1.05f,  // 60°
    /** Minimum progress before arrival conditions are checked. */
    private val minProgressForArrival: Float = 0.3f
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

    // ── Internal ────────────────────────────────────────────────────────────

    private var startPosition: Offset = Offset.Zero
    private var endPosition: Offset = Offset.Zero
    private var estimatedTotalSteps: Int = 0
    private var stepsReceived: Int = 0
    private var headingAtStart: Float = 0f
    /** Travel direction — used to distinguish "same" vs "opposite" ML labels. */
    private var direction: StairDirection = StairDirection.UP

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
        headingAtStart = heading
        direction = event.direction
        stepsReceived = 0
        progress = 0f
        currentPosition = startPosition

        // Estimate total steps from the stairwell drawing length
        val dist = GeometryUtils.distance(startPosition, endPosition)
        estimatedTotalSteps = (dist / stairStepUnitLength).toInt()
            .coerceIn(minEstimatedSteps, maxEstimatedSteps)

        // ── Head-start: the user already climbed some steps before the ML
        // model confirmed the transition.  Seed the progress so the
        // animation doesn't visually lag behind reality.
        val preClimbed = event.preClimbedSteps.coerceAtMost(estimatedTotalSteps - 1)
        if (preClimbed > 0) {
            stepsReceived = preClimbed
            progress = (preClimbed.toFloat() / estimatedTotalSteps).coerceAtMost(0.6f)
            currentPosition = Offset(
                startPosition.x + (endPosition.x - startPosition.x) * progress,
                startPosition.y + (endPosition.y - startPosition.y) * progress
            )
        }

        state = StairClimbingState.CLIMBING
        Log.d(TAG, "startTransition: ${event.originFloorId} → ${event.destinationFloorId}, " +
                "dist=${"%.1f".format(dist)}, estSteps=$estimatedTotalSteps, " +
                "preClimbed=$preClimbed, initialProgress=${"%.2f".format(progress)}")
    }

    /**
     * Called on each real step from the step detector while CLIMBING.
     * Advances the dot along the interpolation line and checks for arrival
     * or cancellation (turnaround).
     *
     * ### Arrival triggers
     *  - ML label = "walking" (user left the stairs) **after min progress**.
     *  - Sharp heading change + ML label is the **same** direction as travel
     *    (or "walking" / null) — user turned at the landing.
     *  - 100% progress does **not** auto-arrive; the dot holds at the end
     *    position until one of the above signals fires.
     *
     * ### Cancellation triggers
     *  - Sharp heading change + ML label is the **opposite** direction —
     *    user reversed mid-stairwell.
     *  - ML label firmly shows opposite direction after min progress, even
     *    without a turn (sustained opposite signal).
     *
     * @param currentHeading The user's heading at this step.
     * @param motionLabel    Latest ML classification label.
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
        val isOppositeLabel = motionLabel == oppositeDirectionLabel
        val isSameDirectionLabel = motionLabel == sameDirectionLabel
        // "walking", "idle", or null — user is NOT on stairs
        val isOffStairsOrUnknown = !isSameDirectionLabel && !isOppositeLabel

        // ── Heading check ───────────────────────────────────────────────
        val headingDelta = abs(GeometryUtils.angleDifference(headingAtStart, currentHeading))
        val isSharpTurn = headingDelta > sharpTurnThreshold

        // ── Arrival conditions ──────────────────────────────────────────
        // The dot holds at the end position until a real signal confirms
        // the user has left the stairs.  100% progress alone does NOT
        // trigger arrival — it just means the dot stops advancing.
        //
        // Key distinction: if ML still says the user is on stairs (same
        // direction label), a sharp turn is a TURNAROUND, not a landing
        // turn.  Only "walking" / "idle" / null confirms the user left
        // the stairs.
        val arrived = progress > minProgressForArrival && (
                // ML says "walking" → user left the stairs
                motionLabel == "walking" ||
                // Sharp turn + ML is NOT a stair label → landing turn
                (isSharpTurn && isOffStairsOrUnknown)
        )

        // ── Cancellation conditions (turnaround) ────────────────────────
        // Sharp turn + ML says still on stairs (same OR opposite) → going back
        // Sustained opposite label after min progress → going back
        val cancelled = !arrived && stepsReceived >= 3 && (
                (isSharpTurn && (isSameDirectionLabel || isOppositeLabel)) ||
                (progress > minProgressForArrival && isOppositeLabel)
        )

        Log.d(TAG, "step #$stepsReceived  progress=${"%.2f".format(progress)}  " +
                "label=$motionLabel  turn=$isSharpTurn  " +
                "headingΔ=${"%.1f".format(Math.toDegrees(headingDelta.toDouble()))}°  " +
                "arrived=$arrived  cancelled=$cancelled")

        when {
            arrived -> forceArrive()
            cancelled -> cancelTransition()
        }

        return currentPosition
    }

    /**
     * Forces immediate arrival — snaps to the end position and transitions
     * to ARRIVED state.
     */
    fun forceArrive() {
        progress = 1f
        currentPosition = endPosition
        state = StairClimbingState.ARRIVED
        Log.d(TAG, "ARRIVED after $stepsReceived steps")
    }

    /**
     * Begins the reverse animation back to the origin floor.
     * Transitions to RETURNING state; subsequent [advanceReturnStep] calls
     * will decrement progress until arrivedBack at the start position.
     */
    fun cancelTransition() {
        // Record how far the user got — this determines how many steps
        // the return animation needs.
        returnTotalSteps = (stepsReceived * progress).toInt().coerceAtLeast(minEstimatedSteps / 2)
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

        Log.d(TAG, "return step #$returnStepsReceived  progress=${"%.2f".format(progress)}  " +
                "returnProgress=${"%.2f".format(returnProgress)}  arrivedBack=$arrivedBack")

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
    }

    /** Full reset (e.g. tracking cleared). */
    fun reset() {
        finalize()
        currentPosition = Offset.Zero
        startPosition = Offset.Zero
        endPosition = Offset.Zero
        headingAtStart = 0f
        direction = StairDirection.UP
    }
}
