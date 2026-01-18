package `in`.project.enroute.data.model

/**
 * Represents a single boundary point that defines the floor plan boundary polygon.
 */
data class BoundaryPoint(
    val id: Int,
    val x: Float,
    val y: Float,
    val source_point_id: String
)
