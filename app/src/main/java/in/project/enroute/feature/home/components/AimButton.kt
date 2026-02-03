package `in`.project.enroute.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

/**
 * Circular aim/target button for centering the canvas view.
 * Positioned at bottom right as an overlay on the canvas.
 *
 * @param onClick Callback when the button is clicked
 * @param modifier Modifier for customization
 */
@Composable
fun AimButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimaryContainer
    val buttonSize = 56.dp

    Box(
        modifier = modifier
            .size(buttonSize)
            .shadow(elevation = 4.dp, shape = CircleShape)
            .background(color = primaryColor, shape = CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MyLocation,
            contentDescription = "Center view",
            tint = onPrimaryColor,
            modifier = Modifier.size(28.dp)
        )
    }
}
