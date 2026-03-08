package `in`.project.enroute.feature.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.project.enroute.data.model.Room

/**
 * A bottom panel that slides up when a room label is tapped.
 * Occupies ~1/3 of the screen height with rounded top corners.
 * Shows the room name left-aligned in onPrimaryContainer.
 *
 * Visibility is driven by [room] being non-null (same lifecycle as the map pin).
 * Keeps the last non-null room in memory to display during exit animation.
 *
 * @param room The tapped room, or null when no room is selected
 * @param modifier Modifier applied to the outer wrapper (typically Alignment.BottomCenter)
 */
@Composable
fun RoomInfoPanel(
    modifier: Modifier = Modifier,
    room: Room?,
    isCalculatingPath: Boolean = false,
    hasPath: Boolean = false,
    isNavigationStarted: Boolean = false,
    showShowOnMapButton: Boolean = false,
    onDismiss: () -> Unit = {},
    onDirectionsClick: (Room) -> Unit = {},
    onShowOnMapClick: (Room) -> Unit = {},
    onStartClick: () -> Unit = {},
    onExitClick: () -> Unit = {}
) {
    // Keep last non-null room so exit animation can display it
    var lastRoom by remember { mutableStateOf(room) }
    var lastIsCalculating by remember { mutableStateOf(isCalculatingPath) }
    var lastHasPath by remember { mutableStateOf(hasPath) }
    var lastIsNavigationStarted by remember { mutableStateOf(isNavigationStarted) }
    var lastShowOnMapButton by remember { mutableStateOf(showShowOnMapButton) }
    if (room != null) {
        lastRoom = room
        lastIsCalculating = isCalculatingPath
        lastHasPath = hasPath
        lastIsNavigationStarted = isNavigationStarted
        lastShowOnMapButton = showShowOnMapButton
    }

    AnimatedVisibility(
        visible = room != null,
        enter = slideInVertically(
            animationSpec = tween(durationMillis = 350),
            initialOffsetY = { fullHeight -> fullHeight }   // slide up from below
        ),
        exit = slideOutVertically(
            animationSpec = tween(durationMillis = 300),
            targetOffsetY = { fullHeight -> fullHeight }    // slide back down
        ),
        modifier = modifier
    ) {
        RoomInfoPanelContent(
            room = lastRoom!!,
            isCalculatingPath = lastIsCalculating,
            hasPath = lastHasPath,
            isNavigationStarted = lastIsNavigationStarted,
            showShowOnMapButton = lastShowOnMapButton,
            onDismiss = onDismiss,
            onDirectionsClick = onDirectionsClick,
            onShowOnMapClick = onShowOnMapClick,
            onStartClick = onStartClick,
            onExitClick = onExitClick
        )
    }
}

@Composable
private fun RoomInfoPanelContent(
    room: Room,
    isCalculatingPath: Boolean,
    hasPath: Boolean,
    isNavigationStarted: Boolean,
    showShowOnMapButton: Boolean,
    onDismiss: () -> Unit,
    onDirectionsClick: (Room) -> Unit,
    onShowOnMapClick: (Room) -> Unit,
    onStartClick: () -> Unit,
    onExitClick: () -> Unit
) {
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isNavigationStarted) Modifier.heightIn(min = 64.dp) else Modifier.height(130.dp))
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}  // Consume taps, don't propagate to canvas
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
        ) {
            // Room label with dismiss button
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Room label: "number: name" or just name
                val label = if (room.number != null && room.name != null) {
                    "${room.number}: ${room.name}"
                } else {
                    room.name ?: room.number?.toString() ?: ""
                }

                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    maxLines = if (isNavigationStarted) 6 else 2,
                    modifier = Modifier.weight(1f)
                )

                if (isNavigationStarted) {
                    TextButton(onClick = onExitClick) {
                        Text(
                            text = "Exit",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss
                            )
                            .padding(4.dp)
                    )
                }
            }

            if (isNavigationStarted) return@Column

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = if (hasPath) true else !isCalculatingPath,
                            onClick = {
                                if (hasPath) onStartClick() else onDirectionsClick(room)
                            }
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hasPath) "Start" else "Directions",
                        color = MaterialTheme.colorScheme.background,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (!hasPath && isCalculatingPath) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.background,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (hasPath) Icons.Rounded.PlayArrow else Icons.Rounded.NorthEast,
                            contentDescription = if (hasPath) "Start" else "Directions",
                            tint = MaterialTheme.colorScheme.background,
                            modifier = Modifier.width(18.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showShowOnMapButton,
                    enter = scaleIn(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                    exit = scaleOut(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .height(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onShowOnMapClick(room) }
                            )
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (hasPath) "Show full route" else "Show on map",
                            color = MaterialTheme.colorScheme.onSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.Map,
                            contentDescription = if (hasPath) "Show full route" else "Show on map",
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
