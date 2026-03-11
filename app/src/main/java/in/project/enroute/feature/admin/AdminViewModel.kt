package `in`.project.enroute.feature.admin

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.project.enroute.data.cache.CachedCampusData
import `in`.project.enroute.data.cache.FloorPlanCache
import `in`.project.enroute.data.model.CampusMetadata
import `in`.project.enroute.data.model.FloorPlanMetadata
import `in`.project.enroute.data.model.LabelPosition
import `in`.project.enroute.data.model.RelativePosition
import `in`.project.enroute.data.repository.FirebaseFloorPlanRepository
import `in`.project.enroute.feature.admin.auth.AdminAuthRepository
import `in`.project.enroute.feature.campussearch.CampusItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── File types recognized for floor data upload ──────────────────
enum class FloorFileType(val label: String) {
    WALLS("Walls"),
    STAIRS("Stairs"),
    ENTRANCES("Entrances"),
    ROOMS("Rooms"),
    BOUNDARY("Boundary")
}

// ── Represents a file the user has selected ──────────────────────
data class SelectedFile(
    val uri: Uri,
    val displayName: String,
    val type: FloorFileType
)

// ── Upload step / mode for the UI wizard ─────────────────────────
enum class AdminStep {
    SELECT_CAMPUS,       // Choose existing campus or create new one
    CAMPUS_HOME,         // Campus selected → show buildings + options
    ADD_BUILDING,        // Upload building metadata
    ADD_FLOOR,           // Select building + floor + upload floor files
    EDIT_CAMPUS,         // Edit campus metadata fields
    EDIT_BUILDING        // Edit building metadata fields
}

// ── UI State ─────────────────────────────────────────────────────
data class AdminUiState(
    val step: AdminStep = AdminStep.SELECT_CAMPUS,

    // Admin's campuses
    val myCampuses: List<CampusItem> = emptyList(),
    val isMyCampusesLoading: Boolean = false,
    val myCampusesError: String? = null,

    // Campus data
    val selectedCampusId: String = "",
    val selectedCampusName: String = "",
    val newCampusId: String = "",
    val newCampusName: String = "",
    val newCampusLocation: String = "",
    val newCampusLatitude: String = "",
    val newCampusLongitude: String = "",
    val newCampusNorth: String = "",

    // Building data
    val availableBuildings: List<String> = emptyList(),
    val buildingId: String = "",
    val buildingMetadataUri: Uri? = null,
    val buildingMetadataFileName: String = "",

    // Floor data
    val selectedBuildingForFloor: String = "",
    val availableFloors: List<String> = emptyList(),
    val floorId: String = "",
    val selectedFiles: List<SelectedFile> = emptyList(),

    // Status
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val loadedFromCache: Boolean = false,

    // Edit campus metadata
    val editCampusName: String = "",
    val editCampusLocation: String = "",
    val editCampusLatitude: String = "",
    val editCampusLongitude: String = "",
    val editCampusNorth: String = "",

    // Edit building metadata
    val editBuildingId: String = "",
    val editBuildingName: String = "",
    val editBuildingScale: String = "",
    val editBuildingRotation: String = "",
    val editBuildingLabelX: String = "",
    val editBuildingLabelY: String = "",
    val editBuildingRelX: String = "",
    val editBuildingRelY: String = ""
)

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private var repository: FirebaseFloorPlanRepository? = null
    private val cache = FloorPlanCache(application.applicationContext)

    /** In-memory reference to the cached campus data for the selected campus. */
    private var cachedCampusData: CachedCampusData? = null

    init {
        // React to login/logout: reset on logout, reload on login
        viewModelScope.launch {
            AdminAuthRepository.isLoggedIn.collect { loggedIn ->
                if (loggedIn) {
                    loadMyCampuses()
                } else {
                    _uiState.value = AdminUiState()
                    repository = null
                }
            }
        }
    }

    // ── Admin's campuses ─────────────────────────────────────────

    /**
     * Loads all campuses created by the currently logged-in admin.
     */
    fun loadMyCampuses() {
        val uid = AdminAuthRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isMyCampusesLoading = true, myCampusesError = null) }
            try {
                val results = FirebaseFloorPlanRepository.getCampusesByAdmin(uid)
                val items = results.map { CampusItem(it.first, it.second) }
                _uiState.update {
                    it.copy(myCampuses = items, isMyCampusesLoading = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isMyCampusesLoading = false, myCampusesError = e.message)
                }
            }
        }
    }

    fun selectCampus(campusId: String, campusName: String) {
        repository = FirebaseFloorPlanRepository(campusId)
        cachedCampusData = null
        _uiState.update {
            it.copy(
                selectedCampusId = campusId,
                selectedCampusName = campusName,
                step = AdminStep.CAMPUS_HOME,
                loadedFromCache = false
            )
        }
        loadBuildings()
    }

    fun createCampus() {
        val state = _uiState.value
        if (state.newCampusId.isBlank() || state.newCampusName.isBlank()) {
            _uiState.update {
                it.copy(statusMessage = "Campus ID and Name are required", isError = true)
            }
            return
        }

        val uid = AdminAuthRepository.currentUser?.uid
        if (uid == null) {
            _uiState.update {
                it.copy(statusMessage = "Not logged in", isError = true)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val metadata = CampusMetadata(
                    name = state.newCampusName,
                    location = state.newCampusLocation,
                    latitude = state.newCampusLatitude.toDoubleOrNull() ?: 0.0,
                    longitude = state.newCampusLongitude.toDoubleOrNull() ?: 0.0,
                    north = state.newCampusNorth.toFloatOrNull() ?: 0f
                )
                FirebaseFloorPlanRepository.createCampus(state.newCampusId, metadata, uid)

                repository = FirebaseFloorPlanRepository(state.newCampusId)
                _uiState.update {
                    it.copy(
                        selectedCampusId = state.newCampusId,
                        selectedCampusName = state.newCampusName,
                        step = AdminStep.CAMPUS_HOME,
                        isLoading = false,
                        statusMessage = "Campus '${state.newCampusName}' created",
                        isError = false,
                        // Reset create form
                        newCampusId = "",
                        newCampusName = "",
                        newCampusLocation = "",
                        newCampusLatitude = "",
                        newCampusLongitude = "",
                        newCampusNorth = ""
                    )
                }
                loadBuildings()
                loadMyCampuses()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Failed to create campus: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    // ── Building management ──────────────────────────────────────

    /**
     * Loads the building list for the selected campus.
     * Checks the disk cache first; falls back to Firebase on a cache miss.
     */
    private fun loadBuildings() {
        val campusId = _uiState.value.selectedCampusId
        if (campusId.isBlank()) return

        viewModelScope.launch {
            try {
                // ── 1. Try disk cache ──
                val cached = cache.loadCampusData(campusId)
                if (cached != null) {
                    cachedCampusData = cached
                    val buildingIds = cached.buildings
                        .map { it.building.buildingId }
                        .sortedBy { it.removePrefix("building_").toIntOrNull() ?: 0 }
                    _uiState.update {
                        it.copy(availableBuildings = buildingIds, loadedFromCache = true)
                    }
                    return@launch
                }

                // ── 2. Fall back to Firebase ──
                val repo = repository ?: return@launch
                val buildings = repo.getAvailableBuildings()
                _uiState.update {
                    it.copy(availableBuildings = buildings, loadedFromCache = false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun navigateToAddBuilding() {
        _uiState.update {
            it.copy(
                step = AdminStep.ADD_BUILDING,
                buildingId = "",
                buildingMetadataUri = null,
                buildingMetadataFileName = "",
                statusMessage = null
            )
        }
    }

    fun navigateToAddFloor() {
        _uiState.update {
            it.copy(
                step = AdminStep.ADD_FLOOR,
                selectedBuildingForFloor = "",
                floorId = "",
                selectedFiles = emptyList(),
                availableFloors = emptyList(),
                statusMessage = null
            )
        }
    }

    fun navigateToCampusHome() {
        _uiState.update {
            it.copy(step = AdminStep.CAMPUS_HOME, statusMessage = null)
        }
        loadBuildings()
    }

    fun navigateToSelectCampus() {
        _uiState.update {
            it.copy(
                step = AdminStep.SELECT_CAMPUS,
                statusMessage = null
            )
        }
        loadMyCampuses()
    }

    fun uploadBuildingMetadata(context: Context) {
        val repo = repository ?: return
        val state = _uiState.value

        if (state.buildingId.isBlank()) {
            _uiState.update {
                it.copy(statusMessage = "Building ID is required", isError = true)
            }
            return
        }
        if (state.buildingMetadataUri == null) {
            _uiState.update {
                it.copy(statusMessage = "Select a metadata JSON file", isError = true)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val success = repo.uploadBuildingMetadataFromUri(
                    context,
                    state.buildingId,
                    state.buildingMetadataUri
                )
                if (success) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Building '${state.buildingId}' uploaded",
                            isError = false,
                            buildingId = "",
                            buildingMetadataUri = null,
                            buildingMetadataFileName = ""
                        )
                    }
                    invalidateCacheForSelectedCampus()
                    loadBuildings()
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Failed to upload building metadata",
                            isError = true
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Error: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    // ── Floor data upload ────────────────────────────────────────

    fun selectBuildingForFloor(buildingId: String) {
        _uiState.update { it.copy(selectedBuildingForFloor = buildingId) }
        loadFloorsForBuilding(buildingId)
    }

    /**
     * Loads the floor list for [buildingId].
     * Checks the in-memory cached campus data first; falls back to Firebase.
     */
    private fun loadFloorsForBuilding(buildingId: String) {
        viewModelScope.launch {
            try {
                // ── 1. Try cached campus data ──
                val cached = cachedCampusData
                if (cached != null) {
                    val cachedBuilding = cached.buildings
                        .find { it.building.buildingId == buildingId }
                    if (cachedBuilding != null) {
                        val floors = cachedBuilding.building.availableFloors
                        _uiState.update { it.copy(availableFloors = floors) }
                        return@launch
                    }
                }

                // ── 2. Fall back to Firebase ──
                val repo = repository ?: return@launch
                val floors = repo.getAvailableFloors(buildingId)
                _uiState.update { it.copy(availableFloors = floors) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addSelectedFiles(uris: List<Uri>, fileNames: List<String>) {
        val newFiles = uris.zip(fileNames).mapNotNull { (uri, name) ->
            val type = detectFileType(name) ?: return@mapNotNull null
            SelectedFile(uri = uri, displayName = name, type = type)
        }

        _uiState.update { state ->
            // Replace files of the same type, keep others
            val existingTypes = newFiles.map { it.type }.toSet()
            val filtered = state.selectedFiles.filter { it.type !in existingTypes }
            state.copy(selectedFiles = filtered + newFiles)
        }
    }

    fun removeSelectedFile(type: FloorFileType) {
        _uiState.update { state ->
            state.copy(selectedFiles = state.selectedFiles.filter { it.type != type })
        }
    }

    fun uploadFloorData(context: Context) {
        val repo = repository ?: return
        val state = _uiState.value

        if (state.selectedBuildingForFloor.isBlank()) {
            _uiState.update {
                it.copy(statusMessage = "Select a building", isError = true)
            }
            return
        }
        if (state.floorId.isBlank()) {
            _uiState.update {
                it.copy(statusMessage = "Enter a floor ID", isError = true)
            }
            return
        }
        if (state.selectedFiles.isEmpty()) {
            _uiState.update {
                it.copy(statusMessage = "Select at least one file", isError = true)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            try {
                var uploadedCount = 0
                val totalFiles = state.selectedFiles.size

                for (file in state.selectedFiles) {
                    _uiState.update {
                        it.copy(
                            statusMessage = "Uploading ${file.type.label} (${uploadedCount + 1}/$totalFiles)..."
                        )
                    }

                    val result = repo.uploadFloorDataFromUri(
                        context,
                        state.selectedBuildingForFloor,
                        state.floorId,
                        file.uri,
                        file.displayName
                    )

                    if (result != null) {
                        uploadedCount++
                    } else {
                        throw Exception("Failed to upload ${file.displayName}")
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "$uploadedCount file(s) uploaded to ${state.selectedBuildingForFloor}/${state.floorId}",
                        isError = false,
                        selectedFiles = emptyList(),
                        floorId = ""
                    )
                }
                // Refresh floor list after invalidating cache
                invalidateCacheForSelectedCampus()
                loadFloorsForBuilding(state.selectedBuildingForFloor)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Upload error: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    // ── Field updates ────────────────────────────────────────────

    fun updateNewCampusId(value: String) {
        _uiState.update { it.copy(newCampusId = value) }
    }

    fun updateNewCampusName(value: String) {
        _uiState.update { it.copy(newCampusName = value) }
    }

    fun updateNewCampusLocation(value: String) {
        _uiState.update { it.copy(newCampusLocation = value) }
    }

    fun updateNewCampusLatitude(value: String) {
        _uiState.update { it.copy(newCampusLatitude = value) }
    }

    fun updateNewCampusLongitude(value: String) {
        _uiState.update { it.copy(newCampusLongitude = value) }
    }

    fun updateNewCampusNorth(value: String) {
        _uiState.update { it.copy(newCampusNorth = value) }
    }

    fun updateBuildingId(value: String) {
        _uiState.update { it.copy(buildingId = value) }
    }

    fun setBuildingMetadataFile(uri: Uri, fileName: String) {
        _uiState.update {
            it.copy(buildingMetadataUri = uri, buildingMetadataFileName = fileName)
        }
    }

    fun updateFloorId(value: String) {
        _uiState.update { it.copy(floorId = value) }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null, isError = false) }
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * Clears both the in-memory and disk cache for the currently selected
     * campus so that the next load fetches fresh data from Firebase.
     */
    private fun invalidateCacheForSelectedCampus() {
        val campusId = _uiState.value.selectedCampusId
        if (campusId.isNotBlank()) {
            cachedCampusData = null
            viewModelScope.launch {
                cache.clearCache(campusId)
            }
            _uiState.update { it.copy(loadedFromCache = false) }
        }
    }

    /**
     * Public action: clears the campus cache and reloads buildings
     * from Firebase so the admin can see the latest backend state.
     */
    fun clearCampusCache() {
        invalidateCacheForSelectedCampus()
        _uiState.update {
            it.copy(
                statusMessage = "Cache cleared for ${it.selectedCampusName}",
                isError = false
            )
        }
        loadBuildings()
    }

    /**
     * Forces a fresh load of building data from Firebase,
     * bypassing and clearing the disk cache.
     */
    fun refreshFromDatabase() {
        invalidateCacheForSelectedCampus()
        _uiState.update {
            it.copy(
                statusMessage = "Refreshing from database…",
                isError = false
            )
        }
        loadBuildings()
    }

    // ── Delete operations ────────────────────────────────────────

    /**
     * Deletes a single floor from Firebase and refreshes floor list.
     */
    fun deleteFloor(buildingId: String, floorId: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repo.deleteFloor(buildingId, floorId)
                invalidateCacheForSelectedCampus()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Floor '$floorId' deleted from '$buildingId'",
                        isError = false
                    )
                }
                loadBuildings()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Failed to delete floor: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    /**
     * Deletes a building and all its floors from Firebase.
     */
    fun deleteBuilding(buildingId: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repo.deleteBuilding(buildingId)
                invalidateCacheForSelectedCampus()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Building '$buildingId' deleted",
                        isError = false
                    )
                }
                loadBuildings()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Failed to delete building: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    /**
     * Deletes the entire campus and returns to SELECT_CAMPUS step.
     */
    fun deleteCampus() {
        val repo = repository ?: return
        val campusId = _uiState.value.selectedCampusId
        val campusName = _uiState.value.selectedCampusName
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repo.deleteCampus()
                cache.clearCache(campusId)
                cachedCampusData = null
                repository = null
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        step = AdminStep.SELECT_CAMPUS,
                        selectedCampusId = "",
                        selectedCampusName = "",
                        availableBuildings = emptyList(),
                        statusMessage = "Campus '$campusName' deleted",
                        isError = false,
                        loadedFromCache = false
                    )
                }
                loadMyCampuses()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Failed to delete campus: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    /**
     * Loads floors for a given building (used by delete-floor dialog).
     */
    fun loadFloorsForBuildingPublic(buildingId: String) {
        loadFloorsForBuilding(buildingId)
    }

    // ── Edit campus metadata ─────────────────────────────────────

    /**
     * Opens the edit-campus screen, pre-populating fields from Firebase.
     */
    fun navigateToEditCampus() {
        val repo = repository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val meta = repo.loadCampusMetadata()
                _uiState.update {
                    it.copy(
                        step = AdminStep.EDIT_CAMPUS,
                        isLoading = false,
                        statusMessage = null,
                        editCampusName = meta.name,
                        editCampusLocation = meta.location,
                        editCampusLatitude = if (meta.latitude != 0.0) meta.latitude.toString() else "",
                        editCampusLongitude = if (meta.longitude != 0.0) meta.longitude.toString() else "",
                        editCampusNorth = if (meta.north != 0f) meta.north.toString() else ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Failed to load campus metadata: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    fun updateEditCampusName(value: String) { _uiState.update { it.copy(editCampusName = value) } }
    fun updateEditCampusLocation(value: String) { _uiState.update { it.copy(editCampusLocation = value) } }
    fun updateEditCampusLatitude(value: String) { _uiState.update { it.copy(editCampusLatitude = value) } }
    fun updateEditCampusLongitude(value: String) { _uiState.update { it.copy(editCampusLongitude = value) } }
    fun updateEditCampusNorth(value: String) { _uiState.update { it.copy(editCampusNorth = value) } }

    fun saveEditCampus() {
        val repo = repository ?: return
        val state = _uiState.value
        if (state.editCampusName.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Campus name is required", isError = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val fields = mutableMapOf<String, Any>(
                    "name" to state.editCampusName,
                    "location" to state.editCampusLocation,
                    "latitude" to (state.editCampusLatitude.toDoubleOrNull() ?: 0.0),
                    "longitude" to (state.editCampusLongitude.toDoubleOrNull() ?: 0.0),
                    "north" to (state.editCampusNorth.toFloatOrNull() ?: 0f)
                )
                repo.updateCampusMetadata(fields)
                invalidateCacheForSelectedCampus()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedCampusName = state.editCampusName,
                        step = AdminStep.CAMPUS_HOME,
                        statusMessage = "Campus metadata updated",
                        isError = false
                    )
                }
                loadMyCampuses()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Failed to update: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    // ── Edit building metadata ───────────────────────────────────

    /**
     * Opens the edit-building screen, pre-populating fields from Firebase.
     */
    fun navigateToEditBuilding(buildingId: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val meta = repo.loadBuildingMetadata(buildingId)
                _uiState.update {
                    it.copy(
                        step = AdminStep.EDIT_BUILDING,
                        isLoading = false,
                        statusMessage = null,
                        editBuildingId = buildingId,
                        editBuildingName = meta.buildingName,
                        editBuildingScale = meta.scale.toString(),
                        editBuildingRotation = meta.rotation.toString(),
                        editBuildingLabelX = meta.labelPosition?.x?.toString() ?: "",
                        editBuildingLabelY = meta.labelPosition?.y?.toString() ?: "",
                        editBuildingRelX = meta.relativePosition.x.toString(),
                        editBuildingRelY = meta.relativePosition.y.toString()
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Failed to load building metadata: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    fun updateEditBuildingName(value: String) { _uiState.update { it.copy(editBuildingName = value) } }
    fun updateEditBuildingScale(value: String) { _uiState.update { it.copy(editBuildingScale = value) } }
    fun updateEditBuildingRotation(value: String) { _uiState.update { it.copy(editBuildingRotation = value) } }
    fun updateEditBuildingLabelX(value: String) { _uiState.update { it.copy(editBuildingLabelX = value) } }
    fun updateEditBuildingLabelY(value: String) { _uiState.update { it.copy(editBuildingLabelY = value) } }
    fun updateEditBuildingRelX(value: String) { _uiState.update { it.copy(editBuildingRelX = value) } }
    fun updateEditBuildingRelY(value: String) { _uiState.update { it.copy(editBuildingRelY = value) } }

    fun saveEditBuilding() {
        val repo = repository ?: return
        val state = _uiState.value
        if (state.editBuildingId.isBlank() || state.editBuildingName.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Building name is required", isError = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val fields = mutableMapOf<String, Any?>(
                    "buildingId" to state.editBuildingId,
                    "buildingName" to state.editBuildingName,
                    "scale" to (state.editBuildingScale.toFloatOrNull() ?: 1f),
                    "rotation" to (state.editBuildingRotation.toFloatOrNull() ?: 0f),
                    "relativePosition" to mapOf(
                        "x" to (state.editBuildingRelX.toFloatOrNull() ?: 0f),
                        "y" to (state.editBuildingRelY.toFloatOrNull() ?: 0f)
                    )
                )
                // Only include labelPosition if at least one coordinate is set
                val lx = state.editBuildingLabelX.toFloatOrNull()
                val ly = state.editBuildingLabelY.toFloatOrNull()
                if (lx != null || ly != null) {
                    fields["labelPosition"] = mapOf(
                        "x" to (lx ?: 0f),
                        "y" to (ly ?: 0f)
                    )
                }
                repo.updateBuildingMetadata(state.editBuildingId, fields)
                invalidateCacheForSelectedCampus()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        step = AdminStep.CAMPUS_HOME,
                        statusMessage = "Building '${state.editBuildingName}' updated",
                        isError = false
                    )
                }
                loadBuildings()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Failed to update: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    private fun detectFileType(fileName: String): FloorFileType? {
        val lower = fileName.lowercase()
        return when {
            lower.contains("wall") -> FloorFileType.WALLS
            lower.contains("stair") -> FloorFileType.STAIRS
            lower.contains("entrance") -> FloorFileType.ENTRANCES
            lower.contains("room") -> FloorFileType.ROOMS
            lower.contains("boundary") -> FloorFileType.BOUNDARY
            else -> null
        }
    }
}
