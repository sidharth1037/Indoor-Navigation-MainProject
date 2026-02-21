package `in`.project.enroute.feature.floorplan.rendering

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.hypot
import androidx.compose.foundation.layout.fillMaxSize
import `in`.project.enroute.data.model.Room
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
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawStairwells
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawWalls
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawCampusBackground
import androidx.compose.ui.geometry.Offset
import `in`.project.enroute.feature.floorplan.utils.screenToWorldCoordinates
import `in`.project.enroute.feature.floorplan.state.BuildingState
import `in`.project.enroute.feature.floorplan.CampusBounds

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
    val backgroundColor: Color = Color.White,
    val boundaryColor: Color = Color(0xFFF5F5F5),
    val campusBackgroundColor: Color = Color(0xFFE8E8E8),
    val minZoom: Float = 0.15f,
    val maxZoom: Float = 2.2f
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
 * Supports multi-building rendering where each building is placed at its relative position.
 * A campus background canvas is drawn behind all buildings.
 *
 * @param buildingStates Map of building ID to its state with floors to render
 * @param campusBounds Bounds of the campus background canvas
 * @param canvasState Current canvas transformation state
 * @param onCanvasStateChange Callback when canvas state changes (gestures)
 * @param modifier Modifier for the canvas
 * @param displayConfig Configuration for what to show and how
 */
@Composable
fun FloorPlanCanvas(
    buildingStates: Map<String, BuildingState>,
    campusBounds: CampusBounds,
    canvasState: CanvasState,
    onCanvasStateChange: (CanvasState) -> Unit,
    modifier: Modifier = Modifier,
    displayConfig: FloorPlanDisplayConfig = FloorPlanDisplayConfig(),
    onRoomTap: (Room) -> Unit = {},
    onBackgroundTap: () -> Unit = {},
    isSelectingOrigin: Boolean = false,
    onOriginTap: ((Offset) -> Unit)? = null
) {
    if (buildingStates.isEmpty()) return

    // Use rememberUpdatedState to capture latest state without restarting gesture handler
    val currentCanvasState = rememberUpdatedState(canvasState)
    val currentOnCanvasStateChange = rememberUpdatedState(onCanvasStateChange)
    val currentBuildingStates = rememberUpdatedState(buildingStates)
    val currentCampusBounds = rememberUpdatedState(campusBounds)
    val currentIsSelectingOrigin = rememberUpdatedState(isSelectingOrigin)
    val currentOnOriginTap = rememberUpdatedState(onOriginTap)
    val canvasSize = remember { mutableStateOf(IntSize.Zero) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(displayConfig.campusBackgroundColor)
            .onSizeChanged { canvasSize.value = it }
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    // Origin selection mode: convert tap to world coordinates
                    if (currentIsSelectingOrigin.value) {
                        val size = canvasSize.value
                        if (size.width > 0 && size.height > 0) {
                            val worldPoint = screenToWorldCoordinates(
                                screenX = tapOffset.x,
                                screenY = tapOffset.y,
                                canvasState = currentCanvasState.value,
                                screenWidth = size.width.toFloat(),
                                screenHeight = size.height.toFloat()
                            )
                            // Only accept taps inside the campus bounds
                            val bounds = currentCampusBounds.value
                            if (!bounds.isEmpty &&
                                worldPoint.x in bounds.left..bounds.right &&
                                worldPoint.y in bounds.top..bounds.bottom
                            ) {
                                currentOnOriginTap.value?.invoke(worldPoint)
                            }
                        }
                        return@detectTapGestures
                    }

                    val cs = currentCanvasState.value
                    val size = canvasSize.value
                    if (size.width == 0 || size.height == 0) return@detectTapGestures

                    // Labels are hidden below this zoom – no hit detection needed
                    if (cs.scale < 0.48f) {
                        onBackgroundTap()
                        return@detectTapGestures
                    }

                    val buildings = currentBuildingStates.value
                    if (buildings.isEmpty()) return@detectTapGestures

                    val centerX = size.width / 2f
                    val centerY = size.height / 2f

                    val canvasRotRad = Math.toRadians(cs.rotation.toDouble()).toFloat()
                    val canvasCos = cos(canvasRotRad)
                    val canvasSin = sin(canvasRotRad)

                    // ---------- dynamic hitbox sizing (matches RoomLabelRenderer) ----------
                    val textSize = 30f
                    val minZoomConst = 0.76f
                    val effectiveTextSize = if (cs.scale >= minZoomConst) {
                        textSize / minZoomConst
                    } else {
                        textSize / cs.scale
                    }
                    val screenTextSize = effectiveTextSize * cs.scale
                    val charWidthPx = screenTextSize * 0.5f
                    val lineHeightPx = screenTextSize * 1.3f
                    val hitPadX = screenTextSize * 0.3f
                    val hitPadY = screenTextSize * 0.4f
                    val maxCharsPerLine = 15

                    val candidates = mutableListOf<Pair<Room, Float>>()

                    for ((_, buildingState) in buildings) {
                        val relX = buildingState.building.relativePosition.x
                        val relY = buildingState.building.relativePosition.y
                        val floors = buildingState.floorsToRender

                        for ((index, floorData) in floors.withIndex()) {
                            val floorsAbove = floors.subList(index + 1, floors.size)
                            val visibleRooms = filterVisibleRooms(
                                rooms = floorData.rooms,
                                roomFloorScale = floorData.metadata.scale,
                                roomFloorRotation = floorData.metadata.rotation,
                                floorsAbove = floorsAbove
                            )

                            val fpScale = floorData.metadata.scale
                            val fpRotRad = Math.toRadians(floorData.metadata.rotation.toDouble()).toFloat()
                            val fpCos = cos(fpRotRad)
                            val fpSin = sin(fpRotRad)

                            for (room in visibleRooms) {
                                if (room.name == null) continue

                                val labelText = if (room.number != null) {
                                    "${room.number}: ${room.name}"
                                } else {
                                    room.name
                                }

                                val numLines = if (labelText.length <= maxCharsPerLine) 1
                                               else ((labelText.length + maxCharsPerLine - 1) / maxCharsPerLine)
                                val maxLineChars = minOf(labelText.length, maxCharsPerLine)

                                val halfW = (maxLineChars * charWidthPx) / 2f + hitPadX
                                val halfH = (numLines * lineHeightPx) / 2f + hitPadY

                                // Floor-plan transform + relative position offset
                                val rx = room.x * fpScale
                                val ry = room.y * fpScale
                                val fprX = rx * fpCos - ry * fpSin + relX
                                val fprY = rx * fpSin + ry * fpCos + relY

                                // Center translate
                                val cx = fprX + centerX
                                val cy = fprY + centerY

                                // Canvas scale
                                val sx = cx * cs.scale
                                val sy = cy * cs.scale

                                // Canvas rotation (around origin 0,0) + translation
                                val screenX = sx * canvasCos - sy * canvasSin + cs.offsetX
                                val screenY = sx * canvasSin + sy * canvasCos + cs.offsetY

                                val dx = tapOffset.x - screenX
                                val dy = tapOffset.y - screenY
                                if (abs(dx) <= halfW && abs(dy) <= halfH) {
                                    candidates.add(Pair(room, hypot(dx, dy)))
                                }
                            }
                        }
                    }

                    val chosen = candidates.minByOrNull { it.second }?.first

                    if (chosen != null) {
                        onRoomTap(chosen)
                    } else {
                        onBackgroundTap()
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, rotationChange ->
                    val state = currentCanvasState.value
                    
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
                    val newScale = (state.scale * effectiveZoom).coerceIn(displayConfig.minZoom, displayConfig.maxZoom)
                    val actualZoom = newScale / oldScale

                    val offsetFromCentroidX = state.offsetX - centroid.x
                    val offsetFromCentroidY = state.offsetY - centroid.y

                    val scaledOffsetFromCentroidX = offsetFromCentroidX * actualZoom
                    val scaledOffsetFromCentroidY = offsetFromCentroidY * actualZoom

                    val angleRad = Math.toRadians(effectiveRotationChange.toDouble()).toFloat()
                    val cosA = cos(angleRad)
                    val sinA = sin(angleRad)
                    val rotatedOffsetFromCentroidX = scaledOffsetFromCentroidX * cosA - scaledOffsetFromCentroidY * sinA
                    val rotatedOffsetFromCentroidY = scaledOffsetFromCentroidX * sinA + scaledOffsetFromCentroidY * cosA

                    var newOffsetX = centroid.x + rotatedOffsetFromCentroidX + pan.x
                    var newOffsetY = centroid.y + rotatedOffsetFromCentroidY + pan.y
                    val newRotation = state.rotation + effectiveRotationChange

                    // Constrain pan to campus bounds
                    val bounds = currentCampusBounds.value
                    if (!bounds.isEmpty) {
                        val screenSize = canvasSize.value
                        val constrained = constrainPanToBounds(
                            offsetX = newOffsetX,
                            offsetY = newOffsetY,
                            scale = newScale,
                            rotation = newRotation,
                            campusBounds = bounds,
                            screenWidth = screenSize.width.toFloat(),
                            screenHeight = screenSize.height.toFloat()
                        )
                        newOffsetX = constrained.first
                        newOffsetY = constrained.second
                    }

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
            // Draw campus background canvas (light gray rectangle behind all buildings)
            drawCampusBackground(
                campusBounds = campusBounds,
                color = displayConfig.campusBackgroundColor
            )

            // Render each building at its relative position
            for ((_, buildingState) in buildingStates) {
                val building = buildingState.building
                val relX = building.relativePosition.x
                val relY = building.relativePosition.y
                val floorsToRender = buildingState.floorsToRender

                // Offset all drawing for this building by its relative position
                translate(left = relX, top = relY) {
                    // Render floors from bottom to top (stacked)
                    for ((index, floorData) in floorsToRender.withIndex()) {
                        val isCurrentFloor = index == floorsToRender.size - 1
                        val floorPlanScale = floorData.metadata.scale
                        val floorPlanRotation = floorData.metadata.rotation

                        drawBoundary(
                            boundaryPolygons = floorData.boundaryPolygons,
                            scale = floorPlanScale,
                            rotationDegrees = floorPlanRotation,
                            color = displayConfig.boundaryColor
                        )

                        if (displayConfig.showStairwells) {
                            drawStairwells(
                                stairwells = floorData.stairwells,
                                scale = floorPlanScale,
                                rotationDegrees = floorPlanRotation
                            )
                        }

                        if (displayConfig.showWalls) {
                            drawWalls(
                                walls = floorData.walls,
                                scale = floorPlanScale,
                                rotationDegrees = floorPlanRotation,
                                color = displayConfig.wallColor
                            )
                        }

                        if (isCurrentFloor && displayConfig.showEntrances) {
                            drawEntrances(
                                entrances = floorData.entrances,
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
    }
}



/**
 * Filters rooms to only include those not covered by floors above.
 * A room is considered covered if its center point is inside any boundary polygon of a floor above.
 */
internal fun filterVisibleRooms(
    rooms: List<Room>,
    roomFloorScale: Float,
    roomFloorRotation: Float,
    floorsAbove: List<FloorPlanData>
): List<Room> {
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

/**
 * Constrains pan offsets so the campus bounds remain at least partially visible on screen.
 * Transforms the four corners of the campus bounds through the full transform chain
 * and ensures their axis-aligned bounding box overlaps the screen.
 *
 * This prevents the user from panning the entire campus off-screen.
 */
private fun constrainPanToBounds(
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    rotation: Float,
    campusBounds: CampusBounds,
    screenWidth: Float,
    screenHeight: Float
): Pair<Float, Float> {
    if (screenWidth <= 0f || screenHeight <= 0f) return Pair(offsetX, offsetY)
    
    val centerX = screenWidth / 2f
    val centerY = screenHeight / 2f
    
    val rotRad = Math.toRadians(rotation.toDouble()).toFloat()
    val cosR = cos(rotRad)
    val sinR = sin(rotRad)
    
    // Transform campus corner to screen coordinates
    fun toScreen(worldX: Float, worldY: Float): Pair<Float, Float> {
        // Inner translate: world + center
        val cx = worldX + centerX
        val cy = worldY + centerY
        // Scale
        val sx = cx * scale
        val sy = cy * scale
        // Rotate
        val rx = sx * cosR - sy * sinR
        val ry = sx * sinR + sy * cosR
        // Translate
        return Pair(rx + offsetX, ry + offsetY)
    }
    
    // Transform all 4 corners
    val corners = listOf(
        toScreen(campusBounds.left, campusBounds.top),
        toScreen(campusBounds.right, campusBounds.top),
        toScreen(campusBounds.right, campusBounds.bottom),
        toScreen(campusBounds.left, campusBounds.bottom)
    )
    
    // Find axis-aligned bounding box of transformed corners
    var minSX = Float.MAX_VALUE
    var minSY = Float.MAX_VALUE
    var maxSX = Float.MIN_VALUE
    var maxSY = Float.MIN_VALUE
    for ((sx, sy) in corners) {
        minSX = kotlin.math.min(minSX, sx)
        minSY = kotlin.math.min(minSY, sy)
        maxSX = kotlin.math.max(maxSX, sx)
        maxSY = kotlin.math.max(maxSY, sy)
    }
    
    // Constrain: the campus bounding box must overlap the screen
    // If campus is smaller than screen in either dimension, center it
    var adjustX = 0f
    var adjustY = 0f
    
    val campusScreenWidth = maxSX - minSX
    val campusScreenHeight = maxSY - minSY
    
    if (campusScreenWidth <= screenWidth) {
        // Campus fits in screen: center it horizontally
        val campusCenterX = (minSX + maxSX) / 2f
        val screenCenterX = screenWidth / 2f
        adjustX = screenCenterX - campusCenterX
    } else {
        // Campus larger than screen: don't let it pan past edges
        if (minSX > 0f) adjustX = -minSX
        if (maxSX < screenWidth) adjustX = screenWidth - maxSX
    }
    
    if (campusScreenHeight <= screenHeight) {
        val campusCenterY = (minSY + maxSY) / 2f
        val screenCenterY = screenHeight / 2f
        adjustY = screenCenterY - campusCenterY
    } else {
        if (minSY > 0f) adjustY = -minSY
        if (maxSY < screenHeight) adjustY = screenHeight - maxSY
    }
    
    return Pair(offsetX + adjustX, offsetY + adjustY)
}
