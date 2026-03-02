package `in`.project.enroute.feature.pdr.correction

import androidx.compose.ui.geometry.Offset
import `in`.project.enroute.feature.pdr.data.model.PathPoint
import kotlin.math.cos
import kotlin.math.sin

/**
 * Orchestrates the full PDR error-correction pipeline.
 *
 * ```
 * Raw step ─► [Buffer (N steps)] ─► Turn Detection
 *                                        │
 *                                   Entrance Snap (if turn)
 *                                        │
 *                                   Wall Constraint
 *                                        │
 *                                   Commit oldest ─► committedPath
 * ```
 *
 * The visual path intentionally lags by [CorrectionConfig.bufferSize] steps so
 * that the pipeline can look ahead (across the buffer) before finalising
 * positions.
 */
class StepCorrectionEngine(
    private var config: CorrectionConfig = CorrectionConfig(),
    private val constraintProvider: FloorConstraintProvider = FloorConstraintProvider(),
    private var wallConstraint: WallConstraint = WallConstraint(config),
    private var turnDetector: TurnDetector = TurnDetector(config),
    private var entranceSnapper: EntranceSnapper = EntranceSnapper(config)
) {

    // ── State ───────────────────────────────────────────────────────────────

    /** Steps waiting to be committed. */
    private val rawBuffer = mutableListOf<RawStep>()

    /** Finalised, corrected path (includes the origin as first element). */
    private val _committedPath = mutableListOf<PathPoint>()
//    val committedPath: List<PathPoint> get() = _committedPath

    /**
     * Full visual path: committed (corrected) steps + speculative (buffered) steps.
     * Use this for rendering so every step shows immediately. When the buffer
     * commits/corrects, the tail adjusts automatically.
     */
    val visualPath: List<PathPoint>
        get() = _committedPath + rawBuffer.map { step ->
            PathPoint(position = step.position, heading = step.heading)
        }

    /** Anchor for computing the next raw position. */
    private var lastCommittedPosition: Offset? = null

    /** Small sliding window of recently committed raw-steps for turn-detection context. */
    private val recentCommitted = mutableListOf<RawStep>()

    /** Heading correction accumulated from wall slides. */
    private var accumulatedHeadingCorrection: Float = 0f

    /** Latest stride calibration factor derived from entrance snapping. */
    private var _strideCalibrationFactor: Float = 1f
    val strideCalibrationFactor: Float get() = _strideCalibrationFactor

    // ── Lifecycle ───────────────────────────────────────────────────────────

    /** `true` when floor data has been loaded. */
    val isActive: Boolean get() = constraintProvider.isLoaded()

    /**
     * Initialises the engine with a starting position.
     * Must be called before [processStep].
     */
    fun setOrigin(origin: Offset, heading: Float) {
        rawBuffer.clear()
        _committedPath.clear()
        recentCommitted.clear()
        accumulatedHeadingCorrection = 0f
        _strideCalibrationFactor = 1f
        lastCommittedPosition = origin
        _committedPath.add(PathPoint(position = origin, heading = heading))
    }

    /** Clears all state. */
    fun reset() {
        rawBuffer.clear()
        _committedPath.clear()
        recentCommitted.clear()
        lastCommittedPosition = null
        accumulatedHeadingCorrection = 0f
        _strideCalibrationFactor = 1f
    }

    /**
     * Replaces the live configuration.
     * Sub-components are recreated with the new values; existing state
     * (buffer, committed path) is preserved.
     */
    fun updateConfig(newConfig: CorrectionConfig) {
        config = newConfig
        wallConstraint = WallConstraint(newConfig)
        turnDetector = TurnDetector(newConfig)
        entranceSnapper = EntranceSnapper(newConfig)
    }

    // ── Main pipeline ───────────────────────────────────────────────────────

    /**
     * Feeds one step into the correction pipeline.
     *
     * Position is computed internally from
     * `lastAnchor + stride × correctedHeading`.
     *
     * @param heading          Sensor heading (radians, 0 = North, CW positive).
     * @param strideLengthUnits Stride length in campus-wide coordinate units.
     * @return                 [CorrectionResult] with committed points and
     *                         current position.
     */
    fun processStep(heading: Float, strideLengthUnits: Float): CorrectionResult {

        // ── Compute raw position from anchor ────────────────────────────────
        val correctedHeading = heading + accumulatedHeadingCorrection
        val anchor = rawBuffer.lastOrNull()?.position
            ?: lastCommittedPosition
            ?: return passThrough(heading)

        val rawPosition = Offset(
            anchor.x + strideLengthUnits * sin(correctedHeading),
            anchor.y - strideLengthUnits * cos(correctedHeading)
        )

        rawBuffer.add(
            RawStep(
                position = rawPosition,
                heading = correctedHeading,
                strideLengthUnits = strideLengthUnits
            )
        )

        // ── Buffer not full yet? Just return the live position. ─────────────
        if (rawBuffer.size < config.bufferSize) {
            return CorrectionResult(
                newCommittedPoints = emptyList(),
                correctedCurrentPosition = rawPosition,
                headingCorrection = accumulatedHeadingCorrection,
                strideCalibrationFactor = _strideCalibrationFactor
            )
        }

        // ── 1. Turn detection ───────────────────────────────────────────────
        val turnEvent = turnDetector.detect(rawBuffer, recentCommitted)

        // ── 2. Entrance snap (only when a turn is detected) ─────────────────
        if (turnEvent != null) {
            val turnStep = rawBuffer[turnEvent.bufferIndex]
            val snapResult = entranceSnapper.snap(
                turnPosition = turnStep.position,
                preHeading = turnEvent.preHeading,
                strideLengthUnits = turnStep.strideLengthUnits,
                constraintProvider = constraintProvider
            )
            if (snapResult.wasSnapped) {
                applySnapCorrection(turnEvent.bufferIndex, snapResult)
                // Blend calibration toward the new factor subtly
                // (90% keep / 10% new) so each snap nudges rather than jumps.
                _strideCalibrationFactor = _strideCalibrationFactor * 0.9f +
                        snapResult.strideCalibrationFactor * 0.1f
            }
        }

        // ── 3. Wall constraint on the oldest buffered step ──────────────────
        val oldest = rawBuffer.first()
        val previousPos = lastCommittedPosition ?: oldest.position
        val nearbyWalls = constraintProvider.getWallsNear(
            oldest.position, config.wallSearchRadius
        )
        val wallResult = wallConstraint.constrain(previousPos, oldest.position, nearbyWalls)

        if (wallResult.wasConstrained) {
            accumulatedHeadingCorrection += wallResult.headingCorrection
        }

        // ── 4. Commit the oldest step ───────────────────────────────────────
        val committedPoint = PathPoint(
            position = wallResult.constrainedPosition,
            heading = oldest.heading
        )
        _committedPath.add(committedPoint)
        lastCommittedPosition = wallResult.constrainedPosition

        // Keep a small trailing window for turn-detection context
        recentCommitted.add(oldest)
        while (recentCommitted.size > config.bufferSize * 2) {
            recentCommitted.removeAt(0)
        }

        rawBuffer.removeAt(0)

        // ── 5. Rebase remaining buffer from the new anchor ──────────────────
        rebaseBuffer(wallResult.constrainedPosition)

        return CorrectionResult(
            newCommittedPoints = listOf(committedPoint),
            correctedCurrentPosition = rawBuffer.lastOrNull()?.position
                ?: wallResult.constrainedPosition,
            headingCorrection = accumulatedHeadingCorrection,
            strideCalibrationFactor = _strideCalibrationFactor
        )
    }

    /**
     * Commits every remaining buffered step (with wall constraint).
     * Call when tracking stops to avoid losing the tail.
     */
    fun flush(): List<PathPoint> {
        val flushed = mutableListOf<PathPoint>()
        while (rawBuffer.isNotEmpty()) {
            val step = rawBuffer.removeAt(0)
            val prev = lastCommittedPosition ?: step.position
            val walls = constraintProvider.getWallsNear(step.position, config.wallSearchRadius)
            val result = wallConstraint.constrain(prev, step.position, walls)

            val point = PathPoint(position = result.constrainedPosition, heading = step.heading)
            _committedPath.add(point)
            lastCommittedPosition = result.constrainedPosition
            flushed.add(point)
        }
        return flushed
    }

    // ── Internals ───────────────────────────────────────────────────────────

    /**
     * Distributes a snap correction backwards over recently committed steps
     * (gradual ramp). Post-turn buffer steps are NOT shifted here because
     * [rebaseBuffer] will recompute them from the corrected anchor later.
     */
    private fun applySnapCorrection(turnBufferIndex: Int, snapResult: SnapResult) {
        val delta = snapResult.correctionDelta

        // Move the turn step to the snapped position
        rawBuffer[turnBufferIndex] = rawBuffer[turnBufferIndex].copy(
            position = snapResult.snappedPosition
        )

        // Retroactive smoothing on committed path (skip origin at index 0)
        val smoothCount = config.retroactiveSmoothSteps
            .coerceAtMost(_committedPath.size - 1)

        if (smoothCount > 0) {
            val startIdx = _committedPath.size - smoothCount
            for (i in startIdx until _committedPath.size) {
                val fraction = (i - startIdx + 1).toFloat() / (smoothCount + 1)
                val original = _committedPath[i]
                _committedPath[i] = original.copy(
                    position = Offset(
                        original.position.x + delta.x * fraction,
                        original.position.y + delta.y * fraction
                    )
                )
            }
        }

        // Rebase post-turn buffer steps from the snapped turn position
        // so they chain correctly without double-applying the delta.
        if (turnBufferIndex < rawBuffer.lastIndex) {
            var anchor = snapResult.snappedPosition
            for (i in (turnBufferIndex + 1) until rawBuffer.size) {
                val step = rawBuffer[i]
                val newPos = Offset(
                    anchor.x + step.strideLengthUnits * sin(step.heading),
                    anchor.y - step.strideLengthUnits * cos(step.heading)
                )
                rawBuffer[i] = step.copy(position = newPos)
                anchor = newPos
            }
        }
    }

    /**
     * Recomputes positions for every buffered step starting from [newAnchor].
     * Called after the oldest step is committed (and wall-constrained) to keep
     * the buffer consistent with the new anchor.
     */
    private fun rebaseBuffer(newAnchor: Offset) {
        if (rawBuffer.isEmpty()) return
        var anchor = newAnchor
        for (i in rawBuffer.indices) {
            val step = rawBuffer[i]
            val newPos = Offset(
                anchor.x + step.strideLengthUnits * sin(step.heading),
                anchor.y - step.strideLengthUnits * cos(step.heading)
            )
            rawBuffer[i] = step.copy(position = newPos)
            anchor = newPos
        }
    }

    /**
     * Fallback when the engine has no origin yet — commits the step directly.
     */
    private fun passThrough(heading: Float): CorrectionResult {
        val pos = lastCommittedPosition ?: Offset.Zero
        val point = PathPoint(position = pos, heading = heading)
        _committedPath.add(point)
        lastCommittedPosition = pos
        return CorrectionResult(
            newCommittedPoints = listOf(point),
            correctedCurrentPosition = pos,
            headingCorrection = 0f,
            strideCalibrationFactor = 1f
        )
    }
}
