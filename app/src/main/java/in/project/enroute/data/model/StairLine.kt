package `in`.project.enroute.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a single line segment of a stairwell polygon.
 * Multiple StairLines with the same stair_polygon_id form a complete stairwell.
 */
data class StairLine(
    val type: String,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    @SerializedName("stair_polygon_id")
    val stairPolygonId: Int,
    @SerializedName("floors_connected")
    val floorsConnected: List<Float> = emptyList()
)
