package `in`.project.enroute.feature.admin

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.project.enroute.data.cache.FloorPlanCache
import `in`.project.enroute.data.model.CampusMetadata
import `in`.project.enroute.data.repository.FirebaseFloorPlanRepository
import `in`.project.enroute.feature.campussearch.CampusItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    ADD_FLOOR            // Select building + floor + upload floor files
}

// ── UI State ─────────────────────────────────────────────────────
data class AdminUiState(
    val step: AdminStep = AdminStep.SELECT_CAMPUS,

    // Campus search
    val searchQuery: String = "",
    val searchResults: List<CampusItem> = emptyList(),
    val hasSearched: Boolean = false,
    val isSearching: Boolean = false,
    val searchError: String? = null,

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
    val isError: Boolean = false
)

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private var repository: FirebaseFloorPlanRepository? = null
    private val cache = FloorPlanCache(application.applicationContext)
    private var searchJob: Job? = null

    // ── Campus search ────────────────────────────────────────────

    /**
     * Called on every keystroke in the admin campus search.
     * Debounces 250 ms and queries Firebase.
     */
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update {
                it.copy(searchResults = emptyList(), hasSearched = false, searchError = null, isSearching = false)
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(250)
            _uiState.update { it.copy(isSearching = true, searchError = null) }
            try {
                val results = FirebaseFloorPlanRepository.searchCampuses(query)
                val items = results.map { CampusItem(it.first, it.second) }
                _uiState.update {
                    it.copy(searchResults = items, isSearching = false, hasSearched = true)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSearching = false, searchError = e.message, hasSearched = true)
                }
            }
        }
    }

    fun retrySearch() {
        val q = _uiState.value.searchQuery
        if (q.isNotBlank()) {
            _uiState.update { it.copy(searchQuery = "") }
            updateSearchQuery(q)
        }
    }

    fun selectCampus(campusId: String, campusName: String) {
        repository = FirebaseFloorPlanRepository(campusId)
        _uiState.update {
            it.copy(
                selectedCampusId = campusId,
                selectedCampusName = campusName,
                step = AdminStep.CAMPUS_HOME
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
                FirebaseFloorPlanRepository.createCampus(state.newCampusId, metadata)

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

    private fun loadBuildings() {
        val repo = repository ?: return
        viewModelScope.launch {
            try {
                val buildings = repo.getAvailableBuildings()
                _uiState.update { it.copy(availableBuildings = buildings) }
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
                statusMessage = null,
                searchQuery = "",
                searchResults = emptyList(),
                hasSearched = false
            )
        }
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
                    loadBuildings()
                    invalidateCacheForSelectedCampus()
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

    private fun loadFloorsForBuilding(buildingId: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            try {
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
                // Refresh floor list
                loadFloorsForBuilding(state.selectedBuildingForFloor)
                invalidateCacheForSelectedCampus()
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
     * Clears the disk cache for the currently selected campus so that
     * the next time the home screen loads, it fetches fresh data.
     */
    private fun invalidateCacheForSelectedCampus() {
        val campusId = _uiState.value.selectedCampusId
        if (campusId.isNotBlank()) {
            viewModelScope.launch {
                cache.clearCache(campusId)
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
