package `in`.project.enroute.feature.floorplan.rendering.renderers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import `in`.project.enroute.data.model.Entrance
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders room entrance points on the canvas as yellow dots.
 * Stair entrances are excluded — stairwell access is handled via polygon data.
 */
fun DrawScope.drawEntrances(
    entrances: List<Entrance>,
    scale: Float,
    rotationDegrees: Float,
    radius: Float = 8f,
    color: Color = Color(0xFFFFFF00) // Yellow
) {
    val angleRad = Math.toRadians(rotationDegrees.toDouble()).toFloat()
    val cosAngle = cos(angleRad)
    val sinAngle = sin(angleRad)

    // Draw only room entrances (skip stair entrances)
    for (entrance in entrances) {
        if (entrance.isStairs) continue

        val x = entrance.x * scale
        val y = entrance.y * scale

        val rotatedX = x * cosAngle - y * sinAngle
        val rotatedY = x * sinAngle + y * cosAngle

        drawCircle(
            color = color,
            radius = radius,
            center = Offset(rotatedX, rotatedY)
        )
    }

    // Draw entrance labels
    // drawIntoCanvas { canvas ->
    //     val paint = Paint().apply {
    //         color = android.graphics.Color.BLACK
    //         textSize = 24f / canvasScale
    //         textAlign = Paint.Align.CENTER
    //         isAntiAlias = true
    //     }

    //     // Counter-rotate text so it stays readable
    //     canvas.nativeCanvas.save()
    //     canvas.nativeCanvas.rotate(-canvasRotation)

    //     for (entrance in entrances) {
    //         if (entrance.name != null) {
    //             val x = entrance.x * scale
    //             val y = entrance.y * scale

    //             val rotatedX = x * cosAngle - y * sinAngle
    //             val rotatedY = x * sinAngle + y * cosAngle

    //             // Rotate point back for text positioning
    //             val textAngleRad = Math.toRadians(canvasRotation.toDouble()).toFloat()
    //             val textCos = cos(textAngleRad)
    //             val textSin = sin(textAngleRad)
    //             val textX = rotatedX * textCos - rotatedY * textSin
    //             val textY = rotatedX * textSin + rotatedY * textCos

    //             canvas.nativeCanvas.drawText(
    //                 entrance.name,
    //                 textX,
    //                 textY - 15f / canvasScale,
    //                 paint
    //             )
    //         }
    //     }

    //     canvas.nativeCanvas.restore()
    // }
}
