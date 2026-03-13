package `in`.project.enroute.feature.navigation.data

import kotlin.math.cos
import kotlin.math.sin

/**
 * Shared coordinate transform: raw floor-plan coords → campus-wide coords.
 * raw → scale → rotate → offset by building relativePosition.
 */
object CoordinateTransform {
    fun rawToCampus(
        x: Float, y: Float,
        scale: Float, rotationDegrees: Float,
        offsetX: Float, offsetY: Float
    ): Pair<Float, Float> {
        val sx = x * scale
        val sy = y * scale
        val rad = Math.toRadians(rotationDegrees.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val rx = sx * cosA - sy * sinA
        val ry = sx * sinA + sy * cosA
        return Pair(rx + offsetX, ry + offsetY)
    }
}
