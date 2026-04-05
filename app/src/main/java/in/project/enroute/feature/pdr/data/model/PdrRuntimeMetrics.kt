package `in`.project.enroute.feature.pdr.data.model

/**
 * Lightweight runtime telemetry for PDR processing lanes.
 * Kept in UI state so debug surfaces can observe realtime health.
 */
data class PdrRuntimeMetrics(
    val motionLabelLagMs: Long = 0L,
    val stepQueueDepth: Int = 0,
    val maxStepQueueDepth: Int = 0,
    val lastStepQueueAgeMs: Long = 0L,
    val lastStepProcessingMs: Long = 0L
)
