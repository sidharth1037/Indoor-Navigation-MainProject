package `in`.project.enroute.feature.floorplan.rendering.renderers

import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.VectorDrawable
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.sin
import androidx.core.graphics.createBitmap

/**
 * Renders a pin drawable at a specific floor-plan coordinate.
 * The pin stays upright regardless of canvas rotation (counter-rotated like room labels).
 * The pin's bottom tip points at the exact coordinate.
 *
 * @param pinX X coordinate in floor-plan space (room coordinate)
 * @param pinY Y coordinate in floor-plan space (room coordinate)
 * @param scale Floor plan metadata scale
 * @param rotationDegrees Floor plan metadata rotation in degrees
 * @param canvasScale Current canvas zoom level
 * @param canvasRotation Current canvas rotation in degrees
 * @param pinDrawable The vector drawable resource for the pin
 * @param tintColor Android color int to tint the pin (e.g. MaterialTheme primary)
 * @param pinSizeDp Base size of the pin in dp-like units (scaled inversely with canvas zoom)
 * @param minZoomForPinSize Minimum zoom level at which pin maintains full size; below this it scales down
 * @param maxZoomForVerticalOffset Maximum zoom level for vertical offset growth; above this offset is constant
 */
fun DrawScope.drawPin(
    pinX: Float,
    pinY: Float,
    scale: Float,
    rotationDegrees: Float,
    canvasScale: Float,
    canvasRotation: Float,
    pinDrawable: VectorDrawable,
    tintColor: Int,
    pinSizeDp: Float = 140f,
    minZoomForPinSize: Float = 1f,
    maxZoomForVerticalOffset: Float = 1f
) {
    // Transform room coordinate to rotated floor-plan coordinate
    val angleRad = Math.toRadians(rotationDegrees.toDouble()).toFloat()
    val cosAngle = cos(angleRad)
    val sinAngle = sin(angleRad)

    val x = pinX * scale
    val y = pinY * scale
    val rotatedX = x * cosAngle - y * sinAngle
    val rotatedY = x * sinAngle + y * cosAngle

    // Counter-rotate to find screen-aligned position (same as room labels)
    val textAngleRad = Math.toRadians(canvasRotation.toDouble()).toFloat()
    val textCos = cos(textAngleRad)
    val textSin = sin(textAngleRad)
    val screenX = rotatedX * textCos - rotatedY * textSin
    val screenY = rotatedX * textSin + rotatedY * textCos

    // Pin size scales inversely with canvas zoom so it stays a consistent screen size
    // At zoom levels >= minZoomForPinSize: pin maintains full size
    // At zoom levels < minZoomForPinSize: pin scales down with zoom
    val pinSize = if (canvasScale >= minZoomForPinSize) {
        pinSizeDp // minZoomForPinSize  // fixed size at higher zoom
    } else {
        pinSizeDp / canvasScale  // scale down at lower zoom
    }
    
    // Offset pin upward in screen space (away from room label)
    // Directly proportional to zoom level up to maxZoomForVerticalOffset, then constant
    val verticalOffset = if (canvasScale <= maxZoomForVerticalOffset) {
        70f * canvasScale  // proportional below threshold
    } else {
        70f * maxZoomForVerticalOffset  // constant above threshold
    }
    val adjustedScreenY = screenY - pinSize - verticalOffset

    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.save()

        // Counter-rotate to keep pin upright
        canvas.nativeCanvas.rotate(-canvasRotation)

        // Tint the drawable
        pinDrawable.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)

        // Render drawable to bitmap at target size
        val bitmapSize = pinSize.toInt().coerceAtLeast(1)
        val bitmap = createBitmap(bitmapSize, bitmapSize)
        val bitmapCanvas = Canvas(bitmap)
        pinDrawable.setBounds(0, 0, bitmapSize, bitmapSize)
        pinDrawable.draw(bitmapCanvas)

        // Draw so the bottom-center of the pin sits above the coordinate
        val left = screenX - pinSize / 2f
        val top = adjustedScreenY // offset upward from room label position
        canvas.nativeCanvas.drawBitmap(bitmap, left, top, null)

        bitmap.recycle()
        canvas.nativeCanvas.restore()
    }
}
