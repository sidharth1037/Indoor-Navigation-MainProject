package `in`.project.enroute.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.project.enroute.feature.settings.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = false,
    val currentHeight: Float? = null,
    val isEditingHeight: Boolean = false,
    val heightInputValue: String = ""
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = SettingsRepository(application.applicationContext)
    
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
}
