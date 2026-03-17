package `in`.project.enroute.feature.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFloorScreen(
    viewModel: AdminViewModel,
    uiState: AdminUiState,
    onBack: () -> Unit
) {
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
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Add Floor Data",
                fontSize = 20.sp,
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
