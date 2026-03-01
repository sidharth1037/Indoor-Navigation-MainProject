package `in`.project.enroute.feature.pdr.correction

import androidx.compose.ui.geometry.Offset

/**
 * Prevents the PDR path from penetrating walls.
 *
 * Algorithm per step:
 *  1. Cast the movement vector (from → to) against all nearby walls.
 *  2. If an intersection is found:
 *     a. Clamp the position to just before the wall (by [CorrectionConfig.wallEpsilon]).
 *     b. Project the remaining movement along the wall direction (slide).
 *  3. Repeat up to [CorrectionConfig.maxWallIterations] times in case the
 *     slide runs into another wall.
 *
 * A small heading correction is accumulated from wall slides so that future
 * steps gradually align with the physical corridor.
 */
class WallConstraint(private val config: CorrectionConfig = CorrectionConfig()) {

    /**
     * Constrains a single movement from [from] to [to].
     *
     * @param from  Last committed (or anchor) position.
     * @param to    Raw target position for this step.
     * @param walls Nearby walls already in campus coordinates.
     * @return      The constrained position together with metadata.
     */
    fun constrain(
        from: Offset,
        to: Offset,
        walls: List<CampusWall>
    ): WallConstraintResult {

        if (walls.isEmpty()) {
            return WallConstraintResult(
                constrainedPosition = to,
                wasConstrained = false
            )
        }

        var currentFrom = from
        var currentTo = to
        var wasConstrained = false
        var totalHeadingCorrection = 0f

        repeat(config.maxWallIterations) {

            val hit = findFirstIntersection(currentFrom, currentTo, walls)
                ?: return WallConstraintResult(currentTo, wasConstrained, totalHeadingCorrection)

            wasConstrained = true

            // ── 1. Stop just before the wall ────────────────────────────────
            val dist = GeometryUtils.distance(currentFrom, hit.point)
            val epsilon = if (dist > config.wallEpsilon) config.wallEpsilon else dist * 0.5f

            val stoppedPosition = if (dist > epsilon) {
                val ratio = (dist - epsilon) / dist
                Offset(
                    currentFrom.x + (hit.point.x - currentFrom.x) * ratio,
                    currentFrom.y + (hit.point.y - currentFrom.y) * ratio
                )
            } else {
                currentFrom // too close – stay put
            }

            // ── 2. Slide remaining movement along the wall ──────────────────
            val remaining = Offset(
                currentTo.x - hit.point.x,
                currentTo.y - hit.point.y
            )
            val slide = GeometryUtils.projectOntoWall(
                remaining,
                hit.wall.start,
                hit.wall.end
            )

            // ── 3. Derive heading correction from the slide ─────────────────
            val slideTarget = Offset(stoppedPosition.x + slide.x, stoppedPosition.y + slide.y)
            if (GeometryUtils.distance(stoppedPosition, slideTarget) > 0.5f) {
                val originalHeading = GeometryUtils.directionAngle(currentFrom, currentTo)
                val newHeading = GeometryUtils.directionAngle(stoppedPosition, slideTarget)
                totalHeadingCorrection += GeometryUtils.angleDifference(
                    originalHeading, newHeading
                ) * config.headingCorrectionFactor
            }

            // Prepare for potential next iteration (slide into another wall)
            currentFrom = stoppedPosition
            currentTo = slideTarget
        }

        return WallConstraintResult(currentTo, wasConstrained, totalHeadingCorrection)
    }

    // ── Internals ───────────────────────────────────────────────────────────

    /**
     * Finds the wall intersection closest to [from] along the segment from→to.
     */
    private fun findFirstIntersection(
        from: Offset,
        to: Offset,
        walls: List<CampusWall>
    ): WallIntersection? {
        var closest: WallIntersection? = null
        var minDist = Float.MAX_VALUE

        for (wall in walls) {
            val point = GeometryUtils.segmentIntersection(from, to, wall.start, wall.end)
            if (point != null) {
                val dist = GeometryUtils.distance(from, point)
                if (dist < minDist) {
                    minDist = dist
                    closest = WallIntersection(point, wall)
                }
            }
        }
        return closest
    }

    /** Internal struct for an intersection hit. */
    private data class WallIntersection(
        val point: Offset,
        val wall: CampusWall
    )
}
