package `in`.project.enroute.feature.pdr.correction

import kotlin.math.abs

/**
 * Detects turns by analysing heading changes across the step buffer
 * (and optionally a few recently committed steps for extra context).
 *
 * A turn is flagged when:
 *  • any single inter-step heading change ≥ [CorrectionConfig.turnDetectionThreshold], **or**
 *  • the cumulative heading change across the analysis window ≥ that threshold.
 */
class TurnDetector(private val config: CorrectionConfig = CorrectionConfig()) {

    /**
     * Scans the given steps for a turn.
     *
     * @param bufferSteps     Current raw buffer (oldest first).
     * @param recentCommitted A few recently committed raw-steps used as
     *                        leading context (oldest first). May be empty.
     * @return A [TurnEvent] if a turn was detected; `null` otherwise.
     */
    fun detect(
        bufferSteps: List<RawStep>,
        recentCommitted: List<RawStep> = emptyList()
    ): TurnEvent? {

        if (bufferSteps.size < 2) return null

        // Build a wider analysis window: up to 2 committed + full buffer
        val context = recentCommitted.takeLast(2)
        val allSteps = context + bufferSteps

        if (allSteps.size < 2) return null

        // ── Find the sharpest single-step heading change ────────────────────
        var maxSingleDelta = 0f
        var sharpIndex = -1
        var preHeading = allSteps.first().heading
        var postHeading = allSteps.first().heading

        for (i in 1 until allSteps.size) {
            val delta = abs(
                GeometryUtils.angleDifference(allSteps[i - 1].heading, allSteps[i].heading)
            )
            if (delta > maxSingleDelta) {
                maxSingleDelta = delta
                sharpIndex = i
                preHeading = allSteps[i - 1].heading
                postHeading = allSteps[i].heading
            }
        }

        // ── Also check total cumulative change ──────────────────────────────
        val totalChange = abs(
            GeometryUtils.angleDifference(allSteps.first().heading, allSteps.last().heading)
        )

        val isTurn = maxSingleDelta >= config.turnDetectionThreshold ||
                totalChange >= config.turnDetectionThreshold

        if (!isTurn || sharpIndex < 0) return null

        // Map the detected index back into the *buffer* coordinate system.
        val contextCount = context.size
        val bufferIndex = (sharpIndex - contextCount).coerceIn(0, bufferSteps.lastIndex)

        return TurnEvent(
            bufferIndex = bufferIndex,
            preHeading = preHeading,
            postHeading = postHeading,
            headingDelta = GeometryUtils.angleDifference(preHeading, postHeading),
            approximatePosition = bufferSteps[bufferIndex].position
        )
    }
}
