package `in`.project.enroute.feature.floorplan.rendering

import android.graphics.drawable.VectorDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import `in`.project.enroute.data.model.Room
import `in`.project.enroute.feature.floorplan.FloorPlanViewConstants
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawBuildingName
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawPin
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawRoomLabels
import `in`.project.enroute.feature.floorplan.state.BuildingState

/**
 * Overlay that renders room labels, building names, and the search pin
 * on a separate [Canvas] above the navigation path layer.
 *
 * Kept separate from [FloorPlanCanvas] so the navigation path can be
 * sandwiched between the base floor plan artwork and these labels/pins,
 * while each layer recomposes independently for best performance.
 *
 * Applies the same [graphicsLayer] transform as [FloorPlanCanvas].
 */
@Composable
fun FloorPlanLabelsOverlay(
    buildingStates: Map<String, BuildingState>,
    canvasState: CanvasState,
    modifier: Modifier = Modifier,
    displayConfig: FloorPlanDisplayConfig = FloorPlanDisplayConfig(),
    pinnedRoom: Room? = null,
    pinDrawable: VectorDrawable? = null,
    pinTintColor: Int = android.graphics.Color.BLACK
) {
    if (buildingStates.isEmpty()) return

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer(
                scaleX = canvasState.scale,
                scaleY = canvasState.scale,
                rotationZ = canvasState.rotation,
                translationX = canvasState.offsetX,
                translationY = canvasState.offsetY,
                transformOrigin = TransformOrigin(0f, 0f)
            )
    ) {
        val centerX = size.width / 2
        val centerY = size.height / 2

        translate(left = centerX, top = centerY) {
            for ((_, buildingState) in buildingStates) {
                val building = buildingState.building
                val relX = building.relativePosition.x
                val relY = building.relativePosition.y
                val floorsToRender = buildingState.floorsToRender

                translate(left = relX, top = relY) {
                    for ((index, floorData) in floorsToRender.withIndex()) {
                        val floorPlanScale = floorData.metadata.scale
                        val floorPlanRotation = floorData.metadata.rotation

                        if (displayConfig.showRoomLabels) {
                            val floorsAbove = floorsToRender.subList(index + 1, floorsToRender.size)
                            val visibleRooms = filterVisibleRooms(
                                rooms = floorData.rooms,
                                roomFloorScale = floorPlanScale,
                                roomFloorRotation = floorPlanRotation,
                                floorsAbove = floorsAbove
                            )

                            drawRoomLabels(
                                rooms = visibleRooms,
                                scale = floorPlanScale,
                                rotationDegrees = floorPlanRotation,
                                canvasScale = canvasState.scale,
                                canvasRotation = canvasState.rotation
                            )
                        }
                    }

                    // Building name at low zoom
                    if (canvasState.scale in FloorPlanViewConstants.BUILDING_NAME_MIN_ZOOM..<FloorPlanViewConstants.BUILDING_NAME_MAX_ZOOM && floorsToRender.isNotEmpty()) {
                        val topFloor = floorsToRender.last()
                        drawBuildingName(
                            buildingName = topFloor.metadata.buildingName,
                            labelPosition = topFloor.metadata.labelPosition,
                            scale = topFloor.metadata.scale,
                            rotationDegrees = topFloor.metadata.rotation,
                            canvasScale = canvasState.scale,
                            canvasRotation = canvasState.rotation
                        )
                    }
                }
            }

            // Pin on the pinned room
            if (pinnedRoom != null && pinDrawable != null) {
                val ownerBuilding = if (pinnedRoom.buildingId != null) {
                    buildingStates[pinnedRoom.buildingId]
                } else {
                    buildingStates.values.find { bs ->
                        bs.floorsToRender.any { fd ->
                            fd.rooms.any { it.id == pinnedRoom.id && it.floorId == pinnedRoom.floorId }
                        }
                    }
                }
                if (ownerBuilding != null) {
                    val topFloor = ownerBuilding.floorsToRender.lastOrNull()
                    if (topFloor != null) {
                        val relX = ownerBuilding.building.relativePosition.x
                        val relY = ownerBuilding.building.relativePosition.y
                        translate(left = relX, top = relY) {
                            drawPin(
                                pinX = pinnedRoom.x,
                                pinY = pinnedRoom.y,
                                scale = topFloor.metadata.scale,
                                rotationDegrees = topFloor.metadata.rotation,
                                canvasScale = canvasState.scale,
                                canvasRotation = canvasState.rotation,
                                pinDrawable = pinDrawable,
                                tintColor = pinTintColor
                            )
                        }
                    }
                }
            }
        }
    }
}
