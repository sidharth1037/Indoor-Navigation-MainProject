package `in`.project.enroute.feature.home.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
    onDismiss: () -> Unit = {}
) {
    // Keep last non-null room so exit animation can display it
    var lastRoom by remember { mutableStateOf(room) }
    if (room != null) {
        lastRoom = room
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
        RoomInfoPanelContent(room = lastRoom!!, onDismiss = onDismiss)
    }
}

@Composable
private fun RoomInfoPanelContent(room: Room, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
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
                .padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 16.dp)
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
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    modifier = Modifier.weight(1f)
                )

                // Dismiss button with down arrow
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .width(50.dp)
                        .height(46.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Directions button row
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
                        onClick = {
                            // TODO: Implement directions feature
                        }
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Directions",
                    color = MaterialTheme.colorScheme.background,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.NorthEast,
                    contentDescription = "Directions",
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.width(18.dp)
                )
            }
        }
    }
}
