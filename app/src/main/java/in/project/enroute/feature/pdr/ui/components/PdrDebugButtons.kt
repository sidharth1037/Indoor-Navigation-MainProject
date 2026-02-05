package `in`.project.enroute.feature.pdr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Debug buttons for PDR functionality.
 * Temporary component for testing PDR features.
 *
 * Contains:
 * - Set Origin button: Opens origin selection dialog
 * - Clear button: Clears path and stops tracking
 */
@Composable
fun PdrDebugButtons(
    isTracking: Boolean,
    onSetOriginClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Set Origin Button
        FloatingActionButton(
            onClick = onSetOriginClick,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Set Origin",
                modifier = Modifier.size(24.dp)
            )
        }
        
        // Clear Button (only show when tracking)
        if (isTracking) {
            FloatingActionButton(
                onClick = onClearClick,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear Path",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
