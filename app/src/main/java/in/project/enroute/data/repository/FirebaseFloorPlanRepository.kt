package `in`.project.enroute.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import `in`.project.enroute.data.model.*
import `in`.project.enroute.feature.navigation.data.PrecalculatedFloorGrid
import `in`.project.enroute.feature.navigation.data.PrecalculatedNavMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

/**
 * Firebase Firestore implementation of [FloorPlanRepository].
 *
 * Firestore structure:
 * ```
 * campuses/{campusId}                          → CampusMetadata fields
 *   └── buildings/{buildingId}                 → FloorPlanMetadata fields
 *         └── floors/{floorId}                 → { available: true }
 *               ├── floor_data/walls           → { data: "<raw JSON string>" }
 *               ├── floor_data/stairs          → { data: "<raw JSON string>" }
 *               ├── floor_data/entrances       → { data: "<raw JSON string>" }
 *               ├── floor_data/rooms           → { data: "<raw JSON string>" }
 *               └── floor_data/boundary        → { data: "<raw JSON string>" }
 * ```
 *
 * JSON strings are stored as-is in a single "data" field per document.
 * This avoids Firestore's nested map/array complexity and lets us reuse
 * the exact same Gson parsing logic as [LocalFloorPlanRepository].
 */
class FirebaseFloorPlanRepository(
    private val campusId: String,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : FloorPlanRepository {

    private val gson = GsonBuilder()
        .registerTypeAdapter(Entrance::class.java, EntranceDeserializer())
        .create()

    // ── Campus-level ─────────────────────────────────────────────

    override suspend fun loadCampusMetadata(): CampusMetadata = withContext(Dispatchers.IO) {
        val doc = firestore
            .collection("campuses")
            .document(campusId)
            .get()
            .await()

        if (!doc.exists()) {
            throw IllegalStateException("Campus '$campusId' not found in Firestore")
        }

        CampusMetadata(
            name = doc.getString("name") ?: "",
            location = doc.getString("location") ?: "",
            latitude = doc.getDouble("latitude") ?: 0.0,
            longitude = doc.getDouble("longitude") ?: 0.0,
            north = doc.getDouble("north")?.toFloat() ?: 0f,
            createdBy = doc.getString("createdBy") ?: ""
        )
    }

    override suspend fun getAvailableBuildings(): List<String> = withContext(Dispatchers.IO) {
        val snapshot = firestore
            .collection("campuses")
            .document(campusId)
            .collection("buildings")
            .get()
            .await()

        snapshot.documents
            .map { it.id }
            .sortedBy { it.removePrefix("building_").toIntOrNull() ?: 0 }
    }

    // ── Building-level ───────────────────────────────────────────

    override suspend fun loadBuildingMetadata(buildingId: String): FloorPlanMetadata =
        withContext(Dispatchers.IO) {
            val doc = firestore
                .collection("campuses")
                .document(campusId)
                .collection("buildings")
                .document(buildingId)
                .get()
                .await()

            if (!doc.exists()) {
                throw IllegalStateException("Building '$buildingId' not found in Firestore")
            }

            val labelPos = (doc.get("labelPosition") as? Map<*, *>)?.let { map ->
                LabelPosition(
                    x = (map["x"] as? Number)?.toFloat() ?: 0f,
                    y = (map["y"] as? Number)?.toFloat() ?: 0f
                )
            }

            val relPos = (doc.get("relativePosition") as? Map<*, *>)?.let { map ->
                RelativePosition(
                    x = (map["x"] as? Number)?.toFloat() ?: 0f,
                    y = (map["y"] as? Number)?.toFloat() ?: 0f
                )
            } ?: RelativePosition()

            FloorPlanMetadata(
                buildingId = doc.getString("buildingId") ?: buildingId,
                scale = doc.getDouble("scale")?.toFloat() ?: 1f,
                rotation = doc.getDouble("rotation")?.toFloat() ?: 0f,
                buildingName = doc.getString("buildingName") ?: buildingId,
                labelPosition = labelPos,
                relativePosition = relPos
            )
        }

    override suspend fun getAvailableFloors(buildingId: String): List<String> =
        withContext(Dispatchers.IO) {
            val snapshot = firestore
                .collection("campuses")
                .document(campusId)
                .collection("buildings")
                .document(buildingId)
                .collection("floors")
                .get()
                .await()

            snapshot.documents
                .map { it.id }
                .sortedBy { it.removePrefix("floor_").toFloatOrNull() ?: 0f }
        }

    // ── Floor-level ──────────────────────────────────────────────

    override suspend fun loadFloorPlan(buildingId: String, floorId: String): FloorPlanData =
        withContext(Dispatchers.IO) {
            val metadata = loadBuildingMetadata(buildingId)
            val basePath = firestore
                .collection("campuses").document(campusId)
                .collection("buildings").document(buildingId)
                .collection("floors").document(floorId)
                .collection("floor_data")

            val walls = loadFloorDataJson(basePath, "walls") { json ->
                val wallListType = object : TypeToken<List<Wall>>() {}.type
                gson.fromJson<List<Wall>>(json, wallListType)
            } ?: emptyList()

            val stairwells = loadFloorDataJson(basePath, "stairs") { json ->
                parseStairwells(json)
            } ?: emptyList()

            val entrances = loadFloorDataJson(basePath, "entrances") { json ->
                parseEntrances(json)
            }?.map { it.copy(floorId = floorId) } ?: emptyList()

            val rooms = loadFloorDataJson(basePath, "rooms") { json ->
                parseRooms(json)
            }?.map { it.copy(floorId = floorId, buildingId = buildingId) } ?: emptyList()

            val boundaryPolygons = loadFloorDataJson(basePath, "boundary") { json ->
                parseBoundaryPolygons(json)
            } ?: emptyList()

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

    // ── Private JSON parsing (mirrors LocalFloorPlanRepository) ──

    private suspend fun <T> loadFloorDataJson(
        basePath: com.google.firebase.firestore.CollectionReference,
        docName: String,
        parser: (String) -> T
    ): T? {
        return try {
            val doc = basePath.document(docName).get().await()
            val json = doc.getString("data") ?: return null
            parser(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseStairwells(json: String): List<Stairwell> {
        val stairLineListType = object : TypeToken<List<StairLine>>() {}.type
        val stairLines: List<StairLine> = gson.fromJson(json, stairLineListType)

        return stairLines.groupBy { it.stairPolygonId }.map { (polygonId, lines) ->
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
    }

    private fun buildOrderedPolygon(lines: List<StairLine>): List<Pair<Float, Float>> {
        if (lines.isEmpty()) return emptyList()

        val edges = mutableMapOf<Pair<Float, Float>, MutableList<Pair<Float, Float>>>()
        for (line in lines) {
            val p1 = Pair(line.x1, line.y1)
            val p2 = Pair(line.x2, line.y2)
            edges.computeIfAbsent(p1) { mutableListOf() }.add(p2)
            edges.computeIfAbsent(p2) { mutableListOf() }.add(p1)
        }

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

    private fun parseEntrances(json: String): List<Entrance> {
        val jsonObject = gson.fromJson(json, JsonObject::class.java)
        val entrancesArray = jsonObject.getAsJsonArray("entrances")
        val entranceListType = object : TypeToken<List<Entrance>>() {}.type
        return gson.fromJson(entrancesArray, entranceListType)
    }

    private fun parseRooms(json: String): List<Room> {
        val jsonObject = gson.fromJson(json, JsonObject::class.java)
        val roomsArray = jsonObject.getAsJsonArray("rooms")
        val roomListType = object : TypeToken<List<Room>>() {}.type
        return gson.fromJson(roomsArray, roomListType)
    }

    private fun parseBoundaryPolygons(json: String): List<BoundaryPolygon> {
        val jsonObject = gson.fromJson(json, JsonObject::class.java)
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
        return polygons
    }

    // ── Upload helpers ───────────────────────────────────────────

    /**
     * Uploads campus metadata to Firestore.
     * Overwrites existing data silently.
     */
    suspend fun uploadCampusMetadata(metadata: CampusMetadata) = withContext(Dispatchers.IO) {
        firestore
            .collection("campuses")
            .document(campusId)
            .set(metadata)
            .await()
    }

    /**
     * Uploads building metadata to Firestore.
     * Overwrites existing data silently.
     */
    suspend fun uploadBuildingMetadata(
        buildingId: String,
        metadata: FloorPlanMetadata
    ) = withContext(Dispatchers.IO) {
        firestore
            .collection("campuses")
            .document(campusId)
            .collection("buildings")
            .document(buildingId)
            .set(metadata)
            .await()
    }

    /**
     * Uploads a raw JSON string for a specific floor data type.
     * Stores the JSON as a single "data" field in a Firestore document.
     *
     * @param buildingId Building identifier (e.g., "building_1")
     * @param floorId Floor identifier (e.g., "floor_1")
     * @param dataType Type of floor data ("walls", "stairs", "entrances", "rooms", "boundary")
     * @param jsonString The raw JSON content from the file
     */
    suspend fun uploadFloorData(
        buildingId: String,
        floorId: String,
        dataType: String,
        jsonString: String
    ) = withContext(Dispatchers.IO) {
        // Ensure floor document exists
        val floorRef = firestore
            .collection("campuses").document(campusId)
            .collection("buildings").document(buildingId)
            .collection("floors").document(floorId)

        floorRef.set(mapOf("available" to true), com.google.firebase.firestore.SetOptions.merge()).await()

        // Store JSON data
        floorRef
            .collection("floor_data")
            .document(dataType)
            .set(mapOf("data" to jsonString))
            .await()
    }

    /**
     * Uploads a floor data JSON file from a content URI.
     * Reads the file, determines the data type from filename, and uploads.
     *
     * @param context Android context for content resolver
     * @param buildingId Building identifier
     * @param floorId Floor identifier
     * @param fileUri URI of the JSON file from device storage
     * @param fileName Original filename (used to detect data type)
     * @return The detected data type, or null if upload failed
     */
    suspend fun uploadFloorDataFromUri(
        context: Context,
        buildingId: String,
        floorId: String,
        fileUri: Uri,
        fileName: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(fileUri)
                ?: return@withContext null

            val jsonString = InputStreamReader(inputStream).use { it.readText() }

            val dataType = detectDataType(fileName) ?: return@withContext null

            uploadFloorData(buildingId, floorId, dataType, jsonString)
            dataType
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Uploads campus metadata from a content URI.
     */
    suspend fun uploadCampusMetadataFromUri(
        context: Context,
        fileUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(fileUri)
                ?: return@withContext false
            val jsonString = InputStreamReader(inputStream).use { it.readText() }
            val metadata = gson.fromJson(jsonString, CampusMetadata::class.java)
            uploadCampusMetadata(metadata)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Uploads building metadata from a content URI.
     */
    suspend fun uploadBuildingMetadataFromUri(
        context: Context,
        buildingId: String,
        fileUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(fileUri)
                ?: return@withContext false
            val jsonString = InputStreamReader(inputStream).use { it.readText() }
            val metadata = gson.fromJson(jsonString, FloorPlanMetadata::class.java)
            uploadBuildingMetadata(buildingId, metadata)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Deletes a single floor and all its floor_data sub-collection documents
     * from Firestore.
     */
    suspend fun deleteFloor(buildingId: String, floorId: String) = withContext(Dispatchers.IO) {
        val floorRef = firestore
            .collection("campuses").document(campusId)
            .collection("buildings").document(buildingId)
            .collection("floors").document(floorId)

        // Delete floor_data sub-collection documents
        val floorDataDocs = floorRef.collection("floor_data").get().await()
        for (doc in floorDataDocs.documents) {
            doc.reference.delete().await()
        }
        // Delete the floor document itself
        floorRef.delete().await()
    }

    /**
     * Deletes a building, all its floors (with their floor_data), and the
     * building document itself from Firestore.
     */
    suspend fun deleteBuilding(buildingId: String) = withContext(Dispatchers.IO) {
        val buildingRef = firestore
            .collection("campuses").document(campusId)
            .collection("buildings").document(buildingId)

        // Delete all floors under this building
        val floorDocs = buildingRef.collection("floors").get().await()
        for (floorDoc in floorDocs.documents) {
            // Delete floor_data sub-collection
            val floorDataDocs = floorDoc.reference.collection("floor_data").get().await()
            for (dataDoc in floorDataDocs.documents) {
                dataDoc.reference.delete().await()
            }
            floorDoc.reference.delete().await()
        }
        // Delete the building document itself
        buildingRef.delete().await()
    }

    /**
     * Deletes a campus document and recursively deletes all buildings,
     * floors, and floor_data sub-collections underneath it.
     */
    suspend fun deleteCampus() = withContext(Dispatchers.IO) {
        val campusRef = firestore.collection("campuses").document(campusId)

        // Delete all buildings (which recursively deletes floors + floor_data)
        val buildingDocs = campusRef.collection("buildings").get().await()
        for (buildingDoc in buildingDocs.documents) {
            deleteBuilding(buildingDoc.id)
        }
        // Delete the campus document itself
        campusRef.delete().await()
        invalidateCampusCache()
        clearAdminCampusCache()
    }

    /**
     * Updates campus metadata fields in Firestore using merge so that
     * only the provided fields are overwritten (preserves createdBy etc.).
     */
    suspend fun updateCampusMetadata(fields: Map<String, Any>) = withContext(Dispatchers.IO) {
        firestore
            .collection("campuses")
            .document(campusId)
            .set(fields, com.google.firebase.firestore.SetOptions.merge())
            .await()
        invalidateCampusCache()
        clearAdminCampusCache()
    }

    /**
     * Updates building metadata fields in Firestore using merge.
     */
    suspend fun updateBuildingMetadata(
        buildingId: String,
        fields: Map<String, Any?>
    ) = withContext(Dispatchers.IO) {
        firestore
            .collection("campuses")
            .document(campusId)
            .collection("buildings")
            .document(buildingId)
            .set(fields, com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    // ── Precalculated navigation data ─────────────────────────────

    /**
     * Uploads precalculated metadata document.
     */
    suspend fun uploadPrecalculatedMetadata(
        metadata: PrecalculatedNavMetadata
    ) = withContext(Dispatchers.IO) {
        firestore
            .collection("campuses").document(campusId)
            .collection("precalculated_nav")
            .document("metadata")
            .set(mapOf(
                "version" to metadata.version,
                "computedAt" to metadata.computedAt,
                "gridSize" to metadata.gridSize,
                "floorIds" to metadata.floorIds
            ))
            .await()
    }

    /**
     * Uploads a precalculated distance-transform grid for one floor.
     */
    suspend fun uploadPrecalculatedGrid(
        floorId: String,
        grid: PrecalculatedFloorGrid
    ) = withContext(Dispatchers.IO) {
        firestore
            .collection("campuses").document(campusId)
            .collection("precalculated_nav")
            .document("grid_$floorId")
            .set(mapOf(
                "originX" to grid.originX,
                "originY" to grid.originY,
                "maxGridX" to grid.maxGridX,
                "maxGridY" to grid.maxGridY,
                "gridSize" to grid.gridSize,
                "gridData" to grid.gridData
            ))
            .await()
    }

    /**
     * Loads precalculated metadata, or null if not available.
     */
    suspend fun loadPrecalculatedMetadata(): PrecalculatedNavMetadata? =
        withContext(Dispatchers.IO) {
            try {
                val doc = firestore
                    .collection("campuses").document(campusId)
                    .collection("precalculated_nav")
                    .document("metadata")
                    .get().await()
                if (!doc.exists()) return@withContext null
                PrecalculatedNavMetadata(
                    version = doc.getLong("version")?.toInt() ?: 1,
                    computedAt = doc.getLong("computedAt") ?: 0L,
                    gridSize = doc.getDouble("gridSize")?.toFloat() ?: 15f,
                    floorIds = (doc.get("floorIds") as? List<*>)
                        ?.mapNotNull { it as? String } ?: emptyList()
                )
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Loads all precalculated floor grids for this campus.
     * Returns null if no precalculated data exists.
     */
    suspend fun loadAllPrecalculatedGrids(): Map<String, PrecalculatedFloorGrid>? =
        withContext(Dispatchers.IO) {
            try {
                val metadata = loadPrecalculatedMetadata() ?: return@withContext null
                val grids = mutableMapOf<String, PrecalculatedFloorGrid>()
                for (floorId in metadata.floorIds) {
                    val doc = firestore
                        .collection("campuses").document(campusId)
                        .collection("precalculated_nav")
                        .document("grid_$floorId")
                        .get().await()
                    if (!doc.exists()) continue
                    grids[floorId] = PrecalculatedFloorGrid(
                        floorId = floorId,
                        originX = doc.getDouble("originX")?.toFloat() ?: 0f,
                        originY = doc.getDouble("originY")?.toFloat() ?: 0f,
                        maxGridX = doc.getLong("maxGridX")?.toInt() ?: 0,
                        maxGridY = doc.getLong("maxGridY")?.toInt() ?: 0,
                        gridSize = doc.getDouble("gridSize")?.toFloat() ?: 15f,
                        gridData = doc.getString("gridData") ?: ""
                    )
                }
                if (grids.isEmpty()) null else grids
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Deletes all precalculated navigation data for this campus.
     * Called when floor data changes to invalidate stale grids.
     */
    suspend fun deletePrecalculatedNav() = withContext(Dispatchers.IO) {
        val navCol = firestore
            .collection("campuses").document(campusId)
            .collection("precalculated_nav")
        val docs = navCol.get().await()
        for (doc in docs.documents) {
            doc.reference.delete().await()
        }
    }

    // ── Landmark operations ────────────────────────────────────

    /**
     * Saves a new landmark to Firestore under `campuses/{campusId}/landmarks`.
     * Uses auto-generated document ID.
     *
     * @return The generated document ID.
     */
    suspend fun saveLandmark(landmark: Landmark): String = withContext(Dispatchers.IO) {
        val landmarksCol = firestore
            .collection("campuses").document(campusId)
            .collection("landmarks")

        val data = mapOf(
            "name" to landmark.name,
            "icon" to landmark.icon,
            "x" to landmark.x,
            "y" to landmark.y,
            "floorId" to landmark.floorId,
            "buildingId" to landmark.buildingId,
            "campusX" to landmark.campusX,
            "campusY" to landmark.campusY
        )

        val docRef = landmarksCol.add(data).await()
        docRef.id
    }

    /**
     * Loads all landmarks for this campus.
     */
    suspend fun loadLandmarks(): List<Landmark> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore
                .collection("campuses").document(campusId)
                .collection("landmarks")
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                try {
                    Landmark(
                        id = doc.id,
                        name = doc.getString("name") ?: return@mapNotNull null,
                        icon = doc.getString("icon") ?: "",
                        x = doc.getDouble("x")?.toFloat() ?: 0f,
                        y = doc.getDouble("y")?.toFloat() ?: 0f,
                        floorId = doc.getString("floorId") ?: "",
                        buildingId = doc.getString("buildingId") ?: "",
                        campusX = doc.getDouble("campusX")?.toFloat() ?: 0f,
                        campusY = doc.getDouble("campusY")?.toFloat() ?: 0f
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Updates an existing landmark document in Firestore.
     */
    suspend fun updateLandmark(landmark: Landmark) = withContext(Dispatchers.IO) {
        val data = mapOf(
            "name" to landmark.name,
            "icon" to landmark.icon,
            "x" to landmark.x,
            "y" to landmark.y,
            "floorId" to landmark.floorId,
            "buildingId" to landmark.buildingId,
            "campusX" to landmark.campusX,
            "campusY" to landmark.campusY
        )

        firestore
            .collection("campuses").document(campusId)
            .collection("landmarks").document(landmark.id)
            .set(data)
            .await()
    }

    /**
     * Deletes a landmark document from Firestore.
     */
    suspend fun deleteLandmark(landmarkId: String) = withContext(Dispatchers.IO) {
        firestore
            .collection("campuses").document(campusId)
            .collection("landmarks").document(landmarkId)
            .delete()
            .await()
    }

    // ── Room Info operations ────────────────────────────────────

    /**
     * Loads all room info documents for this campus.
     * Used for populating the search index and tag matching.
     *
     * @return List of RoomInfo objects with description and tags.
     */
    suspend fun loadAllRoomInfo(): List<RoomInfo> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore
                .collection("campuses").document(campusId)
                .collection("room_info")
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                try {
                    RoomInfo(
                        buildingId = doc.getString("buildingId") ?: return@mapNotNull null,
                        floorId = doc.getString("floorId") ?: return@mapNotNull null,
                        roomId = doc.getLong("roomId")?.toInt() ?: return@mapNotNull null,
                        description = doc.getString("description") ?: "",
                        tags = (doc.get("tags") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Loads room info for a specific room.
     *
     * @param buildingId Building ID
     * @param floorId Floor ID
     * @param roomId Room ID
     * @return RoomInfo if exists, or null if no info document found
     */
    suspend fun loadRoomInfo(buildingId: String, floorId: String, roomId: Int): RoomInfo? =
        withContext(Dispatchers.IO) {
            try {
                val docId = "${buildingId}_${floorId}_$roomId"
                val doc = firestore
                    .collection("campuses").document(campusId)
                    .collection("room_info")
                    .document(docId)
                    .get()
                    .await()

                if (!doc.exists()) return@withContext null

                RoomInfo(
                    buildingId = doc.getString("buildingId") ?: buildingId,
                    floorId = doc.getString("floorId") ?: floorId,
                    roomId = doc.getLong("roomId")?.toInt() ?: roomId,
                    description = doc.getString("description") ?: "",
                    tags = (doc.get("tags") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                )
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Saves or updates room info in Firestore.
     * Uses set() with merge so that existing descriptions/tags are overwritten cleanly.
     *
     * @param buildingId Building ID
     * @param floorId Floor ID
     * @param roomId Room ID
     * @param description Room description
     * @param tags List of tag strings
     */
    suspend fun saveRoomInfo(
        buildingId: String,
        floorId: String,
        roomId: Int,
        description: String,
        tags: List<String>
    ) = withContext(Dispatchers.IO) {
        val docId = "${buildingId}_${floorId}_$roomId"
        val data = mapOf(
            "buildingId" to buildingId,
            "floorId" to floorId,
            "roomId" to roomId,
            "description" to description,
            "tags" to tags
        )

        firestore
            .collection("campuses").document(campusId)
            .collection("room_info")
            .document(docId)
            .set(data)
            .await()
    }

    companion object {
        /**
         * In-memory cache of all campus documents. Populated lazily on the
         * first search and invalidated when a campus is created or deleted.
         */
        private var cachedCampuses: List<Pair<String, String>>? = null

        /**
         * In-memory cache of campuses created by a specific admin UID.
         * Keyed by UID so switching admins works correctly.
         */
        private var adminCampusCache: Pair<String, List<Pair<String, String>>>? = null

        /**
         * Searches campuses whose name or ID contains [query] (case-insensitive).
         * Returns an empty list when [query] is blank.
         *
         * Internally fetches all campuses on the first call and caches them;
         * subsequent calls filter from the in-memory cache for instant results.
         */
        suspend fun searchCampuses(
            query: String,
            firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
        ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()

            val all = cachedCampuses ?: run {
                val snapshot = firestore.collection("campuses").get().await()
                snapshot.documents.map { doc ->
                    val name = doc.getString("name") ?: doc.id
                    doc.id to name
                }.sortedBy { it.second }.also { cachedCampuses = it }
            }

            val normalizedQuery = query.replace(Regex("[\\s.,;:!?'\"\\-_/\\\\]"), "").lowercase()

            all.filter { (id, name) ->
                val normalizedName = name.replace(Regex("[\\s.,;:!?'\"\\-_/\\\\]"), "").lowercase()
                val normalizedId   = id.replace(Regex("[\\s.,;:!?'\"\\-_/\\\\]"), "").lowercase()
                normalizedName.contains(normalizedQuery) || normalizedId.contains(normalizedQuery)
            }
        }

        /** Clears the in-memory campus list so the next search re-fetches. */
        fun invalidateCampusCache() {
            cachedCampuses = null
        }

        /**
         * Fetches all campuses with their GPS coordinates from Firestore.
         * Skips campuses without valid coordinates.
         */
        suspend fun getAllCampusesWithLocation(
            firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
        ): List<CampusLocationInfo> = withContext(Dispatchers.IO) {
            val snapshot = firestore.collection("campuses").get().await()
            snapshot.documents.mapNotNull { doc ->
                val name = doc.getString("name") ?: doc.id
                val lat = doc.getDouble("latitude") ?: return@mapNotNull null
                val lng = doc.getDouble("longitude") ?: return@mapNotNull null
                if (lat == 0.0 && lng == 0.0) return@mapNotNull null
                CampusLocationInfo(id = doc.id, name = name, latitude = lat, longitude = lng)
            }
        }

        /**
         * Creates a new campus document with the admin's UID.
         */
        suspend fun createCampus(
            campusId: String,
            metadata: CampusMetadata,
            adminUid: String,
            firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
        ) = withContext(Dispatchers.IO) {
            val metadataWithOwner = metadata.copy(createdBy = adminUid)
            firestore
                .collection("campuses")
                .document(campusId)
                .set(metadataWithOwner)
                .await()
            invalidateCampusCache()
        }

        /**
         * Returns all campuses created by the given admin UID.
         * Results are cached in memory; subsequent calls return instantly.
         */
        suspend fun getCampusesByAdmin(
            adminUid: String,
            firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
        ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
            adminCampusCache?.let { (uid, campuses) ->
                if (uid == adminUid) return@withContext campuses
            }

            val snapshot = firestore.collection("campuses")
                .whereEqualTo("createdBy", adminUid)
                .get()
                .await()
            val result = snapshot.documents.map { doc ->
                val name = doc.getString("name") ?: doc.id
                doc.id to name
            }.sortedBy { it.second }

            adminCampusCache = adminUid to result
            result
        }

        /** Clears the admin campus cache (call on logout). */
        fun clearAdminCampusCache() {
            adminCampusCache = null
        }

        /**
         * Determines the floor data type from a filename.
         * Matches patterns like "floor_1_walls.json", "walls.json", etc.
         */
        fun detectDataType(fileName: String): String? {
            val lower = fileName.lowercase()
            return when {
                lower.contains("wall") -> "walls"
                lower.contains("stair") -> "stairs"
                lower.contains("entrance") -> "entrances"
                lower.contains("room") -> "rooms"
                lower.contains("boundary") -> "boundary"
                else -> null
            }
        }
    }
}
