package `in`.project.enroute.data.model

/**
 * Represents a complete stairwell polygon, built from grouped StairLines.
 * Points are ordered for proper polygon filling.
 */
data class Stairwell(
    val polygonId: Int,
    val points: List<Pair<Float, Float>>,
    val floorsConnected: List<Float> = emptyList()
)
