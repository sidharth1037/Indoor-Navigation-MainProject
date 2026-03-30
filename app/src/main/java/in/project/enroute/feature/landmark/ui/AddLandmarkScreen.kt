package `in`.project.enroute.feature.landmark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalPrintshop
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Represents an icon option in the landmark icon picker.
 */
data class LandmarkIconOption(
    val id: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Available landmark icons for the picker.
 */
val landmarkIconOptions = listOf(
    LandmarkIconOption("restaurant", "Restaurant", Icons.Default.Restaurant),
    LandmarkIconOption("coffee", "Coffee", Icons.Default.Coffee),
    LandmarkIconOption("restroom", "Restroom", Icons.Default.Wc),
    LandmarkIconOption("parking", "Parking", Icons.Default.LocalParking),
    LandmarkIconOption("library", "Library", Icons.AutoMirrored.Filled.MenuBook),
    LandmarkIconOption("lab", "Lab", Icons.Default.Biotech),
    LandmarkIconOption("office", "Office", Icons.Default.MeetingRoom),
    LandmarkIconOption("info", "Info", Icons.Default.Info),
    LandmarkIconOption("atm", "ATM", Icons.Default.LocalAtm),
    LandmarkIconOption("water", "Water", Icons.Default.WaterDrop),
    LandmarkIconOption("medical", "Medical", Icons.Default.LocalHospital),
    LandmarkIconOption("store", "Store", Icons.Default.Store),
    LandmarkIconOption("gym", "Gym", Icons.Default.FitnessCenter),
    LandmarkIconOption("auditorium", "Auditorium", Icons.Default.AccountBalance),
    LandmarkIconOption("printer", "Printer", Icons.Default.LocalPrintshop),
    LandmarkIconOption("wifi", "WiFi", Icons.Default.Wifi),
    LandmarkIconOption("garden", "Garden", Icons.Default.Forest),
    LandmarkIconOption("bus", "Bus Stop", Icons.Default.DirectionsBus),
    LandmarkIconOption("security", "Security", Icons.Default.Security),
    LandmarkIconOption("chapel", "Chapel", Icons.Default.Church),
    LandmarkIconOption("computer", "Computer Lab", Icons.Default.Computer),
)

/**
 * Resolves a landmark icon ID to its [ImageVector].
 * Returns [Icons.Default.Info] as fallback for unknown IDs.
 */
fun resolveLandmarkIcon(iconId: String): ImageVector {
    return landmarkIconOptions.find { it.id == iconId }?.icon ?: Icons.Default.Info
}

/**
 * Full-screen form page for adding a new landmark.
 * Admin enters a name and selects an icon from a grid.
 *
 * @param isSaving Whether a save operation is in progress
 * @param onSave Called with (name, iconId) when the Save button is pressed
 * @param onBack Called when back button is pressed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLandmarkScreen(
    isSaving: Boolean = false,
    initialName: String = "",
    initialIconId: String? = null,
    title: String = "Add Landmark",
    saveButtonLabel: String = "Save Landmark",
    onSave: (name: String, iconId: String) -> Unit,
    onBack: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var selectedIcon by remember(initialIconId) { mutableStateOf(initialIconId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Name field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Landmark Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Icon picker section
            Text(
                text = "Select Icon",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Icon grid — fixed height, scrollable within
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                items(landmarkIconOptions) { option ->
                    val isSelected = selectedIcon == option.id
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(12.dp)
                                        )
                                } else {
                                    Modifier
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            RoundedCornerShape(12.dp)
                                        )
                                }
                            )
                            .clickable { selectedIcon = option.id }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = option.label,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save button
            Button(
                onClick = {
                    val iconId = selectedIcon ?: return@Button
                    onSave(name.trim(), iconId)
                },
                enabled = name.isNotBlank() && selectedIcon != null && !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Text(saveButtonLabel)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
