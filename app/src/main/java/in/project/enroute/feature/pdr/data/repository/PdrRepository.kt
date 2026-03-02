package `in`.project.enroute.feature.pdr.data.repository

import android.util.Log
import androidx.compose.ui.geometry.Offset
import `in`.project.enroute.feature.pdr.correction.CorrectionConfig
import `in`.project.enroute.feature.pdr.correction.FloorConstraintProvider
import `in`.project.enroute.feature.pdr.correction.FloorConstraintData
import `in`.project.enroute.feature.pdr.correction.BuildingDetector
import `in`.project.enroute.feature.pdr.correction.CampusBuilding
import `in`.project.enroute.feature.pdr.correction.StepCorrectionEngine
import `in`.project.enroute.feature.pdr.correction.ArrivalReason
import `in`.project.enroute.feature.pdr.correction.StairClimbingState
import `in`.project.enroute.feature.pdr.correction.StairPair
import `in`.project.enroute.feature.pdr.correction.StairTransitionEvent
import `in`.project.enroute.feature.pdr.correction.StairwellTransitionAnimator
import `in`.project.enroute.feature.pdr.correction.StairwellTransitionDetector
import `in`.project.enroute.feature.pdr.data.model.CadenceState
import `in`.project.enroute.feature.pdr.data.model.PathPoint
import `in`.project.enroute.feature.pdr.data.model.PdrState
import `in`.project.enroute.feature.pdr.data.model.StepEvent
import `in`.project.enroute.feature.pdr.data.model.StrideConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.sin

/**
 * Repository for PDR (Pedestrian Dead Reckoning) path calculation logic.
 * Handles stride calculation, path tracking, and state management.
 * Uses StateFlow for reactive data emission.
 *
 * Heading is stored in a **separate** StateFlow so that high-frequency
 * compass updates don't trigger copies of the path list inside [PdrState].
 */
class PdrRepository {

    private var strideConfig = StrideConfig()
    private val recentCadences = mutableListOf<Float>()

    // Conversion factor: pixels per centimeter (can be calibrated)
    private val pixelsPerCm = 0.5f

    // Current position tracking
    private var currentX = 0f
    private var currentY = 0f
    private var stepCount = 0

    // ── Error-correction engine ─────────────────────────────────────────
    private val constraintProvider = FloorConstraintProvider()
    private var correctionEngine: StepCorrectionEngine? = null
    private var correctionConfig = CorrectionConfig()

    // ── Building / floor detection ─────────────────────────────────────
    private val buildingDetector = BuildingDetector()

    // ── Stairwell transition ────────────────────────────────────────────
    private val stairDetector = StairwellTransitionDetector()
    private val stairAnimator = StairwellTransitionAnimator()
    private var stairPairs: List<StairPair> = emptyList()
    private var allFloorConstraintData: Map<String, FloorConstraintData> = emptyMap()
    private var lastMotionLabel: String? = null
    private var currentFloorNumber: Float = 1f

    /**
     * The overall PDR tracking state (path, origin, cadence, etc.).
     * Does NOT include heading — see [heading] for that.
     */
    private val _pdrState = MutableStateFlow(PdrState())
    val pdrState: StateFlow<PdrState> = _pdrState.asStateFlow()

    /**
     * Current heading in radians, updated independently from [pdrState]
     * so compass-only changes don't trigger path-related recompositions.
     */
    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading.asStateFlow()

    /**
     * Emits individual step events for observers that need per-step updates.
     */
    private val _stepEvents = MutableStateFlow<StepEvent?>(null)
//    val stepEvents: StateFlow<StepEvent?> = _stepEvents.asStateFlow()

    /**
     * Updates the stride calculation configuration.
     */
    fun updateStrideConfig(config: StrideConfig) {
        strideConfig = config
    }

    // ── Floor constraints ───────────────────────────────────────────────

    /**
     * Loads wall/entrance data for the current floor and (re)creates the
     * correction engine.  Can be called before or after [setOrigin].
     *
     * When called **mid-tracking** (building transition) the existing
     * engine's buffer is flushed so pending steps are committed with the
     * old constraints, then the constraint provider is hot-swapped.  The
     * engine keeps its committed path and current anchor — no position jump.
     */
    fun setFloorConstraints(data: FloorConstraintData) {
        val existingEngine = correctionEngine

        if (existingEngine != null && _pdrState.value.isTracking) {
            // ── Mid-tracking transition: flush buffer, reload walls ──────
            val flushed = existingEngine.flush()
            if (flushed.isNotEmpty()) {
                // Update the visual path so the flush isn't lost
                val currentState = _pdrState.value
                _pdrState.value = currentState.copy(
                    path = existingEngine.visualPath,
                    currentPosition = flushed.last().position
                )
            }
            // Hot-swap the underlying wall/entrance data.
            // The engine already holds a reference to constraintProvider,
            // so future steps will use the new geometry automatically.
            constraintProvider.loadFloor(
                data.floorPlanData,
                data.scale,
                data.rotationDegrees,
                data.offsetX,
                data.offsetY
            )
        } else {
            // ── First-time setup (before tracking or no engine yet) ─────
            constraintProvider.loadFloor(
                data.floorPlanData,
                data.scale,
                data.rotationDegrees,
                data.offsetX,
                data.offsetY
            )
            correctionEngine = StepCorrectionEngine(
                config = correctionConfig,
                constraintProvider = constraintProvider
            )
            // If already tracking, initialise the engine with the current position.
            val state = _pdrState.value
            if (state.isTracking) {
                val anchor = state.currentPosition ?: state.origin ?: return
                correctionEngine?.setOrigin(anchor, _heading.value)
                // Seed the engine's committed path with the existing path
                // so we don't lose history.
            }
        }
    }

    /**
     * Hot-swaps correction parameters without resetting the path.
     */
    fun updateCorrectionConfig(config: CorrectionConfig) {
        correctionConfig = config
        correctionEngine?.updateConfig(config)
    }

    /**
     * Loads pre-transformed campus building boundary data for
     * automatic building/floor detection during tracking.
     */
    fun loadBuildingData(campusBuildings: List<CampusBuilding>) {
        buildingDetector.loadBuildings(campusBuildings)
    }

    /**
     * Supplies pre-computed stair pairs for the entire campus.
     * Called once from HomeScreen when building data loads.
     */
    fun loadStairPairs(pairs: List<StairPair>) {
        stairPairs = pairs
    }

    /**
     * Supplies constraint data for ALL floors so the stairwell transition
     * can load the destination floor's walls/entrances autonomously.
     */
    fun loadAllFloorConstraintData(data: Map<String, FloorConstraintData>) {
        allFloorConstraintData = data
    }

    /**
     * Called by PdrViewModel whenever a new ML motion classification arrives.
     * Feeds the stairwell transition detector.
     */
    fun onMotionLabel(label: String, confidence: Float) {
        lastMotionLabel = label
        stairDetector.onMotionLabel(label, confidence)
    }

    /**
     * Sets the origin point and starts tracking.
     * All positions are stored in campus-wide coordinates.
     *
     * @param origin The starting coordinate in campus-wide space
     * @param currentFloor The floor the user is on (e.g. "floor_1")
     */
    fun setOrigin(origin: Offset, currentFloor: String? = null) {
        currentX = origin.x
        currentY = origin.y
        stepCount = 0
        recentCadences.clear()

        // Initialise correction engine if floor data is already loaded.
        correctionEngine?.setOrigin(origin, _heading.value)

        // Reset stairwell transition state
        stairDetector.reset()
        stairAnimator.reset()
        lastMotionLabel = null
        // Derive floor number from floor ID (e.g. "floor_1" → 1f)
        currentFloorNumber = currentFloor?.removePrefix("floor_")?.toFloatOrNull() ?: 1f

        // Detect the building at the origin position so the first step
        // doesn't see a building "change" (null → real building) that
        // would override the user-selected floor with the building default.
        if (buildingDetector.isLoaded()) {
            buildingDetector.detect(origin)  // sets _currentBuildingId
            buildingDetector.setInitial(buildingDetector.currentBuildingId, currentFloor)
        } else {
            buildingDetector.setInitial(null, currentFloor)
        }

        // Origin point with initial heading
        val originPoint = PathPoint(position = origin, heading = _heading.value)
        val path = listOf(originPoint)

        _pdrState.value = PdrState(
            isTracking = true,
            origin = origin,
            currentPosition = origin,
            path = path,
            cadenceState = CadenceState(),
            currentFloor = currentFloor ?: buildingDetector.currentFloorId,
            currentBuilding = buildingDetector.currentBuildingId
        )
    }

    /**
     * Processes a detected step and calculates the new position.
     * Only works if tracking is active (origin has been set).
     *
     * Handles three modes:
     * - **IDLE**: Normal PDR (correction engine or fallback).
     *   Also checks whether the user is entering a stairwell.
     * - **CLIMBING**: Steps advance the stairwell animation instead of XY
     *   position. No footstep markers are emitted.
     * - **ARRIVED**: Finalises the floor transition (loads new constraints,
     *   resets the correction engine, resumes normal PDR).
     *
     * @param stepIntervalMs Time since last step in milliseconds
     * @param heading Current heading in radians
     * @return The new position, or null if not tracking
     */
    fun processStep(stepIntervalMs: Long, heading: Float): Offset? {
        var currentState = _pdrState.value
        if (!currentState.isTracking || currentState.origin == null) {
            return null
        }

        // ── Handle ARRIVED left over from a previous step ───────────────
        if (stairAnimator.state == StairClimbingState.ARRIVED) {
            val wasWalkingArrival = stairAnimator.arrivalReason == ArrivalReason.WALKING
            val compensationSteps = if (wasWalkingArrival) {
                stairAnimator.preClimbedStepsAtDetection.coerceIn(1, 4)
            } else 0
            finaliseStairTransition(compensationSteps)
            currentState = _pdrState.value
        }

        // ── Handle CANCELLED (turnaround) left over from a previous step
        if (stairAnimator.state == StairClimbingState.CANCELLED) {
            cancelStairTransition()
            currentState = _pdrState.value
        }

        // ── Handle CLIMBING → advance animation ─────────────────────────
        if (stairAnimator.state == StairClimbingState.CLIMBING) {
            processStairStep(heading, currentState)

            // If the step just triggered arrival (walking / 100%),
            // finalise immediately and replay this step as a normal PDR
            // footstep on the new floor so the turn produces a footprint.
            if (stairAnimator.state == StairClimbingState.ARRIVED) {
                val wasWalkingArrival = stairAnimator.arrivalReason == ArrivalReason.WALKING
                val compensationSteps = if (wasWalkingArrival) {
                    stairAnimator.preClimbedStepsAtDetection.coerceIn(1, 4)
                } else 0
                finaliseStairTransition(compensationSteps)
                currentState = _pdrState.value
                return processNormalStep(stepIntervalMs, heading, currentState)
            }

            // Turnaround detected → enters RETURNING; the dot will
            // animate back in subsequent steps (handled below).
            if (stairAnimator.state == StairClimbingState.RETURNING) {
                return processReturnStep(currentState)
            }

            return stairAnimator.currentPosition
        }

        // ── Handle RETURNING → reverse animation back to origin ──────────
        if (stairAnimator.state == StairClimbingState.RETURNING) {
            val pos = processReturnStep(currentState)

            // If the return animation just finished (→ CANCELLED),
            // restore origin floor and replay this step normally.
            if (stairAnimator.state == StairClimbingState.CANCELLED) {
                cancelStairTransition()
                currentState = _pdrState.value
                return processNormalStep(stepIntervalMs, heading, currentState)
            }

            return pos
        }

        // ── Normal IDLE processing ──────────────────────────────────────
        return processNormalStep(stepIntervalMs, heading, currentState)
    }

    /**
     * Normal PDR step processing (IDLE state).
     * After computing the new position, checks if the user is entering a stairwell.
     */
    private fun processNormalStep(
        stepIntervalMs: Long,
        heading: Float,
        currentState: PdrState
    ): Offset {
        // Calculate cadence (steps per second)
        val cadence = if (stepIntervalMs > 0) 1000f / stepIntervalMs else 0f

        // Update cadence average
        recentCadences.add(cadence)
        while (recentCadences.size > strideConfig.cadenceAverageSize) {
            recentCadences.removeAt(0)
        }
        val averageCadence = if (recentCadences.isNotEmpty()) {
            recentCadences.average().toFloat()
        } else {
            0f
        }

        // Calculate dynamic stride length based on cadence and height
        val rawStrideCm = calculateStrideLength(cadence, averageCadence)
        stepCount++

        // ── Route through correction engine if active ───────────────────
        val engine = correctionEngine
        if (engine != null && engine.isActive) {
            // Apply stride calibration from previous snap corrections
            val calibratedCm = rawStrideCm * engine.strideCalibrationFactor
            val strideUnits = calibratedCm * pixelsPerCm

            val result = engine.processStep(heading, strideUnits)

            // Sync internal position tracker with engine output
            currentX = result.correctedCurrentPosition.x
            currentY = result.correctedCurrentPosition.y

            val cadenceState = CadenceState(
                averageCadence = averageCadence,
                lastStrideLengthCm = calibratedCm,
                stepCount = stepCount
            )

            _stepEvents.value = StepEvent(
                strideLengthCm = calibratedCm,
                cadence = cadence,
                position = result.correctedCurrentPosition,
                heading = heading
            )

            // Use the engine's visual path (committed + buffered) so every
            // step shows immediately.  When correction fires the committed
            // tail shifts and the buffer is rebased → footsteps adjust.
            val buildingResult = runBuildingDetection(result.correctedCurrentPosition, currentState)
            _pdrState.value = currentState.copy(
                currentPosition = result.correctedCurrentPosition,
                path = engine.visualPath,
                cadenceState = cadenceState,
                currentFloor = buildingResult?.first ?: currentState.currentFloor,
                currentBuilding = buildingResult?.second ?: currentState.currentBuilding
            )

            // ── Check for stairwell entry ───────────────────────────────
            checkAndStartStairTransition(result.correctedCurrentPosition, heading)

            return result.correctedCurrentPosition
        }

        // ── Fallback: no correction engine (original behaviour) ─────────
        val strideLengthCm = rawStrideCm
        val strideInPixels = strideLengthCm * pixelsPerCm

        val newX = currentX + strideInPixels * sin(heading)
        val newY = currentY - strideInPixels * cos(heading)
        currentX = newX
        currentY = newY
        val newPosition = Offset(newX, newY)

        // Update path with PathPoint including heading at this step
        val newPathPoint = PathPoint(position = newPosition, heading = heading)
        val updatedPath = currentState.path + newPathPoint

        // Update cadence state
        val cadenceState = CadenceState(
            averageCadence = averageCadence,
            lastStrideLengthCm = strideLengthCm,
            stepCount = stepCount
        )

        // Emit step event
        _stepEvents.value = StepEvent(
            strideLengthCm = strideLengthCm,
            cadence = cadence,
            position = newPosition,
            heading = heading
        )

        // Update PDR state (heading is stored separately)
        val buildingResult = runBuildingDetection(newPosition, currentState)
        _pdrState.value = currentState.copy(
            currentPosition = newPosition,
            path = updatedPath,
            cadenceState = cadenceState,
            currentFloor = buildingResult?.first ?: currentState.currentFloor,
            currentBuilding = buildingResult?.second ?: currentState.currentBuilding
        )

        // ── Check for stairwell entry ───────────────────────────────────
        checkAndStartStairTransition(newPosition, heading)

        return newPosition
    }

    /**
     * Processes a step while the user is on stairs (CLIMBING state).
     * The dot slides along the stairwell; no footstep is emitted.
     * The floor plan stays on the origin floor during the animation.
     */
    private fun processStairStep(heading: Float, currentState: PdrState) {
        val pos = stairAnimator.advanceStep(heading, lastMotionLabel)

        // Sync internal trackers
        currentX = pos.x
        currentY = pos.y

        // Don't switch floor during animation — stays on the origin floor.
        // The floor switch happens in finaliseStairTransition() when ARRIVED.
        _pdrState.value = currentState.copy(
            currentPosition = pos,
            isOnStairs = true,
            stairTransitionProgress = stairAnimator.progress,
            stairDestinationFloor = stairAnimator.activeEvent?.destinationFloorId
        )
    }

    /**
     * Processes a step while the user is RETURNING (reverse animation back
     * to the origin floor).  Similar to [processStairStep] but uses
     * [StairwellTransitionAnimator.advanceReturnStep] which decrements
     * progress.
     */
    private fun processReturnStep(currentState: PdrState): Offset {
        val pos = stairAnimator.advanceReturnStep(lastMotionLabel)

        // Sync internal trackers
        currentX = pos.x
        currentY = pos.y

        _pdrState.value = currentState.copy(
            currentPosition = pos,
            isOnStairs = true,
            stairTransitionProgress = stairAnimator.progress,
            stairDestinationFloor = stairAnimator.activeEvent?.destinationFloorId
        )

        return pos
    }

    /**
     * Checks proximity/heading/ML conditions and triggers a stairwell
     * transition if met.  Two independent detection paths:
     *
     * 1. **Spatial + ML** (two-stage): candidate latch near entrance + ML
     *    confirmation within the sliding window.
     * 2. **Sustained ML** (fallback): 3+ consecutive same-direction stair
     *    labels → nearest stairwell by proximity only (no FOV).
     *
     * Both paths produce the same [StairTransitionEvent]; the first one
     * to fire wins.
     */
    private fun checkAndStartStairTransition(position: Offset, heading: Float) {
        if (stairPairs.isEmpty()) return
        if (stairAnimator.state != StairClimbingState.IDLE) return

        // Path 1: Spatial candidate + ML window confirmation
        val isSustainedPath: Boolean
        val event: StairTransitionEvent

        val path1 = stairDetector.checkTransition(
            position = position,
            heading = heading,
            stairPairs = stairPairs,
            currentFloorNumber = currentFloorNumber
        )
        if (path1 != null) {
            event = path1
            isSustainedPath = false
        } else {
            // Path 2: Sustained ML fallback (independent of spatial candidate)
            val path2 = stairDetector.checkSustainedMLTransition(
                position = position,
                stairPairs = stairPairs,
                currentFloorNumber = currentFloorNumber
            ) ?: return
            event = path2
            isSustainedPath = true
        }

        // Transition confirmed — start the animation
        stairAnimator.startTransition(event, heading)
        stairDetector.reset() // prevent re-triggering

        // Flush the correction engine buffer before entering stairs
        correctionEngine?.flush()

        // Snap the user position to the stairwell entrance.  This is
        // especially important for sustained-ML triggers where the user
        // may be some distance from the entrance by the time 3+ labels
        // accumulate.
        if (isSustainedPath) {
            currentX = event.startPosition.x
            currentY = event.startPosition.y
        }

        val currentState = _pdrState.value
        _pdrState.value = currentState.copy(
            currentPosition = if (isSustainedPath) event.startPosition else currentState.currentPosition,
            isOnStairs = true,
            stairTransitionProgress = 0f,
            stairDestinationFloor = event.destinationFloorId
        )
    }

    /**
     * Called when the animator reaches ARRIVED.  Loads the destination
     * floor's constraint data, resets the correction engine, and resumes
     * normal PDR from the stairwell endpoint.
     *
     * @param compensationSteps Number of extra steps to replay on the new
     *        floor to account for ML detection lag.  Only non-zero when
     *        arrival was triggered by "walking" label (the user already
     *        walked past the top of the stairwell).  When arrival is
     *        triggered by a heading turn, no compensation is needed
     *        because the user is exactly at the landing.
     *
     * Uses StairTransitionEvent.endPosition directly as the arrival
     * position.  This is the campus-coordinate endpoint of the StairPair,
     * which matches the stairwell shaft location on the destination floor.
     */
    private fun finaliseStairTransition(compensationSteps: Int = 0) {
        val event = stairAnimator.activeEvent ?: run {
            stairAnimator.finalize()
            return
        }

        val destinationFloorId = event.destinationFloorId
        // Use the StairPair endpoint — already in campus coordinates and
        // known to be at the correct stairwell location.
        val arrivalPosition = event.endPosition

        // Update floor number
        currentFloorNumber = destinationFloorId
            .removePrefix("floor_").toFloatOrNull() ?: currentFloorNumber

        // Load destination floor constraints if available
        val destConstraints = allFloorConstraintData[destinationFloorId]
        if (destConstraints != null) {
            constraintProvider.loadFloor(
                destConstraints.floorPlanData,
                destConstraints.scale,
                destConstraints.rotationDegrees,
                destConstraints.offsetX,
                destConstraints.offsetY
            )
            correctionEngine = StepCorrectionEngine(
                config = correctionConfig,
                constraintProvider = constraintProvider
            )
            correctionEngine?.setOrigin(arrivalPosition, _heading.value)
        }

        // Sync internal position
        currentX = arrivalPosition.x
        currentY = arrivalPosition.y

        // Clear stair state, set new floor, and start a fresh path from
        // the arrival point so old-floor footsteps are dropped.
        val arrivalPoint = PathPoint(position = arrivalPosition, heading = _heading.value)
        val currentState = _pdrState.value
        _pdrState.value = currentState.copy(
            currentPosition = arrivalPosition,
            path = listOf(arrivalPoint),
            isOnStairs = false,
            stairTransitionProgress = 0f,
            stairDestinationFloor = null,
            stairBottomPosition = null,
            stairTopPosition = null,
            currentFloor = destinationFloorId
        )

        // Sync building detector so it doesn't detect a floor "change" on
        // the next step and re-load the old floor's constraints.
        buildingDetector.setInitial(
            buildingDetector.currentBuildingId,
            destinationFloorId
        )

        stairAnimator.finalize()
        // Re-arm the detector so the user can enter another stairwell on
        // the new floor.  Clear stale ML labels to prevent instant triggers.
        stairDetector.reset()

        // ── Walking-arrival compensation ────────────────────────────────
        // When the ML model said "walking" to trigger arrival, the user
        // has already taken a few steps past the top of the stairwell.
        // Replay those steps as normal PDR so the position isn't stuck
        // at the stairwell entrance.
        if (compensationSteps > 0) {
            val heading = _heading.value
            val defaultIntervalMs = 500L  // assume ~2 steps/sec
            for (i in 0 until compensationSteps) {
                processNormalStep(defaultIntervalMs, heading, _pdrState.value)
            }
            Log.d("PdrRepo", "Walking-arrival compensation: replayed $compensationSteps steps")
        }
    }

    /**
     * Called when the animator is CANCELLED (turnaround detected).
     * Snaps the user back to the stairwell entry on the **origin** floor,
     * restores the origin floor's correction engine, and resumes normal PDR.
     * No floor change occurs.
     */
    private fun cancelStairTransition() {
        val event = stairAnimator.activeEvent ?: run {
            stairAnimator.finalize()
            return
        }

        val originFloorId = event.originFloorId
        val returnPosition = event.startPosition

        Log.d("PdrRepo", "cancelStairTransition: returning to $originFloorId " +
                "at (${returnPosition.x}, ${returnPosition.y})")

        // Restore origin floor number
        currentFloorNumber = originFloorId
            .removePrefix("floor_").toFloatOrNull() ?: currentFloorNumber

        // Reload origin floor constraints (they may have been flushed before
        // entering stairs, but the correction engine is still for this floor)
        val originConstraints = allFloorConstraintData[originFloorId]
        if (originConstraints != null) {
            constraintProvider.loadFloor(
                originConstraints.floorPlanData,
                originConstraints.scale,
                originConstraints.rotationDegrees,
                originConstraints.offsetX,
                originConstraints.offsetY
            )
            correctionEngine = StepCorrectionEngine(
                config = correctionConfig,
                constraintProvider = constraintProvider
            )
            correctionEngine?.setOrigin(returnPosition, _heading.value)
        }

        // Sync internal position
        currentX = returnPosition.x
        currentY = returnPosition.y

        // Clear stair state, stay on the origin floor, restart the path
        // from the return position.
        val returnPoint = PathPoint(position = returnPosition, heading = _heading.value)
        val currentState = _pdrState.value
        _pdrState.value = currentState.copy(
            currentPosition = returnPosition,
            path = listOf(returnPoint),
            isOnStairs = false,
            stairTransitionProgress = 0f,
            stairDestinationFloor = null,
            stairBottomPosition = null,
            stairTopPosition = null,
            currentFloor = originFloorId
        )

        stairAnimator.finalize()

        // Re-arm the stair detector so the user can attempt the stairwell
        // again if they change their mind.
        stairDetector.reset()
    }

    /**
     * Updates the current heading without touching PdrState.
     * This avoids copying the entire path list on every compass tick.
     */
    fun updateHeading(heading: Float) {
        _heading.value = heading
    }

    /**
     * Clears the path and stops tracking.
     * Resets all internal state.
     */
    fun clearAndStopTracking() {
        currentX = 0f
        currentY = 0f
        stepCount = 0
        recentCadences.clear()

        // Reset correction engine (discard buffer without flushing)
        correctionEngine?.reset()

        // Reset building detection (but keep loaded building data)
        buildingDetector.setInitial(null, null)

        // Reset stairwell transition
        stairDetector.reset()
        stairAnimator.reset()
        lastMotionLabel = null
        currentFloorNumber = 1f

        _pdrState.value = PdrState(
            isTracking = false,
            origin = null,
            currentPosition = null,
            path = emptyList(),
            cadenceState = CadenceState(),
            currentFloor = null,
            currentBuilding = null
        )
        _heading.value = 0f

        _stepEvents.value = null
    }

    /**
     * Runs building boundary detection on the given position.
     *
     * Only applies floor-constraint swaps when the **building** itself
     * changed (entered/exited).  The building detector only stores one
     * floor per building (the initial floor), so a floor change from a
     * stairwell transition should NOT cause it to reload old constraints.
     *
     * @return A [Pair] of (floorId, buildingId) if a building change was
     *         detected, or `null` if unchanged or building data is not loaded.
     */
    private fun runBuildingDetection(position: Offset, currentState: PdrState): Pair<String?, String?>? {
        if (!buildingDetector.isLoaded()) return null

        val result = buildingDetector.detect(position)
        if (!result.changed) return null

        // Building changed — load the new building's wall constraints.
        if (result.newConstraintData != null) {
            setFloorConstraints(result.newConstraintData)
        }

        // When entering a new building, use the building's default floor.
        // Within the same building, stairwell transitions manage floor changes
        // independently (detect().changed is only true for building changes now).
        return Pair(result.floorId, result.buildingId)
    }

    /**
     * Calculates stride length using a normalized linear gait model.
     * Formula: Stride = Height * (k * Cadence + c)
     * * This version is tuned to prevent "short-stepping" on the map.
     */
    private fun calculateStrideLength(instantCadence: Float, averageCadence: Float): Float {
        val heightCm = strideConfig.heightCm ?: return 0f
        val heightInMeters = heightCm / 100f

        // 1. SMOOTHING: High weight on average cadence to prevent erratic jumps,
        // but enough instant cadence to feel responsive.
        val smoothedCadence = (instantCadence * 0.35f) + (averageCadence * 0.65f)

        // 2. DYNAMIC GAIT SCALING:
        // If cadence is very low (< 1 step/sec), the user is likely hesitant.
        // If cadence is high (> 2.2 steps/sec), they are running.
        val k = strideConfig.kValue
        val c = strideConfig.cValue

        // General Equation: Stride = H * (k * f + c) where f is frequency (cadence)
        var stride = heightInMeters * (k * smoothedCadence + c)

        // 3. CALIBRATION OFFSET:
        // If the user is shorter (like 165cm), the proportional stride often
        // underestimates real-world movement due to hip flexibility.
        if (heightCm < 170f) {
            stride *= 1.05f // 5% boost for shorter users to compensate for sensor lag
        }

        // 4. BIOLOGICAL CLAMPING:
        // Minimum: 0.4m (40cm) - Anything less is a shuffle, not a step.
        // Maximum: 0.85 * Height - Standard limit for human leg extension.
        val minStride = 40f
        val maxStride = heightCm * 0.85f

        return (stride * 100f).coerceIn(minStride, maxStride)
    }

    /* // first calculation with fixed k (kept as requested)
    private fun calculateStrideLength(cadence: Float): Float {
        val heightInMeters = strideConfig.heightCm / 100f
        val stride = heightInMeters * (strideConfig.kValue * cadence + strideConfig.cValue)
        return (stride * 100f).coerceIn(30f, 120f)
    }
    */
}

//first calculation with fixed k
//private fun calculateStrideLength(cadence: Float): Float {
//    // Formula: stride = height * (k * cadence + c)
//    // This gives reasonable stride lengths based on walking speed
//    val heightInMeters = strideConfig.heightCm / 100f
//    val stride = heightInMeters * (strideConfig.kValue * cadence + strideConfig.cValue)
//
//    // Convert to cm and clamp to reasonable range (30-120 cm)
//    return (stride * 100f).coerceIn(30f, 120f)
//}
