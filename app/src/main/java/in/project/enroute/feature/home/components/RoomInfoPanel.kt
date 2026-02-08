package `in`.project.enroute.feature.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 *
 * @param room The tapped room, or null when no room is selected
 * @param modifier Modifier applied to the outer wrapper (typically Alignment.BottomCenter)
 */
@Composable
fun RoomInfoPanel(
    room: Room?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

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
        // room is guaranteed non-null while visible, but guard anyway
        val displayRoom = room ?: return@AnimatedVisibility

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.33f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                // Room label: "number: name" or just name
                val label = if (displayRoom.number != null && displayRoom.name != null) {
                    "${displayRoom.number}: ${displayRoom.name}"
                } else {
                    displayRoom.name ?: displayRoom.number?.toString() ?: ""
                }

                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp
                )
            }
        }
    }
}
