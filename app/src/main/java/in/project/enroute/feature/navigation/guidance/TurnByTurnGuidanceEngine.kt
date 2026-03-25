package `in`.project.enroute.feature.navigation.guidance

import androidx.compose.ui.geometry.Offset
import `in`.project.enroute.feature.navigation.data.MultiFloorPath
import `in`.project.enroute.feature.navigation.data.PathTransition
import `in`.project.enroute.feature.navigation.data.TransitionDirection
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

class TurnByTurnGuidanceEngine(
    private val config: GuidanceConfig = GuidanceConfig()
) {

    fun compute(input: GuidanceInput): TurnByTurnInstruction {
        if (input.path.isEmpty) {
            return TurnByTurnInstruction(text = "", type = GuidanceType.IDLE, updatedAtMs = input.nowMs)
        }
        if (input.forceRerouteMessage) {
            return TurnByTurnInstruction(
                text = "Rerouting, hold on.",
                type = GuidanceType.REROUTING,
                updatedAtMs = input.nowMs
            )
        }

        val floorSegments = input.path.segments.filter { it.floorId == input.currentFloorId }
        if (floorSegments.isEmpty()) {
            return TurnByTurnInstruction(
                text = "Continue to the highlighted route.",
                type = GuidanceType.ALIGN,
                updatedAtMs = input.nowMs
            )
        }

        val nearest = findNearestPoint(input.path, input.userPosition, input.currentFloorId)
            ?: return TurnByTurnInstruction(
                text = "Continue to the highlighted route.",
                type = GuidanceType.ALIGN,
                updatedAtMs = input.nowMs
            )

        val destination = input.path.segments.lastOrNull()?.points?.lastOrNull()
        if (destination != null && input.path.segments.last().floorId == input.currentFloorId) {
            val goalDist = distance(input.userPosition, destination)
            if (goalDist <= config.arrivalDistanceUnits) {
                return TurnByTurnInstruction(
                    text = "You are near your destination.",
                    type = GuidanceType.ARRIVAL,
                    updatedAtMs = input.nowMs
                )
            }
        }

        val pathBearing = computePathBearing(input.path, nearest)
        val headingDeg = Math.toDegrees(input.headingRadians.toDouble()).toFloat()
        val pathDeg = Math.toDegrees(pathBearing.toDouble()).toFloat()
        val headingErrorDeg = signedAngleDegrees(headingDeg, pathDeg)

        val upcomingTransition = findUpcomingTransition(input.path, nearest, input.userPosition)
        if (upcomingTransition != null) {
            val transitionText = stairInstruction(upcomingTransition)
            if (transitionText != null) {
                return TurnByTurnInstruction(
                    text = transitionText,
                    type = GuidanceType.STAIR,
                    distanceMeters = roundedMeters(upcomingTransition.distanceUnits),
                    updatedAtMs = input.nowMs
                )
            }
        }

        if (abs(headingErrorDeg) > config.fieldOfViewHalfAngleDeg) {
            return TurnByTurnInstruction(
                text = alignmentInstruction(headingErrorDeg),
                type = GuidanceType.ALIGN,
                updatedAtMs = input.nowMs
            )
        }

        val upcomingTurn = findUpcomingTurn(input.path, nearest, input.currentFloorId)
        if (upcomingTurn != null && upcomingTurn.distanceUnits <= config.nearTurnDistanceUnits) {
            return TurnByTurnInstruction(
                text = turnInstruction(upcomingTurn.signedAngleDeg),
                type = GuidanceType.TURN,
                updatedAtMs = input.nowMs
            )
        }

        val straightDistanceUnits = when {
            upcomingTurn != null -> upcomingTurn.distanceUnits
            upcomingTransition != null -> upcomingTransition.distanceUnits
            else -> distanceToFloorSegmentEnd(input.path, nearest)
        }

        val meters = roundedMeters(straightDistanceUnits)
        return TurnByTurnInstruction(
            text = "Walk straight for around $meters m.",
            type = GuidanceType.STRAIGHT,
            distanceMeters = meters,
            updatedAtMs = input.nowMs
        )
    }

    private data class NearestPathPoint(
        val segmentIndex: Int,
        val pointIndex: Int,
        val point: Offset
    )

    private fun findNearestPoint(
        path: MultiFloorPath,
        userPosition: Offset,
        floorId: String
    ): NearestPathPoint? {
        var bestSegmentIndex = -1
        var bestPointIndex = -1
        var bestDistSq = Float.MAX_VALUE

        path.segments.forEachIndexed { segIdx, segment ->
            if (segment.floorId != floorId) return@forEachIndexed
            segment.points.forEachIndexed { ptIdx, pt ->
                val dx = pt.x - userPosition.x
                val dy = pt.y - userPosition.y
                val distSq = dx * dx + dy * dy
                if (distSq < bestDistSq) {
                    bestDistSq = distSq
                    bestSegmentIndex = segIdx
                    bestPointIndex = ptIdx
                }
            }
        }

        if (bestSegmentIndex < 0) return null
        return NearestPathPoint(
            segmentIndex = bestSegmentIndex,
            pointIndex = bestPointIndex,
            point = path.segments[bestSegmentIndex].points[bestPointIndex]
        )
    }

    private fun computePathBearing(path: MultiFloorPath, nearest: NearestPathPoint): Float {
        val segment = path.segments[nearest.segmentIndex]
        val startIdx = nearest.pointIndex.coerceAtMost(segment.points.lastIndex)
        val maxIdx = (nearest.pointIndex + config.lookaheadPoints)
            .coerceAtMost(segment.points.lastIndex)

        var target = segment.points[maxIdx]
        for (i in startIdx..maxIdx) {
            val candidate = segment.points[i]
            if (distance(candidate, nearest.point) > 1f) {
                target = candidate
                break
            }
        }

        return headingFromVector(
            dx = target.x - nearest.point.x,
            dy = target.y - nearest.point.y
        )
    }

    private fun findUpcomingTurn(
        path: MultiFloorPath,
        nearest: NearestPathPoint,
        currentFloorId: String
    ): UpcomingTurn? {
        val currentSegment = path.segments[nearest.segmentIndex]
        if (currentSegment.floorId != currentFloorId) return null

        val points = currentSegment.points
        if (points.size < 3) return null

        val start = nearest.pointIndex.coerceAtLeast(1)
        for (i in start until points.lastIndex) {
            val prev = points[i - 1]
            val curr = points[i]
            val next = points[i + 1]
            val b1 = Math.toDegrees(
                headingFromVector(
                    dx = curr.x - prev.x,
                    dy = curr.y - prev.y
                ).toDouble()
            ).toFloat()
            val b2 = Math.toDegrees(
                headingFromVector(
                    dx = next.x - curr.x,
                    dy = next.y - curr.y
                ).toDouble()
            ).toFloat()
            val delta = signedAngleDegrees(b1, b2)
            if (abs(delta) >= config.minTurnAngleDeg) {
                val turnDistance = distanceAlongSegment(points, nearest.pointIndex, i)
                return UpcomingTurn(delta, turnDistance)
            }
        }
        return null
    }

    private fun findUpcomingTransition(
        path: MultiFloorPath,
        nearest: NearestPathPoint,
        userPosition: Offset
    ): UpcomingTransition? {
        val candidates = path.transitions
            .filter { it.fromSegmentIndex >= nearest.segmentIndex }
            .sortedBy { it.fromSegmentIndex }
        val nearestTransition = candidates.firstOrNull() ?: return null

        val units = distanceToTransitionEntry(path, nearest, userPosition, nearestTransition)
        return UpcomingTransition(nearestTransition, units)
    }

    private fun distanceToTransitionEntry(
        path: MultiFloorPath,
        nearest: NearestPathPoint,
        userPosition: Offset,
        transition: PathTransition
    ): Float {
        val segment = path.segments.getOrNull(transition.fromSegmentIndex) ?: return Float.MAX_VALUE
        if (nearest.segmentIndex > transition.fromSegmentIndex) return Float.MAX_VALUE

        val targetIdx = nearestPointIndex(segment.points, transition.entryPoint)
        if (nearest.segmentIndex == transition.fromSegmentIndex) {
            val fromNearestPoint = distanceAlongSegment(segment.points, nearest.pointIndex, targetIdx)
            return distance(userPosition, nearest.point) + fromNearestPoint
        }

        var total = distance(userPosition, nearest.point)
        val currentSegment = path.segments[nearest.segmentIndex]
        total += distanceAlongSegment(currentSegment.points, nearest.pointIndex, currentSegment.points.lastIndex)

        for (i in (nearest.segmentIndex + 1) until transition.fromSegmentIndex) {
            total += distanceAlongSegment(path.segments[i].points, 0, path.segments[i].points.lastIndex)
        }

        total += distanceAlongSegment(segment.points, 0, targetIdx)
        return total
    }

    private fun distanceToFloorSegmentEnd(path: MultiFloorPath, nearest: NearestPathPoint): Float {
        val segment = path.segments[nearest.segmentIndex]
        return distanceAlongSegment(segment.points, nearest.pointIndex, segment.points.lastIndex)
    }

    private fun stairInstruction(upcoming: UpcomingTransition): String? {
        return when {
            upcoming.distanceUnits <= config.stairFinalDistanceUnits -> {
                when (upcoming.transition.direction) {
                    TransitionDirection.UP -> "Go upstairs to the next floor."
                    TransitionDirection.DOWN -> "Go downstairs to the previous floor."
                }
            }
            upcoming.distanceUnits <= config.stairEarlyDistanceUnits -> {
                val meters = roundedMeters(upcoming.distanceUnits)
                val action = when (upcoming.transition.direction) {
                    TransitionDirection.UP -> "go upstairs to the next floor"
                    TransitionDirection.DOWN -> "go downstairs to the previous floor"
                }
                "In around $meters meters, $action."
            }
            else -> null
        }
    }

    private fun alignmentInstruction(headingErrorDeg: Float): String {
        val right = headingErrorDeg > 0f
        val dir = if (right) "right" else "left"
        val absAngle = abs(headingErrorDeg)
        return when {
            absAngle >= 150f -> "Turn around."
            absAngle >= 80f -> "Turn $dir."
            absAngle >= 35f -> "Turn slightly $dir."
            else -> "Keep straight."
        }
    }

    private fun turnInstruction(angleDeg: Float): String {
        val right = angleDeg > 0f
        val dir = if (right) "right" else "left"
        val absAngle = abs(angleDeg)
        return when {
            absAngle >= 140f -> "Turn around."
            absAngle >= 95f -> "Turn sharply $dir."
            absAngle >= 35f -> "Turn $dir."
            else -> "Turn slightly $dir."
        }
    }

    private fun roundedMeters(distanceUnits: Float): Int {
        val rawMeters = (distanceUnits * 0.02f).coerceAtLeast(0f)
        val bucket = when {
            rawMeters <= config.distanceSmallUpperBoundMeters -> config.distanceBucketSmallMeters
            rawMeters <= config.distanceMediumUpperBoundMeters -> config.distanceBucketMediumMeters
            else -> config.distanceBucketLargeMeters
        }
        val rounded = (rawMeters / bucket).roundToInt() * bucket
        return rounded.coerceAtLeast(bucket)
    }

    private fun nearestPointIndex(points: List<Offset>, target: Offset): Int {
        var bestIdx = 0
        var bestDistSq = Float.MAX_VALUE
        points.forEachIndexed { index, point ->
            val dx = point.x - target.x
            val dy = point.y - target.y
            val distSq = dx * dx + dy * dy
            if (distSq < bestDistSq) {
                bestDistSq = distSq
                bestIdx = index
            }
        }
        return bestIdx
    }

    private fun distanceAlongSegment(points: List<Offset>, startIndex: Int, endIndex: Int): Float {
        if (points.size < 2) return 0f
        val s = startIndex.coerceIn(0, points.lastIndex)
        val e = endIndex.coerceIn(0, points.lastIndex)
        if (e <= s) return 0f

        var total = 0f
        for (i in (s + 1)..e) {
            total += distance(points[i - 1], points[i])
        }
        return total
    }

    private fun distance(a: Offset, b: Offset): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun signedAngleDegrees(fromDeg: Float, toDeg: Float): Float {
        var diff = (toDeg - fromDeg + 540f) % 360f - 180f
        if (diff <= -180f) diff += 360f
        return diff
    }

    /**
     * Converts a 2D vector into the same heading convention used by PDR:
     * 0 rad = north (screen up), +pi/2 = east (screen right).
     */
    private fun headingFromVector(dx: Float, dy: Float): Float {
        return atan2(dx, -dy)
    }
}
