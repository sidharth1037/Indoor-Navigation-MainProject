package `in`.project.enroute.feature.pdr.correction

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure 2-D geometry helpers specific to stairwell boundary operations.
 *
 * Works in campus-wide coordinates.  All stairwell polygons and edges
 * should already be transformed before calling these methods.
 */
object StairwellGeometry {

    // ── Point-in-polygon ────────────────────────────────────────────────────

    /**
     * Ray-casting point-in-polygon test.
     *
     * Casts a horizontal ray to the right from [point] and counts how many
     * edges of [polygon] it crosses.  Odd count ⇒ inside.
     *
     * @param point   The point to test.
     * @param polygon Ordered list of polygon vertices (campus coords).
     * @return `true` if [point] lies inside the polygon.
     */
    fun isInsidePolygon(point: Offset, polygon: List<Offset>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            if ((pi.y > point.y) != (pj.y > point.y) &&
                point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    // ── Edge projection ─────────────────────────────────────────────────────

    /**
     * Projects [point] onto the line segment [edgeStart]→[edgeEnd] and
     * returns the closest point on that segment.
     */
    fun projectOntoEdge(point: Offset, edgeStart: Offset, edgeEnd: Offset): Offset {
        return GeometryUtils.closestPointOnSegment(point, edgeStart, edgeEnd)
    }

    /**
     * Estimates the exit point on [targetEdge] by casting a ray from
     * [entryPoint] along [heading] (PDR convention: 0 = North, CW+).
     *
     * If the ray doesn't intersect the target edge (e.g. heading is
     * perpendicular), falls back to projecting [entryPoint] onto the edge.
     *
     * @param entryPoint  Start of the ray (user position at entry).
     * @param heading     Heading in radians (0 = N, CW+).
     * @param edgeStart   First endpoint of the target edge.
     * @param edgeEnd     Second endpoint of the target edge.
     * @return The estimated exit point on the target edge.
     */
    fun estimateExitPoint(
        entryPoint: Offset,
        heading: Float,
        edgeStart: Offset,
        edgeEnd: Offset
    ): Offset {
        // Build a ray from entryPoint along heading.
        // Heading convention: 0 = North (negative Y), positive = clockwise.
        val dx = sin(heading)
        val dy = -cos(heading)

        // Extend the ray far enough to guarantee intersection (10000 units).
        val rayEnd = Offset(entryPoint.x + dx * 10000f, entryPoint.y + dy * 10000f)

        // Try segment–segment intersection.
        val hit = GeometryUtils.segmentIntersection(entryPoint, rayEnd, edgeStart, edgeEnd)
        if (hit != null) return hit

        // Fallback: project onto the edge.
        return projectOntoEdge(entryPoint, edgeStart, edgeEnd)
    }

    // ── Segment crossing ────────────────────────────────────────────────────

    /**
     * Checks whether the movement from [prevPos] to [newPos] crosses
     * **any** edge of the given [polygon].
     *
     * @return The intersection point closest to [prevPos], or `null` if
     *         no crossing occurred.
     */
    fun findBoundaryCrossing(
        prevPos: Offset,
        newPos: Offset,
        polygon: List<Offset>
    ): Offset? {
        if (polygon.size < 3) return null

        var bestHit: Offset? = null
        var bestDistSq = Float.MAX_VALUE

        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            val hit = GeometryUtils.segmentIntersection(prevPos, newPos, a, b)
            if (hit != null) {
                val dx = hit.x - prevPos.x
                val dy = hit.y - prevPos.y
                val dSq = dx * dx + dy * dy
                if (dSq < bestDistSq) {
                    bestDistSq = dSq
                    bestHit = hit
                }
            }
        }

        return bestHit
    }

    // ── Edge midpoint ───────────────────────────────────────────────────────

    /**
     * Returns the midpoint of a line segment.
     */
    fun edgeMidpoint(a: Offset, b: Offset): Offset {
        return Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    }

    /**
     * Returns the length of a line segment.
     */
    fun edgeLength(a: Offset, b: Offset): Float {
        return GeometryUtils.distance(a, b)
    }
}
