package `in`.project.enroute.feature.pdr.correction

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pre-transformed building boundary data in campus-wide coordinates.
 * One instance per building.
 *
 * @param buildingId  e.g. "main_block"
 * @param floorId     e.g. "floor_1" (first floor assumed for now)
 * @param polygons    Boundary polygons already in campus-wide space.
 *                    A point is "inside" if it's inside any polygon.
 * @param constraintData  Floor constraint data for this building (walls/entrances).
 */
data class CampusBuilding(
    val buildingId: String,
    val floorId: String,
    val polygons: List<List<Pair<Float, Float>>>,
    val constraintData: FloorConstraintData
)

/**
 * Result of a building detection check.
 */
data class BuildingDetectionResult(
    /** Building ID the user is currently inside, or null if outdoors / between buildings. */
    val buildingId: String?,
    /** Floor ID the user is on, or null if outdoors. */
    val floorId: String?,
    /** True if the building/floor changed compared to the previous check. */
    val changed: Boolean,
    /** If changed AND the user entered a building, provides the constraint data for that floor. */
    val newConstraintData: FloorConstraintData? = null
)

/**
 * Continuously detects which building (if any) contains the user's
 * current position.
 *
 * Call [loadBuildings] once with all campus buildings (pre-transformed
 * boundary polygons in campus-wide coordinates). Then call [detect] on
 * every position update.
 *
 * Uses the standard ray-casting point-in-polygon algorithm.
 */
class BuildingDetector {

    private var buildings: List<CampusBuilding> = emptyList()

    /** Last detected building ID (null = outdoors). */
    private var _currentBuildingId: String? = null
    val currentBuildingId: String? get() = _currentBuildingId

    /** Last detected floor ID (null = outdoors). */
    private var _currentFloorId: String? = null
    val currentFloorId: String? get() = _currentFloorId

    // ── Setup ───────────────────────────────────────────────────────────────

    /**
     * Loads pre-transformed building data. Call once when campus data is ready.
     */
    fun loadBuildings(campusBuildings: List<CampusBuilding>) {
        buildings = campusBuildings
    }

    /**
     * Convenience to set the initial building/floor (e.g. at origin time)
     * without running a full polygon check.
     */
    fun setInitial(buildingId: String?, floorId: String?) {
        _currentBuildingId = buildingId
        _currentFloorId = floorId
    }

    /** Clears all state. */
    fun clear() {
        buildings = emptyList()
        _currentBuildingId = null
        _currentFloorId = null
    }

    /** True when building data has been loaded. */
    fun isLoaded(): Boolean = buildings.isNotEmpty()

    // ── Detection ───────────────────────────────────────────────────────────

    /**
     * Checks which building contains [position] and returns a result
     * indicating whether the building/floor changed.
     */
    fun detect(position: Offset): BuildingDetectionResult {
        val previousBuildingId = _currentBuildingId
        val previousFloorId = _currentFloorId

        var foundBuildingId: String? = null
        var foundFloorId: String? = null
        var foundConstraintData: FloorConstraintData? = null

        for (building in buildings) {
            if (isInsideBuilding(position, building)) {
                foundBuildingId = building.buildingId
                foundFloorId = building.floorId
                foundConstraintData = building.constraintData
                break
            }
        }

        _currentBuildingId = foundBuildingId
        _currentFloorId = foundFloorId

        val changed = foundBuildingId != previousBuildingId || foundFloorId != previousFloorId

        return BuildingDetectionResult(
            buildingId = foundBuildingId,
            floorId = foundFloorId,
            changed = changed,
            newConstraintData = if (changed) foundConstraintData else null
        )
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private fun isInsideBuilding(point: Offset, building: CampusBuilding): Boolean {
        return building.polygons.any { polygon ->
            isPointInPolygon(point.x, point.y, polygon)
        }
    }

    /**
     * Standard ray-casting point-in-polygon test.
     * Matches the implementation in FloorPlanViewModel / FloorPlanCanvas.
     */
    private fun isPointInPolygon(x: Float, y: Float, polygon: List<Pair<Float, Float>>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].first; val yi = polygon[i].second
            val xj = polygon[j].first; val yj = polygon[j].second
            val intersect = ((yi > y) != (yj > y)) &&
                    (x < (xj - xi) * (y - yi) / (yj - yi) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    companion object {
        /**
         * Transforms a building's boundary polygons from floor-local coordinates
         * to campus-wide coordinates. Use this when preparing [CampusBuilding] data.
         *
         * @param polygons     Raw boundary polygons from FloorPlanData
         * @param scale        Building metadata scale
         * @param rotationDeg  Building metadata rotation (degrees)
         * @param offsetX      Building relativePosition.x
         * @param offsetY      Building relativePosition.y
         */
        fun transformPolygons(
            polygons: List<`in`.project.enroute.data.model.BoundaryPolygon>,
            scale: Float,
            rotationDeg: Float,
            offsetX: Float,
            offsetY: Float
        ): List<List<Pair<Float, Float>>> {
            val rad = Math.toRadians(rotationDeg.toDouble())
            val cosA = cos(rad).toFloat()
            val sinA = sin(rad).toFloat()

            return polygons.mapNotNull { polygon ->
                if (polygon.points.isEmpty()) return@mapNotNull null
                polygon.points.sortedBy { it.id }.map { p ->
                    val px = p.x * scale
                    val py = p.y * scale
                    Pair(px * cosA - py * sinA + offsetX, px * sinA + py * cosA + offsetY)
                }
            }
        }
    }
}
