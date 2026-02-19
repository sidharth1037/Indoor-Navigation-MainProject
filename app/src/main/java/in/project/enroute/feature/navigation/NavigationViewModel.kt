package `in`.project.enroute.feature.navigation

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.project.enroute.data.model.Entrance
import `in`.project.enroute.data.model.FloorPlanData
import `in`.project.enroute.data.model.Room
import `in`.project.enroute.data.model.Wall
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
 * @param path The computed A* path as campus-wide waypoints. Empty when no path is active.
 * @param isCalculating True while the pathfinding coroutine is running.
 * @param targetRoom The room the user requested directions to.
 * @param targetEntrance The entrance matched for the target room.
 * @param error Human-readable error message, or null.
 */
data class NavigationUiState(
    val path: List<Offset> = emptyList(),
    val isCalculating: Boolean = false,
    val targetRoom: Room? = null,
    val targetEntrance: Entrance? = null,
    val error: String? = null
)

/**
 * ViewModel for A* pathfinding between the user's position and a room entrance.
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
 *  2. User taps "Directions" → [requestDirections] runs A* in campus-wide space
 *  3. Path emitted via [uiState]
 *  4. [clearPath] resets
 */
class NavigationViewModel : ViewModel() {

    companion object {
        private const val TAG = "NavigationVM"
    }

    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

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
     * Both [userPosition] and the resulting path are in campus-wide coordinates,
     * matching the canvas drawing space. No per-building offset math needed.
     *
     * @param room The destination room (must have name or number to match an entrance).
     * @param userPosition The user's current campus-wide position (from PDR).
     * @param currentFloor The floor ID the user is on (e.g. "floor_1"). Only walls
     *   from this floor are used for the A* grid.
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
                "campus(${campusEntrance.campusX}, ${campusEntrance.campusY}) " +
                "for room ${room.name ?: room.number}")

        _uiState.update {
            it.copy(
                isCalculating = true,
                error = null,
                targetRoom = room,
                targetEntrance = campusEntrance.original,
                path = emptyList()
            )
        }

        pathfindingJob = viewModelScope.launch {
            val goal = Offset(campusEntrance.campusX, campusEntrance.campusY)
            val path = computePath(userPosition, goal, currentFloor)

            _uiState.update {
                if (path.isEmpty()) {
                    it.copy(isCalculating = false, error = "Could not find a path")
                } else {
                    it.copy(isCalculating = false, path = path)
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
     * Runs A* on a background dispatcher. A [NavigationRepository] is built
     * lazily **per floor** — only walls from [floorId] are used.
     */
    private suspend fun computePath(
        start: Offset,
        goal: Offset,
        floorId: String
    ): List<Offset> = withContext(Dispatchers.Default) {
        val walls = campusWallsByFloor[floorId]
        if (walls.isNullOrEmpty()) {
            Log.e(TAG, "No walls for floor $floorId — cannot pathfind")
            return@withContext emptyList()
        }
        val repo = repositoryByFloor[floorId] ?: run {
            Log.d(TAG, "Building distance transform for floor $floorId (${walls.size} walls)…")
            NavigationRepository(walls).also { repositoryByFloor[floorId] = it }
        }
        repo.findPath(start, goal)
    }

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

/**
 * An entrance pre-transformed into campus-wide coordinates.
 * Retains the original [Entrance] for room-matching metadata (name, roomNo, etc.).
 */
private data class CampusEntrance(
    val original: Entrance,
    val campusX: Float,
    val campusY: Float,
    val buildingId: String,
    val floorId: String
)
