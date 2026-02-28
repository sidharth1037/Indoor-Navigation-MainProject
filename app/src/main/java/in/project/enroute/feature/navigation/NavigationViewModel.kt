package `in`.project.enroute.feature.navigation

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.project.enroute.data.model.Entrance
import `in`.project.enroute.data.model.FloorPlanData
import `in`.project.enroute.data.model.Room
import `in`.project.enroute.data.model.Wall
import `in`.project.enroute.feature.navigation.data.CampusBoundaryPolygon
import `in`.project.enroute.feature.navigation.data.CampusEntrance
import `in`.project.enroute.feature.navigation.data.MultiFloorPath
import `in`.project.enroute.feature.navigation.data.MultiFloorPathfinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * UI state for the navigation / pathfinding feature.
 *
 * All path coordinates are in **campus-wide** space (metadata-transformed +
 * building relativePosition), matching the canvas drawing coordinate system.
 * No per-building offset is needed when rendering.
 *
 * @param multiFloorPath The computed path, possibly spanning multiple floors.
 * @param isCalculating True while the pathfinding coroutine is running.
 * @param targetRoom The room the user requested directions to.
 * @param targetEntrance The entrance matched for the target room.
 * @param error Human-readable error message, or null.
 */
data class NavigationUiState(
    val multiFloorPath: MultiFloorPath = MultiFloorPath.EMPTY,
    val isCalculating: Boolean = false,
    val targetRoom: Room? = null,
    val targetEntrance: Entrance? = null,
    val error: String? = null
) {
    /** Convenience: true when a path is available. */
    val hasPath: Boolean get() = !multiFloorPath.isEmpty

    /** Backwards-compatible flat list of all waypoints (for simple consumers). */
    val path: List<Offset> get() = multiFloorPath.allPoints
}

/**
 * ViewModel for A* pathfinding between the user's position and a room entrance.
 *
 * Supports **multi-floor** navigation: when the destination room is on a different
 * floor the [MultiFloorPathfinder] stitches per-floor A* segments together via
 * stair entrances.
 *
 * Uses a **single global grid** in campus-wide coordinates. All building walls
 * are transformed (scale → rotate → offset by relativePosition) at supply time
 * so the A* grid sees the entire campus in one coordinate space. This means:
 *
 * - PDR positions (already campus-wide) can be used directly as start points
 * - Entrance coordinates are pre-transformed at supply time
 * - Path output needs no further transform — it matches the canvas space
 * - Cross-building navigation works naturally through the shared grid
 *
 * Lifecycle:
 *  1. [supplyFloorData] is called once floors are loaded → walls & entrances are transformed
 *  2. User taps "Directions" → [requestDirections] runs A* (possibly multi-floor)
 *  3. Path emitted via [uiState] as a [MultiFloorPath]
 *  4. [clearPath] resets
 */
class NavigationViewModel : ViewModel() {

    companion object {
        private const val TAG = "NavigationVM"
    }

    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

    /** Multi-floor pathfinding engine. */
    private val multiFloorPathfinder = MultiFloorPathfinder()

    /**
     * All entrances across all buildings/floors, pre-transformed to campus-wide
     * coordinates.  Populated by [supplyFloorData].
     */
    private val campusEntrances = mutableListOf<CampusEntrance>()

    /**
     * Campus-wide walls **grouped by floor ID**.
     *
     * Different floors on the same building footprint have different wall
     * layouts — combining them would seal corridors.  Pathfinding uses only
     * the walls for the floor the user is currently on.
     */
    private val campusWallsByFloor = mutableMapOf<String, MutableList<Wall>>()

    /**
     * Campus-wide boundary polygons **grouped by floor ID**.
     * Used to block exterior cells in the pathfinding grid.
     */
    private val campusBoundaryByFloor = mutableMapOf<String, MutableList<CampusBoundaryPolygon>>()

    /**
     * Lazily-built [NavigationRepository] **per floor ID**.
     * Invalidated when floor data changes.
     */
    private val repositoryByFloor = mutableMapOf<String, NavigationRepository>()

    /** Running pathfinding job — cancelled if a new request comes in. */
    private var pathfindingJob: Job? = null

    // ──────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────

    /**
     * Transforms and stores floor plan data for pathfinding.
     *
     * Walls and entrances from each building/floor are transformed from raw
     * floor plan coordinates into campus-wide coordinates:
     *   raw → scale → rotate → offset by relativePosition
     *
     * Call whenever new floors are loaded (e.g. from FloorPlanViewModel).
     */
    fun supplyFloorData(floors: List<FloorPlanData>) {
        campusEntrances.clear()
        campusWallsByFloor.clear()
        campusBoundaryByFloor.clear()
        repositoryByFloor.clear()

        for (floor in floors) {
            val meta = floor.metadata
            val scale = meta.scale
            val rotation = meta.rotation
            val offX = meta.relativePosition.x
            val offY = meta.relativePosition.y

            // Transform walls → campus-wide, grouped by floorId
            val floorWalls = campusWallsByFloor.getOrPut(floor.floorId) { mutableListOf() }
            for (wall in floor.walls) {
                val (sx1, sy1) = rawToCampus(wall.x1, wall.y1, scale, rotation, offX, offY)
                val (sx2, sy2) = rawToCampus(wall.x2, wall.y2, scale, rotation, offX, offY)
                floorWalls.add(Wall(sx1, sy1, sx2, sy2))
            }

            // Transform boundary polygons → campus-wide
            val floorBoundary = campusBoundaryByFloor.getOrPut(floor.floorId) { mutableListOf() }
            for (polygon in floor.boundaryPolygons) {
                val campusPoints = polygon.points.map { pt ->
                    val (cx, cy) = rawToCampus(pt.x, pt.y, scale, rotation, offX, offY)
                    Offset(cx, cy)
                }
                floorBoundary.add(CampusBoundaryPolygon(campusPoints))
            }

            // Transform entrances → campus-wide (stored globally for room matching)
            for (entrance in floor.entrances) {
                val (cx, cy) = rawToCampus(entrance.x, entrance.y, scale, rotation, offX, offY)
                campusEntrances.add(
                    CampusEntrance(
                        original = entrance,
                        campusX = cx,
                        campusY = cy,
                        buildingId = floor.buildingId,
                        floorId = floor.floorId
                    )
                )
            }
        }

        val totalWalls = campusWallsByFloor.values.sumOf { it.size }
        Log.d(TAG, "Supplied $totalWalls walls across ${campusWallsByFloor.size} floors, " +
                "${campusEntrances.size} entrances (campus-wide)")
    }

    /**
     * Requests a path from the user's current position to the entrance of [room].
     *
     * Delegates to [MultiFloorPathfinder] which handles both same-floor and
     * cross-floor navigation transparently.
     *
     * @param room The destination room (must have name or number to match an entrance).
     * @param userPosition The user's current campus-wide position (from PDR).
     * @param currentFloor The floor ID the user is on (e.g. "floor_1").
     */
    fun requestDirections(room: Room, userPosition: Offset, currentFloor: String) {
        pathfindingJob?.cancel()

        // Find the entrance that matches this room
        val campusEntrance = findEntranceForRoom(room)
        if (campusEntrance == null) {
            _uiState.update {
                it.copy(
                    error = "No entrance found for room ${room.name ?: room.number}",
                    isCalculating = false,
                    targetRoom = room
                )
            }
            Log.e(TAG, "No entrance matched for room: name=${room.name}, number=${room.number}")
            return
        }

        Log.d(TAG, "Matched entrance id=${campusEntrance.original.id} " +
                "floor=${campusEntrance.floorId} " +
                "campus(${campusEntrance.campusX}, ${campusEntrance.campusY}) " +
                "for room ${room.name ?: room.number}")

        _uiState.update {
            it.copy(
                isCalculating = true,
                error = null,
                targetRoom = room,
                targetEntrance = campusEntrance.original,
                multiFloorPath = MultiFloorPath.EMPTY
            )
        }

        pathfindingJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                multiFloorPathfinder.findMultiFloorPath(
                    start = userPosition,
                    startFloorId = currentFloor,
                    goalEntrance = campusEntrance,
                    allEntrances = campusEntrances.toList(),
                    wallsByFloor = campusWallsByFloor.toMap(),
                    boundaryByFloor = campusBoundaryByFloor.toMap(),
                    repoByFloor = repositoryByFloor
                )
            }

            _uiState.update {
                if (result.isEmpty) {
                    it.copy(isCalculating = false, error = "Could not find a path")
                } else {
                    it.copy(isCalculating = false, multiFloorPath = result)
                }
            }
        }
    }

    /**
     * Clears the current path and resets navigation state.
     */
    fun clearPath() {
        pathfindingJob?.cancel()
        _uiState.update { NavigationUiState() }
    }

    // ──────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────

    /**
     * Finds the campus-wide entrance that matches [room] by room number or name.
     * When the room has a buildingId, entrance matching is scoped to that building.
     */
    private fun findEntranceForRoom(room: Room): CampusEntrance? {
        // Try matching by room number first (most reliable)
        if (room.number != null) {
            val byNumber = campusEntrances.firstOrNull { ce ->
                ce.original.roomNo != null &&
                        ce.original.roomNo == room.number.toString() &&
                        (room.buildingId == null || ce.buildingId == room.buildingId)
            }
            if (byNumber != null) return byNumber
        }

        // Fall back to name matching (case-insensitive) within the same building
        if (room.name != null) {
            val byName = campusEntrances.firstOrNull { ce ->
                ce.original.name != null &&
                        ce.original.name.equals(room.name, ignoreCase = true) &&
                        (room.buildingId == null || ce.buildingId == room.buildingId)
            }
            if (byName != null) return byName
        }

        return null
    }

    // ──────────────────────────────────────────────
    // Coordinate transform
    // ──────────────────────────────────────────────

    /**
     * Transforms raw floor plan coordinates to campus-wide coordinates.
     * raw → scale → rotate → offset by building relativePosition.
     */
    private fun rawToCampus(
        x: Float, y: Float,
        scale: Float, rotationDegrees: Float,
        offsetX: Float, offsetY: Float
    ): Pair<Float, Float> {
        val sx = x * scale
        val sy = y * scale
        val rad = Math.toRadians(rotationDegrees.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val rx = sx * cosA - sy * sinA
        val ry = sx * sinA + sy * cosA
        return Pair(rx + offsetX, ry + offsetY)
    }

    override fun onCleared() {
        super.onCleared()
        pathfindingJob?.cancel()
    }
}
