package `in`.project.enroute.data.model

/**
 * Container for all floor plan data for a single floor.
 * Used to pass complete floor data through the system.
 */
data class FloorPlanData(
    val floorId: String,
    val metadata: FloorPlanMetadata,
    val walls: List<Wall> = emptyList(),
    val stairwells: List<Stairwell> = emptyList(),
    val entrances: List<Entrance> = emptyList(),
    val rooms: List<Room> = emptyList(),
    val boundaryPoints: List<BoundaryPoint> = emptyList()
)
