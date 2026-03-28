package `in`.project.enroute.feature.home.elevator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Elevator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Dialog shown when the user is near an elevator entrance.
 *
 * Asks the user which floor they want to go to. The floor picker shows
 * only floors that have an elevator/lift room in the same building,
 * excluding the current floor.
 *
 * Follows the visual design of [DestinationPromptDialog] and [StopTrackingConfirmDialog]:
 * - RoundedCornerShape(28.dp)
 * - primaryContainer background
 * - 92% width
 * - onPrimaryContainer text colors
 *
 * @param floors Available floors to pick from (ordered, current floor excluded)
 * @param onFloorSelected Called with the selected [ElevatorFloor] when "Take Elevator" is pressed
 * @param onDismiss Called when the dialog is cancelled
 */
@Composable
fun ElevatorPromptDialog(
    floors: List<ElevatorFloor>,
    onFloorSelected: (ElevatorFloor) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFloor by remember { mutableStateOf<ElevatorFloor?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // ── Elevator icon ──────────────────────────────────────────
                Icon(
                    imageVector = Icons.Rounded.Elevator,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ── Title ──────────────────────────────────────────────────
                Text(
                    text = "Using the Elevator?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // ── Subtitle ───────────────────────────────────────────────
                Text(
                    text = "Select the floor you're going to",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── Floor picker (scrollable list of radio-button rows) ───
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Cap height so the dialog doesn't grow unboundedly
                        .height((floors.size.coerceAtMost(5) * 48).dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(floors) { floor ->
                        val isSelected = selectedFloor?.floorId == floor.floorId

                        FloorPickerItem(
                            floor = floor,
                            isSelected = isSelected,
                            onClick = { selectedFloor = floor }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Action buttons ─────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(
                            text = "Cancel",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }

                    // Take Elevator
                    Button(
                        onClick = {
                            selectedFloor?.let { onFloorSelected(it) }
                        },
                        enabled = selectedFloor != null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.background
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Elevator,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Take Elevator",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single row in the floor picker list.
 * Shows a radio button + floor label. Tapping the row selects the floor.
 */
@Composable
private fun FloorPickerItem(
    floor: ElevatorFloor,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = floor.displayLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 16.sp
        )
    }
}
