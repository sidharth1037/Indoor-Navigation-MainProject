@file:Suppress("unused")

package `in`.project.enroute.data.repository

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import `in`.project.enroute.data.model.CampusMetadata
import `in`.project.enroute.data.model.Entrance
import `in`.project.enroute.data.model.EntranceDeserializer
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

    private val gson = GsonBuilder()
        .registerTypeAdapter(Entrance::class.java, EntranceDeserializer())
        .create()

    // ── Campus-level ─────────────────────────────────────────────

    override suspend fun loadCampusMetadata(): CampusMetadata = withContext(Dispatchers.IO) {
        val inputStream = context.assets.open("campus/campus_metadata.json")
        val reader = InputStreamReader(inputStream)
        gson.fromJson(reader, CampusMetadata::class.java)
    }

    override suspend fun getAvailableBuildings(): List<String> = withContext(Dispatchers.IO) {
        try {
            val entries = context.assets.list("campus") ?: emptyArray()
            entries.filter { it.startsWith("building_") }
                .sortedBy { it.removePrefix("building_").toIntOrNull() ?: 0 }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ── Building-level ───────────────────────────────────────────

    override suspend fun loadBuildingMetadata(buildingId: String): FloorPlanMetadata = withContext(Dispatchers.IO) {
        loadMetadata("campus/$buildingId/${buildingId}_metadata.json")
    }

    override suspend fun getAvailableFloors(buildingId: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val buildingPath = "campus/$buildingId"
            val entries = context.assets.list(buildingPath) ?: emptyArray()
            entries.filter { it.startsWith("floor_") }
                .sortedBy { it.removePrefix("floor_").toFloatOrNull() ?: 0f }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ── Floor-level ──────────────────────────────────────────────

    override suspend fun loadFloorPlan(buildingId: String, floorId: String): FloorPlanData = withContext(Dispatchers.IO) {
        val buildingPath = "campus/$buildingId"
        val floorPath = "$buildingPath/$floorId"
        val metadata = loadMetadata("$buildingPath/${buildingId}_metadata.json")
        val walls = loadWalls("$floorPath/${floorId}_walls.json")
        val stairwells = loadStairwells("$floorPath/${floorId}_stairs.json")
        val entrances = loadEntrances("$floorPath/${floorId}_entrances.json").map { it.copy(floorId = floorId) }
        val rooms = loadRooms("$floorPath/${floorId}_rooms.json").map { it.copy(floorId = floorId, buildingId = buildingId) }
        val boundaryPolygons = loadBoundaryPolygons("$floorPath/${floorId}_boundary.json")

        FloorPlanData(
            floorId = floorId,
            buildingId = buildingId,
            metadata = metadata,
            walls = walls,
            stairwells = stairwells,
            entrances = entrances,
            rooms = rooms,
            boundaryPolygons = boundaryPolygons
        )
    }

    // ── Private helpers ──────────────────────────────────────────

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
                val positions = lines.mapNotNull { it.position }.distinct()

                Stairwell(
                    polygonId = polygonId,
                    points = orderedPoints,
                    floorsConnected = floorsConnected,
                    positions = positions,
                    lines = lines
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
    private fun  loadBoundaryPolygons(fileName: String): List<BoundaryPolygon> {
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
