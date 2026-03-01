package `in`.project.enroute.feature.pdr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
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
import kotlin.math.sqrt

/**
 * PDR path overlay that draws the tracked path on the canvas.
 * This is a separate composable to avoid redrawing the entire floor plan
 * when only the PDR path changes.
 *
 * All path positions are in campus-wide coordinates, matching the canvas
 * drawing space. No per-building offset is needed.
 *
 * Applies the same transformations as FloorPlanCanvas to stay aligned.
 */
@Composable
fun PdrPathOverlay(
    path: List<PathPoint>,
    currentHeading: Float,
    canvasState: CanvasState,
    modifier: Modifier = Modifier,
    isOnCurrentFloor: Boolean = true,
    /** When `true` the user is on stairs — footsteps are hidden, only the blue dot moves. */
    isOnStairs: Boolean = false,
    /** Live position for the direction cone. Falls back to path.last() when null. */
    currentPosition: Offset? = null
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
            
            // Only draw the last 10 steps (excluding only current position).
            // path[0] is the origin, path.last() is current position (shown as cone).
            val lastStepsCount = 10
            val historyPath = if (path.size > 1) path.dropLast(1) else emptyList()
            val displayPath = if (historyPath.size > lastStepsCount) {
                historyPath.takeLast(lastStepsCount)
            } else {
                historyPath
            }
            
            // Dim everything when viewing a different floor
            val floorAlphaMultiplier = if (isOnCurrentFloor) 1f else 0.25f

            // Draw footstep icons at each path point with variable opacity
            // Suppressed while on stairs — only the blue dot is shown.
            if (!isOnStairs) {
            displayPath.forEachIndexed { index, pathPoint ->
                val isRightFoot = (path.size - 1 - displayPath.size + index) % 2 == 0
                val point = pathPoint.position
                // Convert heading from radians to degrees
                // heading: 0 = North (up), positive = clockwise
                val headingDegrees = Math.toDegrees(pathPoint.heading.toDouble()).toFloat()
                
                // Calculate opacity: 70% (0.7f) for nearest to 10% (0.1f) for farthest
                // index 0 = farthest (oldest), index n-1 = nearest (newest)
                val alpha = (if (displayPath.size > 1) {
                    0.1f + (index.toFloat() / (displayPath.size - 1)) * 0.7f
                } else {
                    0.8f
                }) * floorAlphaMultiplier
                
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
            } // end if (!isOnStairs)
            
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

            // Draw direction cone at the live position (animated during stairs)
            val conePosition = currentPosition ?: path.last().position
            drawDirectionCone(
                position = conePosition,
                heading = currentHeading,
                scale = canvasState.scale,
                alpha = floorAlphaMultiplier
            )
        }
    }
}

/**
 * Draws a Google Maps–style location indicator: a wide translucent blue cone
 * with radial gradient fade for heading, a solid blue dot with white border
 * and subtle shadow. Sizes grow slightly as the user zooms in (sqrt scaling).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDirectionCone(
    position: Offset,
    heading: Float,
    scale: Float,
    alpha: Float = 1f
) {
    // sqrt(scale) gives gentle growth on zoom: not constant, not fully proportional
    val s = 1f / sqrt(scale)
    val dotRadius = 12f * s
    val borderWidth = 3.5f * s
    val shadowSpread = 4f * s
    val coneLength = 100f * s
    val coneHalfAngleDeg = 34f  // ~68° total spread

    val angleRad = heading

    // --- Build cone path ---
    val steps = 40
    val startAngle = angleRad - Math.toRadians(coneHalfAngleDeg.toDouble()).toFloat()
    val endAngle = angleRad + Math.toRadians(coneHalfAngleDeg.toDouble()).toFloat()
    val angleStep = (endAngle - startAngle) / steps

    val conePath = Path().apply {
        moveTo(position.x, position.y)
        for (i in 0..steps) {
            val a = startAngle + angleStep * i
            val px = position.x + coneLength * sin(a)
            val py = position.y - coneLength * cos(a)
            lineTo(px, py)
        }
        close()
    }

    // --- Shadow behind cone ---
    clipPath(conePath) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.10f * alpha),
                    Color.Black.copy(alpha = 0.04f * alpha),
                    Color.Transparent
                ),
                center = position,
                radius = coneLength
            ),
            radius = coneLength,
            center = position
        )
    }

    // --- Cone fill with radial gradient fade ---
    clipPath(conePath) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF4285F4).copy(alpha = 0.42f * alpha),
                    Color(0xFF4285F4).copy(alpha = 0.18f * alpha),
                    Color(0xFF4285F4).copy(alpha = 0f)
                ),
                center = position,
                radius = coneLength
            ),
            radius = coneLength,
            center = position
        )
    }

    // --- Shadow behind dot (radial gradient for soft glow) ---
    val shadowRadius = dotRadius + borderWidth + shadowSpread * 3f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.35f * alpha),
                Color.Black.copy(alpha = 0.15f * alpha),
                Color.Transparent
            ),
            center = position,
            radius = shadowRadius
        ),
        radius = shadowRadius,
        center = position
    )

    // --- White border ring ---
    drawCircle(
        color = Color.White.copy(alpha = alpha),
        radius = dotRadius + borderWidth,
        center = position
    )

    // --- Solid blue dot ---
    drawCircle(
        color = Color(0xFF4285F4).copy(alpha = alpha),
        radius = dotRadius,
        center = position
    )
}
