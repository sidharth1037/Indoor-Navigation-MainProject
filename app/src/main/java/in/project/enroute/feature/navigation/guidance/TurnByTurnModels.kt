package `in`.project.enroute.feature.navigation.guidance

import androidx.compose.ui.geometry.Offset
import `in`.project.enroute.feature.navigation.data.MultiFloorPath
import `in`.project.enroute.feature.navigation.data.PathTransition

enum class GuidanceType {
    IDLE,
    REROUTING,
    ALIGN,
    STRAIGHT,
    TURN,
    STAIR,
    ARRIVAL
}

data class TurnByTurnInstruction(
    val text: String,
    val type: GuidanceType,
    val distanceMeters: Int? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class GuidanceInput(
    val path: MultiFloorPath,
    val userPosition: Offset,
    val headingRadians: Float,
    val currentFloorId: String,
    val nowMs: Long,
    val forceRerouteMessage: Boolean = false
)

data class GuidanceConfig(
    val fieldOfViewHalfAngleDeg: Float = 45f,
    val lookaheadPoints: Int = 3,
    val minTurnAngleDeg: Float = 22f,
    val nearTurnDistanceUnits: Float = 90f,
    val stairEarlyDistanceUnits: Float = 200f,
    val stairFinalDistanceUnits: Float = 75f,
    val arrivalDistanceUnits: Float = 60f,
    val throttleMs: Long = 650L,
    val distanceBucketSmallMeters: Int = 1,
    val distanceBucketMediumMeters: Int = 2,
    val distanceBucketLargeMeters: Int = 5,
    val distanceSmallUpperBoundMeters: Int = 3,
    val distanceMediumUpperBoundMeters: Int = 15
)

data class UpcomingTurn(
    val signedAngleDeg: Float,
    val distanceUnits: Float
)

data class UpcomingTransition(
    val transition: PathTransition,
    val distanceUnits: Float
)
