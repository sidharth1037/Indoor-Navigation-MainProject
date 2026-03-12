package `in`.project.enroute.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.project.enroute.data.cache.FloorPlanCache
import `in`.project.enroute.data.repository.FirebaseFloorPlanRepository
import `in`.project.enroute.feature.settings.data.SettingsRepository
import `in`.project.enroute.feature.pdr.data.model.StrideConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = false,
    val currentHeight: Float? = null,
    val isEditingHeight: Boolean = false,
    val heightInputValue: String = "",
    val showEntrances: Boolean = false,
    val showMotionLabel: Boolean = true,
    val strideK: Float = StrideConfig().kValue,
    val strideC: Float = StrideConfig().cValue,
    val heightKInfluence: Float = 0.05f,
    val turnWindow: Int = 3,
    val turnThreshold: Float = 60f,
    val turnSensitivity: Float = 0.5f,
    // Step detection
    val stepThreshold: Float = 2.0f,
    val highPassAlpha: Float = 0.9f,
    val compensationSteps: Int = 4,
    // ML model & stair detection
    val mlModel: String = "v6",
    val stairEntryThreshold: Int = 2,
    val stairProximityRadius: Float = 150f,
    val stairLookback: Int = 3,
    val stairReplayCount: Int = 3
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = SettingsRepository(application.applicationContext)
    private val cache = FloorPlanCache(application.applicationContext)
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Load saved height on start
        viewModelScope.launch {
            repository.height.collect { savedHeight ->
                _uiState.update { 
                    it.copy(currentHeight = savedHeight)
                }
            }
        }
        
        // Load saved entrance visibility preference
        viewModelScope.launch {
            repository.showEntrances.collect { showEntrances ->
                _uiState.update {
                    it.copy(showEntrances = showEntrances)
                }
            }
        }

        viewModelScope.launch {
            repository.showMotionLabel.collect { v ->
                _uiState.update { it.copy(showMotionLabel = v) }
            }
        }

        // Load stride K constant
        viewModelScope.launch {
            repository.strideK.collect { k ->
                if (k != null) {
                    _uiState.update { it.copy(strideK = k) }
                }
            }
        }

        // Load stride C constant
        viewModelScope.launch {
            repository.strideC.collect { c ->
                if (c != null) {
                    _uiState.update { it.copy(strideC = c) }
                }
            }
        }

        // Load step detection parameters
        viewModelScope.launch { repository.stepThreshold.collect     { if (it != null) _uiState.update { s -> s.copy(stepThreshold = it) } } }
        viewModelScope.launch { repository.highPassAlpha.collect     { if (it != null) _uiState.update { s -> s.copy(highPassAlpha = it) } } }
        viewModelScope.launch { repository.compensationSteps.collect { if (it != null) _uiState.update { s -> s.copy(compensationSteps = it) } } }

        // Load ML model & stair detection settings
        viewModelScope.launch { repository.mlModel.collect             { if (it != null) _uiState.update { s -> s.copy(mlModel = it) } } }
        viewModelScope.launch { repository.stairEntryThreshold.collect { if (it != null) _uiState.update { s -> s.copy(stairEntryThreshold = it) } } }
        viewModelScope.launch { repository.stairProximityRadius.collect { if (it != null) _uiState.update { s -> s.copy(stairProximityRadius = it) } } }
        viewModelScope.launch { repository.stairLookback.collect       { if (it != null) _uiState.update { s -> s.copy(stairLookback = it) } } }
        viewModelScope.launch { repository.stairReplayCount.collect    { if (it != null) _uiState.update { s -> s.copy(stairReplayCount = it) } } }

        // Load stride tuning — height & turn
        viewModelScope.launch { repository.heightKInfluence.collect  { if (it != null) _uiState.update { s -> s.copy(heightKInfluence = it) } } }
        viewModelScope.launch { repository.turnWindow.collect        { if (it != null) _uiState.update { s -> s.copy(turnWindow = it) } } }
        viewModelScope.launch { repository.turnThreshold.collect     { if (it != null) _uiState.update { s -> s.copy(turnThreshold = it) } } }
        viewModelScope.launch { repository.turnSensitivity.collect   { if (it != null) _uiState.update { s -> s.copy(turnSensitivity = it) } } }
    }

    fun toggleEditHeight() {
        val currentState = _uiState.value
        _uiState.update {
            it.copy(
                isEditingHeight = !currentState.isEditingHeight,
                heightInputValue = if (!currentState.isEditingHeight) {
                    currentState.currentHeight?.toString() ?: ""
                } else {
                    ""
                }
            )
        }
    }

    fun updateHeightInput(value: String) {
        _uiState.update { it.copy(heightInputValue = value) }
    }

    fun saveHeight() {
        val currentState = _uiState.value
        val newHeight = currentState.heightInputValue.toFloatOrNull()
            ?: currentState.currentHeight
            ?: return
        
        viewModelScope.launch {
            repository.saveHeight(newHeight)
        }
        
        _uiState.update {
            it.copy(
                currentHeight = newHeight,
                isEditingHeight = false,
                heightInputValue = ""
            )
        }
    }

    fun toggleUseBackend(enabled: Boolean) {
        // No longer used — backend is always enabled.
        // Kept for binary compatibility.
    }

    /**
     * Clears the backend disk cache so the next home screen load
     * fetches fresh data from Firebase.
     */
    fun clearBackendCache() {
        FirebaseFloorPlanRepository.clearAdminCampusCache()
        viewModelScope.launch {
            cache.clearAllCache()
        }
    }
    
    /**
     * Toggles the entrance visibility preference and saves it.
     */
    fun toggleShowEntrances() {
        val currentState = _uiState.value
        val newValue = !currentState.showEntrances
        _uiState.update { it.copy(showEntrances = newValue) }
        viewModelScope.launch {
            repository.saveShowEntrances(newValue)
        }
    }

    fun toggleShowMotionLabel() {
        val newValue = !_uiState.value.showMotionLabel
        _uiState.update { it.copy(showMotionLabel = newValue) }
        viewModelScope.launch { repository.saveShowMotionLabel(newValue) }
    }

    /**
     * Updates the stride K constant (cadence sensitivity) and persists it.
     */
    fun updateStrideK(k: Float) {
        _uiState.update { it.copy(strideK = k) }
        viewModelScope.launch {
            repository.saveStrideK(k)
        }
    }

    /**
     * Updates the stride C constant (base stride) and persists it.
     */
    fun updateStrideC(c: Float) {
        _uiState.update { it.copy(strideC = c) }
        viewModelScope.launch {
            repository.saveStrideC(c)
        }
    }

    fun updateStepThreshold(v: Float)       { _uiState.update { it.copy(stepThreshold = v) };        viewModelScope.launch { repository.saveStepThreshold(v) } }
    fun updateHighPassAlpha(v: Float)       { _uiState.update { it.copy(highPassAlpha = v) };        viewModelScope.launch { repository.saveHighPassAlpha(v) } }
    fun updateCompensationSteps(v: Int)     { _uiState.update { it.copy(compensationSteps = v) };    viewModelScope.launch { repository.saveCompensationSteps(v) } }

    fun updateMlModel(v: String)             { _uiState.update { it.copy(mlModel = v) };             viewModelScope.launch { repository.saveMlModel(v) } }
    fun updateStairEntryThreshold(v: Int)    { _uiState.update { it.copy(stairEntryThreshold = v) };  viewModelScope.launch { repository.saveStairEntryThreshold(v) } }
    fun updateStairProximityRadius(v: Float)  { _uiState.update { it.copy(stairProximityRadius = v) }; viewModelScope.launch { repository.saveStairProximityRadius(v) } }
    fun updateStairLookback(v: Int)           { _uiState.update { it.copy(stairLookback = v) };       viewModelScope.launch { repository.saveStairLookback(v) } }
    fun updateStairReplayCount(v: Int)        { _uiState.update { it.copy(stairReplayCount = v) };    viewModelScope.launch { repository.saveStairReplayCount(v) } }

    fun updateHeightKInfluence(v: Float)  { _uiState.update { it.copy(heightKInfluence = v) };  viewModelScope.launch { repository.saveHeightKInfluence(v) } }
    fun updateTurnWindow(v: Int)          { _uiState.update { it.copy(turnWindow = v) };        viewModelScope.launch { repository.saveTurnWindow(v) } }
    fun updateTurnThreshold(v: Float)     { _uiState.update { it.copy(turnThreshold = v) };     viewModelScope.launch { repository.saveTurnThreshold(v) } }
    fun updateTurnSensitivity(v: Float)   { _uiState.update { it.copy(turnSensitivity = v) };   viewModelScope.launch { repository.saveTurnSensitivity(v) } }
}
