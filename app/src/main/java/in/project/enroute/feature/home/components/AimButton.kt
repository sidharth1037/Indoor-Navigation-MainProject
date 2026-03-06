package `in`.project.enroute.feature.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

/**
 * Aim/location button for setting or centering the user's location.
 * Shows "Set your location" text when PDR is inactive.
 * Shows circular aim icon when PDR is active.
 * Positioned at bottom right as an overlay on the canvas.
 * Animates in/out with scale animation.
 *
 * @param onClick Callback when the button is clicked
 * @param isVisible Whether the button should be visible
 * @param isPdrActive Whether PDR tracking is active (location is set)
 * @param modifier Modifier for customization
 */
@Composable
fun AimButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    isPdrActive: Boolean = false
) {
    val primaryColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimaryContainer
    val circleButtonSize = 51.dp

    AnimatedVisibility(
        modifier = modifier,
        visible = isVisible,
        enter = scaleIn(animationSpec = tween(300)),
        exit = scaleOut(animationSpec = tween(300))
    ) {
        if (isPdrActive) {
            // Circular aim button when PDR is active
            Box(
                modifier = Modifier
                    .size(circleButtonSize)
                    .shadow(elevation = 4.dp, shape = CircleShape)
                    .background(color = primaryColor, shape = CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = "Center view",
                    tint = onPrimaryColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            // Rounded button with text when PDR is not active
            Box(
                modifier = Modifier
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(24.dp))
                    .background(color = primaryColor, shape = RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClick() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = "Set location",
                        tint = onPrimaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Set your location",
                        style = MaterialTheme.typography.bodyMedium,
                        color = onPrimaryColor
                    )
                }
            }
        }
    }
}
