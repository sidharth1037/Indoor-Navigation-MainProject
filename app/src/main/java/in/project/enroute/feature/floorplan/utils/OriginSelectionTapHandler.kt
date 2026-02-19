package `in`.project.enroute.feature.floorplan.utils

import `in`.project.enroute.feature.floorplan.rendering.CanvasState
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.geometry.Offset


/**
 * Converts screen coordinates to world/canvas coordinates.
 *
 * The FloorPlanCanvas applies transformations in this order:
 * 1. graphicsLayer with TransformOrigin(0,0): scale -> rotate -> translate
 * 2. Inner translate(centerX, centerY)
 *
 * To reverse:
 * 1. Reverse the translation: screen - offset
 * 2. Reverse the rotation
 * 3. Reverse the scale
 * 4. Reverse the inner translate: - center
 */
fun screenToWorldCoordinates(
    screenX: Float,
    screenY: Float,
    canvasState: CanvasState,
    screenWidth: Float,
    screenHeight: Float
): Offset {
    val centerX = screenWidth / 2f
    val centerY = screenHeight / 2f
    
    // Step 1: Reverse translation
    val afterTranslateX = screenX - canvasState.offsetX
    val afterTranslateY = screenY - canvasState.offsetY
    
    // Step 2: Reverse rotation (rotate by -rotation)
    val rotationRad = Math.toRadians(-canvasState.rotation.toDouble())
    val cosR = cos(rotationRad).toFloat()
    val sinR = sin(rotationRad).toFloat()
    
    val afterRotateX = afterTranslateX * cosR - afterTranslateY * sinR
    val afterRotateY = afterTranslateX * sinR + afterTranslateY * cosR
    
    // Step 3: Reverse scale
    val afterScaleX = afterRotateX / canvasState.scale
    val afterScaleY = afterRotateY / canvasState.scale
    
    // Step 4: Reverse inner translate
    val worldX = afterScaleX - centerX
    val worldY = afterScaleY - centerY
    
    return Offset(worldX, worldY)
}