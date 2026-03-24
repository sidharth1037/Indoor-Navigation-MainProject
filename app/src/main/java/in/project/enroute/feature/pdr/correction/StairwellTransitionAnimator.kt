package `in`.project.enroute.feature.pdr.correction

import android.util.Log
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs

/**
 * Stairwell transition state machine.
 *
 * Uses the **stair axis** — the direction vector from the start entrance
 * to the end entrance — as a fixed geometric reference for heading
 * comparisons, replacing the previous sliding-window average approach.
 *
 * ```
 * IDLE ──(startTransition)──► CLIMBING ──(arrival)──► ARRIVED
 *   ▲                             │                     │
 *   │                             ▼                     │
 *   │                         RETURNING                 │
 *   │                             │                     │
 *   │                             ▼                     │
 *   │                         CANCELLED                 │
 *   └──────────(finalize)─────────┴─────────────────────┘
 * ```
 *
 * ## Arrival (after [minProgressForArrival]):
 *  1. **Turn + walking**: heading within [arrivalHeadingThreshold] of the
 *     *opposite* stair axis AND [walkingArrivalCount]+ consecutive "walking"
 *     labels → the user left the stairs at the landing.
 *  2. **Idle then walk**: [idleThreshold]+ consecutive "idle" labels
 *     followed by [walkingArrivalCount]+ consecutive "walking" → the user
 *     paused on the stairs and then resumed walking on the new floor.
 *  3. **Step cap**: steps ≥ [estimatedTotalSteps] AND the latest label is
 *     NOT the same-direction stair label → the animation is full.
 *
 * ## Cancellation (u-turn):
 *  - **Opposite direction**: heading > [returnHeadingThreshold] from stair
 *    axis AND [oppositeDirectionCancelCount]+ consecutive opposite-direction
 *    stair labels → RETURNING.
 */
class StairwellTransitionAnimator(
    /** Average horizontal distance (campus units) per stair step. */
    private val stairStepUnitLength: Float = 15f,
    /** Minimum estimated steps (avoids instant transitions). */
    private val minEstimatedSteps: Int = 6,
    /** Maximum estimated steps (caps long staircases). */
    private val maxEstimatedSteps: Int = 20,
    /** Minimum progress before any arrival/cancel check fires. */
    private var minProgressForArrival: Float = 0.3f,
    /** Consecutive "walking" labels needed for arrival. */
    private var walkingArrivalCount: Int = 2,
    /** Consecutive opposite-direction labels needed for cancel. */
    private var oppositeDirectionCancelCount: Int = 3,
    /** Minimum total steps before cancellation is allowed. */
    private var minStepsBeforeCancel: Int = 3,
    /** Max heading deviation from opposite axis (radians) for arrival. */
    private var arrivalHeadingThreshold: Float = 0.785f,   // 45°
    /** Min heading deviation from stair axis (radians) for return. */
    private var returnHeadingThreshold: Float = 2.094f,     // 120°
    /** Consecutive "idle" labels to mark a pause. */
    private var idleThreshold: Int = 5
) {

    companion object {
        private const val TAG = "StairAnimator"
    }

    // ── Observable state ────────────────────────────────────────────────

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

    // ── Internal ────────────────────────────────────────────────────────

    private var startPosition: Offset = Offset.Zero
    private var endPosition: Offset = Offset.Zero
    private var estimatedTotalSteps: Int = 0
    private var stepsReceived: Int = 0
    private var direction: StairDirection = StairDirection.UP

    /** Stair axis angle (radians, PDR convention: 0=N, CW+). */
    private var stairAxisAngle: Float = 0f
    /** Opposite of stair axis (stairAxisAngle + π, normalised). */
    private var oppositeAxisAngle: Float = 0f

    // ── ML label counters ───────────────────────────────────────────────

    private var consecutiveWalkingCount: Int = 0
    private var consecutiveOppositeCount: Int = 0
    private var consecutiveIdleCount: Int = 0
    /** Whether the user has been idle for ≥ [idleThreshold] labels. */
    private var wasIdlePaused: Boolean = false

    // ── Return animation state ──────────────────────────────────────────

    private var returnTotalSteps: Int = 0
    private var returnStepsReceived: Int = 0
    private var progressAtTurnaround: Float = 0f

    // ── Lifecycle ───────────────────────────────────────────────────────

    /**
     * Hot-updates stair arrival/return heuristics.
     */
    fun updateSettings(
        minProgressForArrival: Float? = null,
        walkingArrivalCount: Int? = null,
        oppositeDirectionCancelCount: Int? = null,
        minStepsBeforeCancel: Int? = null,
        arrivalHeadingThreshold: Float? = null,
        returnHeadingThreshold: Float? = null,
        idleThreshold: Int? = null
    ) {
        if (minProgressForArrival != null) {
            this.minProgressForArrival = minProgressForArrival.coerceIn(0f, 1f)
        }
        if (walkingArrivalCount != null) {
            this.walkingArrivalCount = walkingArrivalCount.coerceAtLeast(1)
        }
        if (oppositeDirectionCancelCount != null) {
            this.oppositeDirectionCancelCount = oppositeDirectionCancelCount.coerceAtLeast(1)
        }
        if (minStepsBeforeCancel != null) {
            this.minStepsBeforeCancel = minStepsBeforeCancel.coerceAtLeast(0)
        }
        if (arrivalHeadingThreshold != null) {
            this.arrivalHeadingThreshold = arrivalHeadingThreshold
        }
        if (returnHeadingThreshold != null) {
            this.returnHeadingThreshold = returnHeadingThreshold
        }
        if (idleThreshold != null) {
            this.idleThreshold = idleThreshold.coerceAtLeast(1)
        }
    }

    /**
     * Begins a stairwell transition animation.
     *
    * @param event   The transition event from the detector.
     */
    fun startTransition(event: StairTransitionEvent) {
        activeEvent = event
        startPosition = event.startPosition
        endPosition = event.endPosition
        direction = event.direction
        stepsReceived = 0
        progress = 0f
        currentPosition = startPosition
        arrivalReason = ArrivalReason.NONE
        preClimbedStepsAtDetection = event.preClimbedSteps

        // Compute stair axis using the same convention as user heading
        stairAxisAngle = GeometryUtils.directionAngle(startPosition, endPosition)
        oppositeAxisAngle = GeometryUtils.normalizeAngle(stairAxisAngle + PI.toFloat())

        // Reset counters
        consecutiveWalkingCount = 0
        consecutiveOppositeCount = 0
        consecutiveIdleCount = 0
        wasIdlePaused = false

        // Estimate total steps from the stairwell geometry
        val dist = GeometryUtils.distance(startPosition, endPosition)
        estimatedTotalSteps = (dist / stairStepUnitLength).toInt()
            .coerceIn(minEstimatedSteps, maxEstimatedSteps)

        state = StairClimbingState.CLIMBING
        Log.d(TAG, "startTransition: ${event.originFloorId} → ${event.destinationFloorId}, " +
                "dist=${"%.1f".format(dist)}, estSteps=$estimatedTotalSteps, " +
                "axis=${"%.0f".format(Math.toDegrees(stairAxisAngle.toDouble()))}°")
    }

    /**
     * Called on each real step while CLIMBING.
     * Advances the dot and checks for arrival or cancellation.
     *
     * @param currentHeading The user's heading at this step.
     * @param motionLabel    Latest ML classification label.
     * @return The interpolated position for the user dot.
     */
    fun advanceStep(currentHeading: Float, motionLabel: String?): Offset {
        if (state != StairClimbingState.CLIMBING) return currentPosition

        stepsReceived++
        progress = (stepsReceived.toFloat() / estimatedTotalSteps).coerceAtMost(1f)

        // Interpolate position
        currentPosition = Offset(
            startPosition.x + (endPosition.x - startPosition.x) * progress,
            startPosition.y + (endPosition.y - startPosition.y) * progress
        )

        // ── Classify the label relative to travel direction ─────────
        val sameLabel = when (direction) {
            StairDirection.UP   -> "upstairs"
            StairDirection.DOWN -> "downstairs"
        }
        val oppositeLabel = when (direction) {
            StairDirection.UP   -> "downstairs"
            StairDirection.DOWN -> "upstairs"
        }
        val isSameLabel = motionLabel == sameLabel
        val isOpposite = motionLabel == oppositeLabel

        // ── Update ML counters ──────────────────────────────────────
        if (motionLabel == "walking") {
            consecutiveWalkingCount++
            consecutiveIdleCount = 0
        } else {
            consecutiveWalkingCount = 0
            if (motionLabel == "idle") {
                consecutiveIdleCount++
                if (consecutiveIdleCount >= idleThreshold) wasIdlePaused = true
            } else {
                consecutiveIdleCount = 0
            }
        }

        if (isOpposite) consecutiveOppositeCount++ else consecutiveOppositeCount = 0

        // ── Heading checks against stair axis ───────────────────────
        val headingFromAxis = abs(GeometryUtils.angleDifference(stairAxisAngle, currentHeading))
        val headingFromOpposite = abs(GeometryUtils.angleDifference(oppositeAxisAngle, currentHeading))

        // ── Arrival conditions ──────────────────────────────────────
        val pastMinProgress = progress > minProgressForArrival

        // 1. Turn + walking: heading close to opposite axis & walking labels
        val arrivedByTurnWalking = pastMinProgress &&
                headingFromOpposite < arrivalHeadingThreshold &&
                consecutiveWalkingCount >= walkingArrivalCount

        // 2. Idle then walk: paused on stairs, now walking
        val arrivedByIdleWalk = pastMinProgress &&
                wasIdlePaused &&
                consecutiveWalkingCount >= walkingArrivalCount

        // 3. Step cap: max steps reached & not still climbing
        val arrivedByStepCap = stepsReceived >= estimatedTotalSteps && !isSameLabel

        val arrived = arrivedByTurnWalking || arrivedByIdleWalk || arrivedByStepCap

        // ── Cancellation (u-turn) ───────────────────────────────────
        val cancelled = !arrived &&
                stepsReceived >= minStepsBeforeCancel &&
                headingFromAxis > returnHeadingThreshold &&
                consecutiveOppositeCount >= oppositeDirectionCancelCount

        Log.d(TAG, "step #$stepsReceived  prog=${"%.2f".format(progress)}  " +
                "label=$motionLabel  " +
                "walkCnt=$consecutiveWalkingCount  oppCnt=$consecutiveOppositeCount  " +
                "idleCnt=$consecutiveIdleCount  idlePaused=$wasIdlePaused  " +
                "headFromAxis=${"%.0f".format(Math.toDegrees(headingFromAxis.toDouble()))}°  " +
                "headFromOpp=${"%.0f".format(Math.toDegrees(headingFromOpposite.toDouble()))}°  " +
                "arrTW=$arrivedByTurnWalking arrIW=$arrivedByIdleWalk arrS=$arrivedByStepCap  " +
                "cancel=$cancelled")

        when {
            arrived -> {
                val reason = when {
                    arrivedByTurnWalking -> ArrivalReason.TURN
                    arrivedByIdleWalk    -> ArrivalReason.WALKING
                    else                 -> ArrivalReason.WALKING
                }
                forceArrive(reason)
            }
            cancelled -> beginReturn()
        }

        return currentPosition
    }

    /**
     * Forces immediate arrival — snaps to end position, transitions to ARRIVED.
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
     * Called on each step while RETURNING.  Moves the dot back toward
     * [startPosition].  Once progress reaches 0 (or "walking" detected),
     * transitions to CANCELLED.
     *
     * @return The interpolated position for this step.
     */
    fun advanceReturnStep(motionLabel: String?): Offset {
        if (state != StairClimbingState.RETURNING) return currentPosition

        returnStepsReceived++
        val returnProgress = if (returnTotalSteps > 0) {
            (returnStepsReceived.toFloat() / returnTotalSteps).coerceAtMost(1f)
        } else 1f

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
     * floor transition.
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
        consecutiveIdleCount = 0
        wasIdlePaused = false
    }

    /** Full reset (e.g. tracking cleared). */
    fun reset() {
        finalize()
        currentPosition = Offset.Zero
        startPosition = Offset.Zero
        endPosition = Offset.Zero
        direction = StairDirection.UP
        stairAxisAngle = 0f
        oppositeAxisAngle = 0f
    }
}
