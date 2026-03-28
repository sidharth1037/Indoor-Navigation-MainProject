package `in`.project.enroute.feature.home.elevator

import android.util.Log
import `in`.project.enroute.data.model.Entrance
import `in`.project.enroute.data.model.Room
import `in`.project.enroute.feature.floorplan.state.BuildingState
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Detects elevator rooms across all buildings/floors and checks user proximity.
 *
 * An elevator room is any [Room] whose [Room.name] matches "lift" or "elevator"
 * (case-insensitive). Each elevator room has one or more [Entrance] objects
 * linked to it via [Entrance.name] or [Entrance.roomNo].
 *
 * The detector pre-computes campus-wide positions for both room labels and
 * entrances, then exposes a [findNearbyElevator] method that efficiently
 * checks whether the user is within a configurable threshold of an elevator
 * entrance on their current floor.
 */
object ElevatorDetector {

    private const val TAG = "ElevatorDetector"

    /** Room names considered as elevator rooms (case-insensitive). */
    private val ELEVATOR_NAMES = setOf("lift", "elevator")

    // ── Cached elevator data ──────────────────────────────────────────────

    /** Pre-computed elevator infos across all floors. Keyed by floorId. */
    private var elevatorsByFloor: Map<String, List<ElevatorInfo>> = emptyMap()

    /** All floor IDs (across all buildings) that have at least one elevator room. */
    private var floorsWithElevators: Set<String> = emptySet()

    /**
     * Scans all buildings and floors to locate elevator rooms and their entrances,
     * pre-computing campus-wide positions. Should be called once when campus data
     * loads (or when building states change).
     *
     * @param buildingStates Map of building ID → [BuildingState]
     */
    fun loadElevatorData(buildingStates: Map<String, BuildingState>) {
        val byFloor = mutableMapOf<String, MutableList<ElevatorInfo>>()

        for ((_, buildingState) in buildingStates) {
            val building = buildingState.building
            val relX = building.relativePosition.x
            val relY = building.relativePosition.y

            for ((_, floorData) in buildingState.floors) {
                val meta = floorData.metadata
                val scale = meta.scale
                val rotRad = Math.toRadians(meta.rotation.toDouble()).toFloat()
                val cosA = cos(rotRad)
                val sinA = sin(rotRad)

                // Transform function: floor-local → campus-wide
                val toCampus: (Float, Float) -> Offset = { x, y ->
                    val sx = x * scale
                    val sy = y * scale
                    Offset(
                        sx * cosA - sy * sinA + relX,
                        sx * sinA + sy * cosA + relY
                    )
                }

                // Find elevator rooms on this floor
                val elevatorRooms = floorData.rooms.filter { room ->
                    room.name != null && room.name.trim().lowercase() in ELEVATOR_NAMES
                }

                for (room in elevatorRooms) {
                    // Find entrances linked to this room
                    val entrances = findEntrancesForRoom(room, floorData.entrances)
                    if (entrances.isEmpty()) {
                        Log.w(TAG, "Elevator room '${room.name}' on ${floorData.floorId} has no entrance")
                        continue
                    }

                    for (entrance in entrances) {
                        val roomPos = toCampus(room.x, room.y)
                        val entrPos = toCampus(entrance.x, entrance.y)

                        // Exit direction: unit vector from room center → entrance
                        val dx = entrPos.x - roomPos.x
                        val dy = entrPos.y - roomPos.y
                        val len = sqrt(dx * dx + dy * dy)
                        val exitDir = if (len > 0.001f) Offset(dx / len, dy / len) else Offset(0f, -1f)

                        val info = ElevatorInfo(
                            room = room,
                            entrance = entrance,
                            roomLabelPosition = roomPos,
                            entrancePosition = entrPos,
                            buildingId = building.buildingId,
                            floorId = floorData.floorId,
                            exitDirection = exitDir
                        )

                        byFloor.getOrPut(floorData.floorId) { mutableListOf() }.add(info)
                    }
                }
            }
        }

        elevatorsByFloor = byFloor
        floorsWithElevators = byFloor.keys.toSet()
        Log.d(TAG, "Loaded elevators on ${floorsWithElevators.size} floors: $floorsWithElevators")
    }

    /**
     * Checks if the user is within [thresholdPx] of any elevator entrance on
     * their current floor.
     *
     * @param position Current user position in campus-wide coordinates
     * @param currentFloorId The floor the user is on (e.g. "floor_1")
     * @param thresholdPx Distance threshold in canvas pixels (1.5m ≈ 75px at 0.5 px/cm)
     * @return The closest [ElevatorInfo] if within threshold, null otherwise
     */
    fun findNearbyElevator(
        position: Offset,
        currentFloorId: String?,
        thresholdPx: Float = 75f
    ): ElevatorInfo? {
        if (currentFloorId == null) return null
        val elevators = elevatorsByFloor[currentFloorId] ?: return null

        var closest: ElevatorInfo? = null
        var closestDist = Float.MAX_VALUE

        for (info in elevators) {
            val dx = position.x - info.entrancePosition.x
            val dy = position.y - info.entrancePosition.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist <= thresholdPx && dist < closestDist) {
                closestDist = dist
                closest = info
            }
        }

        return closest
    }

    /**
     * Returns the list of floors that have an elevator room in the specified building.
     * Used by the dialog to populate the floor picker.
     * Excludes the user's current floor.
     *
     * @param buildingId Building to filter by
     * @param excludeFloorId Floor to exclude (user's current floor)
     * @param buildingStates Full building state map to resolve floor numbers
     * @return Ordered list of [ElevatorFloor] for the picker
     */
    fun getElevatorFloors(
        buildingId: String,
        excludeFloorId: String,
        buildingStates: Map<String, BuildingState>
    ): List<ElevatorFloor> {
        val buildingState = buildingStates[buildingId] ?: return emptyList()

        // Floor IDs in this building that have elevators
        val buildingFloorIds = buildingState.floors.values.map { it.floorId }.toSet()
        val elevatorFloorIds = floorsWithElevators.intersect(buildingFloorIds) - excludeFloorId

        return elevatorFloorIds
            .mapNotNull { floorId ->
                val floorNumber = floorId.removePrefix("floor_").toFloatOrNull() ?: return@mapNotNull null
                ElevatorFloor(
                    floorId = floorId,
                    floorNumber = floorNumber,
                    displayLabel = formatFloorLabel(floorNumber)
                )
            }
            .sortedBy { it.floorNumber }
    }

    /**
     * Finds the elevator room label position on a target floor for the same building.
     * Used to reset the user's position when elevator mode ends.
     *
     * @param buildingId The building to search
     * @param targetFloorId The target floor
     * @param buildingStates Full building state map
     * @return Campus-wide position of the elevator room label on the target floor, or null
     */
    fun getElevatorPositionOnFloor(
        buildingId: String,
        targetFloorId: String,
        buildingStates: Map<String, BuildingState>
    ): Offset? {
        val infos = elevatorsByFloor[targetFloorId] ?: return null
        return infos.firstOrNull { it.buildingId == buildingId }?.roomLabelPosition
    }

    /**
     * Gets the ElevatorInfo for a specific floor and building.
     * Used to determine exit direction on the target floor.
     */
    fun getElevatorInfoOnFloor(
        buildingId: String,
        targetFloorId: String
    ): ElevatorInfo? {
        val infos = elevatorsByFloor[targetFloorId] ?: return null
        return infos.firstOrNull { it.buildingId == buildingId }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Finds entrances linked to a room by room number or name.
     * Excludes stair entrances.
     */
    private fun findEntrancesForRoom(room: Room, entrances: List<Entrance>): List<Entrance> {
        val matched = mutableListOf<Entrance>()

        // Match by room number
        if (room.number != null) {
            matched.addAll(
                entrances.filter { e ->
                    e.roomNo != null && e.roomNo == room.number.toString() && !e.isStairs
                }
            )
        }

        // Fallback: match by name
        if (matched.isEmpty() && room.name != null) {
            matched.addAll(
                entrances.filter { e ->
                    e.name != null && e.name.equals(room.name, ignoreCase = true) && !e.isStairs
                }
            )
        }

        return matched
    }

    /**
     * Formats a floor number for display.
     * Examples: 1.0 → "Floor 1", 1.5 → "Floor 1.5", 0.0 → "Floor 0"
     */
    private fun formatFloorLabel(floorNumber: Float): String {
        return if (floorNumber == floorNumber.toInt().toFloat()) {
            "Floor ${floorNumber.toInt()}"
        } else {
            "Floor $floorNumber"
        }
    }
}
