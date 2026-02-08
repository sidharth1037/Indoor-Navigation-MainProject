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
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawRoomLabels
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawStairwells
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawWalls
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawBuildingName
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawPin
import android.graphics.drawable.VectorDrawable

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
    displayConfig: FloorPlanDisplayConfig = FloorPlanDisplayConfig(),
    pinnedRoom: Room? = null,
    pinDrawable: VectorDrawable? = null,
    pinTintColor: Int = android.graphics.Color.BLACK,
    onRoomTap: (Room) -> Unit = {},
    onBackgroundTap: () -> Unit = {}
) {
    if (floorsToRender.isEmpty()) return

    // Use rememberUpdatedState to capture latest state without restarting gesture handler
    val currentCanvasState = rememberUpdatedState(canvasState)
    val currentOnCanvasStateChange = rememberUpdatedState(onCanvasStateChange)
    val canvasSize = remember { mutableStateOf(IntSize.Zero) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(displayConfig.backgroundColor)
            .onSizeChanged { canvasSize.value = it }
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    val cs = currentCanvasState.value
                    val size = canvasSize.value
                    if (size.width == 0 || size.height == 0) return@detectTapGestures

                    // Labels are hidden below this zoom – no hit detection needed
                    if (cs.scale < 0.48f) {
                        onBackgroundTap()
                        return@detectTapGestures
                    }

                    val topFloor = floorsToRender.lastOrNull() ?: return@detectTapGestures
                    val fpScale = topFloor.metadata.scale
                    val fpRotRad = Math.toRadians(topFloor.metadata.rotation.toDouble()).toFloat()
                    val fpCos = cos(fpRotRad)
                    val fpSin = sin(fpRotRad)

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
                    // Effective size in screen pixels
                    val screenTextSize = effectiveTextSize * cs.scale
                    // Approximate average character width & line height on screen
                    val charWidthPx = screenTextSize * 0.5f
                    val lineHeightPx = screenTextSize * 1.3f
                    // Small padding around the label so it's not pixel-perfect
                    val hitPadX = screenTextSize * 0.3f
                    val hitPadY = screenTextSize * 0.4f
                    val maxCharsPerLine = 15

                    // Forward-transform each room to screen space and compare
                    // against the raw tap position. The rectangular hitbox is
                    // always axis-aligned with the screen, matching the upright labels.
                    val candidates = topFloor.rooms.mapNotNull { room ->
                        if (room.name == null) return@mapNotNull null

                        // Build label text (same as renderer)
                        val labelText = if (room.number != null) {
                            "${room.number}: ${room.name}"
                        } else {
                            room.name
                        }

                        // Estimate line metrics
                        val numLines = if (labelText.length <= maxCharsPerLine) 1
                                       else ((labelText.length + maxCharsPerLine - 1) / maxCharsPerLine)
                        val maxLineChars = minOf(labelText.length, maxCharsPerLine)

                        // Hitbox half-dimensions in screen pixels
                        val halfW = (maxLineChars * charWidthPx) / 2f + hitPadX
                        val halfH = (numLines * lineHeightPx) / 2f + hitPadY

                        // Floor-plan transform (scale + rotation from metadata)
                        val rx = room.x * fpScale
                        val ry = room.y * fpScale
                        val fprX = rx * fpCos - ry * fpSin
                        val fprY = rx * fpSin + ry * fpCos

                        // Center translate (matches translate(centerX, centerY) in draw)
                        val cx = fprX + centerX
                        val cy = fprY + centerY

                        // Canvas scale
                        val sx = cx * cs.scale
                        val sy = cy * cs.scale

                        // Canvas rotation (around origin 0,0) + translation
                        val screenX = sx * canvasCos - sy * canvasSin + cs.offsetX
                        val screenY = sx * canvasSin + sy * canvasCos + cs.offsetY

                        // Screen-pixel rectangle, always axis-aligned
                        val dx = tapOffset.x - screenX
                        val dy = tapOffset.y - screenY
                        if (abs(dx) <= halfW && abs(dy) <= halfH) {
                            Pair(room, hypot(dx, dy))
                        } else null
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
                    val newScale = (state.scale * effectiveZoom).coerceIn(displayConfig.minZoom, displayConfig.maxZoom)
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

                // Draw stairwells
                if (displayConfig.showStairwells) {
                    drawStairwells(
                        stairwells = floorData.stairwells,
                        scale = floorPlanScale,
                        rotationDegrees = floorPlanRotation
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

            // Draw building name when room labels are hidden (low zoom)
            // Building name shows when zoom level is between 0.15 and 0.48
            if (canvasState.scale in 0.18f..<0.48f && floorsToRender.isNotEmpty()) {
                val topFloor = floorsToRender.last()
                drawBuildingName(
                    buildingName = topFloor.metadata.buildingName,
                    labelPosition = topFloor.metadata.labelPosition,
                    scale = topFloor.metadata.scale,
                    rotationDegrees = topFloor.metadata.rotation,
                    canvasScale = canvasState.scale,
                    canvasRotation = canvasState.rotation
                )
            }

            // Draw pin on the pinned room (if any)
            if (pinnedRoom != null && pinDrawable != null) {
                val topFloor = floorsToRender.last()
                drawPin(
                    pinX = pinnedRoom.x,
                    pinY = pinnedRoom.y,
                    scale = topFloor.metadata.scale,
                    rotationDegrees = topFloor.metadata.rotation,
                    canvasScale = canvasState.scale,
                    canvasRotation = canvasState.rotation,
                    pinDrawable = pinDrawable,
                    tintColor = pinTintColor
                )
            }
        }
    }
}



/**
 * Filters rooms to only include those not covered by floors above.
 * A room is considered covered if its center point is inside any boundary polygon of a floor above.
 */
private fun filterVisibleRooms(
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

