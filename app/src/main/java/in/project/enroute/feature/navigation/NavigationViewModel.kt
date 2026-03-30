package `in`.project.enroute.feature.navigation

import android.app.Application
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.project.enroute.data.model.Entrance
import `in`.project.enroute.data.model.FloorPlanData
import `in`.project.enroute.data.model.Room
import `in`.project.enroute.data.model.Wall
import `in`.project.enroute.feature.navigation.data.CampusBoundaryPolygon
import `in`.project.enroute.feature.navigation.data.CampusEntrance
import `in`.project.enroute.feature.navigation.data.CoordinateTransform
import `in`.project.enroute.feature.navigation.data.FloorPathSegment
import `in`.project.enroute.feature.navigation.data.MultiFloorPath
import `in`.project.enroute.feature.navigation.data.MultiFloorPathfinder
import `in`.project.enroute.feature.navigation.data.PathTransition
import `in`.project.enroute.feature.navigation.data.PrecalculatedFloorGrid
import `in`.project.enroute.feature.navigation.data.StairwellConnection
import `in`.project.enroute.feature.navigation.data.TransitionDirection
import `in`.project.enroute.feature.navigation.guidance.GuidanceConfig
import `in`.project.enroute.feature.navigation.guidance.GuidanceType
import `in`.project.enroute.feature.navigation.guidance.GuidanceInput
import `in`.project.enroute.feature.navigation.guidance.TripEtaEstimator
import `in`.project.enroute.feature.navigation.guidance.TurnByTurnGuidanceEngine
import `in`.project.enroute.feature.settings.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
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
    val isNavigationStarted: Boolean = false,
    val remainingDistanceMeters: Int? = null,
    val estimatedTimeText: String? = null,
    val turnByTurnInstruction: String = "",
    val turnByTurnInstructionType: GuidanceType = GuidanceType.IDLE,
    val targetRoom: Room? = null,
    val targetEntrance: Entrance? = null,
    val directGoalCampus: Offset? = null,
    val directGoalFloorId: String? = null,
    val directGoalBuildingId: String? = null,
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
 * stairwell connections (polygon midpoints).
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
class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "NavigationVM"
        /**
         * Reroute threshold in campus-wide units (1 unit = 2cm).
         * 3 meters = 300cm = 150 units.
         */
        private const val REROUTE_DISTANCE_THRESHOLD = 150f
        private const val REROUTE_COOLDOWN_MS = 1800L

        /**
         * Maximum distance between consecutive path points (campus-wide units).
         * Long straight segments are subdivided to this interval so the path
         * disappears smoothly as the user walks along it.
         */
        private const val SUBDIVISION_INTERVAL = 30f
        private const val USER_SPEED_MIN_DISTANCE_M = 12f
        private const val USER_SPEED_MIN_STEP_DISTANCE_M = 0.35f
        private const val USER_SPEED_PERSIST_STEP_DISTANCE_M = 8f
    }

    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

    private val settingsRepository = SettingsRepository(application.applicationContext)

    private val guidanceConfig = GuidanceConfig()
    private val guidanceEngine = TurnByTurnGuidanceEngine(guidanceConfig)
    private var lastGuidanceText: String = ""
    private var lastGuidanceUpdateMs: Long = 0L
    private var pendingRerouteAnnouncement: Boolean = false
    private var lastRerouteAtMs: Long = 0L

    private var longTermSpeedMps: Float = TripEtaEstimator.DEFAULT_WALK_SPEED_MPS
    private var sessionDistanceM: Float = 0f
    private var sessionActiveTimeS: Float = 0f
    private var lastPersistedDistanceM: Float = 0f
    private var persistedSessionDistanceM: Float = 0f
    private var persistedSessionActiveTimeS: Float = 0f
    private var lastObservedPosition: Offset? = null
    private var lastObservedTimestampMs: Long? = null

    /** Multi-floor pathfinding engine. */
    private val multiFloorPathfinder = MultiFloorPathfinder()

    /**
     * All entrances across all buildings/floors, pre-transformed to campus-wide
     * coordinates.  Populated by [supplyFloorData].
     */
    private val campusEntrances = mutableListOf<CampusEntrance>()

    /**
     * Pre-computed stairwell connections for cross-floor navigation.
     * Populated by [supplyStairwellConnections].
     */
    private var stairwellConnections: List<StairwellConnection> = emptyList()

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

    /**
     * Precalculated distance-transform grids, keyed by floor ID.
     * Loaded from Firestore/cache at campus-load time. When present, used
     * by MultiFloorPathfinder to skip expensive grid computation.
     * Stored separately from repositoryByFloor so clearing repos
     * (on data supply) does not lose precalculated data.
     */
    private val precalculatedGrids = mutableMapOf<String, PrecalculatedFloorGrid>()

    /** Running pathfinding job — cancelled if a new request comes in. */
    private var pathfindingJob: Job? = null

    /** Running reroute job — cancelled if a new reroute or path request comes in. */
    private var rerouteJob: Job? = null

    init {
        viewModelScope.launch {
            longTermSpeedMps = settingsRepository.navUserSpeedLongTermMps.first()
                ?: TripEtaEstimator.DEFAULT_WALK_SPEED_MPS
        }
    }

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
     * Supplies pre-computed stairwell connections for cross-floor navigation.
     * Call whenever building states change (same lifecycle as [supplyFloorData]).
     */
    fun supplyStairwellConnections(connections: List<StairwellConnection>) {
        stairwellConnections = connections
        repositoryByFloor.clear()
        Log.d(TAG, "Supplied ${connections.size} stairwell connections")
    }

    /**
     * Supplies precalculated distance-transform grids for instant pathfinding.
     * Called after floor data and stairwell connections have been supplied.
     * The grids are used lazily by MultiFloorPathfinder.getOrBuildRepo().
     */
    fun supplyPrecalculatedGrids(grids: Map<String, PrecalculatedFloorGrid>) {
        precalculatedGrids.clear()
        precalculatedGrids.putAll(grids)
        repositoryByFloor.clear()  // force rebuild from precalculated data
        Log.d(TAG, "Supplied ${grids.size} precalculated floor grids: ${grids.keys}")
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
    fun requestDirections(
        room: Room,
        userPosition: Offset,
        currentFloor: String,
        directGoalCampus: Offset? = null,
        directGoalFloorId: String? = null,
        directGoalBuildingId: String? = null
    ) {
        pathfindingJob?.cancel()
        rerouteJob?.cancel()

        // Prefer direct-goal pathfinding for synthetic targets (landmarks).
        val campusEntrance = if (directGoalCampus != null) {
            CampusEntrance(
                original = Entrance(
                    id = -1,
                    x = directGoalCampus.x,
                    y = directGoalCampus.y,
                    name = room.name,
                    roomNo = room.number?.toString(),
                    floorId = directGoalFloorId ?: currentFloor,
                    floor = floorNumberOf(directGoalFloorId ?: currentFloor)
                ),
                campusX = directGoalCampus.x,
                campusY = directGoalCampus.y,
                buildingId = directGoalBuildingId ?: room.buildingId.orEmpty(),
                floorId = directGoalFloorId ?: currentFloor
            )
        } else {
            findEntranceForRoom(room)
        }

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
                isNavigationStarted = false,
                remainingDistanceMeters = null,
                estimatedTimeText = null,
                turnByTurnInstruction = "",
                turnByTurnInstructionType = GuidanceType.IDLE,
                error = null,
                targetRoom = room,
                targetEntrance = if (directGoalCampus != null) null else campusEntrance.original,
                directGoalCampus = directGoalCampus,
                directGoalFloorId = directGoalFloorId,
                directGoalBuildingId = directGoalBuildingId,
                multiFloorPath = MultiFloorPath.EMPTY
            )
        }

        pathfindingJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                computeRouteWithDescendingReverse(
                    start = userPosition,
                    startFloorId = currentFloor,
                    goalEntrance = campusEntrance
                )
            }

            _uiState.update {
                if (result.isEmpty) {
                    it.copy(isCalculating = false, error = "Could not find a path")
                } else {
                    val subdivided = subdividePath(result)
                    val remainingMeters = computeRemainingDistanceMeters(subdivided)
                    val etaText = TripEtaEstimator.formatEta(
                        TripEtaEstimator.estimateEtaSeconds(remainingMeters, effectiveSpeedMps())
                    )
                    it.copy(
                        isCalculating = false,
                        isNavigationStarted = false,
                        remainingDistanceMeters = remainingMeters,
                        estimatedTimeText = etaText,
                        turnByTurnInstruction = "",
                        turnByTurnInstructionType = GuidanceType.IDLE,
                        multiFloorPath = subdivided,
                        displayPath = subdivided
                    )
                }
            }
        }
    }

    /**
     * Marks the current route as started (user pressed Start).
     */
    fun startNavigation() {
        val state = _uiState.value
        if (!state.hasPath) return
        _uiState.update {
            it.copy(
                isNavigationStarted = true,
                turnByTurnInstruction = "Align with the highlighted route.",
                turnByTurnInstructionType = GuidanceType.ALIGN
            )
        }
    }

    /**
     * Updates turn-by-turn guidance text from the current position and heading.
     * Updates are throttled to keep text stable while the user is walking.
     */
    fun updateTurnByTurn(userPosition: Offset, currentFloor: String, headingRadians: Float) {
        val state = _uiState.value
        if (!state.isNavigationStarted || !state.hasPath) return

        val now = System.currentTimeMillis()
        val forceReroute = pendingRerouteAnnouncement
        val guidance = guidanceEngine.compute(
            GuidanceInput(
                path = state.displayPath,
                userPosition = userPosition,
                headingRadians = headingRadians,
                currentFloorId = currentFloor,
                nowMs = now,
                forceRerouteMessage = forceReroute
            )
        )

        val isThrottled = (now - lastGuidanceUpdateMs) < guidanceConfig.throttleMs
        val textChanged = guidance.text != lastGuidanceText
        val shouldPublish = forceReroute || (!isThrottled && textChanged)
        if (!shouldPublish) return

        lastGuidanceText = guidance.text
        lastGuidanceUpdateMs = now
        pendingRerouteAnnouncement = false
        _uiState.update {
            it.copy(
                turnByTurnInstruction = guidance.text,
                turnByTurnInstructionType = guidance.type
            )
        }
    }

    /**
     * Clears the current path and resets navigation state.
     */
    fun clearPath() {
        pathfindingJob?.cancel()
        rerouteJob?.cancel()
        lastGuidanceText = ""
        lastGuidanceUpdateMs = 0L
        pendingRerouteAnnouncement = false
        lastRerouteAtMs = 0L
        persistSessionSpeedIfAvailable()
        lastObservedPosition = null
        lastObservedTimestampMs = null
        sessionDistanceM = 0f
        sessionActiveTimeS = 0f
        lastPersistedDistanceM = 0f
        persistedSessionDistanceM = 0f
        persistedSessionActiveTimeS = 0f
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

        updateUserSpeedProfile(userPosition)

        // Find the nearest point across all segments of the full path
        val (nearestSegIdx, nearestPtIdx, distSq) = findNearestPointOnPath(
            state.multiFloorPath, userPosition, currentFloor
        ) ?: return

        val distance = sqrt(distSq)

        if (state.isNavigationStarted && distance > REROUTE_DISTANCE_THRESHOLD) {
            // User is too far from the path — reroute silently
            triggerSilentReroute(userPosition, currentFloor)
        } else {
            // Trim the path: remove all segments before the nearest segment,
            // and within that segment remove points before the nearest point.
            val trimmed = trimPath(state.multiFloorPath, nearestSegIdx, nearestPtIdx)
            if (trimmed != null) {
                val remainingMeters = computeRemainingDistanceMeters(trimmed)
                val etaText = TripEtaEstimator.formatEta(
                    TripEtaEstimator.estimateEtaSeconds(remainingMeters, effectiveSpeedMps())
                )
                _uiState.update {
                    it.copy(
                        displayPath = trimmed,
                        remainingDistanceMeters = remainingMeters,
                        estimatedTimeText = etaText
                    )
                }
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
            transitions = trimTransitions(fullPath.transitions, trimSegIdx),
            totalFloors = newSegments.map { it.floorId }.distinct().size,
            isMultiFloor = newSegments.map { it.floorId }.distinct().size > 1
        )
    }

    private fun trimTransitions(
        transitions: List<PathTransition>,
        trimmedSegmentStart: Int
    ): List<PathTransition> {
        return transitions.mapNotNull { t ->
            if (t.toSegmentIndex < trimmedSegmentStart) {
                null
            } else {
                t.copy(
                    fromSegmentIndex = (t.fromSegmentIndex - trimmedSegmentStart).coerceAtLeast(0),
                    toSegmentIndex = (t.toSegmentIndex - trimmedSegmentStart).coerceAtLeast(0)
                )
            }
        }
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
     * Computes remaining path distance in meters from the currently displayed path.
     * 1 campus unit = 2 cm, so meters = units * 0.02.
     */
    private fun computeRemainingDistanceMeters(path: MultiFloorPath): Int? {
        if (path.isEmpty) return null

        var units = 0f
        for (segment in path.segments) {
            for (i in 1 until segment.points.size) {
                val from = segment.points[i - 1]
                val to = segment.points[i]
                val dx = to.x - from.x
                val dy = to.y - from.y
                units += sqrt(dx * dx + dy * dy)
            }
        }

        val meters = (units * 0.02f).coerceAtLeast(0f)
        return meters.roundToInt().coerceAtLeast(1)
    }

    /**
     * Silently recalculates the path from the user's current position to the
     * original destination. The UI continues showing the old path until the
     * new one is ready, then swaps seamlessly.
     */
    private fun triggerSilentReroute(userPosition: Offset, currentFloor: String) {
        // Don't stack reroutes — cancel any previous reroute in progress
        if (rerouteJob?.isActive == true) return

        val now = System.currentTimeMillis()
        if (now - lastRerouteAtMs < REROUTE_COOLDOWN_MS) return
        lastRerouteAtMs = now

        val state = _uiState.value
        val goalEntrance = resolveGoalEntrance(state) ?: return

        Log.d(TAG, "Rerouting from (${userPosition.x}, ${userPosition.y}) floor=$currentFloor")

        rerouteJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                computeRouteWithDescendingReverse(
                    start = userPosition,
                    startFloorId = currentFloor,
                    goalEntrance = goalEntrance
                )
            }

            if (!result.isEmpty) {
                val subdivided = subdividePath(result)
                Log.d(TAG, "Reroute complete: ${subdivided.allPoints.size} waypoints")
                _uiState.update {
                    val remainingMeters = computeRemainingDistanceMeters(subdivided)
                    val etaText = TripEtaEstimator.formatEta(
                        TripEtaEstimator.estimateEtaSeconds(remainingMeters, effectiveSpeedMps())
                    )
                    it.copy(
                        multiFloorPath = subdivided,
                        displayPath = subdivided,
                        remainingDistanceMeters = remainingMeters,
                        estimatedTimeText = etaText
                    )
                }
                pendingRerouteAnnouncement = true
            } else {
                Log.w(TAG, "Reroute failed — keeping existing path")
            }
        }
    }

    // ──────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────

    /**
     * Computes a route from user -> destination, but when user is above destination
     * we run pathfinding in reverse (destination -> user) and flip the result.
     * This keeps descending behavior symmetric with ascending.
     */
    private fun computeRouteWithDescendingReverse(
        start: Offset,
        startFloorId: String,
        goalEntrance: CampusEntrance
    ): MultiFloorPath {
        val startFloorNumber = floorNumberOf(startFloorId)
        val goalFloorNumber = floorNumberOf(goalEntrance.floorId)

        // Normal direction for same-floor or upward travel.
        if (startFloorNumber <= goalFloorNumber) {
            return multiFloorPathfinder.findMultiFloorPath(
                start = start,
                startFloorId = startFloorId,
                goalEntrance = goalEntrance,
                allEntrances = campusEntrances.toList(),
                stairConnections = stairwellConnections,
                wallsByFloor = campusWallsByFloor.toMap(),
                boundaryByFloor = campusBoundaryByFloor.toMap(),
                repoByFloor = repositoryByFloor,
                precalculatedGrids = precalculatedGrids.toMap()
            )
        }

        // Descending: compute destination -> user, then reverse segments/points.
        val syntheticUserGoal = CampusEntrance(
            original = Entrance(
                id = -1,
                x = start.x,
                y = start.y,
                name = "user_position",
                floor = startFloorNumber,
                floorId = startFloorId
            ),
            campusX = start.x,
            campusY = start.y,
            // Building id is not used for geometry; keep destination building for compatibility.
            buildingId = goalEntrance.buildingId,
            floorId = startFloorId
        )

        Log.d(
            TAG,
            "Descending route: computing reverse ${goalEntrance.floorId} -> $startFloorId"
        )

        val reverse = multiFloorPathfinder.findMultiFloorPath(
            start = goalEntrance.campusOffset,
            startFloorId = goalEntrance.floorId,
            goalEntrance = syntheticUserGoal,
            allEntrances = campusEntrances.toList(),
            stairConnections = stairwellConnections,
            wallsByFloor = campusWallsByFloor.toMap(),
            boundaryByFloor = campusBoundaryByFloor.toMap(),
            repoByFloor = repositoryByFloor,
            precalculatedGrids = precalculatedGrids.toMap()
        )

        return reverseRoute(reverse)
    }

    /** Reverse floor order and per-segment waypoints to restore user -> destination direction. */
    private fun reverseRoute(path: MultiFloorPath): MultiFloorPath {
        if (path.isEmpty) return MultiFloorPath.EMPTY

        val oldSize = path.segments.size
        val reversedSegments = path.segments
            .asReversed()
            .map { seg -> seg.copy(points = seg.points.asReversed()) }

        val reversedTransitions = path.transitions
            .asReversed()
            .map { t ->
                val newFrom = (oldSize - 1) - t.toSegmentIndex
                val newTo = (oldSize - 1) - t.fromSegmentIndex
                t.copy(
                    fromSegmentIndex = newFrom,
                    toSegmentIndex = newTo,
                    fromFloorId = t.toFloorId,
                    toFloorId = t.fromFloorId,
                    direction = if (t.direction == TransitionDirection.UP) {
                        TransitionDirection.DOWN
                    } else {
                        TransitionDirection.UP
                    },
                    entryPoint = t.exitPoint,
                    exitPoint = t.entryPoint
                )
            }

        val distinctFloors = reversedSegments.map { it.floorId }.distinct().size
        return MultiFloorPath(
            segments = reversedSegments,
            transitions = reversedTransitions,
            totalFloors = distinctFloors,
            isMultiFloor = distinctFloors > 1
        )
    }

    private fun floorNumberOf(floorId: String): Float =
        floorId.removePrefix("floor_").toFloatOrNull() ?: 1f

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

    private fun resolveGoalEntrance(state: NavigationUiState): CampusEntrance? {
        val directGoal = state.directGoalCampus
        if (directGoal != null) {
            val floorId = state.directGoalFloorId ?: state.targetRoom?.floorId ?: "floor_1"
            val buildingId = state.directGoalBuildingId ?: state.targetRoom?.buildingId.orEmpty()
            return CampusEntrance(
                original = Entrance(
                    id = -1,
                    x = directGoal.x,
                    y = directGoal.y,
                    name = state.targetRoom?.name,
                    roomNo = state.targetRoom?.number?.toString(),
                    floorId = floorId,
                    floor = floorNumberOf(floorId)
                ),
                campusX = directGoal.x,
                campusY = directGoal.y,
                buildingId = buildingId,
                floorId = floorId
            )
        }
        return findEntranceForRoom(state.targetRoom ?: return null)
    }

    // ──────────────────────────────────────────────
    // Coordinate transform
    // ──────────────────────────────────────────────

    private fun rawToCampus(
        x: Float, y: Float,
        scale: Float, rotationDegrees: Float,
        offsetX: Float, offsetY: Float
    ): Pair<Float, Float> = CoordinateTransform.rawToCampus(x, y, scale, rotationDegrees, offsetX, offsetY)

    override fun onCleared() {
        persistSessionSpeedIfAvailable()
        super.onCleared()
        pathfindingJob?.cancel()
    }

    private fun effectiveSpeedMps(): Float {
        val sessionSpeed = if (sessionDistanceM >= USER_SPEED_MIN_DISTANCE_M && sessionActiveTimeS > 0f) {
            sessionDistanceM / sessionActiveTimeS
        } else {
            null
        }
        return (sessionSpeed ?: longTermSpeedMps)
            .coerceIn(0.6f, 2.2f)
    }

    private fun updateUserSpeedProfile(currentPosition: Offset) {
        val now = System.currentTimeMillis()
        val previousPos = lastObservedPosition
        val previousTs = lastObservedTimestampMs

        lastObservedPosition = currentPosition
        lastObservedTimestampMs = now

        if (previousPos == null || previousTs == null) return

        val dtS = ((now - previousTs).coerceAtLeast(1L)) / 1000f
        if (dtS <= 0f || dtS > 8f) return

        val dx = currentPosition.x - previousPos.x
        val dy = currentPosition.y - previousPos.y
        val movedUnits = sqrt(dx * dx + dy * dy)
        val movedM = movedUnits * 0.02f
        if (movedM < USER_SPEED_MIN_STEP_DISTANCE_M) return

        sessionDistanceM += movedM
        sessionActiveTimeS += dtS

        // Update long-term speed gradually once we have enough active movement.
        if (sessionDistanceM >= USER_SPEED_MIN_DISTANCE_M) {
            val sessionSpeed = (sessionDistanceM / sessionActiveTimeS).coerceIn(0.6f, 2.2f)
            longTermSpeedMps = (0.85f * longTermSpeedMps + 0.15f * sessionSpeed)
        }

        if (sessionDistanceM - lastPersistedDistanceM >= USER_SPEED_PERSIST_STEP_DISTANCE_M) {
            lastPersistedDistanceM = sessionDistanceM
            persistSessionSpeedIfAvailable()
        }
    }

    private fun persistSessionSpeedIfAvailable() {
        if (sessionActiveTimeS <= 0f || sessionDistanceM <= 0f) return
        val sessionSpeed = (sessionDistanceM / sessionActiveTimeS).coerceIn(0.6f, 2.2f)
        val longTerm = longTermSpeedMps.coerceIn(0.6f, 2.2f)
        val deltaDistance = (sessionDistanceM - persistedSessionDistanceM).coerceAtLeast(0f)
        val deltaTime = (sessionActiveTimeS - persistedSessionActiveTimeS).coerceAtLeast(0f)
        if (deltaDistance <= 0f || deltaTime <= 0f) return

        persistedSessionDistanceM = sessionDistanceM
        persistedSessionActiveTimeS = sessionActiveTimeS

        viewModelScope.launch {
            val existingDistance = settingsRepository.navUserSpeedTotalDistanceM.first()
            val existingTime = settingsRepository.navUserSpeedTotalActiveTimeS.first()
            settingsRepository.saveNavUserSpeedLastSessionMps(sessionSpeed)
            settingsRepository.saveNavUserSpeedLongTermMps(longTerm)
            settingsRepository.saveNavUserSpeedTotals(
                distanceM = existingDistance + deltaDistance,
                activeTimeS = existingTime + deltaTime
            )
        }
    }
}
