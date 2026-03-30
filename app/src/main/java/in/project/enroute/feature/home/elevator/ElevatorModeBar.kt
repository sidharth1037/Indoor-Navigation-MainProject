package `in`.project.enroute.feature.home.elevator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Elevator
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom bar composable displayed during elevator mode.
 *
 * Spans the full width between left and right button columns with symmetric padding.
 * Height matches two circular buttons (51dp each) plus the gap between them (8dp) = 110dp,
 * so its bottom edge aligns with the bottom edge of the circular buttons on either side.
 *
 * ## Visual states
 * - **ACTIVE**: "Elevator Mode" title + "Going to Floor X" subtitle + "Change" button
 * - **COMPLETING**: "Continuing at Floor X" message, then fades out
 *
 * Animates in with slideInVertically + fadeIn, out with slideOutVertically + fadeOut.
 *
 * @param state Current elevator mode state
 * @param onChangeFloor Called when the user taps the "Change" button to reopen the floor picker
 * @param modifier Modifier for the outer container
 */
@Composable
fun ElevatorModeBar(
    state: ElevatorModeState,
    isNearElevator: Boolean,
    onUseElevator: () -> Unit,
    onChangeFloor: () -> Unit,
    onExitMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentState = when {
        state.phase == ElevatorPhase.COMPLETING -> ElevatorBarContentState.COMPLETING
        state.phase == ElevatorPhase.ACTIVE -> ElevatorBarContentState.ACTIVE
        isNearElevator -> ElevatorBarContentState.NEAR
        else -> ElevatorBarContentState.HIDDEN
    }
    val isVisible = contentState != ElevatorBarContentState.HIDDEN

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            animationSpec = tween(durationMillis = 350),
            initialOffsetY = { it }  // slide up from below
        ) + fadeIn(animationSpec = tween(durationMillis = 350)),
        exit = slideOutVertically(
            animationSpec = tween(durationMillis = 300),
            targetOffsetY = { it }  // slide down
        ) + fadeOut(animationSpec = tween(durationMillis = 300)),
        modifier = modifier
    ) {
        val shape = RoundedCornerShape(20.dp)
        // Height = 2 × buttonSize(51dp) + gap(8dp)
        val barHeight = 110.dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .shadow(elevation = 4.dp, shape = shape)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = shape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Consume taps so they don't propagate to canvas
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = contentState,
                animationSpec = tween(durationMillis = 220),
                label = "elevatorBarContent"
            ) { targetState ->
                when (targetState) {
                    ElevatorBarContentState.COMPLETING -> {
                        CompletionContent(
                            floorLabel = state.targetFloor?.displayLabel ?: "unknown floor"
                        )
                    }

                    ElevatorBarContentState.ACTIVE -> {
                        ActiveContent(
                            floorLabel = state.targetFloor?.displayLabel ?: "unknown floor",
                            onChangeFloor = onChangeFloor,
                            onExitMode = onExitMode
                        )
                    }

                    ElevatorBarContentState.NEAR -> {
                        NearElevatorContent(onUseElevator = onUseElevator)
                    }

                    ElevatorBarContentState.HIDDEN -> Unit
                }
            }
        }
    }
}

private enum class ElevatorBarContentState {
    HIDDEN,
    NEAR,
    ACTIVE,
    COMPLETING
}

@Composable
private fun NearElevatorContent(
    onUseElevator: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.Elevator,
                contentDescription = "Elevator nearby",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "Using elevator?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "Open floor picker",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        TextButton(
            onClick = onUseElevator,
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            colors = ButtonDefaults.textButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.background
            )
        ) {
            Text(
                text = "Yes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.background,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * Content shown during ACTIVE phase: elevator icon, title, destination, change button.
 */
@Composable
private fun ActiveContent(
    floorLabel: String,
    onChangeFloor: () -> Unit,
    onExitMode: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: icon + text
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.Elevator,
                contentDescription = "Elevator",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "Elevator Mode",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "Going to $floorLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right: stacked actions
        Column(
            modifier = Modifier.padding(end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.End
        ) {
            TextButton(
                onClick = onChangeFloor,
                modifier = Modifier.width(96.dp),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.background
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapVert,
                    contentDescription = "Change floor",
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(14.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "Change",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.background,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }

            TextButton(
                onClick = onExitMode,
                modifier = Modifier.width(96.dp),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = "Exit elevator mode",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "Exit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Content shown during COMPLETING phase: centered message with check icon.
 */
@Composable
private fun CompletionContent(
    floorLabel: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Elevator,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Continuing at $floorLabel",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
    }
}
