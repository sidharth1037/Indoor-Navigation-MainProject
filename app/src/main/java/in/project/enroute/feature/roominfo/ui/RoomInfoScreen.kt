package `in`.project.enroute.feature.roominfo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.project.enroute.data.model.RoomInfo
import `in`.project.enroute.feature.roominfo.RoomInfoViewModel

/**
 * Full-screen detail page for viewing/editing room info.
 * Displays room metadata, description, and tags.
 * Admins can edit the description and manage tags.
 *
 * @param campusId Campus ID
 * @param buildingId Building ID
 * @param buildingName Human-readable building name
 * @param floorId Floor ID
 * @param roomId Room ID
 * @param roomNumber Room number/label
 * @param roomName Room name/label
 * @param isAdmin Whether the current user is an admin and owns the campus
 * @param viewModel RoomInfoViewModel (shared from Home backstack entry)
 * @param onBack Callback when back button is pressed
 */
@Composable
fun RoomInfoScreen(
    campusId: String,
    buildingId: String,
    buildingName: String,
    floorId: String,
    roomId: Int,
    roomNumber: Int?,
    roomName: String?,
    isAdmin: Boolean,
    viewModel: RoomInfoViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val roomInfo = uiState.roomInfo

    // Load room info when entering screen
    remember(campusId, buildingId, floorId, roomId) {
        viewModel.loadRoomInfo(buildingId, floorId, roomId)
    }

    var isEditMode by remember(roomInfo) { mutableStateOf(false) }
    var editDescription by remember(roomInfo) { mutableStateOf(roomInfo?.description ?: "") }
    var editTags by remember(roomInfo) { mutableStateOf(roomInfo?.tags ?: emptyList()) }
    var tagInput by remember { mutableStateOf("") }

    val roomTitle = when {
        roomNumber != null && roomName != null -> "$roomNumber: $roomName"
        roomName != null -> roomName
        roomNumber != null -> roomNumber.toString()
        else -> "Room"
    }

    // Parse "floor_1" → "Floor 1"
    val floorDisplay = run {
        val parts = floorId.split("_")
        if (parts.size == 2 && parts[0] == "floor") {
            "Floor ${parts[1]}"
        } else {
            floorId
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header with back button + Edit button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    viewModel.clearRoomInfo()
                    onBack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(modifier = Modifier.weight(1f))

                // Edit button only for admins
                if (isAdmin && !isEditMode) {
                    IconButton(onClick = { isEditMode = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Loading indicator
            if (uiState.isLoading && roomInfo == null) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (roomInfo != null) {
                // Room title and metadata
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = roomTitle,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$floorDisplay  •  $buildingName",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Description section
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Description",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isEditMode) {
                        // Edit mode: text field
                        BasicTextField(
                            value = editDescription,
                            onValueChange = { editDescription = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.shapes.small
                                )
                                .padding(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (editDescription.isEmpty()) {
                                        Text(
                                            "Enter a description...",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    } else {
                        // View mode: display description
                        if (roomInfo.description.isNotBlank()) {
                            Text(
                                text = roomInfo.description,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Text(
                                text = "No description yet",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tags section
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "Tags",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isEditMode) {
                        // Edit mode: tag input + add button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.shapes.small
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = tagInput,
                                onValueChange = { tagInput = it },
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    if (tagInput.isEmpty()) {
                                        Text(
                                            "Type a tag...",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (tagInput.isNotBlank()) {
                                        editTags = editTags + tagInput
                                        tagInput = ""
                                    }
                                },
                                modifier = Modifier.height(40.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 8.dp
                                )
                            ) {
                                Text("+ Add", fontSize = 12.sp)
                            }
                        }

                        // Removable tag chips
                        Spacer(modifier = Modifier.height(12.dp))
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            editTags.forEachIndexed { index, tag ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(tag) },
                                    modifier = Modifier,
                                    onDismiss = { editTags = editTags.filterIndexed { i, _ -> i != index } }
                                )
                            }
                        }
                    } else {
                        // View mode: display tags as non-removable chips
                        if (roomInfo.tags.isNotEmpty()) {
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                            ) {
                                roomInfo.tags.forEach { tag ->
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(tag, fontSize = 13.sp) }
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No tags yet",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Save/Cancel buttons in edit mode
                if (isEditMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                isEditMode = false
                                editDescription = roomInfo.description
                                editTags = roomInfo.tags
                                tagInput = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                viewModel.saveRoomInfo(
                                    buildingId,
                                    floorId,
                                    roomId,
                                    editDescription,
                                    editTags
                                )
                                isEditMode = false
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isSaving
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .height(20.dp)
                                        .width(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Save")
                            }
                        }
                    }
                }

                // Error message
                if (uiState.error != null) {
                    Text(
                        text = uiState.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
