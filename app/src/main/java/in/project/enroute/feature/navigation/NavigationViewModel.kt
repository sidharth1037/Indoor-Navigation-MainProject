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
import `in`.project.enroute.feature.navigation.data.FloorPathSegment
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
import kotlin.math.sqrt

/**
 * UI state for the navigation / pathfinding feature.
 *
 * All path coordinates are in **campus-wide** space (metadata-transformed +
 * building relativePosition), matching the canvas drawing coordinate system.
 * No per-building offset is needed when rendering.
 *
 * @param multiFloorPath The computed path, possibly spanning multiple floors.
 * @param displayPath The path to render — trimmed as the user walks along it.
 *                    Falls back to [multiFloorPath] when no trimming has occurred.
 * @param isCalculating True while the pathfinding coroutine is running.
 * @param targetRoom The room the user requested directions to.
 * @param targetEntrance The entrance matched for the target room.
 * @param error Human-readable error message, or null.
 */
data class NavigationUiState(
    val multiFloorPath: MultiFloorPath = MultiFloorPath.EMPTY,
    val displayPath: MultiFloorPath = MultiFloorPath.EMPTY,
    val isCalculating: Boolean = false,
    val targetRoom: Room? = null,
    val targetEntrance: Entrance? = null,
    val error: String? = null
) {
    /** Convenience: true when a path is available. */
    val hasPath: Boolean get() = !displayPath.isEmpty

    /** Backwards-compatible flat list of all waypoints (for simple consumers). */
    val path: List<Offset> get() = displayPath.allPoints
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
        /**
         * Reroute threshold in campus-wide units (1 unit = 2cm).
         * 3 meters = 300cm = 150 units.
         */
        private const val REROUTE_DISTANCE_THRESHOLD = 150f

        /**
         * Maximum distance between consecutive path points (campus-wide units).
         * Long straight segments are subdivided to this interval so the path
         * disappears smoothly as the user walks along it.
         */
        private const val SUBDIVISION_INTERVAL = 30f
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

    /** Running reroute job — cancelled if a new reroute or path request comes in. */
    private var rerouteJob: Job? = null

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
        rerouteJob?.cancel()

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
                    val subdivided = subdividePath(result)
                    it.copy(
                        isCalculating = false,
                        multiFloorPath = subdivided,
                        displayPath = subdivided
                    )
                }
            }
        }
    }

    /**
     * Clears the current path and resets navigation state.
     */
    fun clearPath() {
        pathfindingJob?.cancel()
        rerouteJob?.cancel()
        _uiState.update { NavigationUiState() }
    }

    // ──────────────────────────────────────────────
    // Path consumption & rerouting
    // ──────────────────────────────────────────────

    /**
     * Called when the user's position changes (every PDR step).
     * Trims the displayed path so past segments disappear, and triggers
     * a silent reroute if the user strays > 3 m from the path.
     *
     * @param userPosition Current user position in campus-wide coordinates.
     * @param currentFloor The floor ID the user is on.
     */
    fun updateUserPosition(userPosition: Offset, currentFloor: String) {
        val state = _uiState.value
        if (!state.hasPath) return

        // Find the nearest point across all segments of the full path
        val (nearestSegIdx, nearestPtIdx, distSq) = findNearestPointOnPath(
            state.multiFloorPath, userPosition, currentFloor
        ) ?: return

        val distance = sqrt(distSq)

        if (distance > REROUTE_DISTANCE_THRESHOLD) {
            // User is too far from the path — reroute silently
            triggerSilentReroute(userPosition, currentFloor)
        } else {
            // Trim the path: remove all segments before the nearest segment,
            // and within that segment remove points before the nearest point.
            val trimmed = trimPath(state.multiFloorPath, nearestSegIdx, nearestPtIdx)
            if (trimmed != null) {
                _uiState.update { it.copy(displayPath = trimmed) }
            }
        }
    }

    /**
     * Finds the nearest path point to [userPosition] on segments matching [currentFloor].
     * Returns (segmentIndex, pointIndex, squaredDistance), or null if no match.
     */
    private fun findNearestPointOnPath(
        path: MultiFloorPath,
        userPosition: Offset,
        currentFloor: String
    ): Triple<Int, Int, Float>? {
        var bestSegIdx = -1
        var bestPtIdx = -1
        var bestDistSq = Float.MAX_VALUE

        for (segIdx in path.segments.indices) {
            val segment = path.segments[segIdx]
            // Only check segments on the user's current floor
            if (segment.floorId != currentFloor) continue

            for (ptIdx in segment.points.indices) {
                val pt = segment.points[ptIdx]
                val dx = pt.x - userPosition.x
                val dy = pt.y - userPosition.y
                val distSq = dx * dx + dy * dy
                if (distSq < bestDistSq) {
                    bestDistSq = distSq
                    bestSegIdx = segIdx
                    bestPtIdx = ptIdx
                }
            }
        }

        if (bestSegIdx < 0) return null
        return Triple(bestSegIdx, bestPtIdx, bestDistSq)
    }

    /**
     * Trims the [MultiFloorPath] so it starts from the given segment and point
     * index, removing already-walked portions.
     * Consumes one extra point ahead of the nearest so the path disappears
     * slightly in front of the user rather than behind.
     */
    private fun trimPath(
        fullPath: MultiFloorPath,
        fromSegIdx: Int,
        fromPtIdx: Int
    ): MultiFloorPath? {
        if (fromSegIdx >= fullPath.segments.size) return null

        // Advance one point ahead so path disappears in front of the user
        var trimSegIdx = fromSegIdx
        var trimPtIdx = fromPtIdx + 1
        val seg = fullPath.segments[trimSegIdx]
        if (trimPtIdx >= seg.points.size) {
            // Overflow into next segment
            trimSegIdx++
            trimPtIdx = 0
        }
        if (trimSegIdx >= fullPath.segments.size) return null

        val newSegments = mutableListOf<FloorPathSegment>()

        // First segment: keep points from trimPtIdx onward
        val firstSeg = fullPath.segments[trimSegIdx]
        if (trimPtIdx < firstSeg.points.size) {
            val trimmedPoints = firstSeg.points.subList(trimPtIdx, firstSeg.points.size)
            if (trimmedPoints.isNotEmpty()) {
                newSegments.add(firstSeg.copy(points = trimmedPoints))
            }
        }

        // All subsequent segments remain intact
        for (i in (trimSegIdx + 1) until fullPath.segments.size) {
            newSegments.add(fullPath.segments[i])
        }

        if (newSegments.isEmpty()) return null

        return MultiFloorPath(
            segments = newSegments,
            totalFloors = newSegments.map { it.floorId }.distinct().size,
            isMultiFloor = newSegments.map { it.floorId }.distinct().size > 1
        )
    }

    /**
     * Subdivides long straight segments into smaller intervals so the path
     * disappears smoothly as the user walks. Each pair of consecutive waypoints
     * that is longer than [SUBDIVISION_INTERVAL] is split into evenly spaced
     * intermediate points.
     */
    private fun subdividePath(path: MultiFloorPath): MultiFloorPath {
        val subdivided = path.segments.map { segment ->
            segment.copy(points = subdividePoints(segment.points))
        }
        return path.copy(segments = subdivided)
    }

    /**
     * Inserts intermediate points between consecutive waypoints so no gap
     * exceeds [SUBDIVISION_INTERVAL] units.
     */
    private fun subdividePoints(points: List<Offset>): List<Offset> {
        if (points.size < 2) return points
        val result = mutableListOf(points.first())
        for (i in 1 until points.size) {
            val from = points[i - 1]
            val to = points[i]
            val dx = to.x - from.x
            val dy = to.y - from.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist > SUBDIVISION_INTERVAL) {
                val steps = (dist / SUBDIVISION_INTERVAL).toInt()
                for (s in 1..steps) {
                    val t = s.toFloat() / (steps + 1)
                    result.add(Offset(from.x + dx * t, from.y + dy * t))
                }
            }
            result.add(to)
        }
        return result
    }

    /**
     * Silently recalculates the path from the user's current position to the
     * original destination. The UI continues showing the old path until the
     * new one is ready, then swaps seamlessly.
     */
    private fun triggerSilentReroute(userPosition: Offset, currentFloor: String) {
        // Don't stack reroutes — cancel any previous reroute in progress
        if (rerouteJob?.isActive == true) return

        val state = _uiState.value
        val goalEntrance = findEntranceForRoom(state.targetRoom ?: return) ?: return

        Log.d(TAG, "Rerouting from (${userPosition.x}, ${userPosition.y}) floor=$currentFloor")

        rerouteJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                multiFloorPathfinder.findMultiFloorPath(
                    start = userPosition,
                    startFloorId = currentFloor,
                    goalEntrance = goalEntrance,
                    allEntrances = campusEntrances.toList(),
                    wallsByFloor = campusWallsByFloor.toMap(),
                    boundaryByFloor = campusBoundaryByFloor.toMap(),
                    repoByFloor = repositoryByFloor
                )
            }

            if (!result.isEmpty) {
                val subdivided = subdividePath(result)
                Log.d(TAG, "Reroute complete: ${subdivided.allPoints.size} waypoints")
                _uiState.update {
                    it.copy(
                        multiFloorPath = subdivided,
                        displayPath = subdivided
                    )
                }
            } else {
                Log.w(TAG, "Reroute failed — keeping existing path")
            }
        }
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
