package `in`.project.enroute.feature.home.locationselection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import `in`.project.enroute.feature.floorplan.rendering.CanvasState

/**
 * Configurable constants for entrance marker rendering.
 * All sizes are in screen units (they remain constant irrespective of zoom level).
 */
object EntranceMarkerConfig {
    /** Radius of the main circle in screen units. */
    var MARKER_RADIUS = 36f

    /** Stroke width of the main circle outline. */
    var RING_STROKE_WIDTH = 5f

    /** Gap between main circle and selection ring. */
    var SELECTION_RING_GAP = 8f

    /** Stroke width of the outer selection ring. */
    var SELECTION_RING_STROKE = 5f

    /** Text size for the number label inside the marker (screen units). */
    var NUMBER_TEXT_SIZE = 34f
}

/**
 * Canvas overlay that draws numbered circle markers at corridor point positions.
 *
 * Uses the same graphicsLayer + center-translate pattern as PdrPathOverlay and
 * NavigationPathOverlay so markers stay aligned with the floor plan.
 *
 * Corridor points are in campus-wide coordinates — no per-building offset needed.
 */
@Composable
fun EntranceMarkerOverlay(
    corridorPoints: List<CorridorPoint>,
    selectedIndex: Int?,
    canvasState: CanvasState,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

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
            for ((idx, point) in corridorPoints.withIndex()) {
                val isSelected = selectedIndex == idx
                drawMarker(
                    center = point.campusPosition,
                    index = point.index,
                    isSelected = isSelected,
                    isSingle = corridorPoints.size == 1,
                    canvasRotation = canvasState.rotation,
                    canvasScale = canvasState.scale,
                    primaryColor = primaryColor,
                    backgroundColor = backgroundColor
                )
            }
        }
    }
}

/**
 * Draws a single entrance marker — numbered circle with outer selection ring when selected.
 * Sizes are divided by an effective scale to appear constant on screen down to a certain
 * zoom level. Below a scale of 0.4f (when room labels disappear), the effective scale
 * is capped, which causes the markers to scale down (shrink) along with the canvas.
 */
private fun DrawScope.drawMarker(
    center: Offset,
    index: Int,
    isSelected: Boolean,
    isSingle: Boolean,
    canvasRotation: Float,
    canvasScale: Float,
    primaryColor: Color,
    backgroundColor: Color
) {
    // When canvasScale drops below 0.4f, we stop increasing the inverse scale,
    // meaning the markers will start shrinking proportionally with the canvas.
    val effectiveScale = canvasScale.coerceAtLeast(0.4f)

    val radius = EntranceMarkerConfig.MARKER_RADIUS / effectiveScale
    val strokeWidth = EntranceMarkerConfig.RING_STROKE_WIDTH / effectiveScale
    val selectionRingGap = EntranceMarkerConfig.SELECTION_RING_GAP / effectiveScale
    val selectionRingStroke = EntranceMarkerConfig.SELECTION_RING_STROKE / effectiveScale

    // White filled background
    drawCircle(
        color = backgroundColor,
        radius = radius,
        center = center
    )
    // Primary ring outline
    drawCircle(
        color = primaryColor,
        radius = radius,
        center = center,
        style = Stroke(width = strokeWidth)
    )

    // Number text
    val paint = android.graphics.Paint().apply {
        color = primaryColor.toArgb()
        textSize = EntranceMarkerConfig.NUMBER_TEXT_SIZE / effectiveScale
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }
    val textY = center.y - (paint.descent() + paint.ascent()) / 2f
    drawContext.canvas.nativeCanvas.save()
    // Counter-rotate text around its own center so digits stay upright like room labels.
    drawContext.canvas.nativeCanvas.rotate(-canvasRotation, center.x, center.y)
    drawContext.canvas.nativeCanvas.drawText(
        index.toString(),
        center.x,
        textY,
        paint
    )
    drawContext.canvas.nativeCanvas.restore()

    // Selection ring: outer circle around the marker
    if (isSelected || isSingle) {
        val selectionRadius = radius + selectionRingGap + selectionRingStroke / 2f
        drawCircle(
            color = primaryColor,
            radius = selectionRadius,
            center = center,
            style = Stroke(width = selectionRingStroke)
        )
    }
}
