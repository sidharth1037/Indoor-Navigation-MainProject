package `in`.project.enroute.feature.navigation.data

import android.util.Log
import androidx.compose.ui.geometry.Offset
import `in`.project.enroute.data.model.*
import `in`.project.enroute.data.repository.FirebaseFloorPlanRepository
import `in`.project.enroute.feature.floorplan.StairwellDataExtractor
import `in`.project.enroute.feature.floorplan.state.BuildingState
import `in`.project.enroute.feature.navigation.NavigationRepository

/**
 * Performs full precalculation of pathfinding distance-transform grids for a campus.
 *
 * Replicates the same coordinate-transform + NavigationRepository construction
 * that happens lazily at direction-request time, but runs it eagerly (admin-side)
 * so the results can be stored in Firestore for instant user-side loading.
 */
object NavDataPrecalculator {

    private const val TAG = "NavDataPrecalculator"

    data class PrecalcResult(
        val floorGrids: Map<String, PrecalculatedFloorGrid>
    )

    /**
     * Loads all floor data from Firestore, transforms to campus coordinates,
     * builds NavigationRepository for each floor, and exports the grids.
     *
     * @param repo Firestore repository for the campus.
     * @param onProgress Callback with status messages for UI updates.
     */
    suspend fun precalculate(
        repo: FirebaseFloorPlanRepository,
        onProgress: suspend (String) -> Unit
    ): PrecalcResult {
        onProgress("Loading buildings...")
        val buildingIds = repo.getAvailableBuildings()

        val wallsByFloor = mutableMapOf<String, MutableList<Wall>>()
        val boundaryByFloor = mutableMapOf<String, MutableList<CampusBoundaryPolygon>>()
        val allEntrances = mutableListOf<CampusEntrance>()
        val buildingStates = mutableMapOf<String, BuildingState>()

        for (buildingId in buildingIds) {
            onProgress("Loading $buildingId...")
            val metadata = repo.loadBuildingMetadata(buildingId)
            val floorIds = repo.getAvailableFloors(buildingId)
            if (floorIds.isEmpty()) continue

            val scale = metadata.scale
            val rotation = metadata.rotation
            val offX = metadata.relativePosition.x
            val offY = metadata.relativePosition.y

            val building = Building(
                buildingId = buildingId,
                buildingName = metadata.buildingName,
                availableFloors = floorIds,
                scale = scale,
                rotation = rotation,
                labelPosition = metadata.labelPosition,
                relativePosition = metadata.relativePosition
            )

            val floorsMap = mutableMapOf<Float, FloorPlanData>()
            val floorNumbers = mutableListOf<Float>()

            for (floorId in floorIds) {
                onProgress("Loading $buildingId / $floorId...")
                val floorData = repo.loadFloorPlan(buildingId, floorId)
                val floorNum = floorId.removePrefix("floor_").toFloatOrNull() ?: 1f
                floorsMap[floorNum] = floorData
                floorNumbers.add(floorNum)

                // Transform walls → campus coords
                val floorWalls = wallsByFloor.getOrPut(floorId) { mutableListOf() }
                for (wall in floorData.walls) {
                    val (sx1, sy1) = CoordinateTransform.rawToCampus(wall.x1, wall.y1, scale, rotation, offX, offY)
                    val (sx2, sy2) = CoordinateTransform.rawToCampus(wall.x2, wall.y2, scale, rotation, offX, offY)
                    floorWalls.add(Wall(sx1, sy1, sx2, sy2))
                }

                // Transform boundaries → campus coords
                val floorBoundary = boundaryByFloor.getOrPut(floorId) { mutableListOf() }
                for (polygon in floorData.boundaryPolygons) {
                    val campusPoints = polygon.points.map { pt ->
                        val (cx, cy) = CoordinateTransform.rawToCampus(pt.x, pt.y, scale, rotation, offX, offY)
                        Offset(cx, cy)
                    }
                    floorBoundary.add(CampusBoundaryPolygon(campusPoints))
                }

                // Transform entrances → campus coords
                for (entrance in floorData.entrances) {
                    val (cx, cy) = CoordinateTransform.rawToCampus(entrance.x, entrance.y, scale, rotation, offX, offY)
                    allEntrances.add(CampusEntrance(
                        original = entrance, campusX = cx, campusY = cy,
                        buildingId = buildingId, floorId = floorId
                    ))
                }
            }

            buildingStates[buildingId] = BuildingState(
                building = building,
                floors = floorsMap,
                availableFloorNumbers = floorNumbers.sorted(),
                currentFloorNumber = floorNumbers.minOrNull() ?: 1f
            )
        }

        // Compute stairwell connections (needed for boundary points in grid)
        onProgress("Computing stairwell connections...")
        val stairConnections = StairwellDataExtractor.computeStairwellConnections(buildingStates)
        Log.d(TAG, "Computed ${stairConnections.size} stairwell connections")

        // Build NavigationRepository for each floor and export grids
        val grids = mutableMapOf<String, PrecalculatedFloorGrid>()
        val allFloorIds = wallsByFloor.keys.toList().sorted()

        for (floorId in allFloorIds) {
            onProgress("Computing distance transform for $floorId...")
            val walls = wallsByFloor[floorId] ?: continue

            // Replicate the same boundary-point logic as MultiFloorPathfinder.getOrBuildRepo()
            val entrancePoints = allEntrances
                .filter { it.floorId == floorId }
                .map { it.campusOffset }
            val stairPoints = stairConnections.flatMap { conn ->
                buildList {
                    if (conn.bottomFloorId == floorId) add(conn.bottomMidpoint)
                    if (conn.topFloorId == floorId) add(conn.topMidpoint)
                }
            }
            val allBoundPoints = entrancePoints + stairPoints

            val floorNum = floorId.removePrefix("floor_").toFloatOrNull() ?: 1f
            val boundaries = if (floorNum <= 1f) emptyList()
                             else boundaryByFloor[floorId] ?: emptyList()

            Log.d(TAG, "Building grid for $floorId: ${walls.size} walls, " +
                    "${allBoundPoints.size} bound points, ${boundaries.size} boundary polygons")

            val navRepo = NavigationRepository.build(walls, allBoundPoints, boundaries)
            grids[floorId] = navRepo.exportGrid(floorId)
        }

        onProgress("Precalculation complete")
        return PrecalcResult(floorGrids = grids)
    }
}
