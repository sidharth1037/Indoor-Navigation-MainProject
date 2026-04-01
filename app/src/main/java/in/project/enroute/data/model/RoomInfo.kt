package `in`.project.enroute.data.model

/**
 * Metadata for a room: description and tags (people names, object names, etc.).
 * Stored in Firestore under `campuses/{campusId}/room_info/{docId}`.
 *
 * Document ID format: "{buildingId}_{floorId}_{roomId}"
 * Example: "building_1_floor_1_42"
 */
data class RoomInfo(
    val buildingId: String = "",
    val floorId: String = "",
    val roomId: Int = 0,
    val description: String = "",
    val tags: List<String> = emptyList()
)
