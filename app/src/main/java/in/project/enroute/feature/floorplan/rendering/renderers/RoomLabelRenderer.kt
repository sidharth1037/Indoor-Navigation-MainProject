package `in`.project.enroute.feature.floorplan.rendering.renderers

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import `in`.project.enroute.data.model.Room
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// ── Internal data class for layout computation ──────────────────────

/**
 * Pre-computed layout for a single room label, used during overlap resolution.
 */
private data class LabelLayout(
    val room: Room,
    val textX: Float,
    val textY: Float,
    val labelText: String,
    var maxChars: Int,
    var lines: List<String>,
    var bounds: RectF
)

// ── Constants ────────────────────────────────────────────────────────

private const val DEFAULT_MAX_CHARS = 15
private const val MIN_MAX_CHARS = 5
private const val MAX_RESOLVE_PASSES = 4
/** Fraction of bounding-box size used as padding when checking overlap. */
private const val PADDING_FRACTION = 0.05f

// ── Public entry point ───────────────────────────────────────────────

/**
 * Renders room labels on the canvas with dynamic overlap resolution.
 *
 * When two labels overlap at the current zoom / rotation, they are
 * progressively re-split into narrower (taller) blocks so they no
 * longer collide.  Splitting first tries word boundaries (" "), then
 * falls back to mid-word hyphenation ("Laborat-" / "ory").
 *
 * Labels maintain constant apparent size at zoom ≥ [minZoomForConstantSize]
 * and scale down proportionally below that threshold.
 * Labels are hidden entirely when zoom < 0.48.
 */
fun DrawScope.drawRoomLabels(
    rooms: List<Room>,
    scale: Float,
    rotationDegrees: Float,
    canvasScale: Float,
    canvasRotation: Float,
    textColor: Int = android.graphics.Color.DKGRAY,
    textSize: Float = 30f,
    minZoomForConstantSize: Float = 0.76f
) {
    if (canvasScale < 0.48f) return

    val angleRad = Math.toRadians(rotationDegrees.toDouble()).toFloat()
    val cosAngle = cos(angleRad)
    val sinAngle = sin(angleRad)

    val effectiveTextSize = if (canvasScale >= minZoomForConstantSize) {
        textSize / minZoomForConstantSize
    } else {
        textSize / canvasScale
    }

    // Shared paint used both for measuring and drawing
    val paint = Paint().apply {
        color = textColor
        this.textSize = effectiveTextSize
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    val textAngleRad = Math.toRadians(canvasRotation.toDouble()).toFloat()
    val textCos = cos(textAngleRad)
    val textSin = sin(textAngleRad)

    // ── 1. Layout pass ──────────────────────────────────────────
    val layouts = buildLayouts(rooms, scale, cosAngle, sinAngle, textCos, textSin, paint)

    // ── 2. Overlap resolution (multi-pass) ──────────────────────
    resolveOverlaps(layouts, paint)

    // ── 3. Draw pass ────────────────────────────────────────────
    val outlinePaint = Paint().apply {
        color = android.graphics.Color.WHITE
        this.textSize = effectiveTextSize
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 8f / canvasScale
    }

    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.save()
        canvas.nativeCanvas.rotate(-canvasRotation)

        val lineHeight = paint.descent() - paint.ascent()

        for (layout in layouts) {
            val totalHeight = lineHeight * layout.lines.size
            val startY = layout.textY - totalHeight / 2

            for ((index, line) in layout.lines.withIndex()) {
                val lineY = startY + index * lineHeight + paint.textSize / 2
                canvas.nativeCanvas.drawText(line, layout.textX, lineY, outlinePaint)
                canvas.nativeCanvas.drawText(line, layout.textX, lineY, paint)
            }
        }

        canvas.nativeCanvas.restore()
    }
}

// ── Layout helpers ───────────────────────────────────────────────────

/**
 * Builds initial [LabelLayout]s for every named room.
 */
private fun buildLayouts(
    rooms: List<Room>,
    scale: Float,
    cosAngle: Float,
    sinAngle: Float,
    textCos: Float,
    textSin: Float,
    paint: Paint
): MutableList<LabelLayout> {
    val layouts = mutableListOf<LabelLayout>()

    for (room in rooms) {
        val name = room.name ?: continue

        val x = room.x * scale
        val y = room.y * scale
        val rotatedX = x * cosAngle - y * sinAngle
        val rotatedY = x * sinAngle + y * cosAngle
        val textX = rotatedX * textCos - rotatedY * textSin
        val textY = rotatedX * textSin + rotatedY * textCos

        val labelText = if (room.number != null) "${room.number}: $name" else name
        val lines = splitLabel(labelText, DEFAULT_MAX_CHARS)
        val bounds = measureBounds(textX, textY, lines, paint)

        layouts += LabelLayout(
            room = room,
            textX = textX,
            textY = textY,
            labelText = labelText,
            maxChars = DEFAULT_MAX_CHARS,
            lines = lines,
            bounds = bounds
        )
    }
    return layouts
}

/**
 * Computes an axis-aligned bounding rectangle for a multi-line label
 * centred at ([cx], [cy]).
 */
private fun measureBounds(cx: Float, cy: Float, lines: List<String>, paint: Paint): RectF {
    val lineHeight = paint.descent() - paint.ascent()
    val totalHeight = lineHeight * lines.size
    var maxWidth = 0f
    for (line in lines) {
        val w = paint.measureText(line)
        if (w > maxWidth) maxWidth = w
    }
    val halfW = maxWidth / 2f
    val halfH = totalHeight / 2f
    return RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
}

// ── Overlap resolution ───────────────────────────────────────────────

/**
 * Iteratively detects overlapping label pairs and narrows them until
 * collisions are resolved or [MIN_MAX_CHARS] is reached.
 */
private fun resolveOverlaps(layouts: MutableList<LabelLayout>, paint: Paint) {
    repeat(MAX_RESOLVE_PASSES) {
        val overlapping = findOverlapping(layouts)
        if (overlapping.isEmpty()) return  // nothing left to fix

        for (idx in overlapping) {
            val layout = layouts[idx]
            if (layout.maxChars <= MIN_MAX_CHARS) continue   // already at minimum

            // Narrow by ~30 % each pass, but never below MIN_MAX_CHARS
            val newMax = max(MIN_MAX_CHARS, (layout.maxChars * 0.7f).toInt())
            if (newMax == layout.maxChars) continue

            layout.maxChars = newMax
            layout.lines = splitLabel(layout.labelText, newMax)
            layout.bounds = measureBounds(layout.textX, layout.textY, layout.lines, paint)
        }
    }
}

/**
 * Returns the set of layout indices that overlap with at least one other label.
 * Uses a small padding around each rect to avoid labels being too close even
 * if they don't technically intersect.
 */
private fun findOverlapping(layouts: List<LabelLayout>): Set<Int> {
    val result = mutableSetOf<Int>()
    val n = layouts.size
    for (i in 0 until n) {
        for (j in i + 1 until n) {
            if (rectsOverlap(layouts[i].bounds, layouts[j].bounds)) {
                result += i
                result += j
            }
        }
    }
    return result
}

/**
 * Checks whether two rects overlap, with a small padding so labels
 * don't sit right against each other.
 */
private fun rectsOverlap(a: RectF, b: RectF): Boolean {
    val padX = (a.width() + b.width()) * PADDING_FRACTION
    val padY = (a.height() + b.height()) * PADDING_FRACTION
    return a.left - padX < b.right &&
            a.right + padX > b.left &&
            a.top - padY < b.bottom &&
            a.bottom + padY > b.top
}

// ── Text splitting ───────────────────────────────────────────────────

/**
 * Splits [text] into lines respecting [maxCharsPerLine].
 *
 * 1. Tries to break at word boundaries (" ").
 * 2. If a single word exceeds [maxCharsPerLine], it is hyphenated:
 *    e.g. "Laboratory" with max 6 → "Labo-" / "rato-" / "ry".
 */
private fun splitLabel(text: String, maxCharsPerLine: Int): List<String> {
    if (text.length <= maxCharsPerLine) return listOf(text)

    val lines = mutableListOf<String>()
    val words = text.split(" ")
    var currentLine = ""

    for (word in words) {
        if (currentLine.isEmpty() && word.length <= maxCharsPerLine) {
            currentLine = word
            continue
        }

        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"

        if (testLine.length <= maxCharsPerLine) {
            currentLine = testLine
        } else {
            // Flush current line if it has content
            if (currentLine.isNotEmpty()) {
                lines += currentLine
            }

            // If the word itself is too long, hyphenate it
            if (word.length > maxCharsPerLine) {
                val hyphenated = hyphenateWord(word, maxCharsPerLine)
                // Add all but the last fragment as complete lines
                for (i in 0 until hyphenated.size - 1) {
                    lines += hyphenated[i]
                }
                // Last fragment becomes the start of the next line
                currentLine = hyphenated.last()
            } else {
                currentLine = word
            }
        }
    }

    if (currentLine.isNotEmpty()) {
        lines += currentLine
    }
    return lines
}

/**
 * Breaks a single word into fragments of at most [maxChars] characters,
 * appending "-" to every fragment except the last.
 *
 * Example: hyphenateWord("Laboratory", 5) → ["Labo-", "rato-", "ry"]
 */
private fun hyphenateWord(word: String, maxChars: Int): List<String> {
    val fragments = mutableListOf<String>()
    var remaining = word
    // Each fragment uses maxChars-1 real characters + "-"
    val chunkSize = max(2, maxChars - 1)

    while (remaining.length > maxChars) {
        fragments += remaining.substring(0, chunkSize) + "-"
        remaining = remaining.substring(chunkSize)
    }
    fragments += remaining
    return fragments
}
