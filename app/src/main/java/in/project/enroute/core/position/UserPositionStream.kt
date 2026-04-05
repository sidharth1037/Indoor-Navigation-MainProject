package `in`.project.enroute.core.position

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.StateFlow

/**
 * One-way contract for publishing realtime user movement state.
 * Producers (PDR) publish; consumers (navigation, guidance) observe.
 */
interface UserPositionStream {
    val userPosition: StateFlow<Offset?>
    val currentFloorId: StateFlow<String?>
    val headingRadians: StateFlow<Float>
    val isTracking: StateFlow<Boolean>
}
