package `in`.project.enroute.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

/**
 * Stacked circular Settings and Admin navigation buttons for bottom-left overlay.
 * Styled consistently with AimButton (51dp circle, primaryContainer colors, 4dp shadow).
 * Admin button sits above Settings with 8dp spacing.
 *
 * @param onSettingsClick Callback when settings button is tapped
 * @param onAdminClick Callback when admin button is tapped
 * @param modifier Modifier for the column container
 */
@Composable
fun OverlayNavButtons(
    onSettingsClick: () -> Unit,
    onAdminClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimaryContainer
    val buttonSize = 51.dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Admin button (above settings)
        Box(
            modifier = Modifier
                .size(buttonSize)
                .shadow(elevation = 4.dp, shape = CircleShape)
                .background(color = primaryColor, shape = CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onAdminClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Admin",
                tint = onPrimaryColor,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Settings button (same size as Admin button)
        Box(
            modifier = Modifier
                .size(buttonSize)
                .shadow(elevation = 4.dp, shape = CircleShape)
                .background(color = primaryColor, shape = CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onSettingsClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = onPrimaryColor,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
