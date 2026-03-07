package `in`.project.enroute.feature.home.locationselection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import `in`.project.enroute.feature.floorplan.rendering.CanvasState
import kotlin.math.sqrt

/**
 * Overlay that draws a blue preview dot at the pending origin location.
 * 
 * Similar to the PDR location dot, but indicates a preview before confirmation.
 * Uses the same graphicsLayer + center-translate pattern as other overlays.
 */
@Composable
fun OriginPreviewOverlay(
    pendingOrigin: Offset?,
    canvasState: CanvasState,
    modifier: Modifier = Modifier
) {
    if (pendingOrigin == null) return

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
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        translate(left = centerX, top = centerY) {
            // Scale inversely with canvas zoom for consistent screen size
            val s = 1f / sqrt(canvasState.scale)
            val dotRadius = 12f * s
            val borderWidth = 3f * s

            // White border (shadow effect)
            drawCircle(
                color = Color.White,
                radius = dotRadius + borderWidth,
                center = pendingOrigin
            )

            // Blue dot
            drawCircle(
                color = Color(0xFF4285F4),
                radius = dotRadius,
                center = pendingOrigin
            )

            // Outer pulse ring
            drawCircle(
                color = Color(0xFF4285F4).copy(alpha = 0.3f),
                radius = dotRadius + 6f * s,
                center = pendingOrigin,
                style = Stroke(width = 2f * s)
            )
        }
    }
}
