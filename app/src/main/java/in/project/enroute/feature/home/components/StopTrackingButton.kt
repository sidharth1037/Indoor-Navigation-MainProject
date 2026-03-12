package `in`.project.enroute.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WrongLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * A circular icon-only button for stopping PDR tracking and clearing the path.
 * Only shown when tracking is active.
 * Uses the WrongLocation icon to indicate clearing/stopping location tracking.
 *
 * @param isSliderVisible Whether the floor slider is currently visible (unused, kept for API compatibility)
 * @param onClick Callback when the button is clicked
 * @param modifier Modifier for the button container
 */
@Composable
fun StopTrackingButton(
    modifier: Modifier = Modifier,
    isSliderVisible: Boolean = false,
    onClick: () -> Unit
) {
    // Blend errorContainer toward surfaceVariant to mute it without transparency
    val errorContainerColor = lerp(
        MaterialTheme.colorScheme.errorContainer,
        MaterialTheme.colorScheme.surfaceVariant,
        0.25f
    )
    val onErrorContainerColor = MaterialTheme.colorScheme.onErrorContainer
    val buttonSize = 51.dp

    Box(
        modifier = modifier
            .size(buttonSize)
            .shadow(elevation = 4.dp, shape = CircleShape)
            .background(color = errorContainerColor, shape = CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.WrongLocation,
            contentDescription = "Stop Tracking",
            tint = onErrorContainerColor,
            modifier = Modifier.size(28.dp)
        )
    }
}
