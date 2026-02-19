package `in`.project.enroute.feature.floorplan.utils

import `in`.project.enroute.data.model.BoundaryPolygon
import `in`.project.enroute.feature.floorplan.rendering.CanvasState
import `in`.project.enroute.feature.floorplan.state.BuildingState
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Represents the bounding box of a building in screen coordinates.
 */
@Suppress("unused") // Convenience properties for potential future use
data class ScreenBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * Utility object for calculating viewport and building visibility.
 * 
 * The approach:
 * 1. Transform building boundary points to screen coordinates
 * 2. Calculate bounding box of transformed points on screen
 * 3. Clip bounding box to screen bounds
 * 4. Calculate ratio of visible area to screen area
 */
object ViewportUtils {
    
    /**
     * Minimum zoom level required to show floor slider.
     * Below this level, building labels are shown instead.
     */
    const val MIN_ZOOM_FOR_SLIDER = 0.48f
    
    /**
     * Minimum ratio of screen area covered by building to show slider.
     * Building must cover at least this much of the screen.
     */
    const val MIN_SCREEN_COVERAGE_RATIO = 0.10f
    
    /**
     * Transforms a point from floor plan coordinates to screen coordinates.
     * Applies the full transformation chain matching FloorPlanCanvas rendering:
     * 1. Building scale and rotation (from metadata)
     * 2. Translate by screen center (done in Canvas draw scope)
     * 3. Canvas scale (graphicsLayer)
     * 4. Canvas rotation (graphicsLayer)
     * 5. Canvas translation/offset (graphicsLayer)
     * 
     * @param x Floor plan X coordinate
     * @param y Floor plan Y coordinate
     * @param buildingScale Scale from building metadata
     * @param buildingRotation Rotation from building metadata (degrees)
     * @param relativeX Building's relative X position on campus
     * @param relativeY Building's relative Y position on campus
     * @param canvasState Canvas transformation state
     * @param screenCenterX Screen center X
     * @param screenCenterY Screen center Y
     * @return Pair of (screenX, screenY)
     */
    private fun transformToScreen(
        x: Float,
        y: Float,
        buildingScale: Float,
        buildingRotation: Float,
        relativeX: Float = 0f,
        relativeY: Float = 0f,
        canvasState: CanvasState,
        screenCenterX: Float,
        screenCenterY: Float
    ): Pair<Float, Float> {
        // Step 1: Apply building scale
        val scaledX = x * buildingScale
        val scaledY = y * buildingScale
        
        // Step 2: Apply building rotation (from metadata)
        val buildingAngleRad = Math.toRadians(buildingRotation.toDouble()).toFloat()
        val buildingCos = cos(buildingAngleRad)
        val buildingSin = sin(buildingAngleRad)
        val rotatedX = scaledX * buildingCos - scaledY * buildingSin
        val rotatedY = scaledX * buildingSin + scaledY * buildingCos
        
        // Step 3: Add building relative position offset
        val offsetX = rotatedX + relativeX
        val offsetY = rotatedY + relativeY
        
        // Step 4: Add screen center (this happens in Canvas draw scope BEFORE graphicsLayer)
        val centeredX = offsetX + screenCenterX
        val centeredY = offsetY + screenCenterY
        
        // Step 5: Apply canvas scale (graphicsLayer with transformOrigin 0,0)
        val canvasScaledX = centeredX * canvasState.scale
        val canvasScaledY = centeredY * canvasState.scale
        
        // Step 6: Apply canvas rotation around origin (0,0)
        val canvasAngleRad = Math.toRadians(canvasState.rotation.toDouble()).toFloat()
        val canvasCos = cos(canvasAngleRad)
        val canvasSin = sin(canvasAngleRad)
        val canvasRotatedX = canvasScaledX * canvasCos - canvasScaledY * canvasSin
        val canvasRotatedY = canvasScaledX * canvasSin + canvasScaledY * canvasCos
        
        // Step 7: Apply canvas translation/offset
        val screenX = canvasRotatedX + canvasState.offsetX
        val screenY = canvasRotatedY + canvasState.offsetY
        
        return Pair(screenX, screenY)
    }
    
    /**
     * Calculates the screen bounds of a building after all transformations.
     * 
     * @param boundaryPolygons Building boundary polygons
     * @param buildingScale Scale from building metadata
     * @param buildingRotation Rotation from building metadata
     * @param canvasState Current canvas state
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @return ScreenBounds or null if no valid bounds
     */
    fun calculateBuildingScreenBounds(
        boundaryPolygons: List<BoundaryPolygon>,
        buildingScale: Float,
        buildingRotation: Float,
        relativeX: Float = 0f,
        relativeY: Float = 0f,
        canvasState: CanvasState,
        screenWidth: Float,
        screenHeight: Float
    ): ScreenBounds? {
        if (boundaryPolygons.isEmpty()) return null
        
        val screenCenterX = screenWidth / 2
        val screenCenterY = screenHeight / 2
        
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        
        for (polygon in boundaryPolygons) {
            for (point in polygon.points) {
                val (screenX, screenY) = transformToScreen(
                    x = point.x,
                    y = point.y,
                    buildingScale = buildingScale,
                    buildingRotation = buildingRotation,
                    relativeX = relativeX,
                    relativeY = relativeY,
                    canvasState = canvasState,
                    screenCenterX = screenCenterX,
                    screenCenterY = screenCenterY
                )
                
                minX = min(minX, screenX)
                minY = min(minY, screenY)
                maxX = max(maxX, screenX)
                maxY = max(maxY, screenY)
            }
        }
        
        if (minX == Float.MAX_VALUE) return null
        
        return ScreenBounds(
            left = minX,
            top = minY,
            right = maxX,
            bottom = maxY
        )
    }
    
    /**
     * Calculates the visible area of building bounds clipped to screen.
     * 
     * @param bounds Building screen bounds
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @return Visible area in square pixels
     */
    fun calculateVisibleScreenArea(
        bounds: ScreenBounds,
        screenWidth: Float,
        screenHeight: Float
    ): Float {
        // Clip to screen bounds
        val clippedLeft = max(0f, bounds.left)
        val clippedTop = max(0f, bounds.top)
        val clippedRight = min(screenWidth, bounds.right)
        val clippedBottom = min(screenHeight, bounds.bottom)
        
        // Check if there's no visible area
        if (clippedLeft >= clippedRight || clippedTop >= clippedBottom) {
            return 0f
        }
        
        return (clippedRight - clippedLeft) * (clippedBottom - clippedTop)
    }
    
    /**
     * Calculates the screen coverage ratio for a building.
     * 
     * @param buildingState Building state containing floor data
     * @param canvasState Current canvas state
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @return Ratio of screen covered by building (0.0 to 1.0)
     */
    fun calculateScreenCoverage(
        buildingState: BuildingState,
        canvasState: CanvasState,
        screenWidth: Float,
        screenHeight: Float
    ): Float {
        val screenArea = screenWidth * screenHeight
        if (screenArea <= 0) return 0f
        
        // Get boundary polygons from any floor (all floors share same boundary)
        val boundaryPolygons = buildingState.floors.values.firstOrNull()?.boundaryPolygons
            ?: return 0f
        
        val bounds = calculateBuildingScreenBounds(
            boundaryPolygons = boundaryPolygons,
            buildingScale = buildingState.building.scale,
            buildingRotation = buildingState.building.rotation,
            relativeX = buildingState.building.relativePosition.x,
            relativeY = buildingState.building.relativePosition.y,
            canvasState = canvasState,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        ) ?: return 0f
        
        val visibleArea = calculateVisibleScreenArea(bounds, screenWidth, screenHeight)
        
        return visibleArea / screenArea
    }
    
    /**
     * Determines which building is dominant on screen.
     * Returns the building with the largest screen coverage that meets the minimum threshold.
     * 
     * @param buildingStates Map of building IDs to their states
     * @param canvasState Current canvas transformation state
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     * @return The dominant building ID, or null if none meets criteria
     */
    fun findDominantBuilding(
        buildingStates: Map<String, BuildingState>,
        canvasState: CanvasState,
        screenWidth: Float,
        screenHeight: Float
    ): String? {
        // Don't show slider if zoomed out too far
        if (canvasState.scale < MIN_ZOOM_FOR_SLIDER) {
            return null
        }
        
        var dominantBuildingId: String? = null
        var maxCoverage = 0f
        
        for ((buildingId, buildingState) in buildingStates) {
            val coverage = calculateScreenCoverage(
                buildingState = buildingState,
                canvasState = canvasState,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )
            
            // Check if this building meets minimum coverage threshold
            if (coverage >= MIN_SCREEN_COVERAGE_RATIO && coverage > maxCoverage) {
                maxCoverage = coverage
                dominantBuildingId = buildingId
            }
        }
        
        return dominantBuildingId
    }
    
    /**
     * Checks if the floor slider should be visible based on current state.
     * 
     * @param canvasScale Current zoom level
     * @param dominantBuildingId ID of the dominant building (if any)
     * @return True if slider should be shown
     */
    fun shouldShowFloorSlider(
        canvasScale: Float,
        dominantBuildingId: String?
    ): Boolean {
        return canvasScale >= MIN_ZOOM_FOR_SLIDER && dominantBuildingId != null
    }
}
