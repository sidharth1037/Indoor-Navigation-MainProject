package `in`.project.enroute.feature.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

@Composable
private fun FloorSliderContent(
    buildingName: String,
    availableFloors: List<Float>,
    currentFloor: Float,
    onFloorChange: (Float) -> Unit
) {
    var lastValidBuildingName by remember { mutableStateOf(buildingName) }
    if (buildingName.isNotEmpty()) {
        lastValidBuildingName = buildingName
    }

    var lastValidFloors by remember { mutableStateOf(availableFloors) }
    if (availableFloors.size >= 2) {
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
            .height(85.dp)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(28.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (lastValidBuildingName.isNotEmpty()) {
            Text(
                text = lastValidBuildingName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }

        FloorControls(
            currentFloor = safeCurrentFloor,
            availableFloors = safeFloors,
            prevFloor = prevFloor,
            nextFloor = nextFloor,
            onFloorChange = onFloorChange
        )
    }
}

@Composable
private fun FloorControls(
    currentFloor: Float,
    availableFloors: List<Float>,
    prevFloor: Float?,
    nextFloor: Float?,
    onFloorChange: (Float) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    val buttonWidth = 80.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        FloorButton(
            enabled = prevFloor != null,
            onClick = { prevFloor?.let { onFloorChange(it) } },
            isPrevious = true,
            primaryColor = primaryColor,
            disabledColor = disabledColor,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(buttonWidth, 32.dp)
        )

        FloorDisplay(
            currentFloor = currentFloor,
            floors = availableFloors,
            onFloorChange = onFloorChange,
            primaryColor = primaryColor,
            modifier = Modifier.align(Alignment.Center)
        )

        FloorButton(
            enabled = nextFloor != null,
            onClick = { nextFloor?.let { onFloorChange(it) } },
            isPrevious = false,
            primaryColor = primaryColor,
            disabledColor = disabledColor,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(buttonWidth, 32.dp)
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
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    val display = if (currentFloor == currentFloor.toInt().toFloat()) {
        "Floor ${currentFloor.toInt()}"
    } else {
        "Floor $currentFloor"
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .height(32.dp)
                .background(color = primaryColor, shape = RoundedCornerShape(percent = 50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = true }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = display,
                    fontSize = 18.sp,
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
