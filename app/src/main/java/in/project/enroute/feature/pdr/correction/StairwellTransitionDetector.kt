package `in`.project.enroute.feature.pdr.correction

import android.util.Log
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/**
 * Detects when a user is entering a stairwell using a **two-stage** approach
 * that decouples the spatial check from the ML confirmation to handle the
 * inherent timing gap between them.
 *
 * ### Stage 1 — Candidate acquisition (every step)
 * The user must be:
 *  1. Within [proximityRadius] of a stair entrance on the current floor.
 *  2. Heading toward it within [fovHalfAngle] **using the heading from 1-2
 *     steps ago** (not the instantaneous heading), which better represents
 *     the approach direction.
 *
 * When both conditions are met the entrance is latched as a *candidate* and
 * stays valid for [candidateExpirySteps] additional steps, even if the user
 * moves out of proximity afterwards.
 *
 * ### Stage 2 — ML confirmation
 * While a candidate is active, the sliding window of the last [windowSize]
 * ML predictions is checked.  A transition fires when at least
 * [requiredInWindow] predictions match "upstairs" (or "downstairs").
 * Low-confidence predictions are **skipped** (not counted against the
 * window), so they don't poison the signal.
 *
 * This two-stage design solves the timing problem: the ML model needs ~2.5 s
 * to produce two "upstairs" labels, during which the user may have walked
 * past the entrance.  The candidate latch bridges that gap.
 */
class StairwellTransitionDetector(
    /** Campus-coordinate radius to consider "near" a stair entrance. */
    private val proximityRadius: Float = 100f,   // ~2 m
    /** Half-angle of the field-of-view cone (radians). */
    private val fovHalfAngle: Float = 1.047f,   // 60° → 120° total
    /** Sliding window size for ML labels. */
    private val windowSize: Int = 3,
    /** How many labels inside the window must match to confirm. */
    private val requiredInWindow: Int = 1,
    /** Minimum confidence from the ML model to accept the label. */
    private val minConfidence: Float = 0.45f,
    /** Steps to keep a candidate alive after the user leaves proximity. */
    private val candidateExpirySteps: Int = 8,
    /** Number of recent headings to keep for the lagged FOV check. */
    private val headingBufferSize: Int = 3,
    /**
     * Number of consecutive same-direction stair labels required to
     * trigger a sustained-ML transition (no spatial/FOV constraints).
     */
    private val sustainedLabelThreshold: Int = 3,
    /**
     * Expanded proximity radius for sustained-ML detection.
     * Larger than [proximityRadius] because the user may have walked
     * past the entrance by the time 4 labels accumulate.
     */
    private val sustainedProximityRadius: Float = 200f   // ~4 m
) {

    companion object {
        private const val TAG = "StairDetector"
    }

    // ── ML label sliding window ─────────────────────────────────────────────

    /** Ring buffer of recent accepted labels (low-confidence ones are skipped). */
    private val labelWindow = mutableListOf<String>()

    // ── Consecutive stair label tracking (for sustained-ML detection) ─────
    /** The stair label currently being counted ("upstairs" or "downstairs"). */
    private var consecutiveStairLabel: String? = null
    /** How many consecutive times that label has appeared. */
    private var consecutiveStairCount: Int = 0

    /**
     * Feed every ML classification result here.
     * Low-confidence predictions are silently dropped.
     */
    fun onMotionLabel(label: String, confidence: Float) {
        if (confidence < minConfidence) return   // skip, don't pollute window

        labelWindow.add(label)
        while (labelWindow.size > windowSize) {
            labelWindow.removeAt(0)
        }

        // Track consecutive stair labels for sustained-ML detection
        if (label == "upstairs" || label == "downstairs") {
            if (label == consecutiveStairLabel) {
                consecutiveStairCount++
            } else {
                consecutiveStairLabel = label
                consecutiveStairCount = 1
            }
        } else {
            // Any non-stair label breaks the streak
            consecutiveStairLabel = null
            consecutiveStairCount = 0
        }
    }

    // ── Heading history ─────────────────────────────────────────────────────

    private val headingHistory = mutableListOf<Float>()

    /** Returns the heading from approximately 1-2 steps ago. */
    private fun laggedHeading(currentHeading: Float): Float {
        headingHistory.add(currentHeading)
        while (headingHistory.size > headingBufferSize) {
            headingHistory.removeAt(0)
        }
        // Use the oldest heading in the buffer (1-2 steps ago)
        return headingHistory.first()
    }

    // ── Candidate latch ─────────────────────────────────────────────────────

    private var candidatePair: StairPair? = null
    private var candidateGoingUp: Boolean = true
    private var candidateStepsRemaining: Int = 0
    /** Steps elapsed since the candidate was first latched (tracks detection lag). */
    private var stepsSinceCandidateLatch: Int = 0

    // ── Public API ──────────────────────────────────────────────────────────

    /** Resets all state (counters, candidate, heading history). */
    fun reset() {
        labelWindow.clear()
        headingHistory.clear()
        candidatePair = null
        candidateStepsRemaining = 0
        stepsSinceCandidateLatch = 0
        consecutiveStairLabel = null
        consecutiveStairCount = 0
    }

    /**
     * Evaluates the two-stage check and returns a [StairTransitionEvent] if a
     * stairwell entry is confirmed, or `null` otherwise.
     *
     * @param position           User's current position (campus-wide).
     * @param heading            User's current heading (radians, 0 = north, CW+).
     * @param stairPairs         Pre-computed stair pairs for all floors.
     * @param currentFloorNumber The floor number the user is currently on.
     */
    fun checkTransition(
        position: Offset,
        heading: Float,
        stairPairs: List<StairPair>,
        currentFloorNumber: Float
    ): StairTransitionEvent? {

        val fovHeading = laggedHeading(heading)

        // ── Stage 1: Acquire or refresh candidate ───────────────────────
        val nearest = findNearestFacingStairPair(
            position = position,
            heading = fovHeading,
            stairPairs = stairPairs,
            currentFloorNumber = currentFloorNumber
        )

        if (nearest != null) {
            val isNewCandidate = candidatePair == null
            candidatePair = nearest.first
            candidateGoingUp = nearest.second
            candidateStepsRemaining = candidateExpirySteps
            if (isNewCandidate) {
                stepsSinceCandidateLatch = 0  // fresh acquisition
            }
            stepsSinceCandidateLatch++
            Log.d(TAG, "Candidate acquired/refreshed: pair=${nearest.first}, goingUp=${nearest.second}, stepsLeft=$candidateExpirySteps, lagSteps=$stepsSinceCandidateLatch")
        } else if (candidateStepsRemaining > 0) {
            candidateStepsRemaining--
            stepsSinceCandidateLatch++
            Log.d(TAG, "Candidate ticking: stepsLeft=$candidateStepsRemaining, lagSteps=$stepsSinceCandidateLatch")
        } else {
            // No candidate and expiry reached
            if (candidatePair != null) {
                Log.d(TAG, "Candidate expired")
                candidatePair = null
                stepsSinceCandidateLatch = 0
            }
        }

        // ── Stage 2: ML confirmation against the active candidate ───────
        val pair = candidatePair ?: return null

        val upCount = labelWindow.count { it == "upstairs" }
        val downCount = labelWindow.count { it == "downstairs" }
        val goingUp = candidateGoingUp
        val confirmed = if (goingUp) upCount >= requiredInWindow else downCount >= requiredInWindow

        Log.d(TAG, "ML window=$labelWindow  upCount=$upCount downCount=$downCount goingUp=$goingUp confirmed=$confirmed")

        if (!confirmed) return null

        // ── Build the transition event ──────────────────────────────────
        val direction = if (goingUp) StairDirection.UP else StairDirection.DOWN

        val startPos: Offset
        val endPos: Offset
        val originFloor: String
        val destFloor: String

        if (direction == StairDirection.UP) {
            startPos = pair.bottomPosition
            endPos = pair.topPosition
            originFloor = pair.bottomFloorId
            destFloor = pair.topFloorId
        } else {
            startPos = pair.topPosition
            endPos = pair.bottomPosition
            originFloor = pair.topFloorId
            destFloor = pair.bottomFloorId
        }

        Log.d(TAG, "TRANSITION TRIGGERED: $direction from $originFloor → $destFloor")

        val preClimbed = stepsSinceCandidateLatch

        return StairTransitionEvent(
            stairPair = pair,
            direction = direction,
            startPosition = startPos,
            endPosition = endPos,
            originFloorId = originFloor,
            destinationFloorId = destFloor,
            preClimbedSteps = preClimbed
        )
    }
    // ── Stage 3: Sustained ML detection (independent fallback) ─────────

    /**
     * Independent stairwell detection based purely on sustained ML output.
     *
     * If the model has output the same stair label ("upstairs" / "downstairs")
     * for [sustainedLabelThreshold] or more consecutive classifications, find
     * the nearest stairwell the user can take in that direction — using
     * **proximity only** (no FOV / heading constraint).
     *
     * This covers cases where the spatial candidate was never latched (e.g.
     * user entered the stairwell from an unexpected angle) but the ML model
     * is very confident.
     *
     * @return A [StairTransitionEvent] if a sustained transition is confirmed,
     *         or `null` otherwise.
     */
    fun checkSustainedMLTransition(
        position: Offset,
        stairPairs: List<StairPair>,
        currentFloorNumber: Float
    ): StairTransitionEvent? {
        if (consecutiveStairCount < sustainedLabelThreshold) return null

        Log.d(TAG, "sustainedML: ${consecutiveStairCount}× \"$consecutiveStairLabel\" " +
                "at (${position.x.toInt()},${position.y.toInt()}) floor=$currentFloorNumber, " +
                "${stairPairs.size} stair pairs available")

        val goingUp = consecutiveStairLabel == "upstairs"

        // Find the nearest stairwell by proximity only (no FOV check)
        val pair = findNearestPairByProximity(
            position, stairPairs, currentFloorNumber, goingUp
        ) ?: return null

        val direction = if (goingUp) StairDirection.UP else StairDirection.DOWN

        val startPos: Offset
        val endPos: Offset
        val originFloor: String
        val destFloor: String

        if (direction == StairDirection.UP) {
            startPos = pair.bottomPosition
            endPos = pair.topPosition
            originFloor = pair.bottomFloorId
            destFloor = pair.topFloorId
        } else {
            startPos = pair.topPosition
            endPos = pair.bottomPosition
            originFloor = pair.topFloorId
            destFloor = pair.bottomFloorId
        }

        Log.d(TAG, "SUSTAINED-ML TRANSITION: $direction from $originFloor → $destFloor " +
                "(${consecutiveStairCount} consecutive \"$consecutiveStairLabel\" labels)")

        return StairTransitionEvent(
            stairPair = pair,
            direction = direction,
            startPosition = startPos,
            endPosition = endPos,
            originFloorId = originFloor,
            destinationFloorId = destFloor,
            preClimbedSteps = consecutiveStairCount
        )
    }

    /**
     * Finds the nearest stair pair by proximity only — no heading / FOV
     * constraint.  Used by the sustained-ML fallback path.
     *
     * First tries to find a pair whose start entrance is on the current floor.
     * If nothing is found, falls back to checking **both** entrances of every
     * pair regardless of floor — the stairwell entrance the user is closest
     * to might be stored on the other floor's data due to how stair pairs
     * are built.
     */
    private fun findNearestPairByProximity(
        position: Offset,
        stairPairs: List<StairPair>,
        currentFloorNumber: Float,
        goingUp: Boolean
    ): StairPair? {
        var bestPair: StairPair? = null
        var bestDist = Float.MAX_VALUE

        // Pass 1: Strict floor match on the expected start entrance
        for (pair in stairPairs) {
            val startPos: Offset
            val startFloorNumber: Float

            if (goingUp) {
                startPos = pair.bottomPosition
                startFloorNumber = pair.bottomFloorNumber
            } else {
                startPos = pair.topPosition
                startFloorNumber = pair.topFloorNumber
            }

            if (startFloorNumber != currentFloorNumber) continue

            val dist = GeometryUtils.distance(position, startPos)
            if (dist > sustainedProximityRadius) continue

            if (dist < bestDist) {
                bestDist = dist
                bestPair = pair
            }
        }

        if (bestPair != null) {
            Log.d(TAG, "sustainedML-proximity: found pair on current floor (dist=${"%.1f".format(bestDist)})")
            return bestPair
        }

        // Pass 2: Cross-floor fallback — check proximity to EITHER entrance
        // of every pair that connects to the current floor.  The stairwell
        // geometry may be stored on the adjacent floor's data.
        for (pair in stairPairs) {
            // The pair must connect the current floor in the correct direction
            val connects = if (goingUp) {
                pair.bottomFloorNumber == currentFloorNumber ||
                        pair.topFloorNumber > currentFloorNumber
            } else {
                pair.topFloorNumber == currentFloorNumber ||
                        pair.bottomFloorNumber < currentFloorNumber
            }
            if (!connects) continue

            // Check distance to both entrances
            val distBottom = GeometryUtils.distance(position, pair.bottomPosition)
            val distTop = GeometryUtils.distance(position, pair.topPosition)
            val minDist = minOf(distBottom, distTop)

            if (minDist > sustainedProximityRadius) continue

            if (minDist < bestDist) {
                bestDist = minDist
                bestPair = pair
            }
        }

        if (bestPair != null) {
            Log.d(TAG, "sustainedML-proximity: cross-floor fallback found pair (dist=${"%.1f".format(bestDist)})")
        } else {
            Log.d(TAG, "sustainedML-proximity: no pair found within ${sustainedProximityRadius} units")
        }

        return bestPair
    }
    // ── Internals ───────────────────────────────────────────────────────────

    /**
     * Finds the nearest stair pair whose start entrance is within
     * [proximityRadius] and within [fovHalfAngle] of the user's (lagged)
     * heading.
     *
     * Returns a Pair of (StairPair, goingUp) or `null`.
     * Tries **up** first; falls back to **down** if no up-candidate found.
     */
    private fun findNearestFacingStairPair(
        position: Offset,
        heading: Float,
        stairPairs: List<StairPair>,
        currentFloorNumber: Float
    ): Pair<StairPair, Boolean>? {
        // Try up first, then down
        findBestPair(position, heading, stairPairs, currentFloorNumber, goingUp = true)
            ?.let { return Pair(it, true) }
        findBestPair(position, heading, stairPairs, currentFloorNumber, goingUp = false)
            ?.let { return Pair(it, false) }
        return null
    }

    private fun findBestPair(
        position: Offset,
        heading: Float,
        stairPairs: List<StairPair>,
        currentFloorNumber: Float,
        goingUp: Boolean
    ): StairPair? {
        var bestPair: StairPair? = null
        var bestDist = Float.MAX_VALUE

        for (pair in stairPairs) {
            val startPos: Offset
            val startFloorNumber: Float

            if (goingUp) {
                startPos = pair.bottomPosition
                startFloorNumber = pair.bottomFloorNumber
            } else {
                startPos = pair.topPosition
                startFloorNumber = pair.topFloorNumber
            }

            // Must be on the user's current floor
            if (startFloorNumber != currentFloorNumber) continue

            // Proximity check
            val dist = GeometryUtils.distance(position, startPos)
            if (dist > proximityRadius) continue

            // Heading / FOV check — is the user facing toward the entrance?
            if (dist > 1f) { // skip angle check if essentially on top of it
                val dirToEntrance = GeometryUtils.directionAngle(position, startPos)
                val angleDiff = abs(GeometryUtils.angleDifference(heading, dirToEntrance))
                if (angleDiff > fovHalfAngle) {
                    continue
                }
            }

            if (dist < bestDist) {
                bestDist = dist
                bestPair = pair
            }
        }

        return bestPair
    }
}
