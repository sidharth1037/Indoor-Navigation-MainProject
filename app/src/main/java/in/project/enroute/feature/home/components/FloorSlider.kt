package `in`.project.enroute.feature.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.max
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Horizontal floor selector with timeline-style design.
 * Shows building name at top, timeline with +/- buttons, current floor in center.
 * Visibility is controlled by the isVisible parameter with animations.
 *
 * @param buildingName Name of the building to display
 * @param availableFloors List of available floor numbers (sorted ascending)
 * @param currentFloor Current floor number
 * @param onFloorChange Callback when floor is changed
 * @param isVisible Whether the slider should be visible
 * @param modifier Modifier for the component
 */
@Composable
fun FloorSlider(
    buildingName: String,
    availableFloors: List<Float>,
    currentFloor: Float,
    onFloorChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    AnimatedVisibility(
        visible = isVisible && availableFloors.size > 1,
        enter = fadeIn() + slideInHorizontally { -it },
        exit = fadeOut() + slideOutHorizontally { -it },
        modifier = modifier
    ) {
        FloorSliderContent(
            buildingName = buildingName,
            availableFloors = availableFloors,
            currentFloor = currentFloor,
            onFloorChange = onFloorChange
        )
    }
}

/**
 * Internal content of the floor slider.
 */
@Composable
private fun FloorSliderContent(
    buildingName: String,
    availableFloors: List<Float>,
    currentFloor: Float,
    onFloorChange: (Float) -> Unit
) {
    // Use safe defaults if list is empty (can happen briefly during exit animation)
    val safeFloors = if (availableFloors.size >= 2) availableFloors.sorted() else listOf(0f, 1f)
    val safeCurrentFloor = if (availableFloors.contains(currentFloor)) currentFloor else safeFloors.first()
    
    // Find current floor index and adjacent floors
    val currentIndex = safeFloors.indexOf(safeCurrentFloor).coerceAtLeast(0)
    val prevFloor = if (currentIndex > 0) safeFloors[currentIndex - 1] else null
    val nextFloor = if (currentIndex < safeFloors.size - 1) safeFloors[currentIndex + 1] else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(28.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Building name at top (no floor number)
        if (buildingName.isNotEmpty()) {
            Text(
                text = buildingName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }

        // Timeline floor selector with clickable buttons
        FloorTimelineWithControls(
            currentFloor = safeCurrentFloor,
            availableFloors = safeFloors,
            prevFloor = prevFloor,
            nextFloor = nextFloor,
            onFloorChange = onFloorChange,
            onDecrement = {
                prevFloor?.let { onFloorChange(it) }
            },
            onIncrement = {
                nextFloor?.let { onFloorChange(it) }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Timeline-style floor selector with clickable +/- buttons.
 */
@Composable
private fun FloorTimelineWithControls(
    currentFloor: Float,
    availableFloors: List<Float>,
    prevFloor: Float?,
    nextFloor: Float?,
    onFloorChange: (Float) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)

    Row(
        modifier = modifier.height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Minus button
        TimelineButton(
            text = "−",
            enabled = prevFloor != null,
            onClick = onDecrement,
            primaryColor = primaryColor,
            inactiveColor = inactiveColor
        )
        
        // Timeline center section with prev floor, line, current, line, next floor
        TimelineCenter(
            currentFloor = currentFloor,
            availableFloors = availableFloors,
            prevFloor = prevFloor,
            nextFloor = nextFloor,
            onFloorChange = onFloorChange,
            primaryColor = primaryColor,
            modifier = Modifier.weight(1f)
        )
        
        // Plus button
        TimelineButton(
            text = "+",
            enabled = nextFloor != null,
            onClick = onIncrement,
            primaryColor = primaryColor,
            inactiveColor = inactiveColor
        )
    }
}

@Composable
private fun TimelineButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    primaryColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    val pillWidth = 32.dp
    val pillHeight = 26.dp
    val onSurfaceColor = MaterialTheme.colorScheme.background
    
    Box(
        modifier = modifier
            .size(pillWidth, pillHeight)
            .background(
                color = if (enabled) primaryColor else inactiveColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(13.dp)
            )
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClick() }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(pillWidth, pillHeight)) {
            val fontSize = 18.sp.toPx()
            val centerX = size.width / 2
            val centerY = size.height / 2
            val textY = centerY + (fontSize / 3)
            
            val textColor = if (enabled) onSurfaceColor else inactiveColor
            val textPaint = android.graphics.Paint().apply {
                color = textColor.toArgb()
                textSize = fontSize
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(text, centerX, textY, textPaint)
            }
        }
    }
}

@Composable
private fun TimelineCenter(
    currentFloor: Float,
    availableFloors: List<Float>,
    prevFloor: Float?,
    nextFloor: Float?,
    onFloorChange: (Float) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val onSurfaceColor = MaterialTheme.colorScheme.background
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // Use density to compute px/dp conversions and measure text width
    val density = LocalDensity.current
    val fontSizePx = with(density) { 18.sp.toPx() }
    val minPillWidthPx = with(density) { 58.dp.toPx() }
    val horizontalPaddingPx = with(density) { 24.dp.toPx() }

    // Prepare the currentText ahead of drawing so we can measure it
    val currentText = if (currentFloor == currentFloor.toInt().toFloat()) {
        "Floor ${currentFloor.toInt()}  ▾"
    } else {
        "Floor $currentFloor  ▾"
    }

    // Measure text width using an Android Paint matching the draw-time paint
    val measurePaint = android.graphics.Paint().apply {
        textSize = fontSizePx
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val measuredTextWidthPx = measurePaint.measureText(currentText)
    val currentPillWidthPx = max(minPillWidthPx, measuredTextWidthPx + horizontalPaddingPx)
    val currentPillWidthDp = with(density) { currentPillWidthPx.toDp() }

    Box(modifier = modifier.height(32.dp)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val pillHeight = 26.dp.toPx()
            val pillCornerRadius = 13.dp.toPx()
            val centerY = size.height / 2
            val centerX = size.width / 2
            val textY = centerY + (fontSizePx / 3)

            // Positions for prev/next floor numbers
            val prevFloorX = size.width * 0.18f
            val nextFloorX = size.width * 0.82f

            // Draw main line in center (between prev and next floor positions) - subdued
            val centerLineStart = if (prevFloor != null) prevFloorX + 16.dp.toPx() else centerX - 22.dp.toPx()
            val centerLineEnd = if (nextFloor != null) nextFloorX - 16.dp.toPx() else centerX + 22.dp.toPx()
            drawLine(
                color = onSurfaceVariant.copy(alpha = 0.5f),
                start = Offset(centerLineStart, centerY),
                end = Offset(centerLineEnd, centerY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Draw left faded line (from button area to prev floor) - only if prev floor exists
            if (prevFloor != null) {
                val fadeStartLeft = 4.dp.toPx()
                val fadeEndLeft = prevFloorX - 16.dp.toPx()
                val leftGradient = Brush.horizontalGradient(
                    colors = listOf(onSurfaceVariant.copy(alpha = 0f), onSurfaceVariant.copy(alpha = 0.5f)),
                    startX = fadeStartLeft,
                    endX = fadeEndLeft
                )
                drawLine(
                    brush = leftGradient,
                    start = Offset(fadeStartLeft, centerY),
                    end = Offset(fadeEndLeft, centerY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Draw right faded line (from next floor to button area) - only if next floor exists
            if (nextFloor != null) {
                val fadeStartRight = nextFloorX + 16.dp.toPx()
                val fadeEndRight = size.width - 4.dp.toPx()
                val rightGradient = Brush.horizontalGradient(
                    colors = listOf(onSurfaceVariant.copy(alpha = 0.5f), onSurfaceVariant.copy(alpha = 0f)),
                    startX = fadeStartRight,
                    endX = fadeEndRight
                )
                drawLine(
                    brush = rightGradient,
                    start = Offset(fadeStartRight, centerY),
                    end = Offset(fadeEndRight, centerY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Text paints - theme aware
            val primaryTextPaint = android.graphics.Paint().apply {
                color = onSurfaceColor.toArgb()
                textSize = fontSizePx
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }

            val secondaryTextPaint = android.graphics.Paint().apply {
                color = onSurfaceVariant.copy(alpha = 0.5f).toArgb()
                textSize = fontSizePx - 8
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }

            // Draw previous floor number (secondary text)
            if (prevFloor != null) {
                val prevText = if (prevFloor == prevFloor.toInt().toFloat()) {
                    prevFloor.toInt().toString()
                } else {
                    prevFloor.toString()
                }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(prevText, prevFloorX, textY, secondaryTextPaint)
                }
            }

            // Draw next floor number (secondary text)
            if (nextFloor != null) {
                val nextText = if (nextFloor == nextFloor.toInt().toFloat()) {
                    nextFloor.toInt().toString()
                } else {
                    nextFloor.toString()
                }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(nextText, nextFloorX, textY, secondaryTextPaint)
                }
            }

            // Draw current floor pill (in center) using dynamic width
            val currentPillWidth = currentPillWidthPx
            val currentPillLeft = centerX - currentPillWidth / 2
            val currentPillTop = centerY - pillHeight / 2

            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(currentPillLeft, currentPillTop),
                size = Size(currentPillWidth, pillHeight),
                cornerRadius = CornerRadius(pillCornerRadius, pillCornerRadius)
            )

            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(currentText, centerX, textY, primaryTextPaint)
            }
        }
    
    // Clickable overlay for the current floor pill (match dynamic width)
    var pillOffsetXPx by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(currentPillWidthDp, 32.dp)
            .onGloballyPositioned { coordinates -> pillOffsetXPx = coordinates.positionInParent().x }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { expanded = true }
    )

    // Dropdown menu: compute offset so its center aligns with the pill center.
    val menuWidthDp = 120.dp
    val menuWidthPx = with(density) { menuWidthDp.toPx() }
    val dropdownOffsetDp = with(density) { (pillOffsetXPx + currentPillWidthPx / 2f - menuWidthPx / 2f).toDp() }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.width(menuWidthDp),
        offset = DpOffset(x = dropdownOffsetDp, y = 0.dp)
    ) {
        availableFloors.forEach { floor ->
            val floorDisplay = if (floor == floor.toInt().toFloat()) {
                "Floor ${floor.toInt()}"
            } else {
                "Floor $floor"
            }
            DropdownMenuItem(
                text = { Text(floorDisplay) },
                onClick = {
                    onFloorChange(floor)
                    expanded = false
                }
            )
        }
    }
    }
}
