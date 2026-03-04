package `in`.project.enroute.feature.floorplan

import androidx.compose.ui.geometry.Offset
import `in`.project.enroute.feature.floorplan.state.BuildingState
import `in`.project.enroute.feature.navigation.data.StairwellConnection
import `in`.project.enroute.feature.pdr.correction.StairwellZone
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Extracts stairwell data from loaded building/floor state.
 *
 * Shared computation logic used by [FloorPlanViewModel] to produce
 * [StairwellZone] (for PDR boundary detection) and [StairwellConnection]
 * (for navigation cross-floor pathfinding) without duplicating the
 * coordinate-transform and floor-resolution code.
 */
object StairwellDataExtractor {

    // ── Internal intermediate used by both outputs ──────────────────────────

    private data class ParsedStairwell(
        val polygonId: Int,
        val buildingId: String,
        val floorId: String,
        val boundary: List<Offset>,
        val topEdge: Pair<Offset, Offset>,
        val bottomEdge: Pair<Offset, Offset>,
        val floorsConnected: List<Float>,
        val isSameFloor: Boolean,
        val bottomFloorId: String,
        val topFloorId: String,
        val bottomFloorNumber: Float,
        val topFloorNumber: Float
    )

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Builds [StairwellZone] objects for PDR boundary-based stair detection.
     *
     * Each zone contains the polygon boundary, "top"/"bottom" edges, and
     * floor resolution data. Zones whose floorsConnected are both
     * sub-integer values on the same base floor are tagged isSameFloor.
     */
    fun computeStairwellZones(
        buildingStates: Map<String, BuildingState>
    ): List<StairwellZone> {
        return parseAll(buildingStates).map { p ->
            StairwellZone(
                polygonId = p.polygonId,
                boundary = p.boundary,
                topEdge = p.topEdge,
                bottomEdge = p.bottomEdge,
                floorsConnected = p.floorsConnected,
                floorId = p.floorId,
                isSameFloor = p.isSameFloor,
                bottomFloorId = p.bottomFloorId,
                topFloorId = p.topFloorId,
                bottomFloorNumber = p.bottomFloorNumber,
                topFloorNumber = p.topFloorNumber
            )
        }
    }

    /**
     * Builds [StairwellConnection] objects for navigation cross-floor routing.
     *
     * Each connection provides the midpoint of the "bottom" and "top" edges
     * in campus-wide coordinates. Since all floors share the same coordinate
     * space, the exit midpoint on one floor is a valid start position on
     * the adjacent floor.
     */
    fun computeStairwellConnections(
        buildingStates: Map<String, BuildingState>
    ): List<StairwellConnection> {
        return parseAll(buildingStates).map { p ->
            StairwellConnection(
                polygonId = p.polygonId,
                buildingId = p.buildingId,
                floorId = p.floorId,
                bottomMidpoint = midpoint(p.bottomEdge),
                topMidpoint = midpoint(p.topEdge),
                floorsConnected = p.floorsConnected,
                bottomFloorId = p.bottomFloorId,
                topFloorId = p.topFloorId,
                bottomFloorNumber = p.bottomFloorNumber,
                topFloorNumber = p.topFloorNumber
            )
        }
    }

    // ── Shared parsing ──────────────────────────────────────────────────────

    private fun parseAll(
        buildingStates: Map<String, BuildingState>
    ): List<ParsedStairwell> {
        val result = mutableListOf<ParsedStairwell>()
        val processedKeys = mutableSetOf<String>()

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

                fun toCampus(x: Float, y: Float): Offset {
                    val px = x * scale
                    val py = y * scale
                    return Offset(
                        px * cosA - py * sinA + relX,
                        px * sinA + py * cosA + relY
                    )
                }

                for (stairwell in floorData.stairwells) {
                    val key = "${building.buildingId}_${stairwell.polygonId}_${stairwell.floorsConnected}"
                    if (key in processedKeys) continue

                    val topLines = stairwell.lines.filter { it.position == "top" }
                    val bottomLines = stairwell.lines.filter { it.position == "bottom" }
                    if (topLines.isEmpty() || bottomLines.isEmpty()) continue

                    val campusBoundary = stairwell.points.map { (x, y) ->
                        toCampus(x, y)
                    }

                    val topLine = topLines.first()
                    val topEdge = Pair(
                        toCampus(topLine.x1, topLine.y1),
                        toCampus(topLine.x2, topLine.y2)
                    )
                    val bottomLine = bottomLines.first()
                    val bottomEdge = Pair(
                        toCampus(bottomLine.x1, bottomLine.y1),
                        toCampus(bottomLine.x2, bottomLine.y2)
                    )

                    val floors = stairwell.floorsConnected.sorted()
                    if (floors.size < 2) continue

                    val bottomFloorNum = floors.first()
                    val topFloorNum = floors.last()

                    val isSameFloor = bottomFloorNum != floor(bottomFloorNum) &&
                            topFloorNum != floor(topFloorNum) &&
                            floor(bottomFloorNum) == floor(topFloorNum)

                    val bottomFloorData = buildingState.floors[bottomFloorNum]
                    val topFloorData = buildingState.floors[topFloorNum]
                    val bottomFloorId = bottomFloorData?.floorId
                        ?: formatFloorId(bottomFloorNum)
                    val topFloorId = topFloorData?.floorId
                        ?: formatFloorId(topFloorNum)

                    result.add(
                        ParsedStairwell(
                            polygonId = stairwell.polygonId,
                            buildingId = building.buildingId,
                            floorId = floorData.floorId,
                            boundary = campusBoundary,
                            topEdge = topEdge,
                            bottomEdge = bottomEdge,
                            floorsConnected = stairwell.floorsConnected,
                            isSameFloor = isSameFloor,
                            bottomFloorId = bottomFloorId,
                            topFloorId = topFloorId,
                            bottomFloorNumber = bottomFloorNum,
                            topFloorNumber = topFloorNum
                        )
                    )
                    processedKeys.add(key)
                }
            }
        }
        return result
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun midpoint(edge: Pair<Offset, Offset>): Offset = Offset(
        (edge.first.x + edge.second.x) / 2f,
        (edge.first.y + edge.second.y) / 2f
    )

    private fun formatFloorId(floorNum: Float): String {
        return if (floorNum == floorNum.toInt().toFloat()) {
            "floor_${floorNum.toInt()}"
        } else {
            "floor_$floorNum"
        }
    }
}
