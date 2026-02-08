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
 * Heading is stored separately from [pdrState] so high-frequency compass
 * updates don't trigger copies of the path list.
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

    /**
     * Current heading exposed as a separate flow so compass-only changes
     * don't cause full HomeScreen recomposition.
     */
    val heading: StateFlow<Float> = repository.heading

    init {
        // Start heading detector immediately for compass functionality
        // (step detector only starts when tracking begins)
        headingDetector.start()

        // Set up step detector callback
        stepDetector.onStepDetected = { stepIntervalMs ->
            // Only process steps if we're tracking (origin is set)
            if (_uiState.value.pdrState.isTracking) {
                repository.processStep(stepIntervalMs, headingDetector.heading.value)
            }
        }

        // Forward heading from sensor → repository (for step calculations)
        viewModelScope.launch {
            headingDetector.heading.collect { heading ->
                repository.updateHeading(heading)
            }
        }

        // Observe repository PDR state (path, origin, cadence)
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
     * Switches the heading sensor between compass mode (slow, low CPU)
     * and tracking mode (fast, for smooth following-mode canvas rotation).
     */
    fun setHeadingTrackingMode(enabled: Boolean) {
        headingDetector.setTrackingMode(enabled)
    }

    /**
     * Starts the step detector sensor.
     * Heading detector is always running for compass functionality.
     * Called internally when origin is set.
     */
    private fun startSensors() {
        stepDetector.start()
    }

    /**
     * Stops the step detector sensor.
     * Heading detector keeps running for compass.
     * Called internally when clearing tracking.
     */
    private fun stopSensors() {
        stepDetector.stop()
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up all sensors when ViewModel is destroyed
        headingDetector.stop()
        stepDetector.stop()
    }
}
