package `in`.project.enroute.feature.navigation.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Draws a destination marker (filled circle with white inner dot) at the
 * given position.
 *
 * @param center     Campus-wide position to draw the marker.
 * @param color      Marker fill colour.
 * @param alpha      Opacity (1f = fully visible, use lower for off-floor markers).
 * @param canvasScale  Current canvas zoom — used to keep marker size constant on screen.
 */
internal fun DrawScope.drawDestinationMarker(
    center: Offset,
    color: Color,
    alpha: Float = 1f,
    canvasScale: Float,
    baseRadius: Float = 12f
) {
    val radius = baseRadius / canvasScale
    drawCircle(
        color = color.copy(alpha = alpha),
        radius = radius,
        center = center
    )
    drawCircle(
        color = Color.White.copy(alpha = alpha),
        radius = radius * 0.5f,
        center = center
    )
}
