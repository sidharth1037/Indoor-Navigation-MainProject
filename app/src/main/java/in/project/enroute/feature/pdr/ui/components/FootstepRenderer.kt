package `in`.project.enroute.feature.pdr.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import `in`.project.enroute.feature.pdr.data.model.PathPoint

/** Canvas-unit radius below which two footsteps are considered overlapping. */
internal const val FOOTSTEP_OVERLAP_RADIUS = 25f

/** Maximum number of history steps to consider before overlap culling. */
internal const val FOOTSTEP_HISTORY_LIMIT = 10

/**
 * Filters footstep history for display by:
 * 1. Taking only the last [FOOTSTEP_HISTORY_LIMIT] steps.
 * 2. Removing older steps that spatially overlap (within [overlapRadius]) a
 *    newer kept step — so the screen never looks cluttered with stacked icons.
 *
 * Works newest-to-oldest so the most recent footstep always wins when there
 * is a conflict. The returned list is in chronological order (oldest first).
 *
 * @param historyPath  Path points to consider (should exclude the live position).
 * @param overlapRadius Canvas units; an older step within this distance of any
 *                      already-kept newer step is discarded.
 * @return A subset of [historyPath] in chronological order, with no two points
 *         closer than [overlapRadius].
 */
fun filterFootstepsForDisplay(
    historyPath: List<PathPoint>,
    overlapRadius: Float = FOOTSTEP_OVERLAP_RADIUS
): List<PathPoint> {
    val candidates =
        if (historyPath.size > FOOTSTEP_HISTORY_LIMIT) historyPath.takeLast(FOOTSTEP_HISTORY_LIMIT)
        else historyPath

    // Iterate newest → oldest; keep a step only if no already-kept (newer)
    // step is within overlapRadius of it.
    val kept = ArrayDeque<PathPoint>()
    for (i in candidates.indices.reversed()) {
        val pos = candidates[i].position
        val overlaps = kept.any { existing ->
            val dx = existing.position.x - pos.x
            val dy = existing.position.y - pos.y
            (dx * dx + dy * dy) < overlapRadius * overlapRadius
        }
        if (!overlaps) kept.addFirst(candidates[i])
    }
    return kept.toList()
}

/**
 * Draws footstep icons for [displayPath] inside this [DrawScope].
 *
 * Must be called within a block that is already translated to the canvas
 * centre (matching `FloorPlanCanvas` / `PdrPathOverlay`'s coordinate origin).
 *
 * Older steps fade toward transparent; the icon alternates left/right foot
 * for each consecutive entry in the list.
 *
 * @param displayPath     Steps to render (oldest first, already overlap-culled).
 * @param footstepPainter [Painter] for the footstep vector icon.
 * @param footstepSize    Width/height of each icon in canvas units.  Default 30f.
 * @param floorAlpha      Global alpha multiplier (e.g. 0.25f when the user is
 *                        viewing a different floor).
 */
fun DrawScope.drawFootsteps(
    displayPath: List<PathPoint>,
    footstepPainter: Painter,
    footstepSize: Float = 30f,
    floorAlpha: Float = 1f
) {
    if (displayPath.isEmpty()) return

    val halfSize = footstepSize / 2f
    val lateralOffset = 5f   // pixel separation between left and right feet

    displayPath.forEachIndexed { index, pathPoint ->
        val isRightFoot = index % 2 == 0
        val point = pathPoint.position
        val headingDegrees = Math.toDegrees(pathPoint.heading.toDouble()).toFloat()

        // Oldest (index 0) → 0.1f alpha; newest (last index) → 0.8f alpha
        val alpha = (if (displayPath.size > 1) {
            0.1f + (index.toFloat() / (displayPath.size - 1)) * 0.7f
        } else {
            0.8f
        }) * floorAlpha

        withTransform({
            // 1. Move to step location
            translate(left = point.x, top = point.y)
            // 2. Rotate to face heading direction
            rotate(degrees = headingDegrees, pivot = Offset.Zero)
            // 3. Lateral offset to separate left/right feet
            val xOffset = if (isRightFoot) lateralOffset else -lateralOffset
            translate(left = xOffset, top = 0f)
            // 4. Mirror for left foot
            if (!isRightFoot) {
                scale(scaleX = -1f, scaleY = 1f, pivot = Offset.Zero)
            }
            // 5. Centre the icon on the step point
            translate(left = -halfSize, top = -halfSize)
        }) {
            with(footstepPainter) {
                draw(Size(footstepSize, footstepSize), alpha = alpha)
            }
        }
    }
}
