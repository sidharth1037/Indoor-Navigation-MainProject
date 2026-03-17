package `in`.project.enroute.feature.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBuildingScreen(
    viewModel: AdminViewModel,
    uiState: AdminUiState,
    onAddFloorForBuilding: (String) -> Unit,
    onBack: () -> Unit
) {
    var showDeleteFloorDialog by remember { mutableStateOf(false) }
    var deleteFloorId by remember { mutableStateOf("") }
    var deleteFloorExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.editBuildingId) {
        if (uiState.editBuildingId.isNotBlank()) {
            viewModel.loadFloorsForBuildingPublic(uiState.editBuildingId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Edit Building",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            text = "Building ID: ${uiState.editBuildingId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = uiState.editBuildingName,
            onValueChange = { viewModel.updateEditBuildingName(it) },
            label = { Text("Building Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.editBuildingScale,
                onValueChange = { viewModel.updateEditBuildingScale(it) },
                label = { Text("Scale") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = uiState.editBuildingRotation,
                onValueChange = { viewModel.updateEditBuildingRotation(it) },
                label = { Text("Rotation") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }

        Text(
            text = "Label Position",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.editBuildingLabelX,
                onValueChange = { viewModel.updateEditBuildingLabelX(it) },
                label = { Text("Label X") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = uiState.editBuildingLabelY,
                onValueChange = { viewModel.updateEditBuildingLabelY(it) },
                label = { Text("Label Y") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }

        Text(
            text = "Relative Position",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.editBuildingRelX,
                onValueChange = { viewModel.updateEditBuildingRelX(it) },
                label = { Text("Offset X") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = uiState.editBuildingRelY,
                onValueChange = { viewModel.updateEditBuildingRelY(it) },
                label = { Text("Offset Y") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { viewModel.saveEditBuilding() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading && uiState.editBuildingName.isNotBlank()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Changes")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text(
            text = "Floor Data",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        FilledTonalButton(
            onClick = { onAddFloorForBuilding(uiState.editBuildingId) },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.editBuildingId.isNotBlank()
        ) {
            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Floor Data")
        }

        OutlinedButton(
            onClick = {
                deleteFloorId = ""
                showDeleteFloorDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.availableFloors.isNotEmpty(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete Floor")
        }

        if (uiState.availableFloors.isNotEmpty()) {
            Text(
                text = "Floors: ${uiState.availableFloors.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDeleteFloorDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteFloorDialog = false },
            title = { Text("Delete Floor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Select a floor in ${uiState.editBuildingId} to delete.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    ExposedDropdownMenuBox(
                        expanded = deleteFloorExpanded,
                        onExpandedChange = { deleteFloorExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = deleteFloorId.ifEmpty { "Select floor" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Floor") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deleteFloorExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(
                                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                    enabled = true
                                )
                        )
                        ExposedDropdownMenu(
                            expanded = deleteFloorExpanded,
                            onDismissRequest = { deleteFloorExpanded = false }
                        ) {
                            uiState.availableFloors.forEach { id ->
                                DropdownMenuItem(
                                    text = { Text(id) },
                                    onClick = {
                                        deleteFloorId = id
                                        deleteFloorExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFloor(uiState.editBuildingId, deleteFloorId)
                        showDeleteFloorDialog = false
                    },
                    enabled = deleteFloorId.isNotEmpty() && !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFloorDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
