package `in`.project.enroute.feature.floorplan.rendering.renderers

import android.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import `in`.project.enroute.data.model.LabelPosition
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the building name on the canvas.
 * Typically displayed at low zoom levels when room labels are hidden.
 */
fun DrawScope.drawBuildingName(
    buildingName: String,
    labelPosition: LabelPosition?,
    scale: Float,
    rotationDegrees: Float,
    canvasScale: Float,
    canvasRotation: Float,
    textColor: Int = android.graphics.Color.DKGRAY,
    textSize: Float = 44f
) {
    if (buildingName.isEmpty() || labelPosition == null) {
        return
    }

    val angleRad = Math.toRadians(rotationDegrees.toDouble()).toFloat()
    val cosAngle = cos(angleRad)
    val sinAngle = sin(angleRad)

    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            color = textColor
            this.textSize = textSize / canvasScale
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        // Paint for the white outline/border
        val outlinePaint = Paint().apply {
            color = android.graphics.Color.WHITE
            this.textSize = textSize / canvasScale
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 12f / canvasScale
        }

        // Counter-rotate text so it stays readable
        canvas.nativeCanvas.save()
        canvas.nativeCanvas.rotate(-canvasRotation)

        // Transform label position to canvas coordinates
        val x = labelPosition.x * scale
        val y = labelPosition.y * scale

        val rotatedX = x * cosAngle - y * sinAngle
        val rotatedY = x * sinAngle + y * cosAngle

        // Rotate point back for text positioning
        val textAngleRad = Math.toRadians(canvasRotation.toDouble()).toFloat()
        val textCos = cos(textAngleRad)
        val textSin = sin(textAngleRad)
        val textX = rotatedX * textCos - rotatedY * textSin
        val textY = rotatedX * textSin + rotatedY * textCos

        // Draw white outline first
        canvas.nativeCanvas.drawText(buildingName, textX, textY, outlinePaint)
        // Draw text on top with original color
        canvas.nativeCanvas.drawText(buildingName, textX, textY, paint)

        canvas.nativeCanvas.restore()
    }
}
