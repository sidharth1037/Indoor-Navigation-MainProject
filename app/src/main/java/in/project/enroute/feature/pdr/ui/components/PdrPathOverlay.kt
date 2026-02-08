package `in`.project.enroute.feature.pdr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import `in`.project.enroute.R
import `in`.project.enroute.feature.floorplan.rendering.CanvasState
import `in`.project.enroute.feature.pdr.data.model.PathPoint
import kotlin.math.cos
import kotlin.math.sin

/**
 * PDR path overlay that draws the tracked path on the canvas.
 * This is a separate composable to avoid redrawing the entire floor plan
 * when only the PDR path changes.
 *
 * Applies the same transformations as FloorPlanCanvas to stay aligned.
 */
@Composable
fun PdrPathOverlay(
    path: List<PathPoint>,
    currentHeading: Float,
    canvasState: CanvasState,
    modifier: Modifier = Modifier
) {
    if (path.isEmpty()) return

    val footstepIcon = ImageVector.vectorResource(id = R.drawable.footstep)
    val footstepPainter = rememberVectorPainter(image = footstepIcon)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            // Match FloorPlanCanvas clipping so drawings don't bleed over UI/status bar
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
            val footstepSize = 30f //* canvasState.scale
            val halfSize = footstepSize / 2f
            
            // Only draw the last 10 steps (excluding current position)
            val lastStepsCount = 10
            val displayPath = if (path.size > lastStepsCount + 1) {
                path.takeLast(lastStepsCount + 1).dropLast(1)
            } else {
                path.dropLast(1)
            }
            
            // Draw footstep icons at each path point with variable opacity
            displayPath.forEachIndexed { index, pathPoint ->
                val isRightFoot = (path.size - 1 - displayPath.size + index) % 2 == 0
                val point = pathPoint.position
                // Convert heading from radians to degrees
                // heading: 0 = North (up), positive = clockwise
                val headingDegrees = Math.toDegrees(pathPoint.heading.toDouble()).toFloat()
                
                // Calculate opacity: 70% (0.7f) for nearest to 10% (0.1f) for farthest
                // index 0 = farthest (oldest), index n-1 = nearest (newest)
                val alpha = if (displayPath.size > 1) {
                    0.1f + (index.toFloat() / (displayPath.size - 1)) * 0.7f
                } else {
                    0.8f
                }
                
                // Small lateral offset to separate left and right feet
                val lateralOffset = 5f // Distance between left and right foot
                
                withTransform({
                    // 1. Move to the step location
                    translate(left = point.x, top = point.y)
                    // 2. Rotate to face heading direction
                    rotate(degrees = headingDegrees, pivot = Offset.Zero)
                    // 3. Apply lateral offset BEFORE mirroring
                    // Right foot: offset to the right (+x in rotated space)
                    // Left foot: offset to the left (-x in rotated space, but becomes +x after mirroring)
                    val xOffset = if (isRightFoot) lateralOffset else -lateralOffset
                    translate(left = xOffset, top = 0f)
                    // 4. Mirror for left foot (scale around center, i.e., Offset.Zero after translate)
                    if (!isRightFoot) {
                        scale(scaleX = -1f, scaleY = 1f, pivot = Offset.Zero)
                    }
                    // 5. Offset to center the footstep image
                    translate(left = -halfSize, top = -halfSize)
                }) {
                    with(footstepPainter) {
                        draw(Size(footstepSize, footstepSize), alpha = alpha)
                    }
                }
            }
            
            // Original red dot drawing (commented out)
            /*
            for (pathPoint in path) {
                drawCircle(
                    color = Color(0xFFE53935), // Red
                    radius = 8f / canvasState.scale,
                    center = pathPoint.position
                )
            }
            */

            // Draw direction cone at the last point
            val lastPathPoint = path.last()
            drawDirectionCone(
                position = lastPathPoint.position,
                heading = currentHeading,
                scale = canvasState.scale
            )
        }
    }
}

/**
 * Draws a direction cone (arrow) indicating the current heading direction.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDirectionCone(
    position: Offset,
    heading: Float,
    scale: Float
) {
    // Cone dimensions (scale-independent)
    val coneLength = 60f / scale
    val coneWidth = 30f
    val vertexRadius = 10f / scale

    // heading is in radians: 0 = North, positive = clockwise
    val angleRad = heading

    // Tip of the cone (forward direction)
    val tipX = position.x + coneLength * sin(angleRad)
    val tipY = position.y - coneLength * cos(angleRad)

    // Left edge of the cone base
    val leftAngleRad = angleRad + Math.toRadians(coneWidth.toDouble()).toFloat()
    val tipX1 = position.x + (coneLength * 0.6f) * sin(leftAngleRad)
    val tipY1 = position.y - (coneLength * 0.6f) * cos(leftAngleRad)

    // Right edge of the cone base
    val rightAngleRad = angleRad - Math.toRadians(coneWidth.toDouble()).toFloat()
    val tipX2 = position.x + (coneLength * 0.6f) * sin(rightAngleRad)
    val tipY2 = position.y - (coneLength * 0.6f) * cos(rightAngleRad)

    // Draw the cone triangle
    val conePath = Path().apply {
        moveTo(tipX, tipY)
        lineTo(tipX1, tipY1)
        lineTo(tipX2, tipY2)
        close()
    }

    // Fill with Google Maps blue
    drawPath(path = conePath, color = Color(0xFF4285F4), style = Fill)
    drawPath(path = conePath, color = Color(0x664285F4), style = Fill)

    // Vertex circle (solid blue dot at user position)
    drawCircle(
        color = Color(0xFF4285F4),
        radius = vertexRadius,
        center = position
    )

    // Outline
    drawPath(
        path = conePath,
        color = Color(0xFF1E88E5),
        style = Stroke(width = 2f / scale)
    )
}
