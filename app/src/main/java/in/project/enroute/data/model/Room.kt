package `in`.project.enroute.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a room with a center point for label display.
 */
data class Room(
    val id: Int,
    val x: Float,
    val y: Float,
    val number: Int? = null,
    val name: String? = null,
    @SerializedName("point_ids")
    val pointIds: List<Int> = emptyList(),
    /** Floor this room belongs to (e.g. "floor_1"). Set after loading, not from JSON. */
    @Transient
    val floorId: String? = null,
    
    @Transient
    val buildingId: String? = null
)
