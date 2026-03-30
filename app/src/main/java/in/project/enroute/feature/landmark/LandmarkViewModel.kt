package `in`.project.enroute.feature.landmark

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.project.enroute.data.model.Landmark
import `in`.project.enroute.data.repository.FirebaseFloorPlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for landmark management.
 */
data class LandmarkUiState(
    /** All loaded landmarks for the current campus. */
    val landmarks: List<Landmark> = emptyList(),
    /** Whether the admin is currently in tap-on-map placement mode. */
    val isAddingLandmark: Boolean = false,
    /** World coordinate of the tapped location (pending confirmation). */
    val pendingLandmarkLocation: Offset? = null,
    /** Floor ID at the pending location. */
    val pendingFloorId: String? = null,
    /** Building ID at the pending location. */
    val pendingBuildingId: String? = null,
    /** Whether a save operation is in progress. */
    val isSaving: Boolean = false,
    /** Error message to display. */
    val error: String? = null,
    /** Currently selected landmark (for info panel). */
    val selectedLandmark: Landmark? = null,
    /** Whether landmarks are currently loading. */
    val isLoading: Boolean = false
)

/**
 * ViewModel for managing landmark CRUD operations and UI state.
 *
 * Scoped to the Home backstack entry so landmarks persist across
 * screen rotations and navigation to/from the Add Landmark screen.
 */
class LandmarkViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LandmarkUiState())
    val uiState: StateFlow<LandmarkUiState> = _uiState.asStateFlow()

    private var repository: FirebaseFloorPlanRepository? = null
    private var currentCampusId: String? = null

    // ── Loading ──────────────────────────────────────────────────

    /**
     * Fetches all landmarks for the given campus from Firebase.
     * Creates the repository instance if needed.
     */
    fun loadLandmarks(campusId: String) {
        currentCampusId = campusId
        repository = FirebaseFloorPlanRepository(campusId)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val landmarks = repository!!.loadLandmarks()
                _uiState.update {
                    it.copy(landmarks = landmarks, isLoading = false, error = null)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load landmarks: ${e.message}")
                }
            }
        }
    }

    // ── Tap-on-map placement mode ────────────────────────────────

    /**
     * Enters landmark placement mode (tap-on-map to select location).
     */
    fun startAddingLandmark() {
        _uiState.update {
            it.copy(
                isAddingLandmark = true,
                pendingLandmarkLocation = null,
                pendingFloorId = null,
                pendingBuildingId = null,
                selectedLandmark = null
            )
        }
    }

    /**
     * Cancels landmark placement mode and clears pending state.
     */
    fun cancelAddingLandmark() {
        _uiState.update {
            it.copy(
                isAddingLandmark = false,
                pendingLandmarkLocation = null,
                pendingFloorId = null,
                pendingBuildingId = null
            )
        }
    }

    /**
     * Stores the tapped world coordinate as the pending landmark location.
     */
    fun setPendingLocation(point: Offset, floorId: String?, buildingId: String?) {
        _uiState.update {
            it.copy(
                pendingLandmarkLocation = point,
                pendingFloorId = floorId,
                pendingBuildingId = buildingId
            )
        }
    }

    /**
     * Clears the pending location (e.g., on floor change).
     */
    fun clearPendingLocation() {
        _uiState.update {
            it.copy(
                pendingLandmarkLocation = null,
                pendingFloorId = null,
                pendingBuildingId = null
            )
        }
    }

    // ── Save ─────────────────────────────────────────────────────

    /**
     * Saves a new landmark to Firebase using the pending location data.
     *
     * @param name Display name for the landmark
     * @param icon Material icon identifier (e.g., "restaurant")
     * @param campusX Campus-wide X coordinate (post-transform)
     * @param campusY Campus-wide Y coordinate (post-transform)
     */
    fun saveLandmark(
        name: String,
        icon: String,
        campusX: Float,
        campusY: Float,
        onResult: (Boolean) -> Unit = {}
    ) {
        val repo = repository
        if (repo == null) {
            onResult(false)
            return
        }
        val state = _uiState.value
        val pending = state.pendingLandmarkLocation
        if (pending == null) {
            onResult(false)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val landmark = Landmark(
                    name = name,
                    icon = icon,
                    x = pending.x,
                    y = pending.y,
                    floorId = state.pendingFloorId ?: "",
                    buildingId = state.pendingBuildingId ?: "",
                    campusX = campusX,
                    campusY = campusY
                )
                val id = repo.saveLandmark(landmark)
                val savedLandmark = landmark.copy(id = id)

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isAddingLandmark = false,
                        pendingLandmarkLocation = null,
                        pendingFloorId = null,
                        pendingBuildingId = null,
                        landmarks = it.landmarks + savedLandmark
                    )
                }
                onResult(true)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = "Failed to save landmark: ${e.message}")
                }
                onResult(false)
            }
        }
    }

    // ── Selection (for info panel) ───────────────────────────────

    /**
     * Selects a landmark to show in the info panel.
     */
    fun selectLandmark(landmark: Landmark) {
        _uiState.update { it.copy(selectedLandmark = landmark) }
    }

    /**
     * Clears the selected landmark (dismisses info panel).
     */
    fun clearSelectedLandmark() {
        _uiState.update { it.copy(selectedLandmark = null) }
    }

    // ── Update ───────────────────────────────────────────────────

    /**
     * Updates an existing landmark in Firebase.
     */
    fun updateLandmark(landmark: Landmark, onResult: (Boolean) -> Unit = {}) {
        val repo = repository
        if (repo == null) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                repo.updateLandmark(landmark)
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        landmarks = state.landmarks.map {
                            if (it.id == landmark.id) landmark else it
                        },
                        selectedLandmark = if (state.selectedLandmark?.id == landmark.id) landmark else state.selectedLandmark
                    )
                }
                onResult(true)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = "Failed to update landmark: ${e.message}")
                }
                onResult(false)
            }
        }
    }

    // ── Delete ───────────────────────────────────────────────────

    /**
     * Deletes a landmark from Firebase.
     */
    fun deleteLandmark(landmarkId: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                repo.deleteLandmark(landmarkId)
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        landmarks = state.landmarks.filter { it.id != landmarkId },
                        selectedLandmark = if (state.selectedLandmark?.id == landmarkId) null else state.selectedLandmark
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = "Failed to delete landmark: ${e.message}")
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
