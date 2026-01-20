package `in`.project.enroute.feature.floorplan.rendering

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import `in`.project.enroute.data.model.FloorPlanData
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawBoundary
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawEntrances
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawRoomLabels
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawStairwells
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawWalls

/**
 * Display configuration for the floor plan rendering.
 * Note: scale and rotation come from FloorPlanData.metadata
 */
data class FloorPlanDisplayConfig(
    val showWalls: Boolean = true,
    val showStairwells: Boolean = true,
    val showEntrances: Boolean = true,
    val showRoomLabels: Boolean = true,
    val wallColor: Color = Color.Black,
    val stairwellColor: Color = Color(0xFFADD8E6),
    val backgroundColor: Color = Color.White,
    val boundaryColor: Color = Color(0xFFF5F5F5)
)

/**
 * Canvas state for pan/zoom/rotate gestures.
 */
data class CanvasState(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotation: Float = 0f
)

/**
 * Main floor plan canvas composable.
 * Handles rendering of all floor plan elements with gesture support.
 * Supports multi-floor rendering by stacking floors from bottom to current.
 *
 * @param floorsToRender List of floors to render from bottom to top (for multi-floor stacking)
 * @param canvasState Current canvas transformation state
 * @param onCanvasStateChange Callback when canvas state changes (gestures)
 * @param modifier Modifier for the canvas
 * @param displayConfig Configuration for what to show and how
 */
@Composable
fun FloorPlanCanvas(
    floorsToRender: List<FloorPlanData>,
    canvasState: CanvasState,
    onCanvasStateChange: (CanvasState) -> Unit,
    modifier: Modifier = Modifier,
    displayConfig: FloorPlanDisplayConfig = FloorPlanDisplayConfig()
) {
    if (floorsToRender.isEmpty()) return

    // Use rememberUpdatedState to capture latest state without restarting gesture handler
    val currentCanvasState = rememberUpdatedState(canvasState)
    val currentOnCanvasStateChange = rememberUpdatedState(onCanvasStateChange)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(displayConfig.backgroundColor)
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, rotationChange ->
                    val state = currentCanvasState.value
                    
                    // Heuristic: if rotation is dominant, ignore zoom; otherwise ignore rotation
                    // This prevents accidental zoom while rotating and vice versa
                    val effectiveZoom: Float
                    val effectiveRotationChange: Float
                    if (abs(rotationChange) > abs(zoom - 1f) * 60) {
                        effectiveRotationChange = rotationChange
                        effectiveZoom = 1f
                    } else {
                        effectiveRotationChange = 0f
                        effectiveZoom = zoom
                    }

                    val oldScale = state.scale
                    val newScale = (state.scale * effectiveZoom).coerceIn(0.1f, 10f)
                    val actualZoom = newScale / oldScale

                    // Calculate offset relative to centroid
                    val offsetFromCentroidX = state.offsetX - centroid.x
                    val offsetFromCentroidY = state.offsetY - centroid.y

                    // Scale the offset from centroid
                    val scaledOffsetFromCentroidX = offsetFromCentroidX * actualZoom
                    val scaledOffsetFromCentroidY = offsetFromCentroidY * actualZoom

                    // Rotate the offset around centroid
                    val angleRad = Math.toRadians(effectiveRotationChange.toDouble()).toFloat()
                    val cos = cos(angleRad)
                    val sin = sin(angleRad)
                    val rotatedOffsetFromCentroidX = scaledOffsetFromCentroidX * cos - scaledOffsetFromCentroidY * sin
                    val rotatedOffsetFromCentroidY = scaledOffsetFromCentroidX * sin + scaledOffsetFromCentroidY * cos

                    // Apply new offset = centroid + rotated/scaled offset + pan
                    val newOffsetX = centroid.x + rotatedOffsetFromCentroidX + pan.x
                    val newOffsetY = centroid.y + rotatedOffsetFromCentroidY + pan.y
                    val newRotation = state.rotation + effectiveRotationChange

                    currentOnCanvasStateChange.value(
                        state.copy(
                            scale = newScale,
                            offsetX = newOffsetX,
                            offsetY = newOffsetY,
                            rotation = newRotation
                        )
                    )
                }
            }
            .graphicsLayer(
                scaleX = canvasState.scale,
                scaleY = canvasState.scale,
                rotationZ = canvasState.rotation,
                translationX = canvasState.offsetX,
                translationY = canvasState.offsetY,
                transformOrigin = TransformOrigin(0f, 0f)
            )
    ) {
        val centerX = size.width / 2
        val centerY = size.height / 2

        translate(left = centerX, top = centerY) {
            // Render floors from bottom to top (stacked)
            // Each floor's boundary naturally occludes floors below it
            for ((index, floorData) in floorsToRender.withIndex()) {
                val isCurrentFloor = index == floorsToRender.size - 1
                val floorPlanScale = floorData.metadata.scale
                val floorPlanRotation = floorData.metadata.rotation

                // Draw boundary polygons to mask floors below
                drawBoundary(
                    boundaryPolygons = floorData.boundaryPolygons,
                    scale = floorPlanScale,
                    rotationDegrees = floorPlanRotation,
                    color = displayConfig.boundaryColor
                )

                // Draw stairwells with auto-generated light color based on floor number
                if (displayConfig.showStairwells) {
                    val stairwellColor = getFloorStairwellColor(floorData.floorId)
                    drawStairwells(
                        stairwells = floorData.stairwells,
                        scale = floorPlanScale,
                        rotationDegrees = floorPlanRotation,
                        color = stairwellColor
                    )
                }

                // Draw walls
                if (displayConfig.showWalls) {
                    drawWalls(
                        walls = floorData.walls,
                        scale = floorPlanScale,
                        rotationDegrees = floorPlanRotation,
                        color = displayConfig.wallColor
                    )
                }

                // Draw entrances for current floor only
                if (isCurrentFloor && displayConfig.showEntrances) {
                    drawEntrances(
                        entrances = floorData.entrances,
                        scale = floorPlanScale,
                        rotationDegrees = floorPlanRotation,
                        canvasScale = canvasState.scale,
                        canvasRotation = canvasState.rotation
                    )
                }

                // Draw room labels, but filter out rooms covered by floors above
                if (displayConfig.showRoomLabels) {
                    val floorsAbove = floorsToRender.subList(index + 1, floorsToRender.size)
                    val visibleRooms = filterVisibleRooms(
                        rooms = floorData.rooms,
                        roomFloorScale = floorPlanScale,
                        roomFloorRotation = floorPlanRotation,
                        floorsAbove = floorsAbove
                    )
                    
                    drawRoomLabels(
                        rooms = visibleRooms,
                        scale = floorPlanScale,
                        rotationDegrees = floorPlanRotation,
                        canvasScale = canvasState.scale,
                        canvasRotation = canvasState.rotation
                    )
                }
            }
        }
    }
}



/**
 * Generates a unique light color for each floor based on floor number.
 * Cycles through a palette of light colors.
 */
private fun getFloorStairwellColor(floorId: String): Color {
    // Extract floor number from floorId (e.g., "floor_1" -> 1, "floor_1.5" -> 1.5)
    val floorNumber = floorId.removePrefix("floor_").toFloatOrNull() ?: 1f
    
    // Palette of light colors
    val lightColors = listOf(
        "#FFB6C1", // Light pink
        "#B0E0E6", // Powder blue
        "#90EE90", // Light green
        "#FFE4B5", // Moccasin (light orange)
        "#DDA0DD", // Plum
        "#98D8C8", // Mint
        "#F7DC6F", // Light yellow
        "#BB8FCE"  // Light purple
    )
    
    // Use floor number to index into palette
    val index = (floorNumber * 10).toInt() % lightColors.size
    return parseHexColor(lightColors[index])
}

/**
 * Parses a hex color string (e.g., "#FFB6C1" or "#ADD8E6") to a Color object.
 */
private fun parseHexColor(hexColor: String): Color {
    return try {
        val hex = hexColor.removePrefix("#")
        Color(hex.toLong(16) or 0xFF000000)
    } catch (e: Exception) {
        Color(0xFFADD8E6) // Fallback to light blue
    }
}

/**
 * Filters rooms to only include those not covered by floors above.
 * A room is considered covered if its center point is inside any boundary polygon of a floor above.
 */
private fun filterVisibleRooms(
    rooms: List<`in`.project.enroute.data.model.Room>,
    roomFloorScale: Float,
    roomFloorRotation: Float,
    floorsAbove: List<FloorPlanData>
): List<`in`.project.enroute.data.model.Room> {
    if (floorsAbove.isEmpty()) {
        return rooms
    }

    val angleRad = Math.toRadians(roomFloorRotation.toDouble()).toFloat()
    val cosAngle = cos(angleRad)
    val sinAngle = sin(angleRad)

    return rooms.filter { room ->
        // Transform room center point to canvas coordinates
        val x = room.x * roomFloorScale
        val y = room.y * roomFloorScale
        val rotatedX = x * cosAngle - y * sinAngle
        val rotatedY = x * sinAngle + y * cosAngle

        // Check if this point is covered by any floor above
        !isPointCoveredByFloors(rotatedX, rotatedY, floorsAbove)
    }
}

/**
 * Checks if a point (in canvas coordinates) is inside any boundary polygon from the given floors.
 */
private fun isPointCoveredByFloors(
    x: Float,
    y: Float,
    floors: List<FloorPlanData>
): Boolean {
    for (floor in floors) {
        val scale = floor.metadata.scale
        val rotationDegrees = floor.metadata.rotation
        val angleRad = Math.toRadians(rotationDegrees.toDouble()).toFloat()
        val cosAngle = cos(angleRad)
        val sinAngle = sin(angleRad)

        for (polygon in floor.boundaryPolygons) {
            if (polygon.points.isEmpty()) continue

            // Transform polygon points to canvas coordinates
            val sortedPoints = polygon.points.sortedBy { it.id }
            val transformedPoints = sortedPoints.map { point ->
                val px = point.x * scale
                val py = point.y * scale
                val rotatedX = px * cosAngle - py * sinAngle
                val rotatedY = px * sinAngle + py * cosAngle
                Pair(rotatedX, rotatedY)
            }

            // Check if point is inside this polygon using ray casting algorithm
            if (isPointInPolygon(x, y, transformedPoints)) {
                return true
            }
        }
    }
    return false
}

/**
 * Ray casting algorithm to check if a point is inside a polygon.
 * Casts a ray from the point to the right and counts intersections with polygon edges.
 * Odd number of intersections = inside, even = outside.
 */
private fun isPointInPolygon(
    x: Float,
    y: Float,
    polygon: List<Pair<Float, Float>>
): Boolean {
    if (polygon.size < 3) return false

    var inside = false
    var j = polygon.size - 1

    for (i in polygon.indices) {
        val xi = polygon[i].first
        val yi = polygon[i].second
        val xj = polygon[j].first
        val yj = polygon[j].second

        // Check if ray from point (x,y) to the right intersects edge (i,j)
        val intersect = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / (yj - yi) + xi)

        if (intersect) {
            inside = !inside
        }

        j = i
    }

    return inside
}

