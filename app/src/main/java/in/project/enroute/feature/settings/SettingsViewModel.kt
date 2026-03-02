package `in`.project.enroute.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.project.enroute.data.cache.FloorPlanCache
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
    /** Stride K value (cadence sensitivity). Null = default. */
    val strideK: Float = StrideConfig().kValue,
    /** Stride C value (base stride constant). Null = default. */
    val strideC: Float = StrideConfig().cValue,
    /** Step detection threshold in m/s². Null = default (12f). */
    val stepThreshold: Float = 12f
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

        // Load step detection threshold
        viewModelScope.launch {
            repository.stepThreshold.collect { threshold ->
                if (threshold != null) {
                    _uiState.update { it.copy(stepThreshold = threshold) }
                }
            }
        }
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

    /**
     * Updates the step detection threshold (acceleration in m/s²) and persists it.
     */
    fun updateStepThreshold(threshold: Float) {
        _uiState.update { it.copy(stepThreshold = threshold) }
        viewModelScope.launch {
            repository.saveStepThreshold(threshold)
        }
    }
}
