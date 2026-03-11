package `in`.project.enroute.feature.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(viewModel: AdminViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Status message bar
        uiState.statusMessage?.let { message ->
            StatusBar(
                message = message,
                isError = uiState.isError,
                onDismiss = { viewModel.clearStatus() }
            )
        }

        AnimatedContent(
            targetState = uiState.step,
            label = "admin_step"
        ) { step ->
            when (step) {
                AdminStep.SELECT_CAMPUS -> SelectCampusScreen(viewModel, uiState)
                AdminStep.CAMPUS_HOME -> CampusHomeScreen(viewModel, uiState)
                AdminStep.ADD_BUILDING -> AddBuildingScreen(viewModel, uiState)
                AdminStep.ADD_FLOOR -> AddFloorScreen(viewModel, uiState)
                AdminStep.EDIT_CAMPUS -> EditCampusScreen(viewModel, uiState)
                AdminStep.EDIT_BUILDING -> EditBuildingScreen(viewModel, uiState)
            }
        }
    }
}

// ── Status Bar ───────────────────────────────────────────────────

@Composable
private fun StatusBar(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Step 1: Select / Create Campus ──────────────────────────────

@Composable
private fun SelectCampusScreen(viewModel: AdminViewModel, uiState: AdminUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Admin Panel",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )

        // ── My Campuses ──────────────────────────────────────────
        Text(
            text = "My Campuses",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        when {
            uiState.isMyCampusesLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            uiState.myCampusesError != null -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Failed to load campuses: ${uiState.myCampusesError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.loadMyCampuses() }) {
                            Text("Retry")
                        }
                    }
                }
            }
            uiState.myCampuses.isEmpty() -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "No campuses created yet. Create one below.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                uiState.myCampuses.forEach { campus ->
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectCampus(campus.id, campus.name) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationCity,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = campus.name,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = campus.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Open",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // ── Create new campus ────────────────────────────────
        Text(
            text = "Create New Campus",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        OutlinedTextField(
            value = uiState.newCampusId,
            onValueChange = { viewModel.updateNewCampusId(it) },
            label = { Text("Campus ID") },
            placeholder = { Text("e.g., sjcet_palai") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.newCampusName,
            onValueChange = { viewModel.updateNewCampusName(it) },
            label = { Text("Campus Name") },
            placeholder = { Text("e.g., SJCET Palai") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.newCampusLocation,
            onValueChange = { viewModel.updateNewCampusLocation(it) },
            label = { Text("Location") },
            placeholder = { Text("e.g., Palai, Kerala") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.newCampusLatitude,
                onValueChange = { viewModel.updateNewCampusLatitude(it) },
                label = { Text("Latitude") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = uiState.newCampusLongitude,
                onValueChange = { viewModel.updateNewCampusLongitude(it) },
                label = { Text("Longitude") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }

        OutlinedTextField(
            value = uiState.newCampusNorth,
            onValueChange = { viewModel.updateNewCampusNorth(it) },
            label = { Text("North bearing (degrees)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        Button(
            onClick = { viewModel.createCampus() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Campus")
        }
    }
}

// ── Step 2: Campus Home ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampusHomeScreen(viewModel: AdminViewModel, uiState: AdminUiState) {

    // Dialog states
    var showDeleteFloorDialog by remember { mutableStateOf(false) }
    var showDeleteBuildingDialog by remember { mutableStateOf(false) }
    var showDeleteCampusDialog by remember { mutableStateOf(false) }
    var showEditBuildingPicker by remember { mutableStateOf(false) }

    // Delete-floor dialog: needs building + floor selection
    var deleteFloorBuilding by remember { mutableStateOf("") }
    var deleteFloorId by remember { mutableStateOf("") }
    var deleteFloorBuildingExpanded by remember { mutableStateOf(false) }
    var deleteFloorFloorExpanded by remember { mutableStateOf(false) }

    // Delete-building dialog: needs building selection
    var deleteBuildingId by remember { mutableStateOf("") }
    var deleteBuildingExpanded by remember { mutableStateOf(false) }

    // Edit-building picker
    var editBuildingExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with back
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.navigateToSelectCampus() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text(
                    text = uiState.selectedCampusName,
                    fontSize = 24.sp,
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
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
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
                            style = MaterialTheme.typography.titleSmall
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
            onClick = { viewModel.navigateToAddBuilding() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Building")
        }

        FilledTonalButton(
            onClick = { viewModel.navigateToAddFloor() },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.availableBuildings.isNotEmpty()
        ) {
            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Floor Data")
        }

        // ── Edit actions ─────────────────────────────────────────
        HorizontalDivider()

        Text(
            text = "Edit",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        FilledTonalButton(
            onClick = { viewModel.navigateToEditCampus() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Edit Campus Metadata")
        }

        FilledTonalButton(
            onClick = { showEditBuildingPicker = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.availableBuildings.isNotEmpty()
        ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Edit Building Metadata")
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
                deleteFloorBuilding = ""
                deleteFloorId = ""
                showDeleteFloorDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.availableBuildings.isNotEmpty(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.LayersClear, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete Floor")
        }

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

    // ── Delete Floor Dialog ──────────────────────────────────────
    if (showDeleteFloorDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteFloorDialog = false },
            title = { Text("Delete Floor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Select the building and floor to delete. This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    // Building dropdown
                    ExposedDropdownMenuBox(
                        expanded = deleteFloorBuildingExpanded,
                        onExpandedChange = { deleteFloorBuildingExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = deleteFloorBuilding.ifEmpty { "Select building" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Building") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deleteFloorBuildingExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(
                                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                    enabled = true
                                )
                        )
                        ExposedDropdownMenu(
                            expanded = deleteFloorBuildingExpanded,
                            onDismissRequest = { deleteFloorBuildingExpanded = false }
                        ) {
                            uiState.availableBuildings.forEach { id ->
                                DropdownMenuItem(
                                    text = { Text(id) },
                                    onClick = {
                                        deleteFloorBuilding = id
                                        deleteFloorId = ""
                                        deleteFloorBuildingExpanded = false
                                        viewModel.loadFloorsForBuildingPublic(id)
                                    }
                                )
                            }
                        }
                    }

                    // Floor dropdown (populated after building selection)
                    if (deleteFloorBuilding.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = deleteFloorFloorExpanded,
                            onExpandedChange = { deleteFloorFloorExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = deleteFloorId.ifEmpty { "Select floor" },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Floor") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deleteFloorFloorExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(
                                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                        enabled = true
                                    )
                            )
                            ExposedDropdownMenu(
                                expanded = deleteFloorFloorExpanded,
                                onDismissRequest = { deleteFloorFloorExpanded = false }
                            ) {
                                uiState.availableFloors.forEach { id ->
                                    DropdownMenuItem(
                                        text = { Text(id) },
                                        onClick = {
                                            deleteFloorId = id
                                            deleteFloorFloorExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFloor(deleteFloorBuilding, deleteFloorId)
                        showDeleteFloorDialog = false
                    },
                    enabled = deleteFloorBuilding.isNotEmpty() && deleteFloorId.isNotEmpty() && !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFloorDialog = false }) { Text("Cancel") }
            }
        )
    }

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

    // ── Edit Building Picker Dialog ──────────────────────────────
    if (showEditBuildingPicker) {
        AlertDialog(
            onDismissRequest = { showEditBuildingPicker = false },
            title = { Text("Select Building to Edit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.availableBuildings.forEach { buildingId ->
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showEditBuildingPicker = false
                                    viewModel.navigateToEditBuilding(buildingId)
                                }
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
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "Edit",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showEditBuildingPicker = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Step 3: Add Building ────────────────────────────────────────

@Composable
private fun AddBuildingScreen(viewModel: AdminViewModel, uiState: AdminUiState) {
    val context = LocalContext.current

    val metadataFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val name = getFileName(context, it) ?: "metadata.json"
            viewModel.setBuildingMetadataFile(it, name)
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
            IconButton(onClick = { viewModel.navigateToCampusHome() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Add Building",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        OutlinedTextField(
            value = uiState.buildingId,
            onValueChange = { viewModel.updateBuildingId(it) },
            label = { Text("Building ID") },
            placeholder = { Text("e.g., building_1") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // File picker for metadata
        Text(
            text = "Building Metadata JSON",
            style = MaterialTheme.typography.titleSmall
        )

        if (uiState.buildingMetadataUri != null) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = uiState.buildingMetadataFileName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.setBuildingMetadataFile(Uri.EMPTY, "") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                    }
                }
            }
        } else {
            OutlinedButton(
                onClick = { metadataFilePicker.launch(arrayOf("application/json", "*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Metadata File")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { viewModel.uploadBuildingMetadata(context) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading &&
                    uiState.buildingId.isNotBlank() &&
                    uiState.buildingMetadataUri != null
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Upload Building")
        }
    }
}

// ── Step 4: Add Floor Data ──────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFloorScreen(viewModel: AdminViewModel, uiState: AdminUiState) {
    val context = LocalContext.current

    val floorFilesPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val names = uris.map { getFileName(context, it) ?: "unknown.json" }
            viewModel.addSelectedFiles(uris, names)
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
            IconButton(onClick = { viewModel.navigateToCampusHome() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Add Floor Data",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Building selector
        var buildingDropdownExpanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = buildingDropdownExpanded,
            onExpandedChange = { buildingDropdownExpanded = it }
        ) {
            OutlinedTextField(
                value = uiState.selectedBuildingForFloor.ifEmpty { "Select building" },
                onValueChange = {},
                readOnly = true,
                label = { Text("Building") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = buildingDropdownExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
            )
            ExposedDropdownMenu(
                expanded = buildingDropdownExpanded,
                onDismissRequest = { buildingDropdownExpanded = false }
            ) {
                uiState.availableBuildings.forEach { buildingId ->
                    DropdownMenuItem(
                        text = { Text(buildingId) },
                        onClick = {
                            viewModel.selectBuildingForFloor(buildingId)
                            buildingDropdownExpanded = false
                        }
                    )
                }
            }
        }

        // Existing floors
        if (uiState.availableFloors.isNotEmpty()) {
            Text(
                text = "Existing floors: ${uiState.availableFloors.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Floor ID
        OutlinedTextField(
            value = uiState.floorId,
            onValueChange = { viewModel.updateFloorId(it) },
            label = { Text("Floor ID") },
            placeholder = { Text("e.g., floor_1") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        HorizontalDivider()

        // File selection
        Text(
            text = "Floor Files",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        Text(
            text = "Select JSON files. Type is detected from filename (walls, stairs, entrances, rooms, boundary). Not all files are required.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Selected files list
        if (uiState.selectedFiles.isNotEmpty()) {
            uiState.selectedFiles.forEach { file ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (file.type) {
                                FloorFileType.WALLS -> Icons.Default.GridOn
                                FloorFileType.STAIRS -> Icons.Default.Stairs
                                FloorFileType.ENTRANCES -> Icons.Default.DoorFront
                                FloorFileType.ROOMS -> Icons.Default.MeetingRoom
                                FloorFileType.BOUNDARY -> Icons.Default.CropSquare
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.type.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = file.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { viewModel.removeSelectedFile(file.type) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { floorFilesPicker.launch(arrayOf("application/json", "*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Files")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { viewModel.uploadFloorData(context) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading &&
                    uiState.selectedBuildingForFloor.isNotBlank() &&
                    uiState.floorId.isNotBlank() &&
                    uiState.selectedFiles.isNotEmpty()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Upload Floor Data")
        }
    }
}

// ── Step 5: Edit Campus Metadata ────────────────────────────────

@Composable
private fun EditCampusScreen(viewModel: AdminViewModel, uiState: AdminUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.navigateToCampusHome() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Edit Campus Metadata",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            text = "Campus ID: ${uiState.selectedCampusId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = uiState.editCampusName,
            onValueChange = { viewModel.updateEditCampusName(it) },
            label = { Text("Campus Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.editCampusLocation,
            onValueChange = { viewModel.updateEditCampusLocation(it) },
            label = { Text("Location") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.editCampusLatitude,
                onValueChange = { viewModel.updateEditCampusLatitude(it) },
                label = { Text("Latitude") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = uiState.editCampusLongitude,
                onValueChange = { viewModel.updateEditCampusLongitude(it) },
                label = { Text("Longitude") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }

        OutlinedTextField(
            value = uiState.editCampusNorth,
            onValueChange = { viewModel.updateEditCampusNorth(it) },
            label = { Text("North bearing (degrees)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { viewModel.saveEditCampus() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading && uiState.editCampusName.isNotBlank()
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
    }
}

// ── Step 6: Edit Building Metadata ──────────────────────────────

@Composable
private fun EditBuildingScreen(viewModel: AdminViewModel, uiState: AdminUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.navigateToCampusHome() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Edit Building",
                fontSize = 24.sp,
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
                label = { Text("Rotation (°)") },
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
    }
}

// ── Utility ──────────────────────────────────────────────────────

private fun getFileName(context: android.content.Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) it.getString(nameIndex) else null
        } else null
    }
}
