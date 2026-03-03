package `in`.project.enroute.feature.pdr

import android.app.Application
import android.hardware.SensorManager
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.project.enroute.feature.pdr.correction.CampusBuilding
import `in`.project.enroute.feature.pdr.correction.CorrectionConfig
import `in`.project.enroute.feature.pdr.correction.FloorConstraintData
import `in`.project.enroute.feature.pdr.correction.StairPair
import `in`.project.enroute.feature.pdr.data.model.PdrState
import `in`.project.enroute.feature.pdr.data.model.StepDetectionConfig
import `in`.project.enroute.feature.pdr.data.model.StrideConfig
import `in`.project.enroute.feature.pdr.data.repository.MotionRepository
import `in`.project.enroute.feature.pdr.data.repository.PdrRepository
import `in`.project.enroute.feature.pdr.sensor.HeadingDetector
import `in`.project.enroute.feature.pdr.sensor.StepDetector
import `in`.project.enroute.feature.settings.data.SettingsRepository
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
    val showHeightRequired: Boolean = false,
    val stepDetectionConfig: StepDetectionConfig = StepDetectionConfig(),
    val strideConfig: StrideConfig = StrideConfig(),
    val motionLabel: String? = null,
    val motionConfidence: Float = 0f
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
    private val motionRepository = MotionRepository(application.applicationContext)
    private val settingsRepository = SettingsRepository(application.applicationContext)
    private val headingDetector = HeadingDetector(sensorManager)
    private val stepDetector = StepDetector(sensorManager)

    private val _uiState = MutableStateFlow(PdrUiState())
    val uiState: StateFlow<PdrUiState> = _uiState.asStateFlow()

    // ── Motion-gated step detection ──────────────────────────────────
    // The TFLite model needs ~1.9 s (96 samples @ 50 Hz) before its
    // first classification.  Steps detected before that are buffered and
    // replayed once the first non-idle label arrives.
    private data class BufferedStep(val intervalMs: Long, val heading: Float)
    private val stepBuffer = mutableListOf<BufferedStep>()
    private var hasReceivedMotionClassification = false

    /**
     * Current heading exposed as a separate flow so compass-only changes
     * don't cause full HomeScreen recomposition.
     */
    val heading: StateFlow<Float> = repository.heading

    init {
        // Load height from settings and update stride config
        viewModelScope.launch {
            settingsRepository.height.collect { height ->
                if (height != null) {
                    val updatedConfig = _uiState.value.strideConfig.copy(heightCm = height)
                    _uiState.update { it.copy(strideConfig = updatedConfig) }
                    repository.updateStrideConfig(updatedConfig)
                }
            }
        }

        // Load stride K constant from settings
        viewModelScope.launch {
            settingsRepository.strideK.collect { k ->
                if (k != null) {
                    val updatedConfig = _uiState.value.strideConfig.copy(kValue = k)
                    _uiState.update { it.copy(strideConfig = updatedConfig) }
                    repository.updateStrideConfig(updatedConfig)
                }
            }
        }

        // Load stride C constant from settings
        viewModelScope.launch {
            settingsRepository.strideC.collect { c ->
                if (c != null) {
                    val updatedConfig = _uiState.value.strideConfig.copy(cValue = c)
                    _uiState.update { it.copy(strideConfig = updatedConfig) }
                    repository.updateStrideConfig(updatedConfig)
                }
            }
        }

        // Load step detection parameters from settings
        fun collectStepParam(flow: kotlinx.coroutines.flow.Flow<Float?>, update: StepDetectionConfig.(Float) -> StepDetectionConfig) {
            viewModelScope.launch {
                flow.collect { value ->
                    if (value != null) {
                        val updated = _uiState.value.stepDetectionConfig.update(value)
                        _uiState.update { it.copy(stepDetectionConfig = updated) }
                        stepDetector.updateConfig(updated)
                    }
                }
            }
        }
        collectStepParam(settingsRepository.stepThreshold)     { copy(threshold = it) }
        collectStepParam(settingsRepository.highPassAlpha)     { copy(highPassAlpha = it) }
        collectStepParam(settingsRepository.minProminence)     { copy(minProminence = it) }
        collectStepParam(settingsRepository.rhythmToleranceLow)  { copy(rhythmToleranceLow = it) }
        collectStepParam(settingsRepository.rhythmToleranceHigh) { copy(rhythmToleranceHigh = it) }
        collectStepParam(settingsRepository.floorThreshold)    { copy(floorThreshold = it) }

        // Set up step detector callbacks — gated by motion classification.
        // Before first model output: buffer steps.
        // After first output: only register if model says non-idle.
        stepDetector.onStepDetected = { stepIntervalMs ->
            if (_uiState.value.pdrState.isTracking) {
                if (!hasReceivedMotionClassification) {
                    // Model hasn't produced output yet — buffer the step
                    stepBuffer.add(BufferedStep(stepIntervalMs, headingDetector.heading.value))
                } else if (_uiState.value.motionLabel != LABEL_IDLE) {
                    // Active motion (walking / upstairs / downstairs) — register
                    repository.processStep(stepIntervalMs, headingDetector.heading.value)
                }
                // else: idle — discard step
            }
        }
        stepDetector.onAccelerometerData = { x, y, z ->
            motionRepository.onAccelerometerSample(x, y, z)
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

        // Observe motion classification results & flush step buffer on first output
        viewModelScope.launch {
            motionRepository.motionEvent.collect { event ->
                _uiState.update {
                    it.copy(
                        motionLabel = event?.classificationName,
                        motionConfidence = event?.confidence ?: 0f
                    )
                }
                // Forward to repository for stairwell detection
                if (event != null) {
                    repository.onMotionLabel(event.classificationName, event.confidence)

                    // First classification after tracking started
                    if (!hasReceivedMotionClassification) {
                        hasReceivedMotionClassification = true
                        if (event.classificationName != LABEL_IDLE) {
                            flushStepBuffer()
                        } else {
                            stepBuffer.clear()
                        }
                    }
                }
            }
        }
    }

    /**
     * Enters origin selection mode.
     * Shows height dialog first if height is not set.
     */
    fun startOriginSelection() {
        if (_uiState.value.strideConfig.heightCm == null) {
            _uiState.update { it.copy(showHeightRequired = true) }
        } else {
            _uiState.update { it.copy(isSelectingOrigin = true) }
        }
    }

    /**
     * Dismisses the height required dialog.
     */
    fun dismissHeightRequired() {
        _uiState.update { it.copy(showHeightRequired = false) }
    }

    /**
     * Saves height from the dialog and proceeds to origin selection.
     */
    fun saveHeightAndProceed(heightCm: Float) {
        viewModelScope.launch {
            settingsRepository.saveHeight(heightCm)
        }
        val updatedConfig = _uiState.value.strideConfig.copy(heightCm = heightCm)
        _uiState.update {
            it.copy(
                strideConfig = updatedConfig,
                showHeightRequired = false,
                isSelectingOrigin = true
            )
        }
        repository.updateStrideConfig(updatedConfig)
    }

    /**
     * Cancels origin selection mode without setting an origin.
     */
    fun cancelOriginSelection() {
        _uiState.update { it.copy(isSelectingOrigin = false) }
    }

    /**
     * Sets the origin point and starts PDR tracking.
     * Origin should be in campus-wide coordinates.
     *
     * @param origin The starting coordinate in campus-wide space
     * @param currentFloor The floor the user is on (e.g. "floor_1")
     * @param floorConstraintData Optional floor plan data for error correction.
     *                            When provided, wall constraint, turn detection,
     *                            and entrance snapping are enabled.
     */
    fun setOrigin(
        origin: Offset,
        currentFloor: String? = null,
        floorConstraintData: FloorConstraintData? = null
    ) {
        // Exit selection mode
        _uiState.update { it.copy(isSelectingOrigin = false) }

        // Supply floor constraints to the repository (if available)
        if (floorConstraintData != null) {
            repository.setFloorConstraints(floorConstraintData)
        }

        // Reset motion gate for this new tracking session
        resetMotionGate()

        // Set origin in repository (this enables tracking)
        repository.setOrigin(origin, currentFloor)
        
        // Start sensors
        startSensors()
    }

    // ── Floor constraint management ──────────────────────────────────

    /**
     * Supplies (or replaces) the floor plan wall/entrance data used for
     * error correction.  Can be called independently of [setOrigin] to
     * support floor switching later.
     */
    fun setFloorConstraints(data: FloorConstraintData) {
        repository.setFloorConstraints(data)
    }

    /**
     * Hot-swaps correction tuning parameters without resetting the path.
     */
    fun updateCorrectionConfig(config: CorrectionConfig) {
        repository.updateCorrectionConfig(config)
    }

    /**
     * Supplies pre-transformed campus building data for automatic
     * building/floor detection during tracking.
     */
    fun loadBuildingData(campusBuildings: List<CampusBuilding>) {
        repository.loadBuildingData(campusBuildings)
    }

    /**
     * Supplies pre-computed stair pairs for stairwell transition detection.
     */
    fun loadStairPairs(pairs: List<StairPair>) {
        repository.loadStairPairs(pairs)
    }

    /**
     * Supplies constraint data for all floors so stairwell transitions
     * can load destination floor data autonomously.
     */
    fun loadAllFloorConstraintData(data: Map<String, FloorConstraintData>) {
        repository.loadAllFloorConstraintData(data)
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

        // Reset motion gate
        resetMotionGate()
    }

    /**
     * Updates step detection configuration.
     * Public API for a future settings/calibration screen.
     */
    @Suppress("unused")
    fun updateStepDetectionConfig(config: StepDetectionConfig) {
        _uiState.update { it.copy(stepDetectionConfig = config) }
        stepDetector.updateConfig(config)
    }

    /**
     * Updates stride calculation configuration.
     * Public API for a future settings/calibration screen.
     */
    @Suppress("unused")
    fun updateStrideConfig(config: StrideConfig) {
        _uiState.update { it.copy(strideConfig = config) }
        repository.updateStrideConfig(config)
    }

    /**
     * Switches the heading sensor between compass mode (slow, low CPU)
     * and tracking mode (fast, for smooth following-mode canvas rotation).
     * Has no effect if sensors are not yet running (e.g. before PDR starts).
     */
    fun setHeadingTrackingMode(enabled: Boolean) {
        headingDetector.setTrackingMode(enabled)
    }

    // ── Motion-gate helpers ─────────────────────────────────────────

    /** Replays all buffered early steps through the repository. */
    private fun flushStepBuffer() {
        for (step in stepBuffer) {
            repository.processStep(step.intervalMs, step.heading)
        }
        stepBuffer.clear()
    }

    /** Resets buffer and flag for a new tracking session. */
    private fun resetMotionGate() {
        stepBuffer.clear()
        hasReceivedMotionClassification = false
    }

    /**
     * Starts all PDR sensors: heading, step detector, and motion classifier.
     * Called internally when origin is set and tracking begins.
     */
    private fun startSensors() {
        headingDetector.start()
        motionRepository.start()
        stepDetector.start()
    }

    /**
     * Stops all PDR sensors.
     * Called internally when tracking is cleared.
     */
    private fun stopSensors() {
        headingDetector.stop()
        stepDetector.stop()
        motionRepository.stop()
    }

    override fun onCleared() {
        super.onCleared()
        headingDetector.stop()
        stepDetector.stop()
        motionRepository.stop()
    }

    companion object {
        /** Label emitted by TFLite model when the user is stationary. */
        private const val LABEL_IDLE = "idle"
    }
}
