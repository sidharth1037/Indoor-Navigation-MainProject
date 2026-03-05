package `in`.project.enroute.feature.pdr.correction

import android.util.Log
import androidx.compose.ui.geometry.Offset

/**
 * Stairwell entry detector.
 *
 * Uses [StairwellZone] polygon boundaries and top/bottom edges to detect
 * when the user is entering a stairwell.
 *
 * ## Detection modes (checked in priority order)
 *
 * ### 1. Inside-polygon + ML
 * User is currently inside a [StairwellZone] polygon and the ML
 * threshold is met.  Most accurate — the user's position is projected
 * onto the entry edge.
 *
 * ### 2. Edge-proximity + ML
 * User is within [proximityRadius] of a stairwell's entry edge and
 * the ML threshold is met.
 *
 * ### 3. Boundary-crossing + ML
 * Every step, [recordBoundaryCrossings] records when the movement
 * segment crosses a polygon boundary.  When the ML threshold is met,
 * stored crossings are checked.  Records older than
 * [MAX_CROSSING_AGE_STEPS] steps are lazily evicted.
 *
 * ### Same-floor stairwells
 * Stairwells whose [StairwellZone.isSameFloor] is true produce
 * transitions with [StairTransitionEvent.isSameFloor] = true.
 *
 * ## Performance
 * AABB bounding-box checks on each [StairwellZone] fast-reject zones
 * the user is nowhere near (<4 float comparisons per zone).
 * Boundary-crossing segment checks and polygon containment only run
 * when the AABB overlaps.  Full geometry (edge distance) only runs
 * when the ML threshold is met.
 */
class StairwellTransitionDetector(
    /** Consecutive same-direction stair labels needed to trigger. */
    private var entryThreshold: Int = 2,
    /** Minimum ML confidence to accept a label. */
    private val minConfidence: Float = 0.45f,
    /**
     * Maximum distance (campus units) from a stairwell top/bottom edge
     * to trigger proximity-based detection (Mode 2).  Uses edge line
     * segments for better coverage on wide stairwells.
     */
    private var proximityRadius: Float = 150f
) {

    companion object {
        private const val TAG = "StairDetector"
        /** Maximum number of stored crossing records. */
        private const val MAX_CROSSING_RECORDS = 4
        /** Steps after which a crossing record is considered stale. */
        private const val MAX_CROSSING_AGE_STEPS = 15
    }

    // ── Crossing record ─────────────────────────────────────────────────

    private data class CrossingRecord(
        val zone: StairwellZone,
        val crossingPoint: Offset,
        val heading: Float,
        val stepCount: Long
    )

    /** Fixed-capacity buffer of recent boundary crossings, newest last. */
    private val pendingCrossings = ArrayDeque<CrossingRecord>(MAX_CROSSING_RECORDS)

    /** Monotonic step counter used for age-based eviction. */
    private var stepCounter: Long = 0L

    // ── Consecutive stair label tracking ────────────────────────────────

    private var consecutiveStairLabel: String? = null
    private var consecutiveStairCount: Int = 0

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
    }

    // ── Boundary crossing recording (called every step) ────────────────

    /**
     * Records whether the movement from [prevPos] to [newPos] crossed a
     * stairwell's **top or bottom edge** on the current floor or the base
     * floor below (e.g. floor 1.0 when the user is on 1.5).
     *
     * Only the two labelled edges are tested — not all polygon boundary
     * edges — giving exactly **2 segment-intersection checks per zone**.
     * Zones are pre-filtered by floor and AABB before any geometry runs.
     *
     * @param prevPos            User position before this step.
     * @param newPos             User position after this step.
     * @param heading            Current heading (radians).
     * @param zones              All stairwell zones.
     * @param currentFloorNumber Numeric floor the user is on.
     */
    fun recordBoundaryCrossings(
        prevPos: Offset,
        newPos: Offset,
        heading: Float,
        zones: List<StairwellZone>,
        currentFloorNumber: Float
    ) {
        stepCounter++

        // Also consider zones on the base floor below (e.g. 1.0 when on 1.5)
        val belowFloor = kotlin.math.floor(currentFloorNumber)

        // Pre-compute movement segment AABB once
        val segMinX = minOf(prevPos.x, newPos.x)
        val segMaxX = maxOf(prevPos.x, newPos.x)
        val segMinY = minOf(prevPos.y, newPos.y)
        val segMaxY = maxOf(prevPos.y, newPos.y)

        for (zone in zones) {
            // Floor filter: current floor OR base floor below
            if (currentFloorNumber !in zone.floorsConnected &&
                (belowFloor == currentFloorNumber || belowFloor !in zone.floorsConnected)
            ) continue

            // AABB fast-reject
            if (segMaxX < zone.boundsMinX || segMinX > zone.boundsMaxX ||
                segMaxY < zone.boundsMinY || segMinY > zone.boundsMaxY) continue

            // Only check top and bottom edges (2 segment checks, not all boundary edges)
            val topHit = GeometryUtils.segmentIntersection(
                prevPos, newPos, zone.topEdge.first, zone.topEdge.second
            )
            val botHit = GeometryUtils.segmentIntersection(
                prevPos, newPos, zone.bottomEdge.first, zone.bottomEdge.second
            )

            // Pick the closest crossing to prevPos
            val crossing: Offset
            if (topHit != null && botHit != null) {
                val tdx = topHit.x - prevPos.x; val tdy = topHit.y - prevPos.y
                val bdx = botHit.x - prevPos.x; val bdy = botHit.y - prevPos.y
                crossing = if (tdx * tdx + tdy * tdy < bdx * bdx + bdy * bdy) topHit else botHit
            } else if (topHit != null) {
                crossing = topHit
            } else if (botHit != null) {
                crossing = botHit
            } else {
                continue
            }

            // Remove any existing record for this zone (keep latest only)
            pendingCrossings.removeAll { it.zone.polygonId == zone.polygonId }
            // Evict oldest if at capacity
            if (pendingCrossings.size >= MAX_CROSSING_RECORDS) {
                pendingCrossings.removeFirst()
            }
            pendingCrossings.addLast(
                CrossingRecord(zone, crossing, heading, stepCounter)
            )
            Log.d(TAG, "edge crossing recorded: polygon=${zone.polygonId} " +
                    "at (${crossing.x.toInt()},${crossing.y.toInt()}) step=$stepCounter")
        }
    }

    // ── Public API ──────────────────────────────────────────────────────

    fun reset() {
        consecutiveStairLabel = null
        consecutiveStairCount = 0
        pendingCrossings.clear()
    }

    /**
     * Checks whether the ML threshold is met and the user is in or near
     * a stairwell zone.  Geometry checks only run when the ML streak is
     * satisfied, keeping per-step overhead near zero.
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
            Log.d(TAG, "Mode 1 hit: inside polygon ${insideZone.polygonId}")
            return buildTransition(insideZone, position, heading, goingUp)
        }

        // ── Mode 2: Edge-proximity — near a top/bottom edge ─────────
        val nearEdge = findNearestEdgeZone(position, zones, currentFloorNumber, goingUp)
        if (nearEdge != null) {
            Log.d(TAG, "Mode 2 hit: edge proximity polygon=${nearEdge.polygonId} " +
                    "dist within $proximityRadius")
            return buildTransition(nearEdge, position, heading, goingUp)
        }

        // ── Mode 3: Stored boundary crossing ────────────────────────
        val crossingRecord = findMatchingCrossing(currentFloorNumber, goingUp)
        if (crossingRecord != null) {
            Log.d(TAG, "Mode 3 hit: stored crossing polygon=${crossingRecord.zone.polygonId} " +
                    "age=${stepCounter - crossingRecord.stepCount} steps")
            return buildTransition(
                crossingRecord.zone, crossingRecord.crossingPoint,
                crossingRecord.heading, goingUp
            )
        }

        Log.d(TAG, "no zone match for position")
        return null
    }

    // ── Internals ───────────────────────────────────────────────────────

    /**
     * Finds the most recent crossing record matching the current floor and
     * direction.  Lazily evicts records older than [MAX_CROSSING_AGE_STEPS].
     */
    private fun findMatchingCrossing(
        currentFloorNumber: Float,
        goingUp: Boolean
    ): CrossingRecord? {
        // Lazy eviction of stale records
        pendingCrossings.removeAll { (stepCounter - it.stepCount) > MAX_CROSSING_AGE_STEPS }

        // Search newest-first (iterate in reverse)
        for (i in pendingCrossings.indices.reversed()) {
            val record = pendingCrossings[i]
            val zone = record.zone
            if (currentFloorNumber !in zone.floorsConnected) continue
            if (goingUp && zone.bottomFloorNumber != currentFloorNumber) continue
            if (!goingUp && zone.topFloorNumber != currentFloorNumber) continue
            return record
        }
        return null
    }

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
            // AABB fast-reject
            if (position.x < zone.boundsMinX || position.x > zone.boundsMaxX ||
                position.y < zone.boundsMinY || position.y > zone.boundsMaxY) continue

            if (StairwellGeometry.isInsidePolygon(position, zone.boundary)) {
                return zone
            }
        }
        return null
    }

    /**
     * Finds the nearest stairwell zone where the user is within
     * [proximityRadius] of the relevant entry edge (bottom edge when
     * going up, top edge when going down).
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
            // AABB fast-reject (inflated by proximityRadius)
            if (position.x < zone.boundsMinX - proximityRadius ||
                position.x > zone.boundsMaxX + proximityRadius ||
                position.y < zone.boundsMinY - proximityRadius ||
                position.y > zone.boundsMaxY + proximityRadius) continue

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
