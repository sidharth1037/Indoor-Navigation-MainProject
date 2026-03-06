package `in`.project.enroute.feature.pdr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
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
    showDirectionCone: Boolean,
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
            // Dim everything when viewing a different floor
            val floorAlphaMultiplier = if (isOnCurrentFloor) 1f else 0.25f

            // Draw footstep icons — suppressed while on stairs (only the blue dot moves).
            if (!isOnStairs) {
                val historyPath = if (path.size > 1) path.dropLast(1) else emptyList()
                val displayPath = filterFootstepsForDisplay(historyPath)
                drawFootsteps(
                    displayPath = displayPath,
                    footstepPainter = footstepPainter,
                    floorAlpha = floorAlphaMultiplier
                )
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

            // Draw current location dot immediately.
            val markerPosition = currentPosition ?: path.last().position
            drawLocationDot(
                position = markerPosition,
                scale = canvasState.scale,
                alpha = floorAlphaMultiplier
            )

            // Draw heading cone only after a fresh heading sample arrives.
            if (showDirectionCone) {
                drawHeadingCone(
                    position = markerPosition,
                    heading = currentHeading,
                    scale = canvasState.scale,
                    alpha = floorAlphaMultiplier
                )
            }
        }
    }
}

/**
 * Draws a Google Maps–style location indicator: a wide translucent blue cone
 * with radial gradient fade for heading, a solid blue dot with white border
 * and subtle shadow. Sizes grow slightly as the user zooms in (sqrt scaling).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeadingCone(
    position: Offset,
    heading: Float,
    scale: Float,
    alpha: Float = 1f
) {
    // sqrt(scale) gives gentle growth on zoom: not constant, not fully proportional
    val s = 1f / sqrt(scale)
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

}

/** Draws only the blue location dot with white border + subtle shadow. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLocationDot(
    position: Offset,
    scale: Float,
    alpha: Float = 1f
) {
    val s = 1f / sqrt(scale)
    val dotRadius = 12f * s
    val borderWidth = 3.5f * s
    val shadowSpread = 4f * s

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
