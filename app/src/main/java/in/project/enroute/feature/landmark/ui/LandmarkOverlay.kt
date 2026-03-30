package `in`.project.enroute.feature.landmark.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import `in`.project.enroute.data.model.Landmark
import `in`.project.enroute.feature.floorplan.rendering.CanvasState
import `in`.project.enroute.feature.floorplan.rendering.renderers.computeLandmarkScreenSizing
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawLandmarks
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Landmark rendering layer placed above room labels and below PDR path.
 */
@Composable
fun LandmarkOverlay(
    landmarks: List<Landmark>,
    currentFloorId: String?,
    canvasState: CanvasState,
    modifier: Modifier = Modifier
) {
    if (landmarks.isEmpty()) return

    val visible = landmarks.filter { currentFloorId != null && it.floorId == currentFloorId }
    if (visible.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
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
                    drawLandmarks(
                        landmarks = visible,
                        canvasScale = canvasState.scale,
                    )
                }
            }

            val screenSizing = computeLandmarkScreenSizing(canvasScale = canvasState.scale)
            val iconSizePx = screenSizing.iconSizePx.roundToInt()
            val iconSizeDp = with(density) { iconSizePx.toDp() }
            val rotationRad = Math.toRadians(canvasState.rotation.toDouble()).toFloat()
            val cosR = cos(rotationRad)
            val sinR = sin(rotationRad)
            val halfW = screenWidthPx / 2f
            val halfH = screenHeightPx / 2f

            visible.forEach { landmark ->
                val px = (landmark.campusX + halfW) * canvasState.scale
                val py = (landmark.campusY + halfH) * canvasState.scale
                val rx = px * cosR - py * sinR
                val ry = px * sinR + py * cosR
                val screenX = rx + canvasState.offsetX
                val screenY = ry + canvasState.offsetY

                Icon(
                    imageVector = resolveLandmarkIcon(landmark.icon),
                    contentDescription = landmark.name,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (screenX - (iconSizePx / 2f)).roundToInt(),
                                y = (screenY - (iconSizePx / 2f)).roundToInt()
                            )
                        }
                        .size(iconSizeDp)
                )
            }
        }
    }
}
