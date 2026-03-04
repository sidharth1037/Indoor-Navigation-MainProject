package `in`.project.enroute.feature.pdr.data.repository

import android.util.Log
import androidx.compose.ui.geometry.Offset
import `in`.project.enroute.feature.pdr.correction.CorrectionConfig
import `in`.project.enroute.feature.pdr.correction.FloorConstraintProvider
import `in`.project.enroute.feature.pdr.correction.FloorConstraintData
import `in`.project.enroute.feature.pdr.correction.BuildingDetector
import `in`.project.enroute.feature.pdr.correction.CampusBuilding
import `in`.project.enroute.feature.pdr.correction.StepCorrectionEngine
import `in`.project.enroute.feature.pdr.correction.StairClimbingState
import `in`.project.enroute.feature.pdr.correction.StairwellTransitionAnimator
import `in`.project.enroute.feature.pdr.correction.StairwellTransitionDetector
import `in`.project.enroute.feature.pdr.correction.StairwellZone
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
    private var stairwellZones: List<StairwellZone> = emptyList()
    private var allFloorConstraintData: Map<String, FloorConstraintData> = emptyMap()
    private var lastMotionLabel: String? = null
    private var currentFloorNumber: Float = 1f
    /** Configurable: how many steps back from arrival to find the first compensation step. */
    private var stairLookback: Int = 3
    /** Configurable: how many buffered steps to replay on the new floor. */
    private var stairReplayCount: Int = 3

    /**
     * Ring buffer of steps recorded during CLIMBING.
     * Each entry stores the interval + heading so they can be replayed
     * with real data instead of fake values on arrival.
     */
    private data class StairStepRecord(val stepIntervalMs: Long, val heading: Float)
    private val stairStepBuffer = mutableListOf<StairStepRecord>()

    /**
     * Ring buffer of recent headings for turn-based stride reduction.
     * Each entry stores heading (radians) + step interval so the turn
     * rate (degrees/second) can be computed alongside the cumulative
     * heading change.
     */
    private data class HeadingRecord(val heading: Float, val stepIntervalMs: Long)
    private val recentHeadings = mutableListOf<HeadingRecord>()

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
     * Supplies pre-computed stairwell zones for the entire campus.
     * Called once from HomeScreen when building data loads.
     */
    fun loadStairwellZones(zones: List<StairwellZone>) {
        stairwellZones = zones
    }

    /**
     * Supplies constraint data for ALL floors so the stairwell transition
     * can load the destination floor's walls/entrances autonomously.
     */
    fun loadAllFloorConstraintData(data: Map<String, FloorConstraintData>) {
        allFloorConstraintData = data
    }

    /**
     * Hot-updates stair detection/animation parameters from Settings.
     */
    fun updateStairSettings(
        entryThreshold: Int? = null,
        proximityRadius: Float? = null,
        lookback: Int? = null,
        replayCount: Int? = null
    ) {
        if (entryThreshold != null || proximityRadius != null) {
            stairDetector.updateSettings(
                entryThreshold = entryThreshold,
                proximityRadius = proximityRadius
            )
        }
        if (lookback != null)    stairLookback    = lookback
        if (replayCount != null) stairReplayCount = replayCount
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
        recentHeadings.clear()

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
            finaliseStairTransition()
            currentState = _pdrState.value
        }

        // ── Handle CANCELLED (turnaround) left over from a previous step
        if (stairAnimator.state == StairClimbingState.CANCELLED) {
            cancelStairTransition()
            currentState = _pdrState.value
        }

        // ── Handle CLIMBING → advance animation ─────────────────────────
        if (stairAnimator.state == StairClimbingState.CLIMBING) {
            processStairStep(stepIntervalMs, heading, currentState)

            // If the step just triggered arrival (walking / 100%),
            // finalise immediately and replay this step as a normal PDR
            // footstep on the new floor so the turn produces a footprint.
            if (stairAnimator.state == StairClimbingState.ARRIVED) {
                finaliseStairTransition()
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
        // Capture position before this step for boundary-crossing detection
        val prevPosition = Offset(currentX, currentY)

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

        // Apply turn-based stride reduction (updates heading ring buffer)
        val turnFactor = calculateTurnFactor(heading, stepIntervalMs)
        val adjustedStrideCm = rawStrideCm * turnFactor
        stepCount++

        // ── Route through correction engine if active ───────────────────
        val engine = correctionEngine
        if (engine != null && engine.isActive) {
            // Apply stride calibration from previous snap corrections
            val calibratedCm = adjustedStrideCm * engine.strideCalibrationFactor
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
            checkAndStartStairTransition(prevPosition, result.correctedCurrentPosition, heading)

            return result.correctedCurrentPosition
        }

        // ── Fallback: no correction engine (original behaviour) ─────────
        val strideLengthCm = adjustedStrideCm
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
        checkAndStartStairTransition(prevPosition, newPosition, heading)

        return newPosition
    }

    /**
     * Processes a step while the user is on stairs (CLIMBING state).
     * The dot slides along the stairwell; no footstep is emitted.
     * The floor plan stays on the origin floor during the animation.
     *
     * The step's interval + heading are buffered so they can be replayed
     * as real PDR steps on the new floor at arrival time.
     */
    private fun processStairStep(stepIntervalMs: Long, heading: Float, currentState: PdrState) {
        // Buffer every climbing step for potential compensation replay
        stairStepBuffer.add(StairStepRecord(stepIntervalMs, heading))

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
     * Boundary-based stairwell check.  Three detection modes:
     *  1. User is inside a stairwell polygon AND ML threshold met → immediate.
     *  2. User previously crossed a stairwell boundary (stored crossing) AND
     *     ML later confirms climbing → retroactive transition.
     *  3. User is within proximity radius of a stairwell entry edge AND
     *     ML threshold met → edge-proximity transition.
     *
     * Boundary crossings are recorded on every step via
     * [StairwellTransitionDetector.recordBoundaryCrossings].
     */
    private fun checkAndStartStairTransition(
        prevPosition: Offset,
        position: Offset,
        heading: Float
    ) {
        if (stairwellZones.isEmpty()) return
        if (stairAnimator.state != StairClimbingState.IDLE) return

        // Record any boundary crossings for deferred detection
        stairDetector.recordBoundaryCrossings(
            prevPos = prevPosition,
            newPos = position,
            heading = heading,
            zones = stairwellZones,
            currentFloorNumber = currentFloorNumber
        )

        val event = stairDetector.checkTransition(
            position = position,
            heading = heading,
            zones = stairwellZones,
            currentFloorNumber = currentFloorNumber
        ) ?: return

        // Transition confirmed — start the animation
        stairStepBuffer.clear()
        stairAnimator.startTransition(event, heading)
        stairDetector.reset()

        // Flush the correction engine buffer before entering stairs
        correctionEngine?.flush()

        // Snap position to the projected entry point
        currentX = event.startPosition.x
        currentY = event.startPosition.y

        val currentState = _pdrState.value
        _pdrState.value = currentState.copy(
            currentPosition = event.startPosition,
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
     * ## Step-buffer compensation
     * During CLIMBING every step's (intervalMs, heading) is recorded in
     * [stairStepBuffer].  On arrival the most recent steps are replayed
     * through [processNormalStep] so that the user dot "catches up" to
     * where the user actually is on the new floor.
     *
     * Two tunable settings control the replay:
     *  - **[stairLookback]**: how many positions back from the end of the
     *    buffer to start compensation (finds the first walking step).
     *  - **[stairReplayCount]**: how many steps from that point to replay.
     *
     * Example: buffer=[1,2,3,4,5,6,7,8], lookback=3, replay=3
     *   → start at index 8−3=5, replay steps 6,7,8 with real heading/interval.
     */
    private fun finaliseStairTransition() {
        val event = stairAnimator.activeEvent ?: run {
            stairAnimator.finalize()
            return
        }

        val destinationFloorId = event.destinationFloorId
        val arrivalPosition = event.endPosition
        val isSameFloor = event.isSameFloor

        // ── Same-floor stairwell (sub-levels like 0.3↔0.6) ─────────
        // No floor number change, no constraint reload, no floor switch.
        // Just resume normal PDR from the arrival position.
        if (!isSameFloor) {
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
        } else {
            // Same-floor: just re-anchor the correction engine at the
            // arrival position without reloading constraints.
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
            currentFloor = if (isSameFloor) currentState.currentFloor else destinationFloorId
        )

        // Sync building detector so it doesn't detect a floor "change" on
        // the next step and re-load the old floor's constraints.
        if (!isSameFloor) {
            buildingDetector.setInitial(
                buildingDetector.currentBuildingId,
                destinationFloorId
            )
        }

        stairAnimator.finalize()
        stairDetector.reset()

        // ── Step-buffer compensation ────────────────────────────────────
        // Clear turn heading buffer so the first compensation steps on the
        // new floor aren't penalised for the heading difference from
        // pre-stairwell walking.
        recentHeadings.clear()

        // Replay the tail of the climbing step buffer using real heading
        // and interval data, so the user dot catches up to where the user
        // actually is on the new floor.
        if (stairStepBuffer.isNotEmpty() && stairReplayCount > 0) {
            val bufSize = stairStepBuffer.size
            // The start index is (bufSize - lookback), clamped to [0, bufSize)
            val startIdx = (bufSize - stairLookback).coerceIn(0, bufSize)
            // The number of steps we can actually replay from startIdx
            val count = stairReplayCount.coerceAtMost(bufSize - startIdx)

            if (count > 0) {
                val stepsToReplay = stairStepBuffer.subList(startIdx, startIdx + count)
                for (record in stepsToReplay) {
                    processNormalStep(record.stepIntervalMs, record.heading, _pdrState.value)
                }
                Log.d("PdrRepo", "Compensation: replayed $count steps " +
                        "(buf=$bufSize, lookback=$stairLookback, " +
                        "startIdx=$startIdx, replayCount=$stairReplayCount)")
            }
        }
        stairStepBuffer.clear()
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
        stairStepBuffer.clear()
        recentHeadings.clear()
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
        recentHeadings.clear()

        // Reset correction engine (discard buffer without flushing)
        correctionEngine?.reset()

        // Reset building detection (but keep loaded building data)
        buildingDetector.setInitial(null, null)

        // Reset stairwell transition
        stairDetector.reset()
        stairAnimator.reset()
        stairStepBuffer.clear()
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
     * Computes a stride-reduction factor (0.3–1.0) based on how sharply
     * and how quickly the user is turning.
     *
     * Uses the [recentHeadings] ring buffer (sized by [StrideConfig.turnWindow]).
     * If the cumulative heading change over the window exceeds the
     * [StrideConfig.turnThreshold], a reduction is applied proportional
     * to both the angle excess and the turn speed (faster turns ⇒ more
     * reduction).  [StrideConfig.turnSensitivity] scales the overall
     * effect.
     *
     * @param heading   Current heading in radians.
     * @param stepIntervalMs  Time since last step.
     * @return Factor in [0.3, 1.0] to multiply against the raw stride.
     */
    private fun calculateTurnFactor(heading: Float, stepIntervalMs: Long): Float {
        // Record the current step
        recentHeadings.add(HeadingRecord(heading, stepIntervalMs))
        while (recentHeadings.size > strideConfig.turnWindow) {
            recentHeadings.removeAt(0)
        }

        // Need at least 2 headings to detect a turn
        if (recentHeadings.size < 2) return 1f

        // Cumulative heading change across the window (in degrees).
        // Each pair wraps to the shortest arc (±180°) so it works across
        // the 0/2π boundary.
        var totalAngleDeg = 0f
        var totalTimeMs = 0L
        for (i in 1 until recentHeadings.size) {
            val prev = recentHeadings[i - 1].heading
            val curr = recentHeadings[i].heading
            var delta = curr - prev               // radians
            // Wrap to [-π, π]
            while (delta > Math.PI)  delta -= (2 * Math.PI).toFloat()
            while (delta < -Math.PI) delta += (2 * Math.PI).toFloat()
            totalAngleDeg += Math.toDegrees(kotlin.math.abs(delta).toDouble()).toFloat()
            totalTimeMs += recentHeadings[i].stepIntervalMs
        }

        val threshold = strideConfig.turnThreshold
        if (totalAngleDeg < threshold) return 1f

        // ── Angle excess: 0 at threshold, 1 at 180° ─────────────────
        val angleExcess = ((totalAngleDeg - threshold) / (180f - threshold))
            .coerceIn(0f, 1f)

        // ── Speed factor: shorter step intervals ⇒ faster turn ──────
        val windowSize = recentHeadings.size - 1   // number of intervals
        val avgStepTimeSec = (totalTimeMs.toFloat() / windowSize / 1000f)
            .coerceAtLeast(0.15f)             // floor to avoid divide-by-zero
        // At 0.5 s/step (normal walk) speedFactor ≈ 1.0;
        // at 0.25 s/step speedFactor ≈ 2.0 (very fast turn).
        val speedFactor = (0.5f / avgStepTimeSec).coerceIn(0.5f, 2.0f)

        // ── Combined reduction ──────────────────────────────────────
        val reduction = strideConfig.turnSensitivity * angleExcess * speedFactor
        return (1f - reduction).coerceIn(0.3f, 1f)
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

        // 2. HEIGHT-DEPENDENT K:
        // Taller users need a higher K (longer stride per cadence unit),
        // shorter users need a lower K.  The influence parameter controls
        // how strongly height deviations from 170 cm affect K.
        val baseK = strideConfig.kValue
        val effectiveK = baseK + strideConfig.heightKInfluence * (heightCm - 170f) / 100f
        val c = strideConfig.cValue

        // General Equation: Stride = H * (k * f + c) where f is frequency (cadence)
        val stride = heightInMeters * (effectiveK * smoothedCadence + c)

        // 3. BIOLOGICAL CLAMPING:
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
