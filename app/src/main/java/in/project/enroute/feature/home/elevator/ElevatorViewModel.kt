package `in`.project.enroute.feature.home.elevator

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class ElevatorViewModel : ViewModel() {

    companion object {
        private const val DISMISS_RESHOW_DISTANCE_PX = 150f
    }

    private val _uiState = MutableStateFlow(ElevatorUiState())
    val uiState: StateFlow<ElevatorUiState> = _uiState.asStateFlow()

    private var completionJob: Job? = null

    fun onNearbyElevatorDetected(
        info: ElevatorInfo,
        availableFloors: List<ElevatorFloor>,
        _userPosition: Offset
    ) {
        if (availableFloors.isEmpty()) return

        _uiState.update {
            it.copy(
                nearbyElevatorInfo = info,
                availableFloors = availableFloors,
                showPromptDialog = false
            )
        }
    }

    fun onNoElevatorNearby(userPosition: Offset) {
        val dismissed = _uiState.value.dismissedEntrancePosition
        val movedAway = if (dismissed != null) {
            val dx = userPosition.x - dismissed.x
            val dy = userPosition.y - dismissed.y
            sqrt(dx * dx + dy * dy) > DISMISS_RESHOW_DISTANCE_PX
        } else {
            false
        }
        _uiState.update {
            it.copy(
                nearbyElevatorInfo = null,
                availableFloors = emptyList(),
                dismissedEntrancePosition = if (movedAway) null else it.dismissedEntrancePosition
            )
        }
    }

    fun dismissPrompt(trackDismissal: Boolean) {
        _uiState.update { state ->
            state.copy(
                showPromptDialog = false,
                nearbyElevatorInfo = state.nearbyElevatorInfo,
                availableFloors = state.availableFloors,
                dismissedEntrancePosition = if (trackDismissal) {
                    state.nearbyElevatorInfo?.entrancePosition
                } else {
                    state.dismissedEntrancePosition
                }
            )
        }
    }

    fun openFloorPrompt(availableFloors: List<ElevatorFloor>) {
        if (availableFloors.isEmpty()) return
        _uiState.update {
            it.copy(
                availableFloors = availableFloors,
                showPromptDialog = true,
                dismissedEntrancePosition = null
            )
        }
    }

    fun activateMode(
        info: ElevatorInfo,
        selectedFloor: ElevatorFloor,
        activationPosition: Offset,
        activationFloorId: String
    ) {
        completionJob?.cancel()
        _uiState.update {
            it.copy(
                modeState = ElevatorModeState(
                    isActive = true,
                    targetFloor = selectedFloor,
                    elevatorInfo = info,
                    activationPosition = activationPosition,
                    activationFloorId = activationFloorId,
                    phase = ElevatorPhase.ACTIVE
                ),
                showPromptDialog = false,
                dismissedEntrancePosition = null
            )
        }
    }

    fun updateTargetFloor(selectedFloor: ElevatorFloor) {
        completionJob?.cancel()
        _uiState.update {
            it.copy(
                modeState = it.modeState.copy(
                    targetFloor = selectedFloor,
                    phase = ElevatorPhase.ACTIVE
                ),
                showPromptDialog = false,
                dismissedEntrancePosition = null
            )
        }
    }

    fun cancelMode() {
        completionJob?.cancel()
        _uiState.update {
            it.copy(
                modeState = ElevatorModeState(),
                showPromptDialog = false
            )
        }
    }

    fun triggerCompletion() {
        val state = _uiState.value.modeState
        if (!state.isActive || state.phase != ElevatorPhase.ACTIVE) return

        completionJob?.cancel()
        _uiState.update {
            it.copy(modeState = it.modeState.copy(phase = ElevatorPhase.COMPLETING))
        }
        completionJob = viewModelScope.launch {
            delay(2000)
            _uiState.update {
                it.copy(modeState = ElevatorModeState())
            }
        }
    }

    fun resetState() {
        completionJob?.cancel()
        _uiState.value = ElevatorUiState()
    }
}
