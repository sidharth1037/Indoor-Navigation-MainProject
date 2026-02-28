package `in`.project.enroute.feature.navigation.data

import androidx.compose.ui.geometry.Offset
import `in`.project.enroute.data.model.Entrance

/**
 * A single segment of a multi-floor navigation path.
 *
 * Each segment lives on one floor and contains the A* waypoints for that
 * portion of the journey. The full cross-floor path is an ordered list of
 * these segments.
 *
 * @param floorId     The floor this segment belongs to (e.g. "floor_1").
 * @param floorNumber Numeric floor level (e.g. 1.0, 1.5) — used for display.
 * @param buildingId  The building this segment belongs to.
 * @param points      Ordered waypoints in campus-wide coordinates.
 * @param isTransition True if this segment represents a stairwell transition
 *                     (drawn differently from walkable path).
 */
data class FloorPathSegment(
    val floorId: String,
    val floorNumber: Float,
    val buildingId: String,
    val points: List<Offset>,
    val isTransition: Boolean = false
)

/**
 * Complete multi-floor navigation result.
 *
 * @param segments     Ordered list of per-floor path segments.
 * @param totalFloors  Number of distinct floors involved.
 * @param isMultiFloor True when the path spans more than one floor.
 */
data class MultiFloorPath(
    val segments: List<FloorPathSegment> = emptyList(),
    val totalFloors: Int = 0,
    val isMultiFloor: Boolean = false
) {
    companion object {
        val EMPTY = MultiFloorPath()
    }

    /** All waypoints flattened (ignoring floor boundaries). */
    val allPoints: List<Offset>
        get() = segments.flatMap { it.points }

    /** True when no path was found. */
    val isEmpty: Boolean get() = segments.isEmpty() || segments.all { it.points.isEmpty() }

}

/**
 * An entrance pre-transformed into campus-wide coordinates.
 * Retains the original [Entrance] for room-matching metadata (name, roomNo, etc.).
 *
 * Promoted from NavigationViewModel private class to a shared data type so
 * [MultiFloorPathfinder] can also use it.
 */
data class CampusEntrance(
    val original: Entrance,
    val campusX: Float,
    val campusY: Float,
    val buildingId: String,
    val floorId: String
) {
    /** Campus-wide position as an [Offset]. */
    val campusOffset: Offset get() = Offset(campusX, campusY)
}

/**
 * A boundary polygon pre-transformed into campus-wide coordinates.
 *
 * Used by NavigationRepository to block exterior cells — any grid cell
 * that falls outside every boundary polygon is hard-blocked.
 */
data class CampusBoundaryPolygon(
    val points: List<Offset>
) {
    /**
     * Point-in-polygon test using the ray-casting algorithm.
     * Returns `true` if [point] is inside this polygon.
     */
    fun contains(point: Offset): Boolean {
        var inside = false
        val n = points.size
        if (n < 3) return false
        var j = n - 1
        for (i in 0 until n) {
            val yi = points[i].y; val yj = points[j].y
            val xi = points[i].x; val xj = points[j].x
            if ((yi > point.y) != (yj > point.y) &&
                point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}
