package `in`.project.enroute.feature.home.elevator

import androidx.compose.ui.geometry.Offset

/**
 * Ephemeral UI state for elevator mode orchestration.
 * This state is scoped to HomeScreen and intentionally not persisted.
 */
data class ElevatorUiState(
    val modeState: ElevatorModeState = ElevatorModeState(),
    val showPromptDialog: Boolean = false,
    val availableFloors: List<ElevatorFloor> = emptyList(),
    val nearbyElevatorInfo: ElevatorInfo? = null,
    val dismissedEntrancePosition: Offset? = null
)
