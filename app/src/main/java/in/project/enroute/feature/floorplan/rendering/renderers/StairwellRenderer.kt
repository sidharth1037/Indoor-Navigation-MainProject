package `in`.project.enroute.feature.floorplan.rendering.renderers

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import `in`.project.enroute.data.model.Stairwell
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Renders stairwell polygons on the canvas.
 */
fun DrawScope.drawStairwells(
    stairwells: List<Stairwell>,
    scale: Float,
    rotationDegrees: Float
) {
    val angleRad = Math.toRadians(rotationDegrees.toDouble()).toFloat()
    val cosAngle = cos(angleRad)
    val sinAngle = sin(angleRad)

    for (stairwell in stairwells) {
        if (stairwell.points.size < 3) continue

        // Build segment list from original stair lines so we can detect which edges
        // were marked with a "position" (top/bottom) and which are side edges.
        val segments = stairwell.lines.map { line ->
            Pair(Pair(line.x1, line.y1), Pair(line.x2, line.y2)) to line.position
        }

        // Find the top (reference) and bottom (limit) lines. If either is missing,
        // skip stair-line drawing for this polygon.
        val topLine = stairwell.lines.firstOrNull { it.position == "top" }
        val bottomLine = stairwell.lines.firstOrNull { it.position == "bottom" }

        if (topLine == null || bottomLine == null) {
            // We used to fill the polygon; keep that code commented for potential future use.
            // val transformedPoints = stairwell.points.map { (x, y) ->
            //     val scaledX = x * scale
            //     val scaledY = y * scale
            //     val rotatedX = scaledX * cosAngle - scaledY * sinAngle
            //     val rotatedY = scaledX * sinAngle + scaledY * cosAngle
            //     Pair(rotatedX, rotatedY)
            // }
            // val path = Path().apply {
            //     val first = transformedPoints.first()
            //     moveTo(first.first, first.second)
            //     transformedPoints.drop(1).forEach { (x, y) -> lineTo(x, y) }
            //     close()
            // }
            // drawPath(path = path, color = color, style = Fill)
            continue
        }

        // Direction along the stair (parallel to the top line)
        val topDx = topLine.x2 - topLine.x1
        val topDy = topLine.y2 - topLine.y1
        val topLen = sqrt(topDx * topDx + topDy * topDy).coerceAtLeast(0.0001f)
        val dirX = topDx / topLen
        val dirY = topDy / topLen

        // Normal (perpendicular) to the top line. We'll step along this vector
        // to place each stair line between top and bottom.
        var normX = -dirY
        var normY = dirX

        // Midpoints of top and bottom lines
        val topCx = (topLine.x1 + topLine.x2) / 2f
        val topCy = (topLine.y1 + topLine.y2) / 2f
        val bottomCx = (bottomLine.x1 + bottomLine.x2) / 2f
        val bottomCy = (bottomLine.y1 + bottomLine.y2) / 2f

        // Determine signed distance from top to bottom along the normal
        val signedDist = (bottomCx - topCx) * normX + (bottomCy - topCy) * normY
        if (signedDist == 0f) continue

        // Ensure normal points from top -> bottom
        if (signedDist < 0f) {
            normX = -normX
            normY = -normY
        }

        val totalDist = kotlin.math.abs(signedDist)

        // Choose spacing between stair lines (in map units). Adjust as needed.
        val spacing = 20f
        val lineCount = (totalDist / spacing).toInt()
        if (lineCount <= 0) continue

        // Helper: intersect infinite line (P0 + u * dir) with segment (A -> B).
        fun intersectLineSegment(p0x: Float, p0y: Float, rdx: Float, rdy: Float,
                                 ax: Float, ay: Float, bx: Float, by: Float): Pair<Float, Float>? {
            val sx = bx - ax
            val sy = by - ay
            val denom = rdx * sy - rdy * sx
            if (kotlin.math.abs(denom) < 1e-6f) return null // parallel

            val t = ((ax - p0x) * sy - (ay - p0y) * sx) / denom
            val u = if (kotlin.math.abs(sx) >= kotlin.math.abs(sy))
                (p0x + t * rdx - ax) / sx
            else
                (p0y + t * rdy - ay) / sy

            // u between 0 and 1 means intersection is on the segment
            if (u < -1e-6f || u > 1f + 1e-6f) return null

            return Pair(p0x + t * rdx, p0y + t * rdy)
        }

        // Build a list of offsets that includes the top (0), intermediate steps, and bottom (totalDist)
        val steps = kotlin.math.floor(totalDist / spacing).toInt()
        val offsets = mutableListOf<Float>()
        offsets.add(0f)
        for (i in 1..steps) offsets.add(i * spacing)
        if (offsets.last() < totalDist - 1e-3f) offsets.add(totalDist)

        // For each offset between top and bottom (inclusive), build the parallel line
        // and intersect it with the polygon side segments (segments with no position).
        for (offset in offsets) {
            val px = topCx + normX * offset
            val py = topCy + normY * offset

            // Infinite line point = (px,py), direction = (dirX, dirY)
            val intersections = mutableListOf<Pair<Float, Float>>()

            for ((seg, pos) in segments) {
                // Only consider segments that are NOT marked as top/bottom
                if (pos != null) continue
                val (a, b) = seg
                val inter = intersectLineSegment(px, py, dirX, dirY, a.first, a.second, b.first, b.second)
                if (inter != null) intersections.add(inter)
            }

            // Need at least two intersection points to draw a stair line
            if (intersections.size < 2) continue

            // Pick two intersections that are farthest apart along the direction
            // Project intersections onto dir to sort
            val proj = intersections.map { (ix, iy) -> Pair((ix - px) * dirX + (iy - py) * dirY, Pair(ix, iy)) }
                .sortedBy { it.first }

            val start = proj.first().second
            val end = proj.last().second

            // Transform points by scale and rotation
            val startScaledX = start.first * scale
            val startScaledY = start.second * scale
            val startRotX = startScaledX * cosAngle - startScaledY * sinAngle
            val startRotY = startScaledX * sinAngle + startScaledY * cosAngle

            val endScaledX = end.first * scale
            val endScaledY = end.second * scale
            val endRotX = endScaledX * cosAngle - endScaledY * sinAngle
            val endRotY = endScaledX * sinAngle + endScaledY * cosAngle

            // Fraction along top->bottom (0 at top, 1 at bottom)
            val t = if (totalDist > 0f) (offset / totalDist) else 0f

            // Draw the stair line. Stroke width varies from top (thick) to bottom (thinner).
            val topStroke = 3.5f
            val bottomStroke = 1.5f
            val strokeWidthVar = max(0.5f, topStroke * (1f - t) + bottomStroke * t)
            drawLine(
                color = Color.Black,
                start = Offset(startRotX, startRotY),
                end = Offset(endRotX, endRotY),
                strokeWidth = strokeWidthVar
            )
        }
    }
}
