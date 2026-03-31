package `in`.project.enroute.feature.landmark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
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
    LandmarkIconOption("urinal", "Urinal", Icons.Default.Wc),
    LandmarkIconOption("sink", "Sink", Icons.Default.Plumbing),
    LandmarkIconOption("pipe", "Pipe", Icons.Default.Plumbing),
    LandmarkIconOption("noticeboard", "Noticeboard", Icons.Default.Description),
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
    LandmarkIconOption("home", "Home", Icons.Default.Home),
    LandmarkIconOption("apartment", "Apartment", Icons.Default.Apartment),
    LandmarkIconOption("business", "Business", Icons.Default.Business),
    LandmarkIconOption("school", "School", Icons.Default.School),
    LandmarkIconOption("work", "Work", Icons.Default.Work),
    LandmarkIconOption("science", "Science", Icons.Default.Science),
    LandmarkIconOption("pharmacy", "Pharmacy", Icons.Default.LocalPharmacy),
    LandmarkIconOption("medical_services", "Medical Services", Icons.Default.MedicalServices),
    LandmarkIconOption("local_cafe", "Cafe", Icons.Default.LocalCafe),
    LandmarkIconOption("local_dining", "Dining", Icons.Default.LocalDining),
    LandmarkIconOption("fastfood", "Fast Food", Icons.Default.Fastfood),
    LandmarkIconOption("bakery", "Bakery", Icons.Default.BakeryDining),
    LandmarkIconOption("icecream", "Ice Cream", Icons.Default.Icecream),
    LandmarkIconOption("storefront", "Storefront", Icons.Default.Storefront),
    LandmarkIconOption("mall", "Mall", Icons.Default.LocalMall),
    LandmarkIconOption("grocery", "Grocery", Icons.Default.LocalGroceryStore),
    LandmarkIconOption("shopping_cart", "Shopping Cart", Icons.Default.ShoppingCart),
    LandmarkIconOption("laundry", "Laundry", Icons.Default.LocalLaundryService),
    LandmarkIconOption("shipping", "Shipping", Icons.Default.LocalShipping),
    LandmarkIconOption("taxi", "Taxi", Icons.Default.LocalTaxi),
    LandmarkIconOption("map", "Map", Icons.Default.Map),
    LandmarkIconOption("location", "Location", Icons.Default.LocationOn),
    LandmarkIconOption("place", "Place", Icons.Default.Place),
    LandmarkIconOption("walk", "Walk", Icons.AutoMirrored.Filled.DirectionsWalk),
    LandmarkIconOption("bike", "Bike", Icons.AutoMirrored.Filled.DirectionsBike),
    LandmarkIconOption("car", "Car", Icons.Default.DirectionsCar),
    LandmarkIconOption("train", "Train", Icons.Default.Train),
    LandmarkIconOption("tram", "Tram", Icons.Default.Tram),
    LandmarkIconOption("subway", "Subway", Icons.Default.Subway),
    LandmarkIconOption("flight", "Flight", Icons.Default.Flight),
    LandmarkIconOption("phone", "Phone", Icons.Default.Phone),
    LandmarkIconOption("email", "Email", Icons.Default.Email),
    LandmarkIconOption("public", "Public", Icons.Default.Public),
    LandmarkIconOption("event", "Event", Icons.Default.Event),
    LandmarkIconOption("time", "Time", Icons.Default.AccessTime),
    LandmarkIconOption("camera", "Camera", Icons.Default.CameraAlt),
    LandmarkIconOption("video", "Video", Icons.Default.Videocam),
    LandmarkIconOption("laptop", "Laptop", Icons.Default.Laptop),
    LandmarkIconOption("desktop", "Desktop", Icons.Default.DesktopWindows),
    LandmarkIconOption("smartphone", "Smartphone", Icons.Default.Smartphone),
    LandmarkIconOption("tv", "TV", Icons.Default.Tv),
    LandmarkIconOption("people", "People", Icons.Default.People),
    LandmarkIconOption("groups", "Groups", Icons.Default.Groups),
    LandmarkIconOption("lock", "Lock", Icons.Default.Lock),
    LandmarkIconOption("key", "Key", Icons.Default.Key),
    LandmarkIconOption("build", "Build", Icons.Default.Build),
    LandmarkIconOption("handyman", "Handyman", Icons.Default.Handyman),
    LandmarkIconOption("plumbing", "Plumbing", Icons.Default.Plumbing),
    LandmarkIconOption("electrical", "Electrical", Icons.Default.ElectricalServices),
    LandmarkIconOption("park", "Park", Icons.Default.Park),
    LandmarkIconOption("pets", "Pets", Icons.Default.Pets),
    LandmarkIconOption("construction", "Construction", Icons.Default.Construction),
    LandmarkIconOption("fire", "Fire Department", Icons.Default.LocalFireDepartment),
    LandmarkIconOption("police", "Police", Icons.Default.LocalPolice),
    LandmarkIconOption("elevator", "Elevator", Icons.Default.Elevator),
    LandmarkIconOption("stairs", "Stairs", Icons.Default.Stairs),
    LandmarkIconOption("charging", "Charging Station", Icons.Default.EvStation),
    LandmarkIconOption("battery", "Battery", Icons.Default.BatteryChargingFull),
    LandmarkIconOption("navigation", "Navigation", Icons.Default.NearMe),
    LandmarkIconOption("search", "Search", Icons.Default.Search),
    LandmarkIconOption("history", "History", Icons.Default.History),
    LandmarkIconOption("refresh", "Refresh", Icons.Default.Refresh),
    LandmarkIconOption("location_city", "City", Icons.Default.LocationCity),
    LandmarkIconOption("location_off", "Location Off", Icons.Default.LocationOff),
    LandmarkIconOption("door", "Door", Icons.Default.DoorFront),
    LandmarkIconOption("grid", "Grid", Icons.Default.GridOn),
    LandmarkIconOption("crop", "Boundary", Icons.Default.CropSquare),
    LandmarkIconOption("description", "Document", Icons.Default.Description),
    LandmarkIconOption("upload", "Upload", Icons.Default.UploadFile),
    LandmarkIconOption("cloud_upload", "Cloud Upload", Icons.Default.CloudUpload),
    LandmarkIconOption("file_open", "File Open", Icons.Default.FileOpen),
    LandmarkIconOption("building_add", "Add Building", Icons.Default.AddBusiness),
    LandmarkIconOption("edit_note", "Edit Note", Icons.Default.EditNote),
    LandmarkIconOption("calculate", "Calculator", Icons.Default.Calculate),
    LandmarkIconOption("admin", "Admin", Icons.Default.AdminPanelSettings),
    LandmarkIconOption("profile", "Profile", Icons.Default.AccountCircle),
    LandmarkIconOption("settings", "Settings", Icons.Default.Settings),
    LandmarkIconOption("visibility", "Visibility", Icons.Default.Visibility),
    LandmarkIconOption("visibility_off", "Privacy", Icons.Default.VisibilityOff),
    LandmarkIconOption("warning", "Warning", Icons.Default.Warning),
    LandmarkIconOption("check", "Check", Icons.Default.CheckCircle),
    LandmarkIconOption("error", "Error", Icons.Default.Error),
    LandmarkIconOption("delete", "Delete", Icons.Default.Delete),
    LandmarkIconOption("delete_forever", "Delete Forever", Icons.Default.DeleteForever),
    LandmarkIconOption("delete_sweep", "Delete Sweep", Icons.Default.DeleteSweep),
    LandmarkIconOption("cached", "Cached", Icons.Default.Cached),
    LandmarkIconOption("add", "Add", Icons.Default.Add),
    LandmarkIconOption("favorite", "Favorite", Icons.Default.Favorite),
    LandmarkIconOption("star", "Star", Icons.Default.Star),
    LandmarkIconOption("faucet", "Faucet", Icons.Default.Plumbing),
    LandmarkIconOption("tap", "Tap", Icons.Default.Plumbing),
    LandmarkIconOption("wash_basin", "Wash Basin", Icons.Default.Plumbing),
    LandmarkIconOption("drinking_water", "Drinking Water", Icons.Default.WaterDrop),
    LandmarkIconOption("pillar", "Pillar", Icons.Default.AccountBalance),
    LandmarkIconOption("column", "Column", Icons.Default.AccountBalance),
    LandmarkIconOption("colonnade", "Colonnade", Icons.Default.AccountBalance),
    LandmarkIconOption("notice", "Notice", Icons.Default.Description),
    LandmarkIconOption("bulletin", "Bulletin", Icons.Default.Description),
    LandmarkIconOption("announcement", "Announcement", Icons.Default.Info),
    LandmarkIconOption("info_desk", "Info Desk", Icons.Default.Info),
    LandmarkIconOption("help_desk", "Help Desk", Icons.Default.Info),
    LandmarkIconOption("reception", "Reception", Icons.Default.AccountCircle),
    LandmarkIconOption("front_desk", "Front Desk", Icons.Default.AccountCircle),
    LandmarkIconOption("entry_gate", "Entry Gate", Icons.Default.DoorFront),
    LandmarkIconOption("exit_gate", "Exit Gate", Icons.Default.DoorFront),
    LandmarkIconOption("checkpoint", "Checkpoint", Icons.Default.CheckCircle),
    LandmarkIconOption("restricted", "Restricted Area", Icons.Default.Lock),
    LandmarkIconOption("key_access", "Key Access", Icons.Default.Key),
    LandmarkIconOption("first_aid", "First Aid", Icons.Default.MedicalServices),
    LandmarkIconOption("clinic", "Clinic", Icons.Default.LocalHospital),
    LandmarkIconOption("classroom", "Classroom", Icons.Default.School),
    LandmarkIconOption("lecture_hall", "Lecture Hall", Icons.Default.School),
    LandmarkIconOption("workspace", "Workspace", Icons.Default.Work),
    LandmarkIconOption("office_desk", "Office Desk", Icons.Default.MeetingRoom),
    LandmarkIconOption("maintenance", "Maintenance", Icons.Default.Handyman),
    LandmarkIconOption("repair", "Repair", Icons.Default.Build),
    LandmarkIconOption("electrical_panel", "Electrical Panel", Icons.Default.ElectricalServices),
    LandmarkIconOption("charging_point", "Charging Point", Icons.Default.EvStation),
    LandmarkIconOption("generator", "Generator", Icons.Default.BatteryChargingFull),
    LandmarkIconOption("security_post", "Security Post", Icons.Default.Security),
    LandmarkIconOption("fire_exit", "Fire Exit", Icons.Default.LocalFireDepartment),
    LandmarkIconOption("warning_zone", "Warning Zone", Icons.Default.Warning),
    LandmarkIconOption("admin_office", "Admin Office", Icons.Default.AdminPanelSettings),
    LandmarkIconOption("records_room", "Records Room", Icons.Default.Description),
    LandmarkIconOption("mail_room", "Mail Room", Icons.Default.Email),
    LandmarkIconOption("phone_booth", "Phone Booth", Icons.Default.Phone),
    LandmarkIconOption("camera_zone", "Camera Zone", Icons.Default.CameraAlt),
    LandmarkIconOption("surveillance", "Surveillance", Icons.Default.Videocam),
    LandmarkIconOption("event_hall", "Event Hall", Icons.Default.Event),
    LandmarkIconOption("transport_hub", "Transport Hub", Icons.Default.Train),
    LandmarkIconOption("subway_station", "Subway Station", Icons.Default.Subway),
    LandmarkIconOption("bus_bay", "Bus Bay", Icons.Default.DirectionsBus),
    LandmarkIconOption("taxi_pickup", "Taxi Pickup", Icons.Default.LocalTaxi),
    LandmarkIconOption("parking_lot", "Parking Lot", Icons.Default.LocalParking),
    LandmarkIconOption("shopping_area", "Shopping Area", Icons.Default.Storefront),
    LandmarkIconOption("food_court", "Food Court", Icons.Default.LocalDining),
    LandmarkIconOption("snack_point", "Snack Point", Icons.Default.Fastfood),
    LandmarkIconOption("bakery_corner", "Bakery Corner", Icons.Default.BakeryDining),
    LandmarkIconOption("icecream_corner", "Ice Cream Corner", Icons.Default.Icecream),
    LandmarkIconOption("garden_area", "Garden Area", Icons.Default.Forest),
    LandmarkIconOption("park_area", "Park Area", Icons.Default.Park),
    LandmarkIconOption("pet_zone", "Pet Zone", Icons.Default.Pets),
    LandmarkIconOption("construction_zone", "Construction Zone", Icons.Default.Construction),
    LandmarkIconOption("map_point", "Map Point", Icons.Default.Map),
    LandmarkIconOption("pin_point", "Pin Point", Icons.Default.LocationOn),
    LandmarkIconOption("city_center", "City Center", Icons.Default.LocationCity),
    LandmarkIconOption("wifi_zone", "WiFi Zone", Icons.Default.Wifi),
)

private val commonLandmarkIconIds = setOf(
    "restaurant", "coffee", "restroom", "parking", "library",
    "office", "info", "atm", "water", "medical",
    "store", "security", "wifi", "elevator", "stairs"
)

/**
 * Resolves a landmark icon ID to its [ImageVector].
 * Returns Icons.Default.Info as fallback for unknown IDs.
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
    var iconQuery by remember { mutableStateOf("") }

    val commonIconOptions = remember {
        landmarkIconOptions.filter { it.id in commonLandmarkIconIds }
    }
    val filteredIconOptions = remember(iconQuery) {
        val query = iconQuery.trim()
        if (query.isEmpty()) {
            emptyList()
        } else {
            landmarkIconOptions.filter { option ->
                option.label.contains(query, ignoreCase = true) ||
                    option.id.contains(query, ignoreCase = true)
            }
        }
    }

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

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Landmark Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Select Icon",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = iconQuery,
                onValueChange = { iconQuery = it },
                label = { Text("Search icons") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (iconQuery.isBlank()) {
                Text(
                    text = "Common in Buildings",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                CommonIconGrid(
                    icons = commonIconOptions,
                    selectedIcon = selectedIcon,
                    onIconSelected = { selectedIcon = it },
                    columns = 5
                )
            } else {
                if (filteredIconOptions.isEmpty()) {
                    Text(
                        text = "No icons match \"$iconQuery\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                } else {
                    CommonIconGrid(
                        icons = filteredIconOptions,
                        selectedIcon = selectedIcon,
                        onIconSelected = { selectedIcon = it },
                        columns = 5
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

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

@Composable
private fun CommonIconGrid(
    icons: List<LandmarkIconOption>,
    selectedIcon: String?,
    onIconSelected: (String) -> Unit,
    columns: Int
) {
    val rows = remember(icons, columns) { icons.chunked(columns) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().wrapContentHeight()
    ) {
        for (row in rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (option in row) {
                    Box(modifier = Modifier.weight(1f)) {
                        IconOptionCard(
                            option = option,
                            isSelected = selectedIcon == option.id,
                            onClick = { onIconSelected(option.id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun IconOptionCard(
    option: LandmarkIconOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .height(84.dp)
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
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Icon(
            imageVector = option.icon,
            contentDescription = option.label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}
