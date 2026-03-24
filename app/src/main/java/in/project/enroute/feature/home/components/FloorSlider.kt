package `in`.project.enroute.feature.home.components

import `in`.project.enroute.R
import `in`.project.enroute.feature.navigation.guidance.GuidanceType
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FloorSlider(
    buildingName: String,
    availableFloors: List<Float>,
    currentFloor: Float,
    onFloorChange: (Float) -> Unit,
    instructionText: String = "",
    instructionType: GuidanceType = GuidanceType.IDLE,
    isNavigationActive: Boolean = false,
    hideControlsForNavigation: Boolean = false,
    showCurrentFloorLabelOnly: Boolean = false,
    currentFloorLabel: String = "",
    navigationExpandedHeight: Dp = 136.dp,
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
            instructionText = instructionText,
            instructionType = instructionType,
            isNavigationActive = isNavigationActive,
            hideControlsForNavigation = hideControlsForNavigation,
            showCurrentFloorLabelOnly = showCurrentFloorLabelOnly,
            currentFloorLabel = currentFloorLabel,
            navigationExpandedHeight = navigationExpandedHeight,
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
    instructionText: String,
    instructionType: GuidanceType,
    isNavigationActive: Boolean,
    hideControlsForNavigation: Boolean,
    showCurrentFloorLabelOnly: Boolean,
    currentFloorLabel: String,
    navigationExpandedHeight: Dp,
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
    val showTopContent = !hideControlsForNavigation

    val minHeight by animateDpAsState(
        targetValue = if (isNavigationActive) navigationExpandedHeight else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "floorSliderMinHeight"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .onSizeChanged { onHeightMeasured(it.height) }
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(28.dp)
            )
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = showTopContent,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180))
        ) {
            Column(
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

        if (showCurrentFloorLabelOnly && currentFloorLabel.isNotBlank()) {
            Text(
                text = currentFloorLabel,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }

        if (isNavigationActive && instructionText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 0.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.22f))
            )

            val normalizedInstruction = instructionText.trim().removeSuffix(".")
            val isStraightInstruction = instructionType == GuidanceType.STRAIGHT &&
                instructionText.startsWith("Walk straight for around")
            val guidanceIconRes = when {
                isStraightInstruction -> R.drawable.straight
                normalizedInstruction == "Turn slightly left" -> R.drawable.turn_slight_left
                normalizedInstruction == "Turn slightly right" -> R.drawable.turn_slight_right
                normalizedInstruction == "Turn left" -> R.drawable.turn_left
                normalizedInstruction == "Turn right" -> R.drawable.turn_right
                normalizedInstruction == "Turn around" -> R.drawable.u_turn_right
                else -> null
            }

            if (guidanceIconRes != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (isStraightInstruction) 0.dp else 1.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = guidanceIconRes),
                        contentDescription = instructionText,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = instructionText,
                        fontSize = 15.sp,
                        lineHeight = if (isStraightInstruction) 16.sp else 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        maxLines = if (isStraightInstruction) 1 else if (hideControlsForNavigation) 4 else 2,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = !isStraightInstruction
                    )
                }
            } else {
                Text(
                    text = instructionText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    maxLines = if (hideControlsForNavigation) 4 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
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
