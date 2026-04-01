package `in`.project.enroute.feature.roominfo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.project.enroute.data.model.RoomInfo
import `in`.project.enroute.data.repository.FirebaseFloorPlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for room info management.
 */
data class RoomInfoUiState(
    /** All room info for the current campus (used for search indexing). */
    val allRoomInfo: List<RoomInfo> = emptyList(),
    /** Current room's info (description + tags). */
    val roomInfo: RoomInfo? = null,
    /** Whether room info is currently loading. */
    val isLoading: Boolean = false,
    /** Whether a save operation is in progress. */
    val isSaving: Boolean = false,
    /** Error message to display. */
    val error: String? = null
)

/**
 * ViewModel for managing room info CRUD operations and UI state.
 *
 * Scoped to the Home backstack entry so room info persists across
 * screen rotations and navigation to/from the RoomInfoScreen.
 */
class RoomInfoViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RoomInfoUiState())
    val uiState: StateFlow<RoomInfoUiState> = _uiState.asStateFlow()

    private var repository: FirebaseFloorPlanRepository? = null
    private var currentCampusId: String? = null

    /**
     * Loads all room info for the given campus once.
     * Call this once when campus loads to populate the search index.
     */
    fun loadAllRoomInfo(campusId: String) {
        currentCampusId = campusId
        repository = FirebaseFloorPlanRepository(campusId)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val roomInfo = repository!!.loadAllRoomInfo()
                _uiState.update {
                    it.copy(allRoomInfo = roomInfo, isLoading = false, error = null)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load room info: ${e.message}")
                }
            }
        }
    }

    /**
     * Loads room info for a specific room to display on the detail page.
     */
    fun loadRoomInfo(buildingId: String, floorId: String, roomId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val roomInfo = repository?.loadRoomInfo(buildingId, floorId, roomId)
                    ?: RoomInfo(buildingId, floorId, roomId)
                _uiState.update {
                    it.copy(roomInfo = roomInfo, isLoading = false, error = null)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load room info: ${e.message}")
                }
            }
        }
    }

    /**
     * Saves room info (description and tags) to Firestore.
     */
    fun saveRoomInfo(
        buildingId: String,
        floorId: String,
        roomId: Int,
        description: String,
        tags: List<String>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                repository?.saveRoomInfo(buildingId, floorId, roomId, description, tags)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = null,
                        roomInfo = RoomInfo(buildingId, floorId, roomId, description, tags)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = "Failed to save room info: ${e.message}")
                }
            }
        }
    }

    /**
     * Clears the current room info when leaving the detail page.
     */
    fun clearRoomInfo() {
        _uiState.update { it.copy(roomInfo = null) }
    }
}
