package `in`.project.enroute.feature.floorplan.rendering.renderers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import `in`.project.enroute.data.model.Landmark
import kotlin.math.max

data class LandmarkRenderSizing(
    val markerRadiusWorld: Float,
    val effectiveNameTextSizeWorld: Float,
    val labelYOffsetWorld: Float
)

const val LANDMARK_LABEL_TEXT_SCALE = 0.78f

data class LandmarkScreenSizing(
    val markerRadiusPx: Float,
    val iconSizePx: Float,
    val labelTextPx: Float,
    val labelCenterOffsetPx: Float
)

fun computeLandmarkRenderSizing(
    canvasScale: Float,
    minZoomForConstantSize: Float = 0.76f
): LandmarkRenderSizing {
    // Match RoomLabelRenderer scale model so landmark label hitboxes remain consistent.
    val nameBaseSize = 30f
    val effectiveNameSize = if (canvasScale >= minZoomForConstantSize) {
        nameBaseSize / minZoomForConstantSize
    } else {
        nameBaseSize / canvasScale
    }

    val markerRadius = max(14f, 18f / canvasScale)
    val labelYOffset = markerRadius + max(14f, 16f / canvasScale)

    return LandmarkRenderSizing(
        markerRadiusWorld = markerRadius,
        effectiveNameTextSizeWorld = effectiveNameSize,
        labelYOffsetWorld = labelYOffset
    )
}

fun computeLandmarkScreenSizing(
    canvasScale: Float,
    minZoomForConstantSize: Float = 0.76f
): LandmarkScreenSizing {
    val base = computeLandmarkRenderSizing(
        canvasScale = canvasScale,
        minZoomForConstantSize = minZoomForConstantSize
    )

    val labelTextPx = base.effectiveNameTextSizeWorld * canvasScale * LANDMARK_LABEL_TEXT_SCALE

    // Simple and robust: icon and circle follow label scale, and labels are always
    // anchored at least one full label-height below the icon with fixed separation.
    val iconSizePx = (labelTextPx * 1.08f).coerceIn(18f, 42f)
    val markerRadiusPx = (iconSizePx * 0.75f).coerceAtLeast(13f)

    val iconHalfPx = iconSizePx / 2f
    // More gap when zoomed in, less gap when zoomed out.
    val zoomAdaptiveGapPx = (20f + (canvasScale - 1f) * 7f).coerceIn(14f, 30f)
    val labelCenterOffsetPx = iconHalfPx + labelTextPx + zoomAdaptiveGapPx

    return LandmarkScreenSizing(
        markerRadiusPx = markerRadiusPx,
        iconSizePx = iconSizePx,
        labelTextPx = labelTextPx,
        labelCenterOffsetPx = labelCenterOffsetPx
    )
}

/**
 * Draws landmark marker circles in campus-wide coordinates.
 *
 * Label text is rendered by FloorPlanLabelsOverlay via RoomLabelRenderer so
 * room and landmark labels share one collision/wrapping pipeline.
 */
fun DrawScope.drawLandmarks(
    landmarks: List<Landmark>,
    canvasScale: Float,
    markerColor: Color = Color(0xFF1F7A8C),
    minZoomForConstantSize: Float = 0.76f
) {
    if (landmarks.isEmpty()) return

    val screenSizing = computeLandmarkScreenSizing(
        canvasScale = canvasScale,
        minZoomForConstantSize = minZoomForConstantSize
    )
    val markerRadiusWorld = screenSizing.markerRadiusPx / canvasScale

    for (landmark in landmarks) {
        val cx = landmark.campusX
        val cy = landmark.campusY

        drawCircle(
            color = markerColor,
            radius = markerRadiusWorld,
            center = Offset(cx, cy)
        )
    }
}
