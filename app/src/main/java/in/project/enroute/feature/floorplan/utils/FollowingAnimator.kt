package `in`.project.enroute.feature.floorplan.utils

import androidx.compose.ui.geometry.Offset
import `in`.project.enroute.feature.floorplan.rendering.CanvasState
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Configuration for following mode behavior.
 *
 * @param scale The zoom level to use in following mode
 * @param animationDurationMs Duration for smooth animation transitions
 * @param frameDelayMs Delay between animation frames (~60fps)
 */
data class FollowingConfig(
    val scale: Float = 0.7f,
    val animationDurationMs: Long = 650L,
    val frameDelayMs: Long = 12L
)

/**
 * Animation timing for centering/zooming on a floor-plan coordinate.
 */
data class CenteringConfig(
    val durationMs: Long = 500L,
    val frameDelayMs: Long = 16L
)

/**
 * Utility for following mode and general canvas centering animations.
 *
 * In following mode:
 * - Centers on the user's current position and rotates so heading points up.
 *
 * For search centering:
 * - Centers a floor-plan coordinate while preserving the current canvas rotation.
 */
object FollowingAnimator {

    fun calculateFollowingState(
        worldPosition: Offset,
        headingRadians: Float,
        scale: Float,
        screenWidth: Float,
        screenHeight: Float
    ): CanvasState {
        val rotationDegrees = -Math.toDegrees(headingRadians.toDouble()).toFloat()

        val (offsetX, offsetY) = calculateCenterOffset(
            worldX = worldPosition.x,
            worldY = worldPosition.y,
            scale = scale,
            rotationDegrees = rotationDegrees,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )

        return CanvasState(
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            rotation = rotationDegrees
        )
    }

    fun calculateCenterState(
        worldPosition: Offset,
        currentRotation: Float,
        scale: Float,
        screenWidth: Float,
        screenHeight: Float
    ): CanvasState {
        val (offsetX, offsetY) = calculateCenterOffset(
            worldX = worldPosition.x,
            worldY = worldPosition.y,
            scale = scale,
            rotationDegrees = currentRotation,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )

        return CanvasState(
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            rotation = currentRotation
        )
    }

    fun calculateFloorPlanCenterState(
        targetX: Float,
        targetY: Float,
        targetScale: Float,
        currentRotation: Float,
        floorPlanScale: Float,
        floorPlanRotation: Float,
        screenWidth: Float,
        screenHeight: Float
    ): CanvasState {
        val (offsetX, offsetY) = calculateFloorPlanCenterOffset(
            targetX = targetX,
            targetY = targetY,
            canvasScale = targetScale,
            canvasRotation = currentRotation,
            floorPlanScale = floorPlanScale,
            floorPlanRotation = floorPlanRotation,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )

        return CanvasState(
            scale = targetScale,
            offsetX = offsetX,
            offsetY = offsetY,
            rotation = currentRotation
        )
    }

    suspend fun animateToState(
        currentState: CanvasState,
        targetState: CanvasState,
        config: FollowingConfig = FollowingConfig(),
        onStateUpdate: (CanvasState) -> Unit
    ) {
        animateToState(
            currentState = currentState,
            targetState = targetState,
            durationMs = config.animationDurationMs,
            frameDelayMs = config.frameDelayMs,
            onStateUpdate = onStateUpdate
        )
    }

    suspend fun animateToState(
        currentState: CanvasState,
        targetState: CanvasState,
        durationMs: Long,
        frameDelayMs: Long,
        onStateUpdate: (CanvasState) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        var elapsed = 0L

        while (elapsed < durationMs) {
            val progress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
            val easedProgress = easeInOutCubic(progress)

            val interpolatedState = interpolate(currentState, targetState, easedProgress)
            onStateUpdate(interpolatedState)

            delay(frameDelayMs)
            elapsed = System.currentTimeMillis() - startTime
        }

        onStateUpdate(targetState)
    }

    suspend fun animateToFloorPlanCoordinate(
        currentState: CanvasState,
        targetX: Float,
        targetY: Float,
        targetScale: Float,
        floorPlanScale: Float,
        floorPlanRotation: Float,
        screenWidth: Float,
        screenHeight: Float,
        config: CenteringConfig = CenteringConfig(),
        onStateUpdate: (CanvasState) -> Unit
    ) {
        val targetState = calculateFloorPlanCenterState(
            targetX = targetX,
            targetY = targetY,
            targetScale = targetScale,
            currentRotation = currentState.rotation,
            floorPlanScale = floorPlanScale,
            floorPlanRotation = floorPlanRotation,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )

        animateToState(
            currentState = currentState,
            targetState = targetState,
            durationMs = config.durationMs,
            frameDelayMs = config.frameDelayMs,
            onStateUpdate = onStateUpdate
        )
    }

    private fun calculateCenterOffset(
        worldX: Float,
        worldY: Float,
        scale: Float,
        rotationDegrees: Float,
        screenWidth: Float,
        screenHeight: Float
    ): Pair<Float, Float> {
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f

        val localX = worldX + centerX
        val localY = worldY + centerY

        val scaledX = localX * scale
        val scaledY = localY * scale

        val rotationRad = Math.toRadians(rotationDegrees.toDouble())
        val cosR = cos(rotationRad).toFloat()
        val sinR = sin(rotationRad).toFloat()

        val rotatedX = scaledX * cosR - scaledY * sinR
        val rotatedY = scaledX * sinR + scaledY * cosR

        val offsetX = centerX - rotatedX
        val offsetY = centerY - rotatedY

        return Pair(offsetX, offsetY)
    }

    private fun calculateFloorPlanCenterOffset(
        targetX: Float,
        targetY: Float,
        canvasScale: Float,
        canvasRotation: Float,
        floorPlanScale: Float,
        floorPlanRotation: Float,
        screenWidth: Float,
        screenHeight: Float
    ): Pair<Float, Float> {
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f

        val fpScaledX = targetX * floorPlanScale
        val fpScaledY = targetY * floorPlanScale

        val fpRotationRad = Math.toRadians(floorPlanRotation.toDouble())
        val fpCos = cos(fpRotationRad).toFloat()
        val fpSin = sin(fpRotationRad).toFloat()

        val fpTransformedX = fpScaledX * fpCos - fpScaledY * fpSin
        val fpTransformedY = fpScaledX * fpSin + fpScaledY * fpCos

        val localX = fpTransformedX + centerX
        val localY = fpTransformedY + centerY

        val canvasRotationRad = Math.toRadians(canvasRotation.toDouble())
        val canvasCos = cos(canvasRotationRad).toFloat()
        val canvasSin = sin(canvasRotationRad).toFloat()

        val canvasScaledX = localX * canvasScale
        val canvasScaledY = localY * canvasScale

        val canvasRotatedX = canvasScaledX * canvasCos - canvasScaledY * canvasSin
        val canvasRotatedY = canvasScaledX * canvasSin + canvasScaledY * canvasCos

        val offsetX = centerX - canvasRotatedX
        val offsetY = centerY - canvasRotatedY

        return Pair(offsetX, offsetY)
    }

    private fun interpolate(
        start: CanvasState,
        end: CanvasState,
        progress: Float
    ): CanvasState {
        return CanvasState(
            scale = lerp(start.scale, end.scale, progress),
            offsetX = lerp(start.offsetX, end.offsetX, progress),
            offsetY = lerp(start.offsetY, end.offsetY, progress),
            rotation = lerpAngle(start.rotation, end.rotation, progress)
        )
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }

    private fun lerpAngle(start: Float, end: Float, fraction: Float): Float {
        var diff = end - start
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        return start + diff * fraction
    }

    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) {
            4f * t * t * t
        } else {
            val t1 = -2f * t + 2f
            1f - (t1 * t1 * t1) / 2f
        }
    }
}
