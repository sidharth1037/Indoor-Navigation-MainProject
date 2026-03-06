package `in`.project.enroute.feature.home.locationselection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom panel for confirming a corridor point as the user's location.
 *
 * Single entrance: Shows "Set this as your location?" with Confirm/Cancel.
 * Multiple entrances: Shows numbered entrance choices. User taps a number to select,
 * then confirms.
 *
 * Slides up from bottom, matching the style of RoomInfoPanel.
 */
@Composable
fun EntranceConfirmationPanel(
    corridorPoints: List<CorridorPoint>,
    selectedIndex: Int?,
    roomLabel: String,
    onSelectEntrance: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchAnother: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = corridorPoints.isNotEmpty(),
        enter = slideInVertically(
            animationSpec = tween(durationMillis = 350),
            initialOffsetY = { it }
        ),
        exit = slideOutVertically(
            animationSpec = tween(durationMillis = 300),
            targetOffsetY = { it }
        ),
        modifier = modifier
    ) {
        val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        val isSingle = corridorPoints.size == 1
        val hasSelection = selectedIndex != null
        val actionButtonHeight = 40.dp
        val actionButtonShape = RoundedCornerShape(12.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
            ) {
                // Header row with title and dismiss
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = roomLabel,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (isSingle) "Set this as your location?"
                            else "Select an entrance",
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .size(36.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss
                            )
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isSingle) {
                    // Multiple entrances: show numbered choice buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        corridorPoints.forEachIndexed { idx, point ->
                            val isSelected = selectedIndex == idx

                            EntranceChoiceChip(
                                index = point.index,
                                isSelected = isSelected,
                                onClick = { onSelectEntrance(idx) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Confirm button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onSearchAnother,
                        modifier = Modifier
                            .weight(1f)
                            .height(actionButtonHeight),
                        shape = actionButtonShape,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text("Search Another")
                    }

                    Button(
                        onClick = onConfirm,
                        enabled = isSingle || hasSelection,
                        modifier = Modifier
                            .weight(1f)
                            .height(actionButtonHeight),
                        shape = actionButtonShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.background
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

/**
 * A numbered chip for selecting an entrance when multiple entrances exist.
 */
@Composable
private fun EntranceChoiceChip(
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.primaryContainer

    val contentColor = if (isSelected)
        MaterialTheme.colorScheme.background
    else
        MaterialTheme.colorScheme.onPrimaryContainer

    val borderColor = MaterialTheme.colorScheme.primary

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bgColor)
            .then(
                if (!isSelected) Modifier
                    .background(bgColor)
                    .then(
                        Modifier
                            .clip(CircleShape)
                            .background(Color.Transparent)
                    )
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        // For non-selected, draw a border ring matching the selected fill color
        if (!isSelected) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .then(
                        Modifier.border(
                            width = 2.dp,
                            color = borderColor,
                            shape = CircleShape
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = index.toString(),
                    color = contentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Text(
                text = index.toString(),
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
