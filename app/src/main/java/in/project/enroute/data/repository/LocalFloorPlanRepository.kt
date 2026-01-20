package `in`.project.enroute.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import `in`.project.enroute.data.model.Entrance
import `in`.project.enroute.data.model.FloorPlanData
import `in`.project.enroute.data.model.FloorPlanMetadata
import `in`.project.enroute.data.model.Room
import `in`.project.enroute.data.model.StairLine
import `in`.project.enroute.data.model.Stairwell
import `in`.project.enroute.data.model.Wall
import `in`.project.enroute.data.model.BoundaryPoint
import `in`.project.enroute.data.model.BoundaryPolygon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

/**
 * Local implementation of FloorPlanRepository.
 * Reads floor plan data from JSON files in the assets folder.
 */
class LocalFloorPlanRepository(
    private val context: Context
) : FloorPlanRepository {

    private val gson = Gson()

    override suspend fun loadFloorPlan(buildingId: String, floorId: String): FloorPlanData = withContext(Dispatchers.IO) {
        val metadata = loadMetadata("${buildingId}_metadata.json")
        val walls = loadWalls("${floorId}_walls.json")
        val stairwells = loadStairwells("${floorId}_stairs.json")
        val entrances = loadEntrances("${floorId}_entrances.json")
        val rooms = loadRooms("${floorId}_rooms.json")
        val boundaryPolygons = loadBoundaryPolygons("${floorId}_boundary.json")

        FloorPlanData(
            floorId = floorId,
            metadata = metadata,
            walls = walls,
            stairwells = stairwells,
            entrances = entrances,
            rooms = rooms,
            boundaryPolygons = boundaryPolygons
        )
    }

    override suspend fun loadBuildingMetadata(buildingId: String): FloorPlanMetadata = withContext(Dispatchers.IO) {
        loadMetadata("${buildingId}_metadata.json")
    }

    override suspend fun getAvailableFloors(buildingId: String): List<String> = withContext(Dispatchers.IO) {
        // For now, return hardcoded list. Later can scan assets or get from backend
        listOf("floor_1", "floor_1.5", "floor_2")
    }

    /**
     * Loads walls from JSON file.
     */
    private fun loadWalls(fileName: String): List<Wall> {
        return try {
            val inputStream = context.assets.open(fileName)
            val reader = InputStreamReader(inputStream)
            val wallListType = object : TypeToken<List<Wall>>() {}.type
            gson.fromJson(reader, wallListType)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Loads stairwells from JSON file.
     * Groups stair lines by polygon ID and creates ordered polygons.
     */
    private fun loadStairwells(fileName: String): List<Stairwell> {
        return try {
            val inputStream = context.assets.open(fileName)
            val reader = InputStreamReader(inputStream)

            val stairLineListType = object : TypeToken<List<StairLine>>() {}.type
            val stairLines: List<StairLine> = gson.fromJson(reader, stairLineListType)

            // Group stair lines by polygon ID
            val groupedByPolygonId = stairLines.groupBy { it.stairPolygonId }

            // Convert each group into a Stairwell polygon
            groupedByPolygonId.map { (polygonId, lines) ->
                val floorsConnected = lines.firstOrNull()?.floorsConnected ?: emptyList()
                val orderedPoints = buildOrderedPolygon(lines)

                Stairwell(
                    polygonId = polygonId,
                    points = orderedPoints,
                    floorsConnected = floorsConnected
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Builds an ordered list of points from line segments that form a polygon.
     * Traces the edges to create a proper polygon path for filling.
     */
    private fun buildOrderedPolygon(lines: List<StairLine>): List<Pair<Float, Float>> {
        if (lines.isEmpty()) return emptyList()

        // Create a map of edges: each point maps to all points it connects to
        val edges = mutableMapOf<Pair<Float, Float>, MutableList<Pair<Float, Float>>>()

        for (line in lines) {
            val p1 = Pair(line.x1, line.y1)
            val p2 = Pair(line.x2, line.y2)

            edges.computeIfAbsent(p1) { mutableListOf() }.add(p2)
            edges.computeIfAbsent(p2) { mutableListOf() }.add(p1)
        }

        // Trace the polygon by following connected edges
        val orderedPoints = mutableListOf<Pair<Float, Float>>()
        val visited = mutableSetOf<Pair<Pair<Float, Float>, Pair<Float, Float>>>()

        if (edges.isEmpty()) return emptyList()

        var currentPoint = edges.keys.first()
        val startPoint = currentPoint

        do {
            orderedPoints.add(currentPoint)
            val neighbors = edges[currentPoint] ?: break

            var nextPoint: Pair<Float, Float>? = null
            for (neighbor in neighbors) {
                val edge = Pair(currentPoint, neighbor)
                val reverseEdge = Pair(neighbor, currentPoint)

                if (!visited.contains(edge) && !visited.contains(reverseEdge)) {
                    nextPoint = neighbor
                    visited.add(edge)
                    break
                }
            }

            if (nextPoint == null) break
            currentPoint = nextPoint

        } while (currentPoint != startPoint && orderedPoints.size < edges.size * 2)

        return orderedPoints
    }

    /**
     * Loads entrances from JSON file.
     */
    private fun loadEntrances(fileName: String): List<Entrance> {
        return try {
            val inputStream = context.assets.open(fileName)
            val reader = InputStreamReader(inputStream)

            val jsonObject = gson.fromJson(reader, JsonObject::class.java)
            val entrancesArray = jsonObject.getAsJsonArray("entrances")

            val entranceListType = object : TypeToken<List<Entrance>>() {}.type
            gson.fromJson(entrancesArray, entranceListType)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Loads rooms from JSON file.
     */
    private fun loadRooms(fileName: String): List<Room> {
        return try {
            val inputStream = context.assets.open(fileName)
            val reader = InputStreamReader(inputStream)

            val jsonObject = gson.fromJson(reader, JsonObject::class.java)
            val roomsArray = jsonObject.getAsJsonArray("rooms")

            val roomListType = object : TypeToken<List<Room>>() {}.type
            gson.fromJson(roomsArray, roomListType)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Loads floor plan metadata from JSON file.
     */
    private fun loadMetadata(fileName: String): FloorPlanMetadata {
        val inputStream = context.assets.open(fileName)
        val reader = InputStreamReader(inputStream)
        return gson.fromJson(reader, FloorPlanMetadata::class.java)
    }

    /**
     * Loads boundary polygons from JSON file.
     * Supports multiple polygons per floor (e.g., separate building sections).
     */
    private fun loadBoundaryPolygons(fileName: String): List<BoundaryPolygon> {
        return try {
            val inputStream = context.assets.open(fileName)
            val reader = InputStreamReader(inputStream)

            val jsonObject = gson.fromJson(reader, JsonObject::class.java)
            val polygonsArray = jsonObject.getAsJsonArray("polygons")

            val polygons = mutableListOf<BoundaryPolygon>()
            for (polygonElement in polygonsArray) {
                val polygonObj = polygonElement.asJsonObject
                val name = polygonObj.get("name").asString
                val pointsArray = polygonObj.getAsJsonArray("points")

                val pointsListType = object : TypeToken<List<BoundaryPoint>>() {}.type
                val points: List<BoundaryPoint> = gson.fromJson(pointsArray, pointsListType)

                polygons.add(BoundaryPolygon(name = name, points = points))
            }

            polygons
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
