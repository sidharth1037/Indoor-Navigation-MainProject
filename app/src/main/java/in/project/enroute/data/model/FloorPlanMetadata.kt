package `in`.project.enroute.data.model

/**
 * Metadata for a floor plan.
 * Contains rendering configuration and other floor-specific info.
 * This data will come from backend in production.
 */
data class FloorPlanMetadata(
    val floorId: String,
    val scale: Float,
    val rotation: Float,
    val stairwellColor: String = "#ADD8E6" // Light blue by default
)
