package `in`.project.enroute.feature.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FloorSlider(
    buildingName: String,
    availableFloors: List<Float>,
    currentFloor: Float,
    onFloorChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    disabled: Boolean = false,
    onHeightMeasured: (Int) -> Unit = {}
) {
    AnimatedVisibility(
        visible = isVisible && availableFloors.isNotEmpty(),
        enter = fadeIn() + slideInHorizontally { -it },
        exit = fadeOut() + slideOutHorizontally { -it },
        modifier = modifier
    ) {
        FloorSliderContent(
            buildingName = buildingName,
            availableFloors = availableFloors,
            currentFloor = currentFloor,
            onFloorChange = onFloorChange,
            disabled = disabled,
            onHeightMeasured = onHeightMeasured
        )
    }
}

@Composable
private fun FloorSliderContent(
    buildingName: String,
    availableFloors: List<Float>,
    currentFloor: Float,
    onFloorChange: (Float) -> Unit,
    disabled: Boolean = false,
    onHeightMeasured: (Int) -> Unit = {}
) {
    var lastValidBuildingName by remember { mutableStateOf(buildingName) }
    if (buildingName.isNotEmpty()) {
        lastValidBuildingName = buildingName
    }

    var lastValidFloors by remember { mutableStateOf(availableFloors) }
    if (availableFloors.isNotEmpty()) {
        lastValidFloors = availableFloors
    }

    val safeFloors = lastValidFloors.sorted()
    val safeCurrentFloor = if (lastValidFloors.contains(currentFloor)) currentFloor else safeFloors.firstOrNull() ?: 0f
    val currentIndex = safeFloors.indexOf(safeCurrentFloor).coerceAtLeast(0)
    val prevFloor = if (currentIndex > 0) safeFloors[currentIndex - 1] else null
    val nextFloor = if (currentIndex < safeFloors.size - 1) safeFloors[currentIndex + 1] else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightMeasured(it.height) }
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(28.dp)
            )
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedContent(
            targetState = lastValidBuildingName,
            transitionSpec = {
                val duration = 240
                val exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(durationMillis = duration)
                ) + fadeOut(animationSpec = tween(durationMillis = duration))

                val enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(durationMillis = duration, delayMillis = duration)
                ) + fadeIn(animationSpec = tween(durationMillis = duration, delayMillis = duration))

                enter.togetherWith(exit)
            },
            label = "buildingNameTransition"
        ) { name ->
            if (name.isNotEmpty()) {
                Text(
                    text = name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        FloorControls(
            currentFloor = safeCurrentFloor,
            availableFloors = safeFloors,
            prevFloor = prevFloor,
            nextFloor = nextFloor,
            onFloorChange = onFloorChange,
            disabled = disabled
        )
    }
}

@Composable
private fun FloorControls(
    currentFloor: Float,
    availableFloors: List<Float>,
    prevFloor: Float?,
    nextFloor: Float?,
    onFloorChange: (Float) -> Unit,
    disabled: Boolean = false
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    val buttonWidth = 62.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        FloorButton(
            enabled = prevFloor != null && !disabled,
            onClick = { prevFloor?.let { onFloorChange(it) } },
            isPrevious = true,
            primaryColor = primaryColor,
            disabledColor = disabledColor,
            modifier = Modifier.width(buttonWidth)
        )

        FloorDisplay(
            currentFloor = currentFloor,
            floors = availableFloors,
            onFloorChange = onFloorChange,
            primaryColor = primaryColor,
            disabled = disabled,
            modifier = Modifier
        )

        FloorButton(
            enabled = nextFloor != null && !disabled,
            onClick = { nextFloor?.let { onFloorChange(it) } },
            isPrevious = false,
            primaryColor = primaryColor,
            disabledColor = disabledColor,
            modifier = Modifier.width(buttonWidth)
        )
    }
}

@Composable
private fun FloorButton(
    enabled: Boolean,
    onClick: () -> Unit,
    isPrevious: Boolean,
    primaryColor: Color,
    disabledColor: Color,
    modifier: Modifier = Modifier
) {
    val bgColor = if (enabled) primaryColor else disabledColor
    val contentColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .background(color = bgColor, shape = RoundedCornerShape(percent = 50))
            .height(30.dp)
            .then(if (enabled) Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPrevious) Icons.AutoMirrored.Rounded.ArrowBackIos else Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = if (isPrevious) "Previous floor" else "Next floor", 
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun FloorDisplay(
    floors: List<Float>,
    currentFloor: Float,
    onFloorChange: (Float) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    disabled: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    
    val display = if (currentFloor == currentFloor.toInt().toFloat()) {
        "Floor ${currentFloor.toInt()}"
    } else {
        "Floor $currentFloor"
    }

    val pillColor = if (disabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else primaryColor

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .height(30.dp)
                .background(color = pillColor, shape = RoundedCornerShape(percent = 50))
                .then(
                    if (!disabled) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = true } else Modifier
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = display,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.background
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Select floor",
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.wrapContentWidth()
        ) {
            floors.forEach { floor ->
                val label = if (floor == floor.toInt().toFloat()) {
                    "Floor ${floor.toInt()}"
                } else {
                    "Floor $floor"
                }
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onFloorChange(floor)
                        expanded = false
                    }
                )
            }
        }
    }
}
