package `in`.project.enroute.feature.pdr

import android.app.Application
import android.hardware.SensorManager
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.project.enroute.feature.pdr.data.model.PdrState
import `in`.project.enroute.feature.pdr.data.model.StepDetectionConfig
import `in`.project.enroute.feature.pdr.data.model.StrideConfig
import `in`.project.enroute.feature.pdr.data.repository.PdrRepository
import `in`.project.enroute.feature.pdr.sensor.HeadingDetector
import `in`.project.enroute.feature.pdr.sensor.StepDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for PDR feature.
 */
data class PdrUiState(
    val pdrState: PdrState = PdrState(),
    val isSelectingOrigin: Boolean = false,
    val stepDetectionConfig: StepDetectionConfig = StepDetectionConfig(),
    val strideConfig: StrideConfig = StrideConfig()
)

/**
 * ViewModel for PDR (Pedestrian Dead Reckoning) feature.
 * Manages sensor lifecycle, step detection, and path tracking.
 * 
 * IMPORTANT: Step detection only starts after origin is set.
 */
class PdrViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorManager = application.getSystemService(SensorManager::class.java)
    
    private val repository = PdrRepository()
    private val headingDetector = HeadingDetector(sensorManager)
    private val stepDetector = StepDetector(sensorManager)

    private val _uiState = MutableStateFlow(PdrUiState())
    val uiState: StateFlow<PdrUiState> = _uiState.asStateFlow()

    // Current heading (updated continuously by sensor)
    private var currentHeading = 0f

    init {
        // Set up heading detector callback
        headingDetector.onHeadingChanged = { heading ->
            currentHeading = heading
            repository.updateHeading(heading)
        }

        // Set up step detector callback
        stepDetector.onStepDetected = { stepIntervalMs ->
            // Only process steps if we're tracking (origin is set)
            if (_uiState.value.pdrState.isTracking) {
                repository.processStep(stepIntervalMs, currentHeading)
            }
        }

        // Observe repository state and update UI state
        viewModelScope.launch {
            repository.pdrState.collect { pdrState ->
                _uiState.update { it.copy(pdrState = pdrState) }
            }
        }
    }

    /**
     * Enters origin selection mode.
     * In this mode, the user can tap on the canvas to set the starting point.
     */
    fun startOriginSelection() {
        _uiState.update { it.copy(isSelectingOrigin = true) }
    }

    /**
     * Cancels origin selection mode without setting an origin.
     */
    fun cancelOriginSelection() {
        _uiState.update { it.copy(isSelectingOrigin = false) }
    }

    /**
     * Sets the origin point and starts PDR tracking.
     * This will:
     * 1. Set the origin in the repository
     * 2. Start the heading sensor
     * 3. Start the step detector
     *
     * @param origin The starting coordinate in canvas/world space
     */
    fun setOrigin(origin: Offset) {
        // Exit selection mode
        _uiState.update { it.copy(isSelectingOrigin = false) }
        
        // Set origin in repository (this enables tracking)
        repository.setOrigin(origin)
        
        // Start sensors
        startSensors()
    }

    /**
     * Clears the PDR path and stops all tracking and sensor activity.
     * Resets everything to initial state.
     */
    fun clearAndStop() {
        // Stop sensors
        stopSensors()
        
        // Clear repository state
        repository.clearAndStopTracking()
    }

    /**
     * Updates step detection configuration.
     */
    fun updateStepDetectionConfig(config: StepDetectionConfig) {
        _uiState.update { it.copy(stepDetectionConfig = config) }
        stepDetector.updateConfig(config)
    }

    /**
     * Updates stride calculation configuration.
     */
    fun updateStrideConfig(config: StrideConfig) {
        _uiState.update { it.copy(strideConfig = config) }
        repository.updateStrideConfig(config)
    }

    /**
     * Starts the heading and step sensors.
     * Called internally when origin is set.
     */
    private fun startSensors() {
        headingDetector.start()
        stepDetector.start()
    }

    /**
     * Stops all sensors.
     * Called internally when clearing, and also on ViewModel destruction.
     */
    private fun stopSensors() {
        headingDetector.stop()
        stepDetector.stop()
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up sensors when ViewModel is destroyed
        stopSensors()
    }
}
