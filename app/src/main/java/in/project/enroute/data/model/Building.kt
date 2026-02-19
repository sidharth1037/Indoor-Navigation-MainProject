package `in`.project.enroute.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a building within an institution.
 * Each building has multiple floors and its own boundary polygon for visibility detection.
 */
/**
 * Represents a position offset relative to the campus origin.
 * Used to place buildings at their correct locations on the campus canvas.
 */
data class RelativePosition(
    val x: Float = 0f,
    val y: Float = 0f
)

data class Building(
    @SerializedName("building_id")
    val buildingId: String,
    
    @SerializedName("building_name")
    val buildingName: String,
    
    /**
     * Available floor IDs for this building (e.g., ["floor_1", "floor_1.5", "floor_2"])
     */
    @SerializedName("available_floors")
    val availableFloors: List<String> = emptyList(),
    
    /**
     * Scale factor for rendering this building
     */
    val scale: Float = 1f,
    
    /**
     * Rotation in degrees for rendering this building
     */
    val rotation: Float = 0f,
    
    /**
     * Position where the building label should be displayed
     */
    @SerializedName("label_position")
    val labelPosition: LabelPosition? = null,
    
    /**
     * Position of this building relative to the campus origin (0,0).
     * All building coordinates are offset by this amount when rendering.
     */
    @SerializedName("relative_position")
    val relativePosition: RelativePosition = RelativePosition()
)
