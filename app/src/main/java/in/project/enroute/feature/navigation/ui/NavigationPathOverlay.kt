package `in`.project.enroute.feature.navigation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import `in`.project.enroute.feature.floorplan.rendering.CanvasState
import `in`.project.enroute.feature.navigation.data.MultiFloorPath

/**
 * Overlay canvas that draws the A* navigation path, supporting multi-floor
 * rendering.
 *
 * - Segments on [currentVisibleFloor] are drawn with full opacity.
 * - Segments on other floors are drawn faded + dashed, with a floor label.
 * - A destination marker is placed at the final waypoint.
 *
 * Drawing logic is delegated to:
 *  - [drawSegmentFull] / [drawSegmentFaded] in PathSegmentDrawing.kt
 *  - [drawDestinationMarker] in DestinationMarker.kt
 *
 * Applies the same graphicsLayer transforms as the base floor-plan canvas and
 * PDR overlay so all layers stay pixel-aligned.
 *
 * @param multiFloorPath       The computed multi-floor path.
 * @param currentVisibleFloor  The floor currently visible on the canvas.
 * @param canvasState          Canvas transformation state (matching FloorPlanCanvas).
 */
@Composable
fun NavigationPathOverlay(
    multiFloorPath: MultiFloorPath,
    currentVisibleFloor: String?,
    canvasState: CanvasState,
    modifier: Modifier = Modifier,
    pathColor: Color = Color(0xFF009688),       // Teal — distinct from blue PDR indicator
    pathWidth: Float = 10f,
    destinationColor: Color = Color(0xFFEA4335) // Google Maps red
) {
    if (multiFloorPath.isEmpty) return

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
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
            // Draw non-current-floor segments first (behind), then current floor on top
            for (segment in multiFloorPath.segments) {
                if (segment.floorId != currentVisibleFloor) {
                    drawSegmentFaded(segment, pathColor, pathWidth, canvasState.scale)
                }
            }
            for (segment in multiFloorPath.segments) {
                if (segment.floorId == currentVisibleFloor) {
                    drawSegmentFull(segment, pathColor, pathWidth, canvasState.scale)
                }
            }

            // Destination marker at the final waypoint of the last segment
            val lastSegment = multiFloorPath.segments.lastOrNull()
            val destination = lastSegment?.points?.lastOrNull()
            if (destination != null) {
                val isOnCurrentFloor = lastSegment.floorId == currentVisibleFloor
                drawDestinationMarker(
                    center = destination,
                    color = destinationColor,
                    alpha = if (isOnCurrentFloor) 1f else 0.35f,
                    canvasScale = canvasState.scale
                )
            }
        }
    }
}
