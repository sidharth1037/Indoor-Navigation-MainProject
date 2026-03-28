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
        userPosition: Offset
    ) {
        if (availableFloors.isEmpty()) return

        val dismissed = _uiState.value.dismissedEntrancePosition
        val tooCloseToDismissed = if (dismissed != null) {
            val dx = userPosition.x - dismissed.x
            val dy = userPosition.y - dismissed.y
            sqrt(dx * dx + dy * dy) < DISMISS_RESHOW_DISTANCE_PX
        } else {
            false
        }

        if (tooCloseToDismissed) return

        _uiState.update {
            it.copy(
                nearbyElevatorInfo = info,
                availableFloors = availableFloors,
                showPromptDialog = true
            )
        }
    }

    fun onNoElevatorNearby(userPosition: Offset) {
        val dismissed = _uiState.value.dismissedEntrancePosition ?: return
        val dx = userPosition.x - dismissed.x
        val dy = userPosition.y - dismissed.y
        val movedAway = sqrt(dx * dx + dy * dy) > DISMISS_RESHOW_DISTANCE_PX
        if (movedAway) {
            _uiState.update { it.copy(dismissedEntrancePosition = null) }
        }
    }

    fun dismissPrompt(trackDismissal: Boolean) {
        _uiState.update { state ->
            state.copy(
                showPromptDialog = false,
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

    fun activateMode(info: ElevatorInfo, selectedFloor: ElevatorFloor) {
        completionJob?.cancel()
        _uiState.update {
            it.copy(
                modeState = ElevatorModeState(
                    isActive = true,
                    targetFloor = selectedFloor,
                    elevatorInfo = info,
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
