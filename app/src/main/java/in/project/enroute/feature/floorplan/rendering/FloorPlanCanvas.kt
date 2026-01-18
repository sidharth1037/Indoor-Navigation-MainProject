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
 *
 * @param floorPlanData The floor plan data to render
 * @param canvasState Current canvas transformation state
 * @param onCanvasStateChange Callback when canvas state changes (gestures)
 * @param modifier Modifier for the canvas
 * @param displayConfig Configuration for what to show and how
 */
@Composable
fun FloorPlanCanvas(
    floorPlanData: FloorPlanData,
    canvasState: CanvasState,
    onCanvasStateChange: (CanvasState) -> Unit,
    modifier: Modifier = Modifier,
    displayConfig: FloorPlanDisplayConfig = FloorPlanDisplayConfig()
) {
    // Get scale and rotation from metadata
    val floorPlanScale = floorPlanData.metadata.scale
    val floorPlanRotation = floorPlanData.metadata.rotation

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
                    val cos = kotlin.math.cos(angleRad)
                    val sin = kotlin.math.sin(angleRad)
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
            // Draw boundary polygon instead of background rectangle
            drawBoundary(
                boundaryPoints = floorPlanData.boundaryPoints,
                scale = floorPlanScale,
                rotationDegrees = floorPlanRotation,
                color = displayConfig.boundaryColor
            )

            // Draw stairwells first (below walls)
            if (displayConfig.showStairwells) {
                drawStairwells(
                    stairwells = floorPlanData.stairwells,
                    scale = floorPlanScale,
                    rotationDegrees = floorPlanRotation,
                    color = displayConfig.stairwellColor
                )
            }

            // Draw walls
            if (displayConfig.showWalls) {
                drawWalls(
                    walls = floorPlanData.walls,
                    scale = floorPlanScale,
                    rotationDegrees = floorPlanRotation,
                    color = displayConfig.wallColor
                )
            }

            // Draw entrances
            if (displayConfig.showEntrances) {
                drawEntrances(
                    entrances = floorPlanData.entrances,
                    scale = floorPlanScale,
                    rotationDegrees = floorPlanRotation,
                    canvasScale = canvasState.scale,
                    canvasRotation = canvasState.rotation
                )
            }

            // Draw room labels
            if (displayConfig.showRoomLabels) {
                drawRoomLabels(
                    rooms = floorPlanData.rooms,
                    scale = floorPlanScale,
                    rotationDegrees = floorPlanRotation,
                    canvasScale = canvasState.scale,
                    canvasRotation = canvasState.rotation
                )
            }
        }
    }
}
