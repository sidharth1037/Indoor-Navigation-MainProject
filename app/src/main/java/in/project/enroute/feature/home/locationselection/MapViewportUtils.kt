package `in`.project.enroute.feature.home.locationselection

import androidx.compose.ui.geometry.Offset
import `in`.project.enroute.data.model.FloorPlanData
import `in`.project.enroute.data.model.Room
import `in`.project.enroute.feature.floorplan.rendering.CanvasState
import `in`.project.enroute.feature.floorplan.state.BuildingState
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Utility methods for evaluating room visibility/focus and fitting world points on screen.
 */
object MapViewportUtils {

    /**
     * Resolve a room's campus-wide coordinates from room-local coordinates.
     */
    fun resolveRoomCampusPosition(
        room: Room,
        buildingStates: Map<String, BuildingState>
    ): Offset? {
        val ownerBuilding = room.buildingId?.let { buildingStates[it] }
            ?: buildingStates.values.firstOrNull { bs ->
                bs.floors.values.any { floor -> floor.rooms.any { it.id == room.id } }
            }
            ?: return null

        val floorData = resolveRoomFloor(room, ownerBuilding) ?: return null
        val relX = ownerBuilding.building.relativePosition.x
        val relY = ownerBuilding.building.relativePosition.y
        val angleRad = Math.toRadians(floorData.metadata.rotation.toDouble()).toFloat()
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        val localX = room.x * floorData.metadata.scale
        val localY = room.y * floorData.metadata.scale

        return Offset(
            x = localX * cosA - localY * sinA + relX,
            y = localX * sinA + localY * cosA + relY
        )
    }

    /**
     * True when a world point is close enough to the screen center to be considered "in focus".
     */
    fun isWorldPointNearScreenCenter(
        worldPoint: Offset,
        canvasState: CanvasState,
        screenWidth: Float,
        screenHeight: Float,
        focusRadiusFraction: Float = 0.24f
    ): Boolean {
        if (screenWidth <= 0f || screenHeight <= 0f) return true

        val screenPoint = worldToScreenCoordinates(
            worldPoint = worldPoint,
            canvasState = canvasState,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )

        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        val radius = minOf(screenWidth, screenHeight) * focusRadiusFraction

        return hypot(screenPoint.x - centerX, screenPoint.y - centerY) <= radius
    }

    /**
     * Compute center + zoom that fits all given world points on screen.
     */
    fun calculateFitBoundsForWorldPoints(
        points: List<Offset>,
        screenWidth: Float,
        screenHeight: Float
    ): Pair<Offset, Float> {
        if (points.isEmpty()) return Pair(Offset.Zero, 1f)
        if (points.size == 1) return Pair(points.first(), 1.3f)

        val centroidX = points.map { it.x }.average().toFloat()
        val centroidY = points.map { it.y }.average().toFloat()

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }

        val minSpan = 320f
        val paddingFactor = 1.75f
        val spanX = maxOf(maxX - minX, minSpan) * paddingFactor
        val spanY = maxOf(maxY - minY, minSpan) * paddingFactor

        val zoomX = screenWidth / spanX
        val zoomY = screenHeight / spanY
        val zoom = minOf(zoomX, zoomY).coerceIn(0.35f, 2.0f)

        return Pair(Offset(centroidX, centroidY), zoom)
    }

    private fun worldToScreenCoordinates(
        worldPoint: Offset,
        canvasState: CanvasState,
        screenWidth: Float,
        screenHeight: Float
    ): Offset {
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f

        val px = (worldPoint.x + centerX) * canvasState.scale
        val py = (worldPoint.y + centerY) * canvasState.scale

        val rotationRad = Math.toRadians(canvasState.rotation.toDouble()).toFloat()
        val cosR = cos(rotationRad)
        val sinR = sin(rotationRad)

        val rx = px * cosR - py * sinR
        val ry = px * sinR + py * cosR

        return Offset(
            x = rx + canvasState.offsetX,
            y = ry + canvasState.offsetY
        )
    }

    private fun resolveRoomFloor(room: Room, buildingState: BuildingState): FloorPlanData? {
        if (room.floorId != null) {
            buildingState.floors.values.firstOrNull { it.floorId == room.floorId }?.let { return it }
        }

        // Fallback by coordinates if floorId is missing.
        return buildingState.floors.values.firstOrNull { floor ->
            floor.rooms.any { it.id == room.id && it.x == room.x && it.y == room.y }
        } ?: buildingState.currentFloorData
    }
}
