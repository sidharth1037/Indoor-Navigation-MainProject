package `in`.project.enroute.feature.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.project.enroute.feature.campussearch.CampusSearchButton
import `in`.project.enroute.feature.campussearch.CampusSearchOverlay
import `in`.project.enroute.feature.campussearch.MORPH_DURATION_MS
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

// Layout constants for the admin search-button morph animation.
private val ADMIN_TITLE_TOP = 16.dp    // Column padding-top
private val ADMIN_TITLE_HEIGHT = 32.dp // 24sp title ≈ 32dp
private val ADMIN_BUTTON_GAP = 24.dp   // spacing between title and button

/** Resting Y for the search button on the Admin screen. */
private val ADMIN_BUTTON_RESTING_Y =
    ADMIN_TITLE_TOP + ADMIN_TITLE_HEIGHT + ADMIN_BUTTON_GAP

@Composable
private fun SelectCampusScreen(viewModel: AdminViewModel, uiState: AdminUiState) {
    var showSearch by remember { mutableStateOf(false) }
    var isMorphing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Background: title + create campus form (fades when morphing) ──
        val bgAlpha by animateFloatAsState(
            targetValue = if (isMorphing) 0f else 1f,
            animationSpec = tween(200),
            label = "admin_bg_alpha"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(bgAlpha)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Admin Panel",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Reserve space for the search button (rendered as overlay)
            Spacer(modifier = Modifier.height(70.dp))

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

        // ── Morphing search button ───────────────────────────────
        CampusSearchButton(
            isMorphing = isMorphing,
            restingY = ADMIN_BUTTON_RESTING_Y,
            placeholderText = "Search for a campus...",
            onAnimationFinished = { showSearch = true },
            onClick = { isMorphing = true }
        )

        // ── Search overlay ───────────────────────────────────────
        AnimatedVisibility(
            visible = showSearch,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(200))
        ) {
            CampusSearchOverlay(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                results = uiState.searchResults,
                isLoading = uiState.isSearching,
                error = uiState.searchError,
                hasSearched = uiState.hasSearched,
                placeholderText = "Search for a campus...",
                onBack = {
                    showSearch = false
                    coroutineScope.launch {
                        delay(100)
                        isMorphing = false
                        delay(MORPH_DURATION_MS.toLong())
                        viewModel.updateSearchQuery("")
                    }
                },
                onCampusSelected = { id ->
                    // Find the name from results
                    val name = uiState.searchResults
                        .firstOrNull { it.id == id }?.name ?: id
                    showSearch = false
                    isMorphing = false
                    viewModel.selectCampus(id, name)
                },
                onRetry = { viewModel.retrySearch() }
            )
        }
    }
}

// ── Step 2: Campus Home ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampusHomeScreen(viewModel: AdminViewModel, uiState: AdminUiState) {
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

        // Action buttons
        HorizontalDivider()

        Text(
            text = "Actions",
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
