package `in`.project.enroute.feature.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusHomeScreen(
    viewModel: AdminViewModel,
    uiState: AdminUiState,
    onBack: () -> Unit,
    onAddBuilding: () -> Unit,
    onEditCampus: () -> Unit,
    onEditBuilding: (String) -> Unit,
    onCampusDeleted: () -> Unit
) {
    // Dialog states
    var showDeleteBuildingDialog by remember { mutableStateOf(false) }
    var showDeleteCampusDialog by remember { mutableStateOf(false) }

    // Delete-building dialog: needs building selection
    var deleteBuildingId by remember { mutableStateOf("") }
    var deleteBuildingExpanded by remember { mutableStateOf(false) }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with back
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text(
                    text = uiState.selectedCampusName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = uiState.selectedCampusId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Buildings list
        Text(
            text = "Buildings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        if (uiState.availableBuildings.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = "No buildings added yet",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            uiState.availableBuildings.forEach { buildingId ->
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditBuilding(buildingId) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = buildingId,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open building",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Cache indicator
        if (uiState.loadedFromCache) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Cached,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Loaded from cache",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.refreshFromDatabase() }) {
                        Text("Refresh", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // ── Add actions ──────────────────────────────────────────
        HorizontalDivider()

        Text(
            text = "Add",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        FilledTonalButton(
            onClick = onAddBuilding,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Building")
        }

        // ── Edit actions ─────────────────────────────────────────
        HorizontalDivider()

        Text(
            text = "Edit",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        FilledTonalButton(
            onClick = onEditCampus,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Edit Campus Metadata")
        }

        // ── Tools ────────────────────────────────────────────────
        HorizontalDivider()

        Text(
            text = "Tools",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        FilledTonalButton(
            onClick = { viewModel.precalculateNavData() },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.availableBuildings.isNotEmpty() && !uiState.isPrecalculating
        ) {
            if (uiState.isPrecalculating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(uiState.precalculationProgress)
            } else {
                Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Precalculate Navigation")
            }
        }

        // ── Delete actions ───────────────────────────────────────
        HorizontalDivider()

        Text(
            text = "Delete",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        OutlinedButton(
            onClick = {
                deleteBuildingId = ""
                showDeleteBuildingDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.availableBuildings.isNotEmpty(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete Building")
        }

        OutlinedButton(
            onClick = { showDeleteCampusDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete Entire Campus")
        }

        // ── Cache section ────────────────────────────────────────
        HorizontalDivider()

        Text(
            text = "Cache",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        OutlinedButton(
            onClick = { viewModel.clearCampusCache() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear Cache for This Campus")
        }
    }

    // ═══════ Dialogs ═══════

    // ── Delete Building Dialog ───────────────────────────────────
    if (showDeleteBuildingDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteBuildingDialog = false },
            title = { Text("Delete Building") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Select the building to delete. All floors under it will also be deleted. This cannot be undone.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    ExposedDropdownMenuBox(
                        expanded = deleteBuildingExpanded,
                        onExpandedChange = { deleteBuildingExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = deleteBuildingId.ifEmpty { "Select building" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Building") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deleteBuildingExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(
                                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                    enabled = true
                                )
                        )
                        ExposedDropdownMenu(
                            expanded = deleteBuildingExpanded,
                            onDismissRequest = { deleteBuildingExpanded = false }
                        ) {
                            uiState.availableBuildings.forEach { id ->
                                DropdownMenuItem(
                                    text = { Text(id) },
                                    onClick = {
                                        deleteBuildingId = id
                                        deleteBuildingExpanded = false
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
                        viewModel.deleteBuilding(deleteBuildingId)
                        showDeleteBuildingDialog = false
                    },
                    enabled = deleteBuildingId.isNotEmpty() && !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBuildingDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Delete Campus Dialog ─────────────────────────────────────
    if (showDeleteCampusDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCampusDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Entire Campus?") },
            text = {
                Text(
                    "This will permanently delete '${uiState.selectedCampusName}' " +
                            "and ALL its buildings, floors, and data. This action cannot be undone.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCampus()
                        showDeleteCampusDialog = false
                        onCampusDeleted()
                    },
                    enabled = !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete Campus") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCampusDialog = false }) { Text("Cancel") }
            }
        )
    }

}
