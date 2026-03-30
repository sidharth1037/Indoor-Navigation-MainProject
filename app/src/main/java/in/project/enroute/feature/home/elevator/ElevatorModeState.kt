package `in`.project.enroute.feature.home.elevator

import `in`.project.enroute.data.model.Entrance
import `in`.project.enroute.data.model.Room
import androidx.compose.ui.geometry.Offset

/**
 * Information about an elevator room detected near the user's position.
 *
 * @param room The elevator room (label contains "lift" or "elevator")
 * @param entrance The entrance to the elevator room
 * @param roomLabelPosition Campus-wide coordinates of the room label (≈ center of elevator)
 * @param entrancePosition Campus-wide coordinates of the entrance
 * @param buildingId The building containing this elevator
 * @param floorId The floor this elevator info was resolved from
 * @param exitDirection Unit vector from room label → entrance.
 *                      When the user walks in roughly this direction, they are exiting the elevator.
 */
data class ElevatorInfo(
    val room: Room,
    val entrance: Entrance,
    val roomLabelPosition: Offset,
    val entrancePosition: Offset,
    val buildingId: String,
    val floorId: String,
    val exitDirection: Offset
)

/**
 * Represents a floor that has an elevator room, used by the floor picker dialog.
 *
 * @param floorId The floor identifier (e.g. "floor_2")
 * @param floorNumber The numeric floor number (e.g. 2.0f)
 * @param displayLabel Human-readable label (e.g. "Floor 2")
 */
data class ElevatorFloor(
    val floorId: String,
    val floorNumber: Float,
    val displayLabel: String
)

/**
 * Overall state of elevator mode.
 *
 * @param isActive True while the user is "in the elevator" (between pressing
 *                 the elevator mode button and walking out on the target floor)
 * @param targetFloor The floor the user selected as their destination
 * @param elevatorInfo Info about the elevator room on the origin floor
 * @param activationPosition User position when elevator mode was first activated
 * @param activationFloorId User floor when elevator mode was first activated
 * @param phase Current visual phase of the elevator mode lifecycle
 */
data class ElevatorModeState(
    val isActive: Boolean = false,
    val targetFloor: ElevatorFloor? = null,
    val elevatorInfo: ElevatorInfo? = null,
    val activationPosition: Offset? = null,
    val activationFloorId: String? = null,
    val phase: ElevatorPhase = ElevatorPhase.IDLE
)

/**
 * Phases of the elevator mode lifecycle, used to drive UI transitions.
 */
enum class ElevatorPhase {
    /** No elevator mode active. */
    IDLE,
    /** Elevator mode is active — user is "in the elevator". */
    ACTIVE,
    /** User has exited the elevator — showing "Continuing at Floor X" message. */
    COMPLETING,
}
