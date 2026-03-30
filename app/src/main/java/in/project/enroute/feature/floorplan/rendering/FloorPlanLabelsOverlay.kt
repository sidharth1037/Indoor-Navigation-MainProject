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
import `in`.project.enroute.data.model.Landmark
import `in`.project.enroute.data.model.Room
import `in`.project.enroute.feature.floorplan.rendering.renderers.computeLandmarkScreenSizing
import `in`.project.enroute.feature.floorplan.rendering.renderers.LANDMARK_LABEL_TEXT_SCALE
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawBuildingName
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawCampusPin
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawPin
import `in`.project.enroute.feature.floorplan.rendering.renderers.drawRoomLabels
import `in`.project.enroute.feature.floorplan.state.BuildingState
import `in`.project.enroute.data.model.FloorPlanData
import kotlin.math.cos
import kotlin.math.sin

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
    landmarks: List<Landmark> = emptyList(),
    currentFloorId: String? = null,
    pinnedRoom: Room? = null,
    showPinnedRoomMarker: Boolean = true,
    isPinAtEntrance: Boolean = false,
    overlayPinnedRoom: Room? = null,
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

                            val landmarkLabelIds = mutableSetOf<Int>()
                            val landmarkLabelYOffsetById = mutableMapOf<Int, Float>()
                            val landmarkScreenSizing = computeLandmarkScreenSizing(canvasScale = canvasState.scale)
                            // drawRoomLabels offsets are in pre-graphicsLayer coordinates.
                            // Convert desired screen-space gap into text-space so zoom-out
                            // does not collapse labels back toward the icon.
                            val safeScale = canvasState.scale.coerceAtLeast(0.01f)
                            val labelAnchorOffsetTextSpace = landmarkScreenSizing.labelCenterOffsetPx / safeScale

                            val landmarkLabelsForFloor = if (currentFloorId == floorData.floorId) {
                                landmarks
                                    .asSequence()
                                    .filter { it.floorId == floorData.floorId }
                                    .filter { lm ->
                                        if (lm.buildingId.isNotBlank()) {
                                            lm.buildingId == building.buildingId
                                        } else {
                                            isCampusPointInsideFloorBoundary(
                                                campusX = lm.campusX,
                                                campusY = lm.campusY,
                                                floorData = floorData,
                                                buildingRelX = relX,
                                                buildingRelY = relY
                                            )
                                        }
                                    }
                                    .map { lm ->
                                        val local = campusToFloorLocal(
                                            campusX = lm.campusX,
                                            campusY = lm.campusY,
                                            buildingRelX = relX,
                                            buildingRelY = relY,
                                            floorScale = floorPlanScale,
                                            floorRotationDegrees = floorPlanRotation
                                        )
                                        val syntheticId = landmarkSyntheticRoomId(lm.id)
                                        landmarkLabelIds += syntheticId
                                        landmarkLabelYOffsetById[syntheticId] = labelAnchorOffsetTextSpace
                                        Room(
                                            id = syntheticId,
                                            x = local.first,
                                            y = local.second,
                                            name = lm.name,
                                            number = null,
                                            floorId = lm.floorId,
                                            buildingId = if (lm.buildingId.isNotBlank()) lm.buildingId else building.buildingId
                                        )
                                    }
                                    .toList()
                            } else {
                                emptyList()
                            }

                            val combinedLabels = if (landmarkLabelsForFloor.isEmpty()) {
                                visibleRooms
                            } else {
                                visibleRooms + landmarkLabelsForFloor
                            }

                            drawRoomLabels(
                                rooms = combinedLabels,
                                scale = floorPlanScale,
                                rotationDegrees = floorPlanRotation,
                                canvasScale = canvasState.scale,
                                canvasRotation = canvasState.rotation,
                                textScaleForRoom = { room ->
                                    if (room.id in landmarkLabelIds) LANDMARK_LABEL_TEXT_SCALE else 1f
                                },
                                textYOffsetForRoomPx = { room ->
                                    landmarkLabelYOffsetById[room.id] ?: 0f
                                }
                            )
                        }
                    }

                    // Building name at low zoom
                    if (canvasState.scale in 0.18f..<0.4f && floorsToRender.isNotEmpty()) {
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

            // Pin on the pinned room (destination when path is active)
            if (showPinnedRoomMarker && pinnedRoom != null && pinDrawable != null) {
                val pinnedLandmark = landmarks.firstOrNull {
                    landmarkSyntheticRoomId(it.id) == pinnedRoom.id
                }

                if (pinnedLandmark != null) {
                    val screenSizing = computeLandmarkScreenSizing(canvasScale = canvasState.scale)
                    val iconHalfHeightPx = screenSizing.iconSizePx / 2f
                    val landmarkPinLiftPx = iconHalfHeightPx + 10f

                    drawCampusPin(
                        campusX = pinnedLandmark.campusX,
                        campusY = pinnedLandmark.campusY,
                        canvasScale = canvasState.scale,
                        canvasRotation = canvasState.rotation,
                        pinDrawable = pinDrawable,
                        tintColor = pinTintColor,
                        tipOffsetPx = landmarkPinLiftPx
                    )
                } else {
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
                        val roomFloorData = pinnedRoom.floorId?.let { floorId ->
                            ownerBuilding.floors.values.firstOrNull { it.floorId == floorId }
                        } ?: ownerBuilding.floorsToRender.lastOrNull()

                        if (roomFloorData != null) {
                            val relX = ownerBuilding.building.relativePosition.x
                            val relY = ownerBuilding.building.relativePosition.y
                            translate(left = relX, top = relY) {
                                drawPin(
                                    pinX = pinnedRoom.x,
                                    pinY = pinnedRoom.y,
                                    scale = roomFloorData.metadata.scale,
                                    rotationDegrees = roomFloorData.metadata.rotation,
                                    canvasScale = canvasState.scale,
                                    canvasRotation = canvasState.rotation,
                                    pinDrawable = pinDrawable,
                                    tintColor = pinTintColor,
                                    applyVerticalOffset = !isPinAtEntrance
                                )
                            }
                        }
                    }
                }
            }

            // Second pin for overlay room (shown when user taps another label while path is active)
            if (overlayPinnedRoom != null && pinDrawable != null) {
                val overlayLandmark = landmarks.firstOrNull {
                    landmarkSyntheticRoomId(it.id) == overlayPinnedRoom.id
                }

                if (overlayLandmark != null) {
                    val screenSizing = computeLandmarkScreenSizing(canvasScale = canvasState.scale)
                    val iconHalfHeightPx = screenSizing.iconSizePx / 2f
                    val landmarkPinLiftPx = iconHalfHeightPx + 10f

                    drawCampusPin(
                        campusX = overlayLandmark.campusX,
                        campusY = overlayLandmark.campusY,
                        canvasScale = canvasState.scale,
                        canvasRotation = canvasState.rotation,
                        pinDrawable = pinDrawable,
                        tintColor = pinTintColor,
                        tipOffsetPx = landmarkPinLiftPx
                    )
                } else {
                    val overlayOwner = if (overlayPinnedRoom.buildingId != null) {
                        buildingStates[overlayPinnedRoom.buildingId]
                    } else {
                        buildingStates.values.find { bs ->
                            bs.floorsToRender.any { fd ->
                                fd.rooms.any { it.id == overlayPinnedRoom.id && it.floorId == overlayPinnedRoom.floorId }
                            }
                        }
                    }

                    if (overlayOwner != null) {
                        val overlayFloorData = overlayPinnedRoom.floorId?.let { floorId ->
                            overlayOwner.floors.values.firstOrNull { it.floorId == floorId }
                        } ?: overlayOwner.floorsToRender.lastOrNull()

                        if (overlayFloorData != null) {
                            val relX = overlayOwner.building.relativePosition.x
                            val relY = overlayOwner.building.relativePosition.y
                            translate(left = relX, top = relY) {
                                drawPin(
                                    pinX = overlayPinnedRoom.x,
                                    pinY = overlayPinnedRoom.y,
                                    scale = overlayFloorData.metadata.scale,
                                    rotationDegrees = overlayFloorData.metadata.rotation,
                                    canvasScale = canvasState.scale,
                                    canvasRotation = canvasState.rotation,
                                    pinDrawable = pinDrawable,
                                    tintColor = pinTintColor,
                                    applyVerticalOffset = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun campusToFloorLocal(
    campusX: Float,
    campusY: Float,
    buildingRelX: Float,
    buildingRelY: Float,
    floorScale: Float,
    floorRotationDegrees: Float
): Pair<Float, Float> {
    if (floorScale == 0f) return Pair(0f, 0f)

    val tx = campusX - buildingRelX
    val ty = campusY - buildingRelY
    val angleRad = Math.toRadians(floorRotationDegrees.toDouble()).toFloat()
    val cosA = cos(angleRad)
    val sinA = sin(angleRad)

    val unrotX = tx * cosA + ty * sinA
    val unrotY = -tx * sinA + ty * cosA

    return Pair(unrotX / floorScale, unrotY / floorScale)
}

private fun landmarkSyntheticRoomId(landmarkId: String): Int {
    val hash = landmarkId.hashCode()
    val safePositive = if (hash == Int.MIN_VALUE) 0 else kotlin.math.abs(hash)
    return -(safePositive + 1)
}

private fun isCampusPointInsideFloorBoundary(
    campusX: Float,
    campusY: Float,
    floorData: FloorPlanData,
    buildingRelX: Float,
    buildingRelY: Float
): Boolean {
    val scale = floorData.metadata.scale
    val angleRad = Math.toRadians(floorData.metadata.rotation.toDouble()).toFloat()
    val cosA = cos(angleRad)
    val sinA = sin(angleRad)

    for (polygon in floorData.boundaryPolygons) {
        if (polygon.points.isEmpty()) continue
        val transformed = polygon.points.sortedBy { it.id }.map { p ->
            val x = p.x * scale
            val y = p.y * scale
            Pair(
                x * cosA - y * sinA + buildingRelX,
                x * sinA + y * cosA + buildingRelY
            )
        }

        if (isPointInPolygon(campusX, campusY, transformed)) {
            return true
        }
    }
    return false
}

private fun isPointInPolygon(
    x: Float,
    y: Float,
    polygon: List<Pair<Float, Float>>
): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val xi = polygon[i].first
        val yi = polygon[i].second
        val xj = polygon[j].first
        val yj = polygon[j].second

        val intersects = ((yi > y) != (yj > y)) &&
            (x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { it != 0f } ?: 1e-6f) + xi)
        if (intersects) inside = !inside
        j = i
    }
    return inside
}
