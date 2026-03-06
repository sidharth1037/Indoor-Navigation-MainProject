package `in`.project.enroute.feature.home.locationselection

import `in`.project.enroute.data.model.Entrance
import `in`.project.enroute.data.model.Room
import `in`.project.enroute.data.model.Wall
import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

/**
 * Corridor point for an entrance — the walkable point just outside a room's doorway.
 *
 * @param campusPosition Campus-wide coordinates of the corridor point
 * @param entrance The original entrance this point corresponds to
 * @param floorId Floor containing this entrance
 * @param buildingId Building containing this entrance
 * @param entrancePosition Campus-wide coordinates of the entrance itself
 * @param index 1-based index for display (when a room has multiple entrances)
 */
data class CorridorPoint(
    val campusPosition: Offset,
    val entrance: Entrance,
    val floorId: String,
    val buildingId: String,
    val entrancePosition: Offset,
    val index: Int
)

/**
 * Configurable constants for corridor point computation.
 * All distances are in floor-plan-local coordinate units.
 */
object CorridorPointConfig {
    /** Distance from entrance along wall-perpendicular to place the corridor point. */
    var OFFSET_DISTANCE = 80f

    /** Max distance (in floor-plan units) an entrance can be from a wall to be considered "on" it. */
    var WALL_SNAP_THRESHOLD = 15f

    /** Fallback offset distance when no wall is found (uses room-center vector instead). */
    var FALLBACK_OFFSET_DISTANCE = 80f

    /** Zoom scale used when showing a single entrance corridor point. */
    var SINGLE_ENTRANCE_ZOOM = 1.5f

    /** Padding factor for multi-entrance bounding box (1.0 = no padding, 2.0 = double). */
    var MULTI_ENTRANCE_PADDING = 1.8f

    /** Minimum zoom scale for multi-entrance view. */
    var MULTI_ENTRANCE_MIN_ZOOM = 0.5f

    /** Maximum zoom scale for multi-entrance view. */
    var MULTI_ENTRANCE_MAX_ZOOM = 2.0f
}

/**
 * Finds walkable corridor points in front of room entrances.
 *
 * Strategy:
 * 1. Find the wall segment closest to the entrance (entrance sits on/near a wall).
 * 2. Compute the wall's perpendicular normal.
 * 3. Pick the normal direction pointing AWAY from the room center.
 * 4. Place the corridor point at [CorridorPointConfig.OFFSET_DISTANCE] along that normal.
 *
 * This guarantees the point is perpendicular to the doorway and in the corridor,
 * not at an angle like a simple room-center→entrance vector would produce.
 */
object CorridorPointFinder {

    /**
     * Find all corridor points for a room's entrances.
     *
     * @param room The room to find corridor points for
     * @param entrances All entrances on the same floor (will be filtered by roomNo/name match)
     * @param walls All walls on the same floor (floor-plan-local coordinates)
     * @param rawToCampus Transform function: (x, y) in floor local → (x, y) in campus-wide
     * @param floorId Floor identifier
     * @param buildingId Building identifier
     * @return List of corridor points, one per matched entrance. Empty if no entrances found.
     */
    fun findCorridorPoints(
        room: Room,
        entrances: List<Entrance>,
        walls: List<Wall>,
        rawToCampus: (Float, Float) -> Pair<Float, Float>,
        floorId: String,
        buildingId: String
    ): List<CorridorPoint> {
        val matchedEntrances = findEntrancesForRoom(room, entrances)
        if (matchedEntrances.isEmpty()) return emptyList()

        return matchedEntrances.mapIndexed { idx, entrance ->
            val corridorLocal = computeCorridorPoint(
                entranceX = entrance.x,
                entranceY = entrance.y,
                roomCenterX = room.x,
                roomCenterY = room.y,
                walls = walls
            )
            val (campusX, campusY) = rawToCampus(corridorLocal.first, corridorLocal.second)
            val (entrCampusX, entrCampusY) = rawToCampus(entrance.x, entrance.y)

            CorridorPoint(
                campusPosition = Offset(campusX, campusY),
                entrance = entrance,
                floorId = floorId,
                buildingId = buildingId,
                entrancePosition = Offset(entrCampusX, entrCampusY),
                index = idx + 1
            )
        }
    }

    /**
     * Match entrances to a room by room number or name.
     * Returns ALL matching entrances (a room can have multiple doors).
     */
    private fun findEntrancesForRoom(room: Room, entrances: List<Entrance>): List<Entrance> {
        val matched = mutableListOf<Entrance>()

        // Match by room number (most reliable)
        if (room.number != null) {
            val byNumber = entrances.filter { e ->
                e.roomNo != null && e.roomNo == room.number.toString() && !e.isStairs
            }
            matched.addAll(byNumber)
        }

        // If no number match, try name matching
        if (matched.isEmpty() && room.name != null) {
            val byName = entrances.filter { e ->
                e.name != null && e.name.equals(room.name, ignoreCase = true) && !e.isStairs
            }
            matched.addAll(byName)
        }

        return matched
    }

    /**
     * Compute the corridor point for a single entrance.
     *
     * Every entrance sits in a gap between two collinear wall segments (the door frame).
     * Both wall segments share either the same x (vertical wall) or same y (horizontal wall)
     * as the entrance, within [CorridorPointConfig.WALL_SNAP_THRESHOLD].
     *
     * The perpendicular to that wall line points into the corridor. We pick the
     * direction away from the room center and offset along it.
     *
     * Falls back to room-center→entrance vector if no door-frame walls are found.
     *
     * @return (x, y) in floor-plan-local coordinates
     */
    private fun computeCorridorPoint(
        entranceX: Float,
        entranceY: Float,
        roomCenterX: Float,
        roomCenterY: Float,
        walls: List<Wall>
    ): Pair<Float, Float> {
        val threshold = CorridorPointConfig.WALL_SNAP_THRESHOLD

        // Look for vertical door frame: two walls with x1==x2 ≈ entrance.x,
        // where entranceY falls in the gap between them.
        val verticalCandidates = walls.filter { w ->
            w.x1 == w.x2 && kotlin.math.abs(w.x1 - entranceX) <= threshold
        }
        val verticalPair = findDoorFramePair(verticalCandidates, entranceY, isVertical = true)

        if (verticalPair != null) {
            // Door is in a vertical wall → corridor is perpendicular (along x-axis)
            // Pick direction away from room center
            val nx = if (roomCenterX < entranceX) 1f else -1f
            return Pair(
                entranceX + nx * CorridorPointConfig.OFFSET_DISTANCE,
                entranceY
            )
        }

        // Look for horizontal door frame: two walls with y1==y2 ≈ entrance.y,
        // where entranceX falls in the gap between them.
        val horizontalCandidates = walls.filter { w ->
            w.y1 == w.y2 && kotlin.math.abs(w.y1 - entranceY) <= threshold
        }
        val horizontalPair = findDoorFramePair(horizontalCandidates, entranceX, isVertical = false)

        if (horizontalPair != null) {
            // Door is in a horizontal wall → corridor is perpendicular (along y-axis)
            val ny = if (roomCenterY < entranceY) 1f else -1f
            return Pair(
                entranceX,
                entranceY + ny * CorridorPointConfig.OFFSET_DISTANCE
            )
        }

        // Fallback: use vector from room center through entrance
        val vx = entranceX - roomCenterX
        val vy = entranceY - roomCenterY
        val vLen = sqrt(vx * vx + vy * vy)
        return if (vLen > 0.001f) {
            Pair(
                entranceX + (vx / vLen) * CorridorPointConfig.FALLBACK_OFFSET_DISTANCE,
                entranceY + (vy / vLen) * CorridorPointConfig.FALLBACK_OFFSET_DISTANCE
            )
        } else {
            Pair(entranceX, entranceY - CorridorPointConfig.FALLBACK_OFFSET_DISTANCE)
        }
    }

    /**
     * Among collinear wall segments, find two that form a door frame around [entranceCoord].
     *
     * For vertical walls (isVertical=true): entranceCoord is the entrance's Y.
     *   Each wall spans a Y range; we look for one ending before entranceCoord and one starting after.
     * For horizontal walls (isVertical=false): entranceCoord is the entrance's X, same logic on X range.
     *
     * @return true if a valid door-frame pair is found, null otherwise
     */
    private fun findDoorFramePair(
        candidates: List<Wall>,
        entranceCoord: Float,
        isVertical: Boolean
    ): Boolean? {
        // For each candidate wall, get its span along the door-opening axis
        // Vertical walls: span is (minY, maxY). Horizontal walls: span is (minX, maxX).
        var hasWallBefore = false
        var hasWallAfter = false

        for (wall in candidates) {
            val (lo, hi) = if (isVertical) {
                minOf(wall.y1, wall.y2) to maxOf(wall.y1, wall.y2)
            } else {
                minOf(wall.x1, wall.x2) to maxOf(wall.x1, wall.x2)
            }
            // Wall ends before the entrance → one side of the door frame
            if (hi < entranceCoord) hasWallBefore = true
            // Wall starts after the entrance → other side of the door frame
            if (lo > entranceCoord) hasWallAfter = true
        }

        return if (hasWallBefore && hasWallAfter) true else null
    }

    /**
     * Calculate the center point and zoom level to fit all corridor points on screen.
     *
     * @param points List of corridor points (campus-wide coordinates)
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     * @return Pair of (centerOffset, zoomScale)
     */
    fun calculateFitBounds(
        points: List<CorridorPoint>,
        screenWidth: Float,
        screenHeight: Float
    ): Pair<Offset, Float> {
        if (points.isEmpty()) return Pair(Offset.Zero, 1f)
        if (points.size == 1) {
            return Pair(points[0].campusPosition, CorridorPointConfig.SINGLE_ENTRANCE_ZOOM)
        }

        // True centroid (midpoint for 2, average for 3+)
        val centroidX = points.map { it.campusPosition.x }.average().toFloat()
        val centroidY = points.map { it.campusPosition.y }.average().toFloat()

        // Bounding box for span calculation
        val minX = points.minOf { it.campusPosition.x }
        val maxX = points.maxOf { it.campusPosition.x }
        val minY = points.minOf { it.campusPosition.y }
        val maxY = points.maxOf { it.campusPosition.y }

        // Minimum span prevents infinite zoom when points are collinear (same X or Y)
        val minSpan = CorridorPointConfig.OFFSET_DISTANCE * 4f
        val spanX = maxOf(maxX - minX, minSpan) * CorridorPointConfig.MULTI_ENTRANCE_PADDING
        val spanY = maxOf(maxY - minY, minSpan) * CorridorPointConfig.MULTI_ENTRANCE_PADDING

        val zoomX = screenWidth / spanX
        val zoomY = screenHeight / spanY
        val zoom = minOf(zoomX, zoomY).coerceIn(
            CorridorPointConfig.MULTI_ENTRANCE_MIN_ZOOM,
            CorridorPointConfig.MULTI_ENTRANCE_MAX_ZOOM
        )

        return Pair(Offset(centroidX, centroidY), zoom)
    }
}
