package `in`.project.enroute.feature.navigation.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import `in`.project.enroute.feature.navigation.data.FloorPathSegment

/**
 * Draws a path segment at full opacity (current floor).
 * Renders an outline for contrast underneath the main path line.
 */
internal fun DrawScope.drawSegmentFull(
    segment: FloorPathSegment,
    color: Color,
    pathWidth: Float,
    canvasScale: Float
) {
    if (segment.points.size < 2) return

    val linePath = Path().apply {
        moveTo(segment.points.first().x, segment.points.first().y)
        for (i in 1 until segment.points.size) {
            lineTo(segment.points[i].x, segment.points[i].y)
        }
    }

    // Outline for contrast
    drawPath(
        path = linePath,
        color = Color(0xFF1A73E8),
        style = Stroke(
            width = (pathWidth + 4f) / canvasScale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    // Main path line
    drawPath(
        path = linePath,
        color = color,
        style = Stroke(
            width = pathWidth / canvasScale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

/**
 * Draws a path segment faded and dashed (other floor) with a floor label
 * at the midpoint.
 */
internal fun DrawScope.drawSegmentFaded(
    segment: FloorPathSegment,
    color: Color,
    pathWidth: Float,
    canvasScale: Float
) {
    if (segment.points.size < 2) return

    val fadedAlpha = 0.30f
    val dashInterval = 20f / canvasScale
    val dashEffect = PathEffect.dashPathEffect(
        floatArrayOf(dashInterval, dashInterval * 0.6f), 0f
    )

    val linePath = Path().apply {
        moveTo(segment.points.first().x, segment.points.first().y)
        for (i in 1 until segment.points.size) {
            lineTo(segment.points[i].x, segment.points[i].y)
        }
    }

    // Dashed path line
    drawPath(
        path = linePath,
        color = color.copy(alpha = fadedAlpha),
        style = Stroke(
            width = pathWidth / canvasScale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = dashEffect
        )
    )
}
