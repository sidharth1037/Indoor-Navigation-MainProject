package `in`.project.enroute.feature.floorplan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.project.enroute.data.model.Building
import `in`.project.enroute.data.model.FloorPlanData
import `in`.project.enroute.data.model.Room
import `in`.project.enroute.data.repository.FloorPlanRepository
import `in`.project.enroute.data.repository.LocalFloorPlanRepository
import `in`.project.enroute.feature.floorplan.rendering.CanvasState
import `in`.project.enroute.feature.floorplan.rendering.FloorPlanDisplayConfig
import `in`.project.enroute.feature.floorplan.state.BuildingState
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

/**
 * UI state for the floor plan feature.
 * Supports multiple buildings with independent floor states.
 */
data class FloorPlanUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    
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
    val pinnedRoom: Room? = null
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

    // Repository can be swapped for remote implementation later
    private val repository: FloorPlanRepository = LocalFloorPlanRepository(application)

    private val _uiState = MutableStateFlow(FloorPlanUiState())
    val uiState: StateFlow<FloorPlanUiState> = _uiState.asStateFlow()

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
                    
                    currentState.copy(
                        isLoading = false,
                        buildingStates = updatedBuildingStates,
                        // Set this as dominant if it's the first/only building
                        dominantBuildingId = currentState.dominantBuildingId ?: building.buildingId,
                        showFloorSlider = true
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
                labelPosition = metadata?.labelPosition
            )
            
            loadBuilding(building)
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
            
            // Check if the pinned room is still visible after floor change
            // If not, unpin it
            if (newState.pinnedRoom != null && !isRoomVisibleInFloorsToRender(newState.pinnedRoom, newState.allFloorsToRender)) {
                newState.copy(pinnedRoom = null)
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
            _uiState.update { state ->
                if (!state.isFollowingMode) state else state.copy(
                    canvasState = targetState,
                    isFollowingAnimating = false
                )
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
        _uiState.update { it.copy(canvasState = CanvasState(), pinnedRoom = null) }
    }
    
    /**
     * Places a pin on the given room. Replaces any existing pin.
     */
    fun pinRoom(room: Room) {
        _uiState.update { it.copy(pinnedRoom = room) }
    }
    
    /**
     * Removes the current pin.
     */
    fun clearPin() {
        _uiState.update { it.copy(pinnedRoom = null) }
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
        animationConfig: CenteringConfig = CenteringConfig()
    ) {
        viewModelScope.launch {
            val currentState = _uiState.value

            // Use dominant building when available; otherwise fall back to any loaded building
            val buildingState = currentState.dominantBuildingState
                ?: currentState.buildingStates.values.firstOrNull()
                ?: return@launch

            val currentFloorNumber = buildingState.currentFloorNumber
            val currentFloorData = buildingState.floors[currentFloorNumber]
            
            val floorPlanScale = currentFloorData?.metadata?.scale ?: 1f
            val floorPlanRotation = currentFloorData?.metadata?.rotation ?: 0f
            
            FollowingAnimator.animateToFloorPlanCoordinate(
                currentState = currentState.canvasState,
                targetX = x,
                targetY = y,
                targetScale = scale,
                floorPlanScale = floorPlanScale,
                floorPlanRotation = floorPlanRotation,
                screenWidth = currentState.screenWidth,
                screenHeight = currentState.screenHeight,
                config = animationConfig,
                onStateUpdate = { newState ->
                    updateCanvasState(newState, isFromGesture = false)
                }
            )
        }
    }
}