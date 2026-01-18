package `in`.project.enroute.feature.floorplan.rendering.renderers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import `in`.project.enroute.data.model.BoundaryPoint
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the floor plan boundary polygon.
 * Connects boundary points in order and fills with a solid color.
 */
fun DrawScope.drawBoundary(
    boundaryPoints: List<BoundaryPoint>,
    scale: Float,
    rotationDegrees: Float,
    color: Color = Color.White
) {
    if (boundaryPoints.isEmpty()) return

    val angleRad = Math.toRadians(rotationDegrees.toDouble()).toFloat()
    val cosAngle = cos(angleRad)
    val sinAngle = sin(angleRad)

    // Transform and sort boundary points by id
    val sortedPoints = boundaryPoints.sortedBy { it.id }
    
    val transformedPoints = sortedPoints.map { point ->
        val x = point.x * scale
        val y = point.y * scale
        
        val rotatedX = x * cosAngle - y * sinAngle
        val rotatedY = x * sinAngle + y * cosAngle
        
        Offset(rotatedX, rotatedY)
    }

    if (transformedPoints.isNotEmpty()) {
        drawPath(
            path = Path().apply {
                moveTo(transformedPoints[0].x, transformedPoints[0].y)
                for (i in 1 until transformedPoints.size) {
                    lineTo(transformedPoints[i].x, transformedPoints[i].y)
                }
                close()
            },
            color = color,
            style = Fill
        )
    }
}
