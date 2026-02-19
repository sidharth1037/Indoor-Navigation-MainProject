package `in`.project.enroute.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a label position on the floor plan.
 */
data class LabelPosition(
    val x: Float,
    val y: Float
)

/**
 * Metadata for a floor plan.
 * Contains rendering configuration and other floor-specific info.
 * This data will come from backend in production.
 */
data class FloorPlanMetadata(
    @SerializedName("building_id")
    val buildingId: String = "",
    val scale: Float,
    val rotation: Float,
    @SerializedName("building_name")
    val buildingName: String = "",
    @SerializedName("label_position")
    val labelPosition: LabelPosition? = null,
    @SerializedName("relative_position")
    val relativePosition: RelativePosition = RelativePosition()
)
