package `in`.project.enroute.feature.floorplan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.project.enroute.data.cache.CachedBuilding
import `in`.project.enroute.data.cache.CachedCampusData
import `in`.project.enroute.data.cache.FloorPlanCache
import `in`.project.enroute.data.model.CampusMetadata
import `in`.project.enroute.data.model.RelativePosition
import `in`.project.enroute.data.model.Building
import `in`.project.enroute.data.model.FloorPlanData
import `in`.project.enroute.data.model.Room
import `in`.project.enroute.data.repository.FloorPlanRepository
import `in`.project.enroute.data.repository.FirebaseFloorPlanRepository
import `in`.project.enroute.feature.floorplan.rendering.CanvasState
import `in`.project.enroute.feature.floorplan.rendering.FloorPlanDisplayConfig
import `in`.project.enroute.feature.floorplan.state.BuildingState
import `in`.project.enroute.feature.pdr.correction.CampusBuilding
import `in`.project.enroute.feature.pdr.correction.FloorConstraintData
import `in`.project.enroute.feature.pdr.correction.StairwellZone
import `in`.project.enroute.feature.navigation.data.PrecalculatedFloorGrid
import `in`.project.enroute.feature.navigation.data.StairwellConnection
import android.util.Log
import `in`.project.enroute.feature.floorplan.utils.FollowingAnimator
import `in`.project.enroute.feature.floorplan.utils.FollowingConfig
import `in`.project.enroute.feature.floorplan.utils.CenteringConfig
import `in`.project.enroute.feature.floorplan.utils.ViewportUtils
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import `in`.project.enroute.feature.settings.data.SettingsRepository
import `in`.project.enroute.feature.home.locationselection.CorridorPoint
import `in`.project.enroute.feature.home.locationselection.CorridorPointFinder

/**
 * Bounds of the campus background canvas in world coordinates.
 * Encompasses all buildings with padding.
 */
data class CampusBounds(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    @Suppress("unused") val centerX: Float get() = (left + right) / 2f
    @Suppress("unused") val centerY: Float get() = (top + bottom) / 2f
    val isEmpty: Boolean get() = width <= 0f || height <= 0f
}

/**
 * UI state for the floor plan feature.
 * Supports multiple buildings with independent floor states.
 */
data class FloorPlanUiState(
    val isLoading: Boolean = true,
    val error: String? = null,

    /**
     * Campus-level metadata (name, location, north bearing, etc.).
     * Loaded once when the campus is opened and persists for the session.
     */
    val campusMetadata: CampusMetadata = CampusMetadata(),
    
    /**
     * Map of building ID to its state (includes floors, current floor, etc.)
     */
    val buildingStates: Map<String, BuildingState> = emptyMap(),
    
    /**
     * ID of the currently dominant building (based on viewport visibility)
     * Null if no building is sufficiently visible
     */
    val dominantBuildingId: String? = null,
    
    /**
     * Whether the floor slider should be shown
     */
    val showFloorSlider: Boolean = false,
    
    /**
     * Canvas transformation state (pan, zoom, rotation)
     */
    val canvasState: CanvasState = CanvasState(),
    
    /**
     * Display configuration (visibility toggles, colors)
     */
    val displayConfig: FloorPlanDisplayConfig = FloorPlanDisplayConfig(),
    
    /**
     * Screen dimensions for viewport calculations
     */
    val screenWidth: Float = 0f,
    val screenHeight: Float = 0f,
    
    /**
     * Whether following mode is enabled (canvas follows user position/heading)
     */
    val isFollowingMode: Boolean = false,

    /**
     * True while the initial follow animation is running.
     * Allows UI to stay in "following" state without overriding the in-flight animation.
     */
    val isFollowingAnimating: Boolean = false,
    
    /**
     * Currently pinned room (shown as a pin on the canvas).
     * Null when no pin is displayed.
     */
    val pinnedRoom: Room? = null,

    /**
     * Controls whether the pinned room marker is drawn on map.
     * The room can remain selected (panel visible) while marker is hidden.
     */
    val showPinnedRoomOnMap: Boolean = true,
    
    /**
     * Bounds of the campus canvas encompassing all buildings.
     * Used for background rendering and pan constraints.
     */
    val campusBounds: CampusBounds = CampusBounds()
) {
    /**
     * Returns the state of the dominant building, if any.
     */
    val dominantBuildingState: BuildingState?
        get() = dominantBuildingId?.let { buildingStates[it] }
    
    /**
     * Returns all floors to render from all buildings.
     * Each building renders its floors up to its current floor level.
     */
    val allFloorsToRender: List<FloorPlanData>
        get() = buildingStates.values.flatMap { it.floorsToRender }
    
    /**
     * Returns floor numbers for the dominant building's slider.
     */
    val sliderFloorNumbers: List<Float>
        get() = dominantBuildingState?.availableFloorNumbers ?: emptyList()
    
    /**
     * Returns current floor number for the dominant building.
     */
    val sliderCurrentFloor: Float
        get() = dominantBuildingState?.currentFloorNumber ?: 1f
    
    /**
     * Returns the name of the dominant building for display.
     */
    val sliderBuildingName: String
        get() = dominantBuildingState?.building?.buildingName ?: ""

    /**
     * Returns the floorId of the current floor in the dominant building (e.g. "floor_1").
     */
    val currentFloorId: String?
        get() = dominantBuildingState?.currentFloorData?.floorId

    /**
     * Returns the current floor's FloorPlanData (walls, entrances, etc.) from the dominant building.
     */
    @Suppress("unused")
    val currentFloorData: FloorPlanData?
        get() = dominantBuildingState?.currentFloorData

    /**
     * Returns all loaded FloorPlanData across all buildings and floors.
     */
    val allLoadedFloors: List<FloorPlanData>
        get() = buildingStates.values.flatMap { it.floors.values }
}

/**
 * ViewModel for floor plan rendering.
 * Manages floor plan data loading and canvas state.
 * Supports multiple buildings with independent floor states.
 */
@Suppress("unused") // Contains API methods for future UI buttons
class FloorPlanViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val cache = FloorPlanCache(application.applicationContext)
    private val settingsRepository = SettingsRepository(application.applicationContext)

    // Always use Firebase backend
    private lateinit var repository: FloorPlanRepository

    /**
     * The campus ID currently being displayed.
     * Set via navigation argument when entering the Home screen.
     */
    private var currentCampusId: String = ""

    private val _uiState = MutableStateFlow(FloorPlanUiState())
    val uiState: StateFlow<FloorPlanUiState> = _uiState.asStateFlow()

    init {
        // Listen to entrance visibility preference and update display config
        viewModelScope.launch {
            settingsRepository.showEntrances.collect { showEntrances ->
                _uiState.update { 
                    it.copy(
                        displayConfig = it.displayConfig.copy(showEntrances = showEntrances)
                    )
                }
            }
        }
    }

    /**
     * Resolves the repository for the currently selected campus.
     * Always uses [FirebaseFloorPlanRepository].
     */
    private fun resolveRepository(): FloorPlanRepository {
        repository = FirebaseFloorPlanRepository(campusId = currentCampusId)
        return repository
    }

    /**
     * Loads a building with all its floors.
     * @param building The building configuration
     */
    fun loadBuilding(building: Building) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val floorsMap = mutableMapOf<Float, FloorPlanData>()
                val floorNumbers = mutableListOf<Float>()

                for (floorId in building.availableFloors) {
                    val floorPlanData = repository.loadFloorPlan(building.buildingId, floorId)
                    val floorNumber = extractFloorNumber(floorId)
                    floorsMap[floorNumber] = floorPlanData
                    floorNumbers.add(floorNumber)
                }

                val sortedFloorNumbers = floorNumbers.sorted()
                val lowestFloor = sortedFloorNumbers.firstOrNull() ?: 1f

                // Create BuildingState for this building
                val buildingState = BuildingState(
                    building = building,
                    floors = floorsMap,
                    availableFloorNumbers = sortedFloorNumbers,
                    currentFloorNumber = lowestFloor // Start at lowest floor
                )

                _uiState.update { currentState ->
                    val updatedBuildingStates = currentState.buildingStates.toMutableMap()
                    updatedBuildingStates[building.buildingId] = buildingState
                    
                    val campusBounds = calculateCampusBounds(updatedBuildingStates)
                    
                    currentState.copy(
                        isLoading = false,
                        buildingStates = updatedBuildingStates,
                        // Set this as dominant if it's the first/only building
                        dominantBuildingId = currentState.dominantBuildingId ?: building.buildingId,
                        showFloorSlider = true,
                        campusBounds = campusBounds
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load building: ${building.buildingId}"
                    )
                }
            }
        }
    }

    /**
     * Legacy method: Loads all floors for a building.
     * Creates a Building object internally for backwards compatibility.
     */
    fun loadAllFloors(buildingId: String, floorIds: List<String>) {
        viewModelScope.launch {
            // Load metadata to get building name
            val metadata = try {
                repository.loadBuildingMetadata(buildingId)
            } catch (e: Exception) {
                null
            }
            
            val building = Building(
                buildingId = buildingId,
                buildingName = metadata?.buildingName ?: buildingId,
                availableFloors = floorIds,
                scale = metadata?.scale ?: 1f,
                rotation = metadata?.rotation ?: 0f,
                labelPosition = metadata?.labelPosition,
                relativePosition = metadata?.relativePosition ?: RelativePosition()
            )
            
            loadBuilding(building)
        }
    }

    /**
     * Loads all buildings on the campus.
     *
     * Loading order:
     * 1. **In-memory** – if buildings are already loaded (e.g. returning from
     *    another tab), this is a no-op.
     * 2. **Disk cache** – when the backend mode is active, a cached JSON
     *    snapshot is checked first so the campus loads instantly without
     *    network calls.
     * 3. **Repository** – local assets or Firebase Firestore.
     *    After a successful backend fetch the data is persisted to the disk
     *    cache for subsequent launches.
     */
    fun loadCampus(campusId: String) {
        viewModelScope.launch {
            // ── 1. Already loaded (tab switch / recomposition) ──
            if (_uiState.value.buildingStates.isNotEmpty()) return@launch

            currentCampusId = campusId
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {

                // ── 2. Disk cache ──
                val cached = cache.loadCampusData(currentCampusId)
                if (cached != null) {
                    restoreFromCache(cached)
                    return@launch
                }

                // ── 3. Load from repository ──
                resolveRepository()

                val campusMetadata = repository.loadCampusMetadata()
                _uiState.update { it.copy(campusMetadata = campusMetadata) }

                val buildingIds = repository.getAvailableBuildings()
                val cachedBuildings = mutableListOf<CachedBuilding>()

                for (buildingId in buildingIds) {
                    val metadata = try {
                        repository.loadBuildingMetadata(buildingId)
                    } catch (_: Exception) {
                        null
                    }

                    val floorIds = repository.getAvailableFloors(buildingId)
                    if (floorIds.isEmpty()) continue

                    val building = Building(
                        buildingId = buildingId,
                        buildingName = metadata?.buildingName ?: buildingId,
                        availableFloors = floorIds,
                        scale = metadata?.scale ?: 1f,
                        rotation = metadata?.rotation ?: 0f,
                        labelPosition = metadata?.labelPosition,
                        relativePosition = metadata?.relativePosition ?: RelativePosition()
                    )

                    // Load all floors inline so we can capture them for caching
                    val floorsMap = mutableMapOf<Float, FloorPlanData>()
                    val floorNumbers = mutableListOf<Float>()
                    val floorDataList = mutableListOf<FloorPlanData>()

                    for (floorId in floorIds) {
                        val floorPlanData = repository.loadFloorPlan(buildingId, floorId)
                        val floorNumber = extractFloorNumber(floorId)
                        floorsMap[floorNumber] = floorPlanData
                        floorNumbers.add(floorNumber)
                        floorDataList.add(floorPlanData)
                    }

                    val sortedFloorNumbers = floorNumbers.sorted()
                    val lowestFloor = sortedFloorNumbers.firstOrNull() ?: 1f

                    val buildingState = BuildingState(
                        building = building,
                        floors = floorsMap,
                        availableFloorNumbers = sortedFloorNumbers,
                        currentFloorNumber = lowestFloor
                    )

                    _uiState.update { currentState ->
                        val updated = currentState.buildingStates.toMutableMap()
                        updated[buildingId] = buildingState
                        val bounds = calculateCampusBounds(updated)
                        currentState.copy(
                            isLoading = false,
                            buildingStates = updated,
                            dominantBuildingId = currentState.dominantBuildingId ?: buildingId,
                            showFloorSlider = true,
                            campusBounds = bounds
                        )
                    }

                    cachedBuildings.add(CachedBuilding(building, floorDataList))
                }

                // Persist to disk cache for next launch
                if (cachedBuildings.isNotEmpty()) {
                    cache.saveCampusData(
                        currentCampusId,
                        CachedCampusData(campusMetadata, cachedBuildings)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load campus"
                    )
                }
            }
        }
    }

    /**
     * Forces a fresh load from the repository, bypassing both the in-memory
     * state and the disk cache.  Call this when the user explicitly wants
     * to pull the latest data from the backend.
     */
    fun refreshCampus() {
        viewModelScope.launch {
            // Clear in-memory state so loadCampus() doesn't short-circuit
            _uiState.update {
                it.copy(
                    buildingStates = emptyMap(),
                    dominantBuildingId = null,
                    campusBounds = CampusBounds()
                )
            }
            // Clear disk cache
            cache.clearCache(currentCampusId)
            // Reload
            loadCampus(currentCampusId)
        }
    }

    /**
     * Restores the full UI state from a [CachedCampusData] snapshot.
     * Used when loading from the disk cache.
     */
    private fun restoreFromCache(cached: CachedCampusData) {
        _uiState.update { it.copy(campusMetadata = cached.campusMetadata) }

        for (cb in cached.buildings) {
            val floorsMap = mutableMapOf<Float, FloorPlanData>()
            val floorNumbers = mutableListOf<Float>()

            for (data in cb.floorDataList) {
                val num = extractFloorNumber(data.floorId)
                floorsMap[num] = data
                floorNumbers.add(num)
            }

            val sortedFloorNumbers = floorNumbers.sorted()
            val lowestFloor = sortedFloorNumbers.firstOrNull() ?: 1f

            val buildingState = BuildingState(
                building = cb.building,
                floors = floorsMap,
                availableFloorNumbers = sortedFloorNumbers,
                currentFloorNumber = lowestFloor
            )

            _uiState.update { state ->
                val updated = state.buildingStates.toMutableMap()
                updated[cb.building.buildingId] = buildingState
                val bounds = calculateCampusBounds(updated)
                state.copy(
                    isLoading = false,
                    buildingStates = updated,
                    dominantBuildingId = state.dominantBuildingId ?: cb.building.buildingId,
                    showFloorSlider = true,
                    campusBounds = bounds
                )
            }
        }
    }

    /**
     * Loads floor plan data for a single floor (legacy support).
     */
    fun loadFloorPlan(buildingId: String, floorId: String) {
        loadAllFloors(buildingId, listOf(floorId))
    }

    /**
     * Extracts the floor number from floor ID (e.g., "floor_1.5" -> 1.5f)
     */
    private fun extractFloorNumber(floorId: String): Float {
        return floorId.removePrefix("floor_").toFloatOrNull() ?: 1f
    }

    /**
     * Changes the current floor for a specific building.
     * @param buildingId The building to update
     * @param floorNumber The new floor number
     */
    fun setCurrentFloor(buildingId: String, floorNumber: Float) {
        _uiState.update { currentState ->
            val buildingState = currentState.buildingStates[buildingId] ?: return@update currentState
            
            if (floorNumber !in buildingState.availableFloorNumbers) return@update currentState
            
            val updatedBuildingState = buildingState.copy(currentFloorNumber = floorNumber)
            val updatedBuildingStates = currentState.buildingStates.toMutableMap()
            updatedBuildingStates[buildingId] = updatedBuildingState
            
            val newState = currentState.copy(buildingStates = updatedBuildingStates)

            // Keep selected room/panel across floor switches, but hide marker if it's not visible.
            val pinnedRoom = newState.pinnedRoom
            if (pinnedRoom != null) {
                val onRoomFloor = if (pinnedRoom.buildingId != null && pinnedRoom.floorId != null) {
                    val owner = newState.buildingStates[pinnedRoom.buildingId]
                    owner?.currentFloorData?.floorId == pinnedRoom.floorId
                } else {
                    pinnedRoom.floorId == null || pinnedRoom.floorId == newState.currentFloorId
                }

                val visibleOnCanvas = isRoomVisibleInFloorsToRender(pinnedRoom, newState.allFloorsToRender)
                newState.copy(showPinnedRoomOnMap = onRoomFloor && visibleOnCanvas)
            } else {
                newState
            }
        }
    }

    /**
     * Changes the current floor for the dominant building.
     * Used by the floor slider UI.
     */
    fun setCurrentFloor(floorNumber: Float) {
        val dominantBuildingId = _uiState.value.dominantBuildingId ?: return
        setCurrentFloor(dominantBuildingId, floorNumber)
    }

    /**
     * Switches to the floor matching the given floorId (e.g. "floor_1").
     * Searches all buildings for a floor with this ID and switches that building to it.
     * No-op if the floorId is null or not found, or if the user is outside all buildings.
     */
    fun switchToFloorById(floorId: String?) {
        if (floorId == null) return
        val state = _uiState.value
        for ((buildingId, buildingState) in state.buildingStates) {
            for ((floorNumber, floorData) in buildingState.floors) {
                if (floorData.floorId == floorId) {
                    setCurrentFloor(buildingId, floorNumber)
                    return
                }
            }
        }
    }

    /**
     * Checks if a room is visible in the given list of floors to render.
     * A room is visible if it exists in one of the floors and is not covered
     * by any floor above it.
     */
    private fun isRoomVisibleInFloorsToRender(room: Room, floorsToRender: List<FloorPlanData>): Boolean {
        if (floorsToRender.isEmpty()) return false
        
        for ((index, floorData) in floorsToRender.withIndex()) {
            // Check if room is in this floor
            if (room !in floorData.rooms) continue
            
            // Room is in this floor - now check if it's covered by floors above
            val floorsAbove = floorsToRender.subList(index + 1, floorsToRender.size)
            if (floorsAbove.isEmpty()) {
                // No floors above, so room is visible
                return true
            }
            
            // Check if room center point is covered by any floor above
            if (!isRoomCoveredByFloorsAbove(room, floorData, floorsAbove)) {
                return true
            }
        }
        
        return false
    }

    /**
     * Checks if a room's center point is covered by any of the given floors.
     * Uses the same point-in-polygon logic as the rendering system.
     */
    private fun isRoomCoveredByFloorsAbove(room: Room, roomFloor: FloorPlanData, floorsAbove: List<FloorPlanData>): Boolean {
        if (floorsAbove.isEmpty()) return false
        
        // Transform room center to canvas coordinates
        val angleRad = Math.toRadians(roomFloor.metadata.rotation.toDouble()).toFloat()
        val cosAngle = cos(angleRad)
        val sinAngle = sin(angleRad)
        
        val x = room.x * roomFloor.metadata.scale
        val y = room.y * roomFloor.metadata.scale
        val rotatedX = x * cosAngle - y * sinAngle
        val rotatedY = x * sinAngle + y * cosAngle
        
        // Check if this point is inside any boundary polygon of floors above
        for (floor in floorsAbove) {
            val scale = floor.metadata.scale
            val rotationDegrees = floor.metadata.rotation
            val floorAngleRad = Math.toRadians(rotationDegrees.toDouble()).toFloat()
            val floorCosAngle = cos(floorAngleRad)
            val floorSinAngle = sin(floorAngleRad)
            
            for (polygon in floor.boundaryPolygons) {
                if (polygon.points.isEmpty()) continue
                
                // Transform polygon points to canvas coordinates
                val transformedPoints = polygon.points.sortedBy { it.id }.map { point ->
                    val px = point.x * scale
                    val py = point.y * scale
                    val rotatedPx = px * floorCosAngle - py * floorSinAngle
                    val rotatedPy = px * floorSinAngle + py * floorCosAngle
                    Pair(rotatedPx, rotatedPy)
                }
                
                // Check if room center point is inside this polygon
                if (isPointInPolygon(rotatedX, rotatedY, transformedPoints)) {
                    return true
                }
            }
        }
        
        return false
    }

    /**
     * Ray casting algorithm to check if a point is inside a polygon.
     */
    private fun isPointInPolygon(x: Float, y: Float, polygon: List<Pair<Float, Float>>): Boolean {
        if (polygon.size < 3) return false
        
        var inside = false
        var j = polygon.size - 1
        
        for (i in polygon.indices) {
            val xi = polygon[i].first
            val yi = polygon[i].second
            val xj = polygon[j].first
            val yj = polygon[j].second
            
            val intersect = ((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)
            if (intersect) {
                inside = !inside
            }
            j = i
        }
        
        return inside
    }

    /**
     * Returns the floor ID at a campus-wide [point] by testing which building's
     * boundary polygon the point lies inside.
     *
     * When the user is zoomed out, there is no dominant building, so
     * [FloorPlanUiState.currentFloorId] can be null even though the user tapped
     * inside a building.  This function resolves the floor reliably regardless
     * of zoom level.
     *
     * @return The floor ID (e.g. "floor_1") of the building the point is inside,
     *         or the default floor of the first building if the point is outside
     *         all buildings (campus open area), or null if there are no buildings.
     */
    fun findFloorAtPoint(point: Offset): String? {
        val state = _uiState.value
        for ((_, buildingState) in state.buildingStates) {
            val building = buildingState.building
            val relX = building.relativePosition.x
            val relY = building.relativePosition.y
            val currentFloorData = buildingState.currentFloorData ?: continue

            val meta = currentFloorData.metadata
            val scale = meta.scale
            val rotRad = Math.toRadians(meta.rotation.toDouble()).toFloat()
            val cosA = cos(rotRad)
            val sinA = sin(rotRad)

            // Test campus-wide point against boundary polygons (in campus-wide space)
            for (polygon in currentFloorData.boundaryPolygons) {
                if (polygon.points.isEmpty()) continue
                val transformed = polygon.points.sortedBy { it.id }.map { p ->
                    val px = p.x * scale
                    val py = p.y * scale
                    Pair(px * cosA - py * sinA + relX, px * sinA + py * cosA + relY)
                }
                if (isPointInPolygon(point.x, point.y, transformed)) {
                    return currentFloorData.floorId
                }
            }
        }
        // Point is outside all buildings but inside campus bounds → use default floor
        return state.buildingStates.values.firstOrNull()?.currentFloorData?.floorId
    }

    /**
     * Returns the [FloorConstraintData] bundle for the floor containing [point]
     * (campus-wide coordinates). Used by PDR error correction to constraint-check
     * against walls and snap to entrances.
     *
     * @return Bundle with FloorPlanData + transform parameters, or `null` if no
     *         floor data is available.
     */
    fun getFloorConstraintData(point: Offset): FloorConstraintData? {
        val state = _uiState.value

        // Try to find the building whose boundary contains the point
        for ((_, buildingState) in state.buildingStates) {
            val building = buildingState.building
            val relX = building.relativePosition.x
            val relY = building.relativePosition.y
            val floorData = buildingState.currentFloorData ?: continue

            val meta = floorData.metadata
            val scale = meta.scale
            val rotRad = Math.toRadians(meta.rotation.toDouble()).toFloat()
            val cosA = cos(rotRad)
            val sinA = sin(rotRad)

            for (polygon in floorData.boundaryPolygons) {
                if (polygon.points.isEmpty()) continue
                val transformed = polygon.points.sortedBy { it.id }.map { p ->
                    val px = p.x * scale
                    val py = p.y * scale
                    Pair(px * cosA - py * sinA + relX, px * sinA + py * cosA + relY)
                }
                if (isPointInPolygon(point.x, point.y, transformed)) {
                    return FloorConstraintData(
                        floorPlanData = floorData,
                        scale = scale,
                        rotationDegrees = meta.rotation,
                        offsetX = relX,
                        offsetY = relY
                    )
                }
            }
        }

        // Fallback: first building's current floor
        val fallback = state.buildingStates.values.firstOrNull() ?: return null
        val floorData = fallback.currentFloorData ?: return null
        val building = fallback.building
        val meta = floorData.metadata
        return FloorConstraintData(
            floorPlanData = floorData,
            scale = meta.scale,
            rotationDegrees = meta.rotation,
            offsetX = building.relativePosition.x,
            offsetY = building.relativePosition.y
        )
    }

    /**
     * Validates if a point can be used for origin selection.
     * User can set origin:
     * 1. Inside the currently viewed floor's boundary, OR
     * 2. Outside all buildings (using first floor boundaries for check)
     *
     * parameter point Campus-wide coordinates
     * @return Validation result with success/failure and error type
     */
    enum class OriginErrorType {
        OUTSIDE_CAMPUS,
        INSIDE_BUILDING_ZOOMED_OUT,
        WRONG_FLOOR
    }
    
    data class OriginValidationResult(
        val isValid: Boolean,
        val errorType: OriginErrorType? = null
    )

    fun validateOriginLocation(point: Offset): OriginValidationResult {
        val state = _uiState.value
        
        // Check if point is inside campus bounds
        val bounds = state.campusBounds
        if (bounds.isEmpty || 
            point.x !in bounds.left..bounds.right ||
            point.y !in bounds.top..bounds.bottom) {
            return OriginValidationResult(
                isValid = false,
                errorType = OriginErrorType.OUTSIDE_CAMPUS
            )
        }

        // Helper to test if point is inside a floor boundary in campus-wide coordinates.
        fun isInsideFloor(buildingState: BuildingState, floorData: FloorPlanData): Boolean {
            val building = buildingState.building
            val relX = building.relativePosition.x
            val relY = building.relativePosition.y
            val meta = floorData.metadata
            val scale = meta.scale
            val rotRad = Math.toRadians(meta.rotation.toDouble()).toFloat()
            val cosA = cos(rotRad)
            val sinA = sin(rotRad)

            for (polygon in floorData.boundaryPolygons) {
                if (polygon.points.isEmpty()) continue
                val transformed = polygon.points.sortedBy { it.id }.map { p ->
                    val px = p.x * scale
                    val py = p.y * scale
                    Pair(px * cosA - py * sinA + relX, px * sinA + py * cosA + relY)
                }
                if (isPointInPolygon(point.x, point.y, transformed)) {
                    return true
                }
            }
            return false
        }

        // If the point is outside every building footprint, it's valid (campus open area).
        val insideAnyBuilding = state.buildingStates.values.any { buildingState ->
            val footprintFloor = buildingState.floors[1f]
                ?: buildingState.availableFloorNumbers.firstOrNull()?.let { buildingState.floors[it] }
                ?: buildingState.currentFloorData

            footprintFloor != null && isInsideFloor(buildingState, footprintFloor)
        }

        if (!insideAnyBuilding) {
            return OriginValidationResult(isValid = true)
        }

        // Point is inside a building. If no dominant floor is active, user is zoomed out.
        val dominantBuildingState = state.dominantBuildingState
        val currentFloorData = dominantBuildingState?.currentFloorData

        if (currentFloorData == null) {
            return OriginValidationResult(
                isValid = false,
                errorType = OriginErrorType.INSIDE_BUILDING_ZOOMED_OUT
            )
        }

        // Valid only when inside the currently viewed floor.
        if (isInsideFloor(dominantBuildingState, currentFloorData)) {
            return OriginValidationResult(isValid = true)
        }

        // Inside a building but not inside the current floor.
        return OriginValidationResult(
            isValid = false,
            errorType = OriginErrorType.WRONG_FLOOR
        )

    }

    /**
     * Returns floor constraint data for a specific floor ID across all buildings.
     * Useful when the user switches floors after PDR origin has been set.
     */
    fun getFloorConstraintDataByFloorId(floorId: String): FloorConstraintData? {
        val state = _uiState.value
        for ((_, buildingState) in state.buildingStates) {
            val floorData = buildingState.floors.values.find { it.floorId == floorId }
                ?: continue
            val building = buildingState.building
            val meta = floorData.metadata
            return FloorConstraintData(
                floorPlanData = floorData,
                scale = meta.scale,
                rotationDegrees = meta.rotation,
                offsetX = building.relativePosition.x,
                offsetY = building.relativePosition.y
            )
        }
        return null
    }

    /**
     * Builds a [List] of [CampusBuilding] from all loaded buildings.
     * Each building's first-floor boundary polygons are transformed into
     * campus-wide coordinates so the BuildingDetector can run point-in-polygon
     * checks without needing to know about per-building transforms.
     *
     * Returns an empty list if no building data is loaded yet.
     */
    fun getCampusBuildings(): List<CampusBuilding> {
        val state = _uiState.value
        val result = mutableListOf<CampusBuilding>()

        for ((_, buildingState) in state.buildingStates) {
            val building = buildingState.building
            // Use first floor (floor number 1) — for single-floor detection
            val floorData = buildingState.floors[1f] ?: buildingState.currentFloorData ?: continue
            if (floorData.boundaryPolygons.isEmpty()) continue

            val meta = floorData.metadata
            val scale = meta.scale
            val rotRad = Math.toRadians(meta.rotation.toDouble()).toFloat()
            val cosA = cos(rotRad)
            val sinA = sin(rotRad)
            val relX = building.relativePosition.x
            val relY = building.relativePosition.y

            val campusPolygons = floorData.boundaryPolygons.map { polygon ->
                polygon.points.sortedBy { it.id }.map { p ->
                    val px = p.x * scale
                    val py = p.y * scale
                    Pair(px * cosA - py * sinA + relX, px * sinA + py * cosA + relY)
                }
            }

            val constraintData = FloorConstraintData(
                floorPlanData = floorData,
                scale = scale,
                rotationDegrees = meta.rotation,
                offsetX = relX,
                offsetY = relY
            )

            result.add(
                CampusBuilding(
                    buildingId = building.buildingId,
                    floorId = floorData.floorId,
                    polygons = campusPolygons,
                    constraintData = constraintData
                )
            )
        }

        return result
    }

    /**
     * Builds [StairwellZone] objects for PDR boundary-based stair detection.
     * Delegates to [StairwellDataExtractor].
     */
    fun getStairwellZones(): List<StairwellZone> =
        StairwellDataExtractor.computeStairwellZones(_uiState.value.buildingStates)

    /**
     * Builds [StairwellConnection] objects for navigation cross-floor routing.
     * Delegates to [StairwellDataExtractor].
     */
    fun getStairwellConnections(): List<StairwellConnection> =
        StairwellDataExtractor.computeStairwellConnections(_uiState.value.buildingStates)

    /**
     * Loads precalculated navigation grids for the current campus.
     * Tries the disk cache first, falls back to Firebase.
     * Returns null if no precalculated data exists.
     */
    suspend fun loadPrecalculatedNavData(): Map<String, PrecalculatedFloorGrid>? {
        val campusId = currentCampusId.ifBlank { return null }
        try {
            // 1. Try disk cache first
            val cached = cache.loadCampusData(campusId)
            if (cached?.precalculatedGrids != null) {
                Log.d("FloorPlanViewModel", "Loaded ${cached.precalculatedGrids.size} precalculated grids from disk cache")
                return cached.precalculatedGrids
            }

            // 2. Fall back to Firebase
            val firebaseRepo = FirebaseFloorPlanRepository(campusId)
            val grids = firebaseRepo.loadAllPrecalculatedGrids()
            if (grids != null) {
                Log.d("FloorPlanViewModel", "Loaded ${grids.size} precalculated grids from Firebase")
                // Update the disk cache with the grids
                val existingCache = cache.loadCampusData(campusId)
                if (existingCache != null) {
                    cache.saveCampusData(campusId, existingCache.copy(precalculatedGrids = grids))
                }
            }
            return grids
        } catch (e: Exception) {
            Log.w("FloorPlanViewModel", "No precalculated nav data available: ${e.message}")
            return null
        }
    }

    /**
     * Returns [FloorConstraintData] for every loaded floor, keyed by floor ID.
     * Allows the PDR stairwell transition to load destination floor data
     * without going through the ViewModel.
     */
    fun getAllFloorConstraintData(): Map<String, FloorConstraintData> {
        val state = _uiState.value
        val result = mutableMapOf<String, FloorConstraintData>()

        for ((_, buildingState) in state.buildingStates) {
            val building = buildingState.building
            for ((_, floorData) in buildingState.floors) {
                val meta = floorData.metadata
                result[floorData.floorId] = FloorConstraintData(
                    floorPlanData = floorData,
                    scale = meta.scale,
                    rotationDegrees = meta.rotation,
                    offsetX = building.relativePosition.x,
                    offsetY = building.relativePosition.y
                )
            }
        }

        return result
    }

    /**
     * Moves to the next higher floor for the dominant building.
     */
    fun goToNextFloor() {
        val dominantBuildingId = _uiState.value.dominantBuildingId ?: return
        val buildingState = _uiState.value.buildingStates[dominantBuildingId] ?: return
        
        val current = buildingState.currentFloorNumber
        val available = buildingState.availableFloorNumbers
        val currentIndex = available.indexOf(current)
        
        if (currentIndex >= 0 && currentIndex < available.size - 1) {
            setCurrentFloor(dominantBuildingId, available[currentIndex + 1])
        }
    }

    /**
     * Moves to the next lower floor for the dominant building.
     */
    fun goToPreviousFloor() {
        val dominantBuildingId = _uiState.value.dominantBuildingId ?: return
        val buildingState = _uiState.value.buildingStates[dominantBuildingId] ?: return
        
        val current = buildingState.currentFloorNumber
        val available = buildingState.availableFloorNumbers
        val currentIndex = available.indexOf(current)
        
        if (currentIndex > 0) {
            setCurrentFloor(dominantBuildingId, available[currentIndex - 1])
        }
    }

    /**
     * Updates canvas state from gesture input.
     * Also recalculates dominant building based on new viewport.
     * Disables following mode when user manually gestures.
     */
    fun updateCanvasState(canvasState: CanvasState, isFromGesture: Boolean = true) {
        _uiState.update { currentState ->
            // Disable following mode if user manually pans/zooms
            val newFollowingMode = if (isFromGesture) false else currentState.isFollowingMode
            val newFollowingAnimating = if (isFromGesture) false else currentState.isFollowingAnimating
            val screenWidth = currentState.screenWidth
            val screenHeight = currentState.screenHeight
            
            // Recalculate dominant building based on new viewport
            val dominantBuildingId = if (screenWidth > 0 && screenHeight > 0) {
                ViewportUtils.findDominantBuilding(
                    buildingStates = currentState.buildingStates,
                    canvasState = canvasState,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight
                )
            } else {
                currentState.dominantBuildingId
            }
            
            val showFloorSlider = ViewportUtils.shouldShowFloorSlider(
                canvasScale = canvasState.scale,
                dominantBuildingId = dominantBuildingId
            )
            
            currentState.copy(
                canvasState = canvasState,
                dominantBuildingId = dominantBuildingId,
                showFloorSlider = showFloorSlider,
                isFollowingMode = newFollowingMode,
                isFollowingAnimating = newFollowingAnimating
            )
        }
    }
    
    /**
     * Enables following mode and centers on the given position.
     * In following mode, the canvas follows the user's position and rotates
     * so their heading always points up (like Google Maps navigation).
     *
     * @param position User's current position in world coordinates
     * @param headingRadians User's heading in radians (0 = north, positive = clockwise)
     * @param scale Zoom level for following mode (default 0.7)
     */
    fun enableFollowingMode(
        position: Offset,
        headingRadians: Float,
        scale: Float = 1f
    ) {
        viewModelScope.launch {
            val currentState = _uiState.value
            // Mark following as active immediately so gestures can cleanly cancel it
            _uiState.update { it.copy(isFollowingMode = true, isFollowingAnimating = true) }
            
            val targetState = FollowingAnimator.calculateFollowingState(
                worldPosition = position,
                headingRadians = headingRadians,
                scale = scale,
                screenWidth = currentState.screenWidth,
                screenHeight = currentState.screenHeight
            )
            
            // Animate to following position. Mark following mode once animation finishes
            FollowingAnimator.animateToState(
                currentState = currentState.canvasState,
                targetState = targetState,
                config = FollowingConfig(scale = scale),
                onStateUpdate = { newState ->
                    // Only apply animation frames while following is still enabled
                    _uiState.update { state ->
                        if (!state.isFollowingMode) state else state.copy(canvasState = newState)
                    }
                }
            )

            // Ensure final state and enable following mode after animation completes
            // Also recalculate dominant building and floor slider since we may have
            // zoomed in from a state where they were null/hidden
            _uiState.update { state ->
                if (!state.isFollowingMode) state else {
                    val dominant = if (state.screenWidth > 0 && state.screenHeight > 0) {
                        ViewportUtils.findDominantBuilding(
                            buildingStates = state.buildingStates,
                            canvasState = targetState,
                            screenWidth = state.screenWidth,
                            screenHeight = state.screenHeight
                        )
                    } else {
                        state.dominantBuildingId
                    }
                    val showSlider = ViewportUtils.shouldShowFloorSlider(
                        canvasScale = targetState.scale,
                        dominantBuildingId = dominant
                    )
                    state.copy(
                        canvasState = targetState,
                        isFollowingAnimating = false,
                        dominantBuildingId = dominant,
                        showFloorSlider = showSlider
                    )
                }
            }
        }
    }
    
    /**
     * Updates the canvas to follow the user's new position/heading.
     * Only updates if following mode is enabled.
     * This should be called on each step/heading change.
     *
     * @param position User's current position in world coordinates
     * @param headingRadians User's heading in radians
     */
    fun updateFollowingPosition(position: Offset, headingRadians: Float) {
        val currentState = _uiState.value
        if (!currentState.isFollowingMode) return
        
        val newState = FollowingAnimator.calculateFollowingState(
            worldPosition = position,
            headingRadians = headingRadians,
            scale = currentState.canvasState.scale, // Keep current zoom
            screenWidth = currentState.screenWidth,
            screenHeight = currentState.screenHeight
        )
        
        // Update immediately (no animation for smooth following)
        _uiState.update { it.copy(canvasState = newState) }
    }
    
    /**
     * Disables following mode and commits the current canvas state.
     * Pass the current effective canvas state to preserve the user's 
     * current view position/rotation when exiting following mode.
     *
     * @param finalCanvasState The canvas state to commit (typically the current following state)
     */
    fun disableFollowingMode(finalCanvasState: CanvasState? = null) {
        _uiState.update { 
            it.copy(
                canvasState = finalCanvasState ?: it.canvasState,
                isFollowingMode = false,
                isFollowingAnimating = false
            ) 
        }
    }

    /**
     * Updates screen dimensions for viewport calculations.
     * Should be called when screen size changes.
     */
    fun updateScreenSize(width: Float, height: Float) {
        _uiState.update { it.copy(screenWidth = width, screenHeight = height) }
    }

    /**
     * Updates display configuration (toggle visibility, colors, etc.)
     */
    fun updateDisplayConfig(displayConfig: FloorPlanDisplayConfig) {
        _uiState.update { it.copy(displayConfig = displayConfig) }
    }

    // Convenience methods for toggling individual display options

    fun toggleWalls() {
        _uiState.update {
            it.copy(displayConfig = it.displayConfig.copy(showWalls = !it.displayConfig.showWalls))
        }
    }

    fun toggleStairwells() {
        _uiState.update {
            it.copy(displayConfig = it.displayConfig.copy(showStairwells = !it.displayConfig.showStairwells))
        }
    }

    fun toggleEntrances() {
        _uiState.update {
            it.copy(displayConfig = it.displayConfig.copy(showEntrances = !it.displayConfig.showEntrances))
        }
    }

    fun toggleRoomLabels() {
        _uiState.update {
            it.copy(displayConfig = it.displayConfig.copy(showRoomLabels = !it.displayConfig.showRoomLabels))
        }
    }

    /**
     * Resets canvas to initial state.
     */
    fun resetCanvas() {
        _uiState.update { it.copy(canvasState = CanvasState(), pinnedRoom = null, showPinnedRoomOnMap = true) }
    }
    
    /**
     * Places a pin on the given room. Replaces any existing pin.
     */
    fun pinRoom(room: Room) {
        _uiState.update { it.copy(pinnedRoom = room, showPinnedRoomOnMap = true) }
    }
    
    /**
     * Removes the current pin.
     */
    fun clearPin() {
        _uiState.update { it.copy(pinnedRoom = null, showPinnedRoomOnMap = true) }
    }

    /**
     * Makes the current pinned room marker visible on map again.
     */
    fun showPinnedRoomOnMap() {
        _uiState.update { it.copy(showPinnedRoomOnMap = true) }
    }
    
    /**
     * Animates the canvas to center on a specific floor plan coordinate with a target zoom level.
     * Similar to how Google Maps centers on a location.
     * Maintains the current canvas rotation.
     *
     * @param x The x coordinate in floor plan space
     * @param y The y coordinate in floor plan space
     * @param scale The target zoom scale
     * @param animationConfig Configuration for animation duration and smoothness
     */
    fun centerOnCoordinate(
        x: Float,
        y: Float,
        scale: Float,
        buildingId: String? = null,
        animationConfig: CenteringConfig = CenteringConfig()
    ) {
        viewModelScope.launch {
            val currentState = _uiState.value

            // Use the specified building if provided; otherwise fall back to dominant or first
            val buildingState = (buildingId?.let { currentState.buildingStates[it] })
                ?: currentState.dominantBuildingState
                ?: currentState.buildingStates.values.firstOrNull()
                ?: return@launch

            val currentFloorNumber = buildingState.currentFloorNumber
            val currentFloorData = buildingState.floors[currentFloorNumber]
            
            val floorPlanScale = currentFloorData?.metadata?.scale ?: 1f
            val floorPlanRotation = currentFloorData?.metadata?.rotation ?: 0f
            val relPos = buildingState.building.relativePosition
            
            FollowingAnimator.animateToFloorPlanCoordinate(
                currentState = currentState.canvasState,
                targetX = x,
                targetY = y,
                targetScale = scale,
                floorPlanScale = floorPlanScale,
                floorPlanRotation = floorPlanRotation,
                screenWidth = currentState.screenWidth,
                screenHeight = currentState.screenHeight,
                buildingOffsetX = relPos.x,
                buildingOffsetY = relPos.y,
                config = animationConfig,
                onStateUpdate = { newState ->
                    updateCanvasState(newState, isFromGesture = false)
                }
            )
        }
    }
    
    /**
     * Calculates the campus bounds that encompass all buildings.
     * Each building's boundary polygons are transformed by their metadata (scale + rotation)
     * and offset by their relativePosition to find the overall extent.
     * Adds padding around the edges.
     */
    private fun calculateCampusBounds(buildingStates: Map<String, BuildingState>): CampusBounds {
        if (buildingStates.isEmpty()) return CampusBounds()
        
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var hasPoints = false
        
        for ((_, buildingState) in buildingStates) {
            val building = buildingState.building
            val relX = building.relativePosition.x
            val relY = building.relativePosition.y
            val scale = building.scale
            val rotation = building.rotation
            val angleRad = Math.toRadians(rotation.toDouble()).toFloat()
            val cosAngle = cos(angleRad)
            val sinAngle = sin(angleRad)
            
            // Check all boundary polygons from any floor
            for (floorData in buildingState.floors.values) {
                for (polygon in floorData.boundaryPolygons) {
                    for (point in polygon.points) {
                        val sx = point.x * scale
                        val sy = point.y * scale
                        val rx = sx * cosAngle - sy * sinAngle + relX
                        val ry = sx * sinAngle + sy * cosAngle + relY
                        
                        minX = kotlin.math.min(minX, rx)
                        minY = kotlin.math.min(minY, ry)
                        maxX = kotlin.math.max(maxX, rx)
                        maxY = kotlin.math.max(maxY, ry)
                        hasPoints = true
                    }
                }
            }
        }
        
        if (!hasPoints) return CampusBounds()
        
        // Add padding around the campus (20% of the largest dimension)
        val padding = kotlin.math.max(maxX - minX, maxY - minY) * 0.20f
        
        return CampusBounds(
            left = minX - padding,
            top = minY - padding,
            right = maxX + padding,
            bottom = maxY + padding
        )
    }

    /**
     * Finds corridor points (walkable spots in front of entrances) for a room.
     * Searches all buildings and floors for the room, matches entrances, and computes
     * perpendicular-to-wall corridor points in campus-wide coordinates.
     *
     * @return List of [CorridorPoint] in campus-wide coordinates. Empty if no entrances found.
     */
    fun findCorridorPointsForRoom(room: Room): List<CorridorPoint> {
        val state = _uiState.value
        val result = mutableListOf<CorridorPoint>()

        for ((_, buildingState) in state.buildingStates) {
            val building = buildingState.building

            // Filter by building: skip if room has a buildingId that doesn't match
            if (room.buildingId != null && room.buildingId != building.buildingId) continue

            for ((_, floorData) in buildingState.floors) {
                // Filter by floor: skip if room has a floorId that doesn't match
                if (room.floorId != null && room.floorId != floorData.floorId) continue

                // Only check floors that contain this room
                val hasRoom = floorData.rooms.any { r ->
                    r.id == room.id && r.x == room.x && r.y == room.y
                }
                if (!hasRoom) continue

                val meta = floorData.metadata
                val scale = meta.scale
                val rotDeg = meta.rotation
                val offX = building.relativePosition.x
                val offY = building.relativePosition.y

                val transform: (Float, Float) -> Pair<Float, Float> = { x, y ->
                    val sx = x * scale
                    val sy = y * scale
                    val rad = Math.toRadians(rotDeg.toDouble())
                    val cosA = cos(rad).toFloat()
                    val sinA = sin(rad).toFloat()
                    Pair(sx * cosA - sy * sinA + offX, sx * sinA + sy * cosA + offY)
                }

                val points = CorridorPointFinder.findCorridorPoints(
                    room = room,
                    entrances = floorData.entrances,
                    walls = floorData.walls,
                    rawToCampus = transform,
                    floorId = floorData.floorId,
                    buildingId = building.buildingId
                )
                result.addAll(points)
            }
        }

        return result
    }

    /**
     * Animates the canvas to center on a campus-wide coordinate with a target zoom level.
     * Unlike [centerOnCoordinate], this takes campus-wide (world) coordinates directly,
     * with no floor plan transform needed.
     */
    fun centerOnCampusCoordinate(
        campusX: Float,
        campusY: Float,
        scale: Float,
        config: CenteringConfig = CenteringConfig()
    ) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val targetState = FollowingAnimator.calculateCenterState(
                worldPosition = Offset(campusX, campusY),
                currentRotation = currentState.canvasState.rotation,
                scale = scale,
                screenWidth = currentState.screenWidth,
                screenHeight = currentState.screenHeight
            )
            FollowingAnimator.animateToState(
                currentState = currentState.canvasState,
                targetState = targetState,
                durationMs = config.durationMs,
                frameDelayMs = config.frameDelayMs,
                onStateUpdate = { updateCanvasState(it, isFromGesture = false) }
            )
        }
    }
}