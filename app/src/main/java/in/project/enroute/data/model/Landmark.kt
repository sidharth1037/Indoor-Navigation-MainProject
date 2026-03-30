package `in`.project.enroute.data.model

/**
 * Represents a landmark (point of interest) placed on the campus map by an admin.
 *
 * Stored in Firestore at: campuses/{campusId}/landmarks/{id}
 *
 * @param id Firestore document ID (auto-generated on first save)
 * @param name Display name of the landmark (e.g., "Main Canteen")
 * @param icon Material icon identifier (e.g., "restaurant", "coffee")
 * @param x X coordinate in floor-plan space (raw, pre-transform)
 * @param y Y coordinate in floor-plan space (raw, pre-transform)
 * @param floorId Floor this landmark belongs to (e.g., "floor_1")
 * @param buildingId Building this landmark belongs to (e.g., "building_1")
 * @param campusX Campus-wide X coordinate (post-transform, used for rendering & pathfinding)
 * @param campusY Campus-wide Y coordinate (post-transform, used for rendering & pathfinding)
 */
data class Landmark(
    val id: String = "",
    val name: String = "",
    val icon: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val floorId: String = "",
    val buildingId: String = "",
    val campusX: Float = 0f,
    val campusY: Float = 0f
)
