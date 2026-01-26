package `in`.project.enroute.feature.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
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
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Building name at top
        if (buildingName.isNotEmpty()) {
            val floorDisplay = if (safeCurrentFloor == safeCurrentFloor.toInt().toFloat()) {
                safeCurrentFloor.toInt().toString()
            } else {
                safeCurrentFloor.toString()
            }
            Text(
                text = "$buildingName : Floor $floorDisplay",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
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
    val outlineColor = MaterialTheme.colorScheme.background
    
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
            inactiveColor = inactiveColor,
            outlineColor = outlineColor
        )
        
        // Timeline center section with prev floor, line, current, line, next floor
        TimelineCenter(
            currentFloor = currentFloor,
            availableFloors = availableFloors,
            prevFloor = prevFloor,
            nextFloor = nextFloor,
            onFloorChange = onFloorChange,
            primaryColor = primaryColor,
            outlineColor = outlineColor,
            modifier = Modifier.weight(1f)
        )
        
        // Plus button
        TimelineButton(
            text = "+",
            enabled = nextFloor != null,
            onClick = onIncrement,
            primaryColor = primaryColor,
            inactiveColor = inactiveColor,
            outlineColor = outlineColor
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
    outlineColor: Color,
    modifier: Modifier = Modifier
) {
    val pillWidth = 32.dp
    val pillHeight = 26.dp
    
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
            
            val textColor = if (enabled) Color.White else inactiveColor
            val textPaint = android.graphics.Paint().apply {
                color = textColor.toArgb()
                textSize = fontSize
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            
            val outlinePaint = android.graphics.Paint().apply {
                color = outlineColor.toArgb()
                textSize = fontSize
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = textPaint.typeface
                strokeWidth = 8f
                style = android.graphics.Paint.Style.STROKE
            }
            
            // Draw pill border if enabled
            if (enabled) {
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(13.dp.toPx(), 13.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(text, centerX, textY, outlinePaint)
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
    outlineColor: Color,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    
    Box(modifier = modifier.height(32.dp)) {
        Canvas(modifier = Modifier.matchParentSize()) {
        val pillHeight = 26.dp.toPx()
        val pillCornerRadius = 13.dp.toPx()
        val fontSize = 18.sp.toPx()
        val centerY = size.height / 2
        val centerX = size.width / 2
        val textY = centerY + (fontSize / 3)
        
        // Positions for prev/next floor numbers
        val prevFloorX = size.width * 0.22f
        val nextFloorX = size.width * 0.78f
        
        // Draw main line in center (between prev and next floor positions) - subdued
        val centerLineStart = if (prevFloor != null) prevFloorX + 20.dp.toPx() else centerX - 22.dp.toPx()
        val centerLineEnd = if (nextFloor != null) nextFloorX - 20.dp.toPx() else centerX + 22.dp.toPx()
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
            val fadeEndLeft = prevFloorX - 20.dp.toPx()
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
            val fadeStartRight = nextFloorX + 20.dp.toPx()
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
            textSize = fontSize
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        
        val secondaryTextPaint = android.graphics.Paint().apply {
            color = onSurfaceVariant.toArgb()
            textSize = fontSize
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        
        val outlinePaintSecondary = android.graphics.Paint().apply {
            color = outlineColor.toArgb()
            textSize = fontSize
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = primaryTextPaint.typeface
            strokeWidth = 8f
            style = android.graphics.Paint.Style.STROKE
        }
        
        // Draw previous floor number (secondary text)
        if (prevFloor != null) {
            val prevText = if (prevFloor == prevFloor.toInt().toFloat()) {
                prevFloor.toInt().toString()
            } else {
                prevFloor.toString()
            }
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(prevText, prevFloorX, textY, outlinePaintSecondary)
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
                canvas.nativeCanvas.drawText(nextText, nextFloorX, textY, outlinePaintSecondary)
                canvas.nativeCanvas.drawText(nextText, nextFloorX, textY, secondaryTextPaint)
            }
        }
        
        // Draw current floor pill (in center)
        val currentPillWidth = 58.dp.toPx()
        val currentPillLeft = centerX - currentPillWidth / 2
        val currentPillTop = centerY - pillHeight / 2
        
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(currentPillLeft, currentPillTop),
            size = Size(currentPillWidth, pillHeight),
            cornerRadius = CornerRadius(pillCornerRadius, pillCornerRadius)
        )
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(currentPillLeft, currentPillTop),
            size = Size(currentPillWidth, pillHeight),
            cornerRadius = CornerRadius(pillCornerRadius, pillCornerRadius),
            style = Stroke(width = 2.dp.toPx())
        )
        
        val currentText = if (currentFloor == currentFloor.toInt().toFloat()) {
            "${currentFloor.toInt()}  ▾"
        } else {
            "$currentFloor  ▾"
        }
        
        // Draw current floor text with dropdown arrow
        val outlinePaintPrimary = android.graphics.Paint().apply {
            color = outlineColor.toArgb()
            textSize = fontSize
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = primaryTextPaint.typeface
            strokeWidth = 8f
            style = android.graphics.Paint.Style.STROKE
        }
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(currentText, centerX, textY, outlinePaintPrimary)
            canvas.nativeCanvas.drawText(currentText, centerX, textY, primaryTextPaint)
        }
    }
    
    // Clickable overlay for the current floor pill
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(50.dp, 32.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { expanded = true }
    )
    
    // Dropdown menu
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.width(120.dp)
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
