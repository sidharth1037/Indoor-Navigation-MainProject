package `in`.project.enroute.feature.floorplan.rendering.renderers

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import `in`.project.enroute.feature.floorplan.CampusBounds

/**
 * Renders the campus background canvas — a rectangular region behind all buildings.
 * This provides a visual boundary for the campus area and helps users understand
 * the panning limits.
 *
 * The background is always rendered as an axis-aligned rectangle in world space
 * (before canvas transformations), so it remains rectangular regardless of
 * zoom/rotate/pan applied via graphicsLayer.
 *
 * @param campusBounds The bounds of the campus in world coordinates
 * @param color The fill color for the background (default: light gray)
 */
fun DrawScope.drawCampusBackground(
    campusBounds: CampusBounds,
    color: Color = Color(0xFFE8E8E8)
) {
    if (campusBounds.isEmpty) return

    drawRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(campusBounds.left, campusBounds.top),
        size = androidx.compose.ui.geometry.Size(campusBounds.width, campusBounds.height)
    )
}
