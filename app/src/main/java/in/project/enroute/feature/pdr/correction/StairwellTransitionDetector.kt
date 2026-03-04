package `in`.project.enroute.feature.pdr.correction

import android.util.Log
import androidx.compose.ui.geometry.Offset

/**
 * Boundary-based stairwell entry detector.
 *
 * Uses [StairwellZone] polygon boundaries and top/bottom edges instead
 * of entrance-proximity checks, giving accurate start/end positioning
 * even for wide stairwells.
 *
 * ## Detection modes
 *
 * ### 1. Inside-polygon + ML
 * When the ML model outputs [entryThreshold] or more consecutive stair
 * labels ("upstairs"/"downstairs") **and** the user is inside a
 * [StairwellZone] polygon, a transition fires immediately.  The user's
 * current position is projected onto the entry edge (bottom for UP,
 * top for DOWN) and the exit position is estimated by ray-casting the
 * heading through to the opposite edge.
 *
 * ### 2. Boundary-crossing + deferred ML
 * For stairwells with fewer steps, the PDR path may already cross the
 * stairwell boundary before the ML model outputs climbing labels.  The
 * detector records every boundary crossing.  When ML subsequently
 * confirms climbing, the detector uses the stored crossing point as the
 * entry position and fires a retroactive transition.
 *
 * ### 3. Edge-proximity + ML
 * When the user is within [proximityRadius] of a stairwell's entry
 * edge (bottom edge when going up, top edge when going down) and ML
 * threshold is met, a transition fires.  This mirrors the old
 * entrance-proximity approach but uses edge line segments instead of
 * entrance points, giving better coverage for wide stairwells.
 *
 * ### Same-floor stairwells
 * Stairwells whose [StairwellZone.isSameFloor] is true (e.g. 0.3↔0.6
 * within the same floor plan) produce transitions with
 * [StairTransitionEvent.isSameFloor] = true.  The caller should NOT
 * switch floors or reload constraints for these.
 */
class StairwellTransitionDetector(
    /** Consecutive same-direction stair labels needed to trigger. */
    private var entryThreshold: Int = 2,
    /** Minimum ML confidence to accept a label. */
    private val minConfidence: Float = 0.45f,
    /**
     * Maximum distance (campus units) from a stairwell top/bottom edge
     * to trigger proximity-based detection (Mode 3).  Mirrors the old
     * entrance-proximity approach but uses edge segments instead of points.
     */
    private var proximityRadius: Float = 150f,
    /**
     * Maximum age (in label ticks) of a stored boundary crossing before
     * it expires.  Prevents stale crossings from triggering transitions
     * long after the user walked through.
     */
    private val maxCrossingAge: Int = 15
) {

    companion object {
        private const val TAG = "StairDetector"
    }

    // ── Consecutive stair label tracking ────────────────────────────────

    private var consecutiveStairLabel: String? = null
    private var consecutiveStairCount: Int = 0

    // ── Boundary crossing memory ────────────────────────────────────────

    /**
     * Records a PDR step that crossed a stairwell boundary.
     */
    private data class CrossingRecord(
        val zone: StairwellZone,
        val crossingPoint: Offset,
        val heading: Float,
        val tickAge: Int
    )

    /** Stored boundary crossings, newest first. */
    private val pendingCrossings = mutableListOf<CrossingRecord>()
    /** Global tick counter incremented every ML label to age crossings. */
    private var tickCounter: Int = 0

    // ── Settings ────────────────────────────────────────────────────────

    fun updateSettings(entryThreshold: Int? = null, proximityRadius: Float? = null) {
        if (entryThreshold != null) this.entryThreshold = entryThreshold
        if (proximityRadius != null) this.proximityRadius = proximityRadius
    }

    // ── Label feed ──────────────────────────────────────────────────────

    /**
     * Feed every ML classification result here.
     * Low-confidence predictions are silently dropped.
     */
    fun onMotionLabel(label: String, confidence: Float) {
        if (confidence < minConfidence) return

        tickCounter++

        if (label == "upstairs" || label == "downstairs") {
            if (label == consecutiveStairLabel) {
                consecutiveStairCount++
            } else {
                consecutiveStairLabel = label
                consecutiveStairCount = 1
            }
        } else {
            consecutiveStairLabel = null
            consecutiveStairCount = 0
        }

        // Expire old crossings
        pendingCrossings.removeAll { (tickCounter - it.tickAge) > maxCrossingAge }
    }

    // ── Boundary crossing feed ──────────────────────────────────────────

    /**
     * Called by PdrRepository on every normal step to record any boundary
     * crossings.  Should be called with the previous and new positions
     * (before and after the step).
     *
     * @param prevPos Previous position (campus coords).
     * @param newPos  New position after the step.
     * @param heading Current heading at this step.
     * @param zones   All stairwell zones for the campus.
     * @param currentFloorNumber The user's current numeric floor.
     */
    fun recordBoundaryCrossings(
        prevPos: Offset,
        newPos: Offset,
        heading: Float,
        zones: List<StairwellZone>,
        currentFloorNumber: Float
    ) {
        for (zone in zones) {
            if (currentFloorNumber !in zone.floorsConnected) continue

            val crossing = StairwellGeometry.findBoundaryCrossing(prevPos, newPos, zone.boundary)
            if (crossing != null) {
                pendingCrossings.removeAll { it.zone.polygonId == zone.polygonId }
                pendingCrossings.add(0, CrossingRecord(zone, crossing, heading, tickCounter))

                Log.d(TAG, "boundary crossing: polygon=${zone.polygonId} " +
                        "at (${crossing.x.toInt()},${crossing.y.toInt()}) " +
                        "floors=${zone.floorsConnected}")
            }
        }
    }

    // ── Public API ──────────────────────────────────────────────────────

    fun reset() {
        consecutiveStairLabel = null
        consecutiveStairCount = 0
        pendingCrossings.clear()
    }

    /**
     * Checks ML streak and stairwell zone containment/crossing.
     *
     * @param position           User's current position (campus coords).
     * @param heading            User's current heading (radians).
     * @param zones              All stairwell zones.
     * @param currentFloorNumber Numeric floor the user is on.
     */
    fun checkTransition(
        position: Offset,
        heading: Float,
        zones: List<StairwellZone>,
        currentFloorNumber: Float
    ): StairTransitionEvent? {
        if (consecutiveStairCount < entryThreshold) return null

        val goingUp = consecutiveStairLabel == "upstairs"

        Log.d(TAG, "ML threshold met: ${consecutiveStairCount}× \"$consecutiveStairLabel\" " +
                "at (${position.x.toInt()},${position.y.toInt()}) floor=$currentFloorNumber")

        // ── Mode 1: Currently inside a zone polygon ─────────────────
        val insideZone = findContainingZone(position, zones, currentFloorNumber, goingUp)
        if (insideZone != null) {
            Log.d(TAG, "inside polygon ${insideZone.polygonId}")
            return buildTransition(insideZone, position, heading, goingUp)
        }

        // ── Mode 2: Deferred — check stored boundary crossings ──────
        val crossingRecord = findMatchingCrossing(currentFloorNumber, goingUp)
        if (crossingRecord != null) {
            Log.d(TAG, "deferred crossing: polygon=${crossingRecord.zone.polygonId} " +
                    "age=${tickCounter - crossingRecord.tickAge}")
            return buildTransition(
                crossingRecord.zone,
                crossingRecord.crossingPoint,
                crossingRecord.heading,
                goingUp
            )
        }

        // ── Mode 3: Edge-proximity — near a top/bottom edge ─────────
        val nearEdge = findNearestEdgeZone(position, zones, currentFloorNumber, goingUp)
        if (nearEdge != null) {
            Log.d(TAG, "edge proximity: polygon=${nearEdge.polygonId} " +
                    "dist within $proximityRadius")
            return buildTransition(nearEdge, position, heading, goingUp)
        }

        Log.d(TAG, "no zone match for position or stored crossings")
        return null
    }

    // ── Internals ───────────────────────────────────────────────────────

    private fun findContainingZone(
        position: Offset,
        zones: List<StairwellZone>,
        currentFloorNumber: Float,
        goingUp: Boolean
    ): StairwellZone? {
        for (zone in zones) {
            if (currentFloorNumber !in zone.floorsConnected) continue
            if (goingUp && zone.bottomFloorNumber != currentFloorNumber) continue
            if (!goingUp && zone.topFloorNumber != currentFloorNumber) continue

            if (StairwellGeometry.isInsidePolygon(position, zone.boundary)) {
                return zone
            }
        }
        return null
    }

    private fun findMatchingCrossing(
        currentFloorNumber: Float,
        goingUp: Boolean
    ): CrossingRecord? {
        for (record in pendingCrossings) {
            val zone = record.zone
            if (currentFloorNumber !in zone.floorsConnected) continue
            if (goingUp && zone.bottomFloorNumber != currentFloorNumber) continue
            if (!goingUp && zone.topFloorNumber != currentFloorNumber) continue
            return record
        }
        return null
    }

    /**
     * Finds the nearest stairwell zone where the user is within
     * [proximityRadius] of the relevant entry edge (bottom edge when
     * going up, top edge when going down).  Mirrors the old
     * entrance-proximity logic but uses edge line segments.
     */
    private fun findNearestEdgeZone(
        position: Offset,
        zones: List<StairwellZone>,
        currentFloorNumber: Float,
        goingUp: Boolean
    ): StairwellZone? {
        var bestZone: StairwellZone? = null
        var bestDist = Float.MAX_VALUE

        for (zone in zones) {
            if (currentFloorNumber !in zone.floorsConnected) continue
            if (goingUp && zone.bottomFloorNumber != currentFloorNumber) continue
            if (!goingUp && zone.topFloorNumber != currentFloorNumber) continue

            // Check distance to the entry edge (bottom when going up, top when going down)
            val entryEdge = if (goingUp) zone.bottomEdge else zone.topEdge
            val dist = GeometryUtils.distanceToSegment(position, entryEdge.first, entryEdge.second)

            if (dist <= proximityRadius && dist < bestDist) {
                bestDist = dist
                bestZone = zone
            }
        }

        return bestZone
    }

    private fun buildTransition(
        zone: StairwellZone,
        position: Offset,
        heading: Float,
        goingUp: Boolean
    ): StairTransitionEvent {
        val direction = if (goingUp) StairDirection.UP else StairDirection.DOWN

        val entryEdge: Pair<Offset, Offset>
        val exitEdge: Pair<Offset, Offset>
        val originFloor: String
        val destFloor: String

        if (direction == StairDirection.UP) {
            entryEdge = zone.bottomEdge
            exitEdge = zone.topEdge
            originFloor = zone.bottomFloorId
            destFloor = zone.topFloorId
        } else {
            entryEdge = zone.topEdge
            exitEdge = zone.bottomEdge
            originFloor = zone.topFloorId
            destFloor = zone.bottomFloorId
        }

        val startPos = StairwellGeometry.projectOntoEdge(
            position, entryEdge.first, entryEdge.second
        )
        val endPos = StairwellGeometry.estimateExitPoint(
            startPos, heading, exitEdge.first, exitEdge.second
        )

        Log.d(TAG, "TRANSITION: $direction from $originFloor → $destFloor " +
                "start=(${startPos.x.toInt()},${startPos.y.toInt()}) " +
                "end=(${endPos.x.toInt()},${endPos.y.toInt()}) " +
                "sameFloor=${zone.isSameFloor}")

        return StairTransitionEvent(
            zone = zone,
            direction = direction,
            startPosition = startPos,
            endPosition = endPos,
            originFloorId = originFloor,
            destinationFloorId = destFloor,
            isSameFloor = zone.isSameFloor,
            preClimbedSteps = consecutiveStairCount
        )
    }
}
