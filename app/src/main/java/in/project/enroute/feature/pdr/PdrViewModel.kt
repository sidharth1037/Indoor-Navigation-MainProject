package `in`.project.enroute.feature.pdr

import android.app.Application
import android.hardware.SensorManager
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.project.enroute.core.position.UserPositionStream
import `in`.project.enroute.feature.pdr.correction.CampusBuilding
import `in`.project.enroute.feature.pdr.correction.FloorConstraintData
import `in`.project.enroute.feature.pdr.correction.StairwellZone
import `in`.project.enroute.feature.pdr.data.model.PdrState
import `in`.project.enroute.feature.pdr.data.model.PdrRuntimeMetrics
import `in`.project.enroute.feature.pdr.data.model.StepDetectionConfig
import `in`.project.enroute.feature.pdr.data.model.StrideConfig
import `in`.project.enroute.feature.pdr.data.repository.MotionRepository
import `in`.project.enroute.feature.pdr.data.repository.PdrRepository
import `in`.project.enroute.feature.pdr.sensor.HeadingDetector
import `in`.project.enroute.feature.pdr.sensor.StepDetector
import `in`.project.enroute.feature.settings.data.SettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

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
    val motionConfidence: Float = 0f,
    val runtimeMetrics: PdrRuntimeMetrics = PdrRuntimeMetrics()
)

/**
 * ViewModel for PDR (Pedestrian Dead Reckoning) feature.
 * Manages sensor lifecycle, step detection, and path tracking.
 * 
 * IMPORTANT: Step detection only starts after origin is set.
 */
class PdrViewModel(application: Application) : AndroidViewModel(application), UserPositionStream {

    private val sensorManager = application.getSystemService(SensorManager::class.java)
    
    private val repository = PdrRepository()
    private val motionRepository = MotionRepository(application.applicationContext)
    private val settingsRepository = SettingsRepository(application.applicationContext)
    private val headingDetector = HeadingDetector(sensorManager)
    private val stepDetector = StepDetector(sensorManager)

    private val _uiState = MutableStateFlow(PdrUiState())
    val uiState: StateFlow<PdrUiState> = _uiState.asStateFlow()

    /** Current ML model key from settings ("v6" or "v6_64"). */
    private var currentMlModel: String = "v6"

    // ── ML-gated peak buffer ─────────────────────────────────────────
    // All peaks from StepDetector are stored in [peakBuffer] with their
    // heading.  When the ML model produces ≥2 consecutive non-idle labels
    // (mlActive becomes true), up to [compensationSteps] peaks are
    // replayed to compensate for model startup lag, then subsequent peaks
    // flow through directly.  If ML goes idle, the buffer is cleared.
    private data class BufferedStep(
        val intervalMs: Long,
        val heading: Float,
        val enqueuedAtMs: Long = System.currentTimeMillis()
    )
    private val peakBuffer = ArrayDeque<BufferedStep>()
    /** True once the ML model has confirmed activity (≥2 consecutive non-idle). */
    private var mlActive = false
    /** How many consecutive non-idle labels we've seen. */
    private var consecutiveNonIdleCount = 0
    /** How many compensation peaks to replay from the buffer. */
    private var compensationSteps: Int = 4

    // ── Async step processing ────────────────────────────────────────
    // Sensor callback sends steps here; consumed on Dispatchers.Default
    // so the sensor thread never blocks on heavy processStep() work.
    private val stepChannel = Channel<BufferedStep>(UNLIMITED)
    private val stepQueueDepth = AtomicInteger(0)
    private var maxStepQueueDepth = 0
    private val _lastHeadingSampleAtMs = MutableStateFlow(0L)
    private val _sensorsStartedAtMs = MutableStateFlow(0L)
    private val _hasFreshHeadingSinceStart = MutableStateFlow(false)
    private val _userPosition = MutableStateFlow<Offset?>(null)
    private val _currentFloorId = MutableStateFlow<String?>(null)
    private val _isTracking = MutableStateFlow(false)

    private val stepProcessingDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable).apply {
            name = "pdr-step-processing"
            isDaemon = true
        }
    }.asCoroutineDispatcher()

    private val motionProcessingDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable).apply {
            name = "pdr-motion-processing"
            isDaemon = true
        }
    }.asCoroutineDispatcher()

    private val stepProcessingScope = CoroutineScope(SupervisorJob() + stepProcessingDispatcher)
    private val motionProcessingScope = CoroutineScope(SupervisorJob() + motionProcessingDispatcher)

    /**
     * Current heading exposed as a separate flow so compass-only changes
     * don't cause full HomeScreen recomposition.
     */
    val heading: StateFlow<Float> = repository.heading
    override val headingRadians: StateFlow<Float> = repository.heading
    override val userPosition: StateFlow<Offset?> = _userPosition.asStateFlow()
    override val currentFloorId: StateFlow<String?> = _currentFloorId.asStateFlow()
    override val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()
    val hasFreshHeadingSinceStart: StateFlow<Boolean> = _hasFreshHeadingSinceStart.asStateFlow()

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

        // Load height→K influence from settings
        viewModelScope.launch {
            settingsRepository.heightKInfluence.collect { v ->
                if (v != null) {
                    val updatedConfig = _uiState.value.strideConfig.copy(heightKInfluence = v)
                    _uiState.update { it.copy(strideConfig = updatedConfig) }
                    repository.updateStrideConfig(updatedConfig)
                }
            }
        }

        // Load turn detection window from settings
        viewModelScope.launch {
            settingsRepository.turnWindow.collect { v ->
                if (v != null) {
                    val updatedConfig = _uiState.value.strideConfig.copy(turnWindow = v)
                    _uiState.update { it.copy(strideConfig = updatedConfig) }
                    repository.updateStrideConfig(updatedConfig)
                }
            }
        }

        // Load turn threshold from settings
        viewModelScope.launch {
            settingsRepository.turnThreshold.collect { v ->
                if (v != null) {
                    val updatedConfig = _uiState.value.strideConfig.copy(turnThreshold = v)
                    _uiState.update { it.copy(strideConfig = updatedConfig) }
                    repository.updateStrideConfig(updatedConfig)
                }
            }
        }

        // Load turn sensitivity from settings
        viewModelScope.launch {
            settingsRepository.turnSensitivity.collect { v ->
                if (v != null) {
                    val updatedConfig = _uiState.value.strideConfig.copy(turnSensitivity = v)
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

        // Load compensation steps setting
        viewModelScope.launch {
            settingsRepository.compensationSteps.collect { v ->
                if (v != null) {
                    compensationSteps = v
                    val updated = _uiState.value.stepDetectionConfig.copy(compensationSteps = v)
                    _uiState.update { it.copy(stepDetectionConfig = updated) }
                }
            }
        }

        // Load ML model preference
        viewModelScope.launch {
            settingsRepository.mlModel.collect { model ->
                if (model != null) currentMlModel = model
            }
        }

        // Load stair detection parameters and forward to repository
        viewModelScope.launch {
            settingsRepository.stairEntryThreshold.collect { v ->
                if (v != null) repository.updateStairSettings(entryThreshold = v)
            }
        }
        viewModelScope.launch {
            settingsRepository.stairMinConfidence.collect { v ->
                if (v != null) repository.updateStairSettings(minConfidence = v)
            }
        }
        viewModelScope.launch {
            settingsRepository.stairLookback.collect { v ->
                if (v != null) repository.updateStairSettings(lookback = v)
            }
        }
        viewModelScope.launch {
            settingsRepository.stairReplayCount.collect { v ->
                if (v != null) repository.updateStairSettings(replayCount = v)
            }
        }
        viewModelScope.launch {
            settingsRepository.stairProximityRadius.collect { v ->
                if (v != null) repository.updateStairSettings(proximityRadius = v)
            }
        }
        viewModelScope.launch {
            settingsRepository.stairArrivalLookback.collect { v ->
                if (v != null) repository.updateStairSettings(arrivalLookback = v)
            }
        }
        viewModelScope.launch {
            settingsRepository.stairMinProgressForArrival.collect { v ->
                if (v != null) repository.updateStairSettings(minProgressForArrival = v)
            }
        }
        viewModelScope.launch {
            settingsRepository.stairWalkingArrivalCount.collect { v ->
                if (v != null) repository.updateStairSettings(walkingArrivalCount = v)
            }
        }
        viewModelScope.launch {
            settingsRepository.stairOppositeCancelCount.collect { v ->
                if (v != null) repository.updateStairSettings(oppositeDirectionCancelCount = v)
            }
        }
        viewModelScope.launch {
            settingsRepository.stairMinStepsBeforeCancel.collect { v ->
                if (v != null) repository.updateStairSettings(minStepsBeforeCancel = v)
            }
        }
        viewModelScope.launch {
            settingsRepository.stairArrivalHeadingDeg.collect { v ->
                if (v != null) repository.updateStairSettings(arrivalHeadingThresholdDeg = v)
            }
        }
        viewModelScope.launch {
            settingsRepository.stairReturnHeadingDeg.collect { v ->
                if (v != null) repository.updateStairSettings(returnHeadingThresholdDeg = v)
            }
        }
        viewModelScope.launch {
            settingsRepository.stairIdleThreshold.collect { v ->
                if (v != null) repository.updateStairSettings(idleThreshold = v)
            }
        }

        // Set up step detector callback.
        // ALL peaks are buffered regardless of ML state.  The ML observer
        // decides when to flush or discard the buffer.
        // If ML is already active, peaks pass through directly.
        stepDetector.onStepDetected = { stepIntervalMs ->
            if (_uiState.value.pdrState.isTracking) {
                val step = BufferedStep(stepIntervalMs, headingDetector.heading.value)
                if (mlActive) {
                    // ML already confirmed activity — dispatch directly
                    enqueueStep(step)
                } else {
                    // ML not yet active — buffer the peak
                    peakBuffer.addLast(step)
                    // Cap buffer size to avoid unbounded growth
                    while (peakBuffer.size > MAX_PEAK_BUFFER) peakBuffer.removeFirst()
                }
            }
        }
        stepDetector.onAccelerometerData = { x, y, z ->
            motionRepository.onAccelerometerSample(x, y, z)
        }

        // Consume step channel on a background thread so heavy
        // processStep() work never blocks the sensor callback.
        stepProcessingScope.launch {
            for (step in stepChannel) {
                val queueAgeMs = (System.currentTimeMillis() - step.enqueuedAtMs).coerceAtLeast(0L)
                val startedAt = System.currentTimeMillis()
                repository.processStep(step.intervalMs, step.heading)
                stepQueueDepth.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
                val processingMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                updateRuntimeMetrics(
                    queueAgeMs = queueAgeMs,
                    processingMs = processingMs,
                    queueDepth = stepQueueDepth.get()
                )
            }
        }

        // Forward heading from sensor → repository (for step calculations)
        viewModelScope.launch {
            headingDetector.heading.collect { heading ->
                repository.updateHeading(heading)
                val now = System.currentTimeMillis()
                _lastHeadingSampleAtMs.value = now
                val startedAt = _sensorsStartedAtMs.value
                if (startedAt > 0L && now >= startedAt) {
                    _hasFreshHeadingSinceStart.value = true
                }
            }
        }

        // Observe repository PDR state (path, origin, cadence)
        viewModelScope.launch {
            repository.pdrState.collect { pdrState ->
                _userPosition.value = pdrState.currentPosition
                _currentFloorId.value = pdrState.currentFloor
                _isTracking.value = pdrState.isTracking
                _uiState.update { it.copy(pdrState = pdrState) }
            }
        }

        // Observe motion classification results.
        // Tracks consecutive non-idle labels.  Once ≥2 consecutive non-idle
        // labels arrive, mlActive flips on and buffered peaks are replayed
        // (up to compensationSteps).  If idle arrives, mlActive is turned
        // off and the peak buffer is cleared.
        motionProcessingScope.launch {
            motionRepository.motionEvent.collect { event ->
                if (event != null) {
                    val lagMs = (System.currentTimeMillis() - event.timestamp).coerceAtLeast(0L)
                    updateRuntimeMetrics(
                        motionLagMs = lagMs,
                        queueDepth = stepQueueDepth.get()
                    )

                    // Forward to repository for stairwell detection on dedicated motion lane.
                    repository.onMotionLabel(event.classificationName, event.confidence)

                    // Keep UI publication lightweight and decoupled from realtime detection lane.
                    _uiState.update {
                        it.copy(
                            motionLabel = event.classificationName,
                            motionConfidence = event.confidence
                        )
                    }

                    if (event.classificationName != LABEL_IDLE) {
                        consecutiveNonIdleCount++

                        // Activation threshold: 2 consecutive non-idle labels
                        if (!mlActive && consecutiveNonIdleCount >= 2) {
                            mlActive = true
                            replayCompensationPeaks()
                        }
                    } else {
                        // Idle — deactivate and discard buffered peaks
                        _uiState.update {
                            it.copy(
                                motionLabel = event.classificationName,
                                motionConfidence = event.confidence
                            )
                        }
                        consecutiveNonIdleCount = 0
                        mlActive = false
                        peakBuffer.clear()
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
     * Supplies pre-transformed campus building data for automatic
     * building/floor detection during tracking.
     */
    fun loadBuildingData(campusBuildings: List<CampusBuilding>) {
        repository.loadBuildingData(campusBuildings)
    }

    /**
     * Supplies pre-computed stairwell zones for boundary-based stair detection.
     */
    fun loadStairwellZones(zones: List<StairwellZone>) {
        repository.loadStairwellZones(zones)
    }

    /**
     * Supplies constraint data for all floors so stairwell transitions
     * can load destination floor data autonomously.
     */
    fun loadAllFloorConstraintData(data: Map<String, FloorConstraintData>) {
        repository.loadAllFloorConstraintData(data)
    }

    /**
     * Re-anchors active tracking at a new position/floor for elevator transitions.
     * Tracking remains enabled and the visible path restarts from [position].
     */
    fun resetPositionForElevator(
        position: Offset,
        floorId: String,
        floorConstraintData: FloorConstraintData?
    ) {
        repository.resetPositionForElevator(position, floorId, floorConstraintData)
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

    /**
     * Waits for a heading sample that arrives AFTER the latest sensor start.
     * Returns null on timeout so callers can fall back to the current heading.
     */
    suspend fun awaitHeadingAfterSensorsStart(timeoutMs: Long = 1200L): Float? {
        val startedAt = _sensorsStartedAtMs.value
        if (startedAt == 0L) return heading.value

        val sampleTime = withTimeoutOrNull(timeoutMs) {
            _lastHeadingSampleAtMs.first { it >= startedAt }
        } ?: return null

        return if (sampleTime >= startedAt) heading.value else null
    }

    // ── ML-gate helpers ────────────────────────────────────────────

    /**
     * Replays the most recent peaks from [peakBuffer] (up to
     * [compensationSteps]) through [stepChannel], then clears the buffer.
     * Called when the ML model first confirms activity.
     */
    private fun replayCompensationPeaks() {
        val count = compensationSteps.coerceAtMost(peakBuffer.size)
        // Take the tail (most recent peaks)
        val start = peakBuffer.size - count
        for (i in start until peakBuffer.size) {
            enqueueStep(peakBuffer.elementAt(i))
        }
        peakBuffer.clear()
    }

    private fun enqueueStep(step: BufferedStep) {
        val result = stepChannel.trySend(step)
        if (result.isSuccess) {
            val depth = stepQueueDepth.incrementAndGet()
            if (depth > maxStepQueueDepth) {
                maxStepQueueDepth = depth
            }
            updateRuntimeMetrics(queueDepth = depth)
        }
    }

    private fun updateRuntimeMetrics(
        motionLagMs: Long? = null,
        queueAgeMs: Long? = null,
        processingMs: Long? = null,
        queueDepth: Int? = null
    ) {
        _uiState.update { state ->
            val current = state.runtimeMetrics
            state.copy(
                runtimeMetrics = current.copy(
                    motionLabelLagMs = motionLagMs ?: current.motionLabelLagMs,
                    stepQueueDepth = queueDepth ?: current.stepQueueDepth,
                    maxStepQueueDepth = maxStepQueueDepth,
                    lastStepQueueAgeMs = queueAgeMs ?: current.lastStepQueueAgeMs,
                    lastStepProcessingMs = processingMs ?: current.lastStepProcessingMs
                )
            )
        }
    }

    /** Resets ML gate state for a new tracking session. */
    private fun resetMotionGate() {
        peakBuffer.clear()
        mlActive = false
        consecutiveNonIdleCount = 0
    }

    /**
     * Starts all PDR sensors: heading, step detector, and motion classifier.
     * Called internally when origin is set and tracking begins.
     */
    private fun startSensors() {
        _hasFreshHeadingSinceStart.value = false
        _sensorsStartedAtMs.value = System.currentTimeMillis()
        headingDetector.start()
        motionRepository.start(currentMlModel)
        stepDetector.start()
    }

    /**
     * Stops all PDR sensors.
     * Called internally when tracking is cleared.
     */
    private fun stopSensors() {
        _sensorsStartedAtMs.value = 0L
        _hasFreshHeadingSinceStart.value = false
        headingDetector.stop()
        stepDetector.stop()
        motionRepository.stop()
    }

    override fun onCleared() {
        super.onCleared()
        stepChannel.close()
        stepProcessingScope.cancel()
        motionProcessingScope.cancel()
        stepProcessingDispatcher.close()
        motionProcessingDispatcher.close()
        headingDetector.stop()
        stepDetector.stop()
        motionRepository.stop()
        repository.close()
    }

    companion object {
        /** Label emitted by TFLite model when the user is stationary. */
        private const val LABEL_IDLE = "idle"
        /** Max peaks to keep in the buffer while waiting for ML confirmation. */
        private const val MAX_PEAK_BUFFER = 20
    }
}
