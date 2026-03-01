package `in`.project.enroute.feature.pdr.correction

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pure 2-D geometry helpers used throughout the correction pipeline.
 * All operations work in the campus-wide coordinate system.
 *
 * Heading convention (same as PdrRepository):
 *   0 = North (screen-up / negative-Y), positive = clockwise.
 */
object GeometryUtils {

    // ── Segment / intersection ──────────────────────────────────────────────

    /**
     * Returns the intersection point of segments A→B and C→D, or `null`
     * if they do not intersect (parallel, or intersection outside [0,1]).
     */
    fun segmentIntersection(a: Offset, b: Offset, c: Offset, d: Offset): Offset? {
        val dx1 = b.x - a.x
        val dy1 = b.y - a.y
        val dx2 = d.x - c.x
        val dy2 = d.y - c.y

        val denom = dx1 * dy2 - dy1 * dx2
        if (abs(denom) < 1e-10f) return null // parallel / collinear

        val t = ((c.x - a.x) * dy2 - (c.y - a.y) * dx2) / denom
        val u = ((c.x - a.x) * dy1 - (c.y - a.y) * dx1) / denom

        if (t < 0f || t > 1f || u < 0f || u > 1f) return null

        return Offset(a.x + t * dx1, a.y + t * dy1)
    }

    // ── Point-to-segment ────────────────────────────────────────────────────

    /**
     * Closest point on segment A→B to point P.
     */
    fun closestPointOnSegment(p: Offset, a: Offset, b: Offset): Offset {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-10f) return a // degenerate segment

        val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq
        val clamped = t.coerceIn(0f, 1f)
        return Offset(a.x + clamped * dx, a.y + clamped * dy)
    }

    /**
     * Euclidean distance from point P to segment A→B.
     */
    fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
        return distance(p, closestPointOnSegment(p, a, b))
    }

    // ── Basic distance / vector ─────────────────────────────────────────────

    /** Euclidean distance between two points. */
    fun distance(a: Offset, b: Offset): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }

    // ── Angle helpers ───────────────────────────────────────────────────────

    /** Normalises an angle to the **(-π, π]** range. */
    fun normalizeAngle(angle: Float): Float {
        var a = angle % (2f * PI.toFloat())
        if (a > PI.toFloat()) a -= 2f * PI.toFloat()
        if (a <= -PI.toFloat()) a += 2f * PI.toFloat()
        return a
    }

    /**
     * Shortest signed angular difference **from** → **to** (radians).
     * Positive = clockwise turn, negative = counter-clockwise.
     */
    fun angleDifference(from: Float, to: Float): Float {
        return normalizeAngle(to - from)
    }

    // ── Projection ──────────────────────────────────────────────────────────

    /**
     * Projects [movement] onto the direction of wall segment [wallStart]→[wallEnd].
     * Returns the component of the movement vector along the wall.
     */
    fun projectOntoWall(movement: Offset, wallStart: Offset, wallEnd: Offset): Offset {
        val wallDx = wallEnd.x - wallStart.x
        val wallDy = wallEnd.y - wallStart.y
        val wallLenSq = wallDx * wallDx + wallDy * wallDy
        if (wallLenSq < 1e-10f) return Offset.Zero

        val dot = (movement.x * wallDx + movement.y * wallDy) / wallLenSq
        return Offset(dot * wallDx, dot * wallDy)
    }

    // ── Heading / direction ─────────────────────────────────────────────────

    /**
     * Heading (radians) from [from] to [to].
     * Follows PDR convention: 0 = North (negative Y), positive = clockwise.
     */
    fun directionAngle(from: Offset, to: Offset): Float {
        val dx = to.x - from.x
        val dy = to.y - from.y
        // atan2(east, north↑) → east-positive; negate dy because screen-Y is down
        return atan2(dx, -dy)
    }
}
