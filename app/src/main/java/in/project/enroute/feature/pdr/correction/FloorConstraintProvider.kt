package `in`.project.enroute.feature.pdr.correction

import androidx.compose.ui.geometry.Offset
import `in`.project.enroute.data.model.FloorPlanData
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Supplies campus-transformed walls and entrances for the active floor.
 *
 * Coordinate pipeline:
 *   floor-local → **scale** → **rotate** → **translate** (building offset) → campus-wide.
 *
 * Call [loadFloor] once per floor change; the class caches the transformed
 * geometry for fast spatial queries via [getWallsNear] / [getEntrancesNear].
 */
class FloorConstraintProvider {

    private var campusWalls: List<CampusWall> = emptyList()
    private var campusEntrances: List<CampusEntrance> = emptyList()

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Transforms and caches all walls/entrances from the given floor.
     *
     * @param floorPlanData  Floor data containing raw walls and entrances.
     * @param scale          Building metadata scale.
     * @param rotationDegrees Building metadata rotation (degrees clockwise).
     * @param offsetX        Building `relativePosition.x`.
     * @param offsetY        Building `relativePosition.y`.
     */
    fun loadFloor(
        floorPlanData: FloorPlanData,
        scale: Float,
        rotationDegrees: Float,
        offsetX: Float,
        offsetY: Float
    ) {
        campusWalls = floorPlanData.walls.map { wall ->
            val (sx, sy) = rawToCampus(wall.x1, wall.y1, scale, rotationDegrees, offsetX, offsetY)
            val (ex, ey) = rawToCampus(wall.x2, wall.y2, scale, rotationDegrees, offsetX, offsetY)
            CampusWall(start = Offset(sx, sy), end = Offset(ex, ey))
        }

        campusEntrances = floorPlanData.entrances.map { entrance ->
            val (cx, cy) = rawToCampus(entrance.x, entrance.y, scale, rotationDegrees, offsetX, offsetY)
            CampusEntrance(id = entrance.id, position = Offset(cx, cy), name = entrance.name)
        }
    }

    /** All walls within [radius] units of [position]. */
    fun getWallsNear(position: Offset, radius: Float): List<CampusWall> =
        campusWalls.filter { wall ->
            GeometryUtils.distanceToSegment(position, wall.start, wall.end) <= radius
        }

    /**
     * All entrances within [radius] units of [position].
     *
     * @param heading          If non-null, only entrances roughly in this
     *                         direction are returned.
     * @param headingTolerance Maximum angle deviation (rad) from [heading].
     */
    fun getEntrancesNear(
        position: Offset,
        radius: Float,
        heading: Float? = null,
        headingTolerance: Float = 0.785f
    ): List<CampusEntrance> =
        campusEntrances.filter { entrance ->
            val dist = GeometryUtils.distance(position, entrance.position)
            if (dist > radius) return@filter false

            if (heading != null && dist > 1f) {
                val dir = GeometryUtils.directionAngle(position, entrance.position)
                abs(GeometryUtils.angleDifference(heading, dir)) <= headingTolerance
            } else {
                true
            }
        }

    /** Unfiltered list of all campus-coordinate walls. */
    fun getAllWalls(): List<CampusWall> = campusWalls

    /** Unfiltered list of all campus-coordinate entrances. */
    fun getAllEntrances(): List<CampusEntrance> = campusEntrances

    /** `true` after [loadFloor] has been called with non-empty wall data. */
    fun isLoaded(): Boolean = campusWalls.isNotEmpty()

    /** Discards all cached data. */
    fun clear() {
        campusWalls = emptyList()
        campusEntrances = emptyList()
    }

    // ── Coordinate transform ────────────────────────────────────────────────

    /**
     * Replicates the `rawToCampus` transform used elsewhere in the app
     * (NavigationViewModel, FloorPlanCanvas, etc.).
     *
     * Pipeline: **scale → rotate → offset**.
     */
    private fun rawToCampus(
        x: Float, y: Float,
        scale: Float, rotationDegrees: Float,
        offsetX: Float, offsetY: Float
    ): Pair<Float, Float> {
        val sx = x * scale
        val sy = y * scale
        val rad = Math.toRadians(rotationDegrees.toDouble())
        val rx = (sx * cos(rad) - sy * sin(rad)).toFloat()
        val ry = (sx * sin(rad) + sy * cos(rad)).toFloat()
        return Pair(rx + offsetX, ry + offsetY)
    }
}
