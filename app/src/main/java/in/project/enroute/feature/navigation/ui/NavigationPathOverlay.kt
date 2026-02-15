package `in`.project.enroute.feature.navigation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import `in`.project.enroute.feature.floorplan.rendering.CanvasState

/**
 * Overlay canvas that draws the A* navigation path.
 *
 * Applies the same graphicsLayer transforms as the base floor-plan canvas and
 * PDR overlay so all layers stay pixel-aligned.
 *
 * All path coordinates are in campus-wide space — no per-building offset needed.
 *
 * @param path List of waypoints in campus-wide coordinates
 * @param canvasState Canvas transformation state (matching FloorPlanCanvas)
 */
@Composable
fun NavigationPathOverlay(
    path: List<Offset>,
    canvasState: CanvasState,
    modifier: Modifier = Modifier,
    pathColor: Color = Color(0xFF4285F4),       // Google Maps blue
    pathWidth: Float = 10f,
    destinationColor: Color = Color(0xFFEA4335) // Google Maps red
) {
    if (path.size < 2) return

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
                // Build the path polyline
                val linePath = Path().apply {
                    moveTo(path.first().x, path.first().y)
                    for (i in 1 until path.size) {
                        lineTo(path[i].x, path[i].y)
                    }
                }

                // Draw path outline (slightly wider, darker) for contrast
                drawPath(
                    path = linePath,
                    color = Color(0xFF1A73E8),
                    style = Stroke(
                        width = (pathWidth + 4f) / canvasState.scale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Draw the main path line
                drawPath(
                    path = linePath,
                    color = pathColor,
                    style = Stroke(
                        width = pathWidth / canvasState.scale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Destination marker at the last point
                val destination = path.last()
                val markerRadius = 12f / canvasState.scale
                drawCircle(
                    color = destinationColor,
                    radius = markerRadius,
                    center = destination
                )
                drawCircle(
                    color = Color.White,
                    radius = markerRadius * 0.5f,
                    center = destination
                )
        }
    }
}
