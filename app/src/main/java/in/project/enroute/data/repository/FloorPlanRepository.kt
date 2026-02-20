package `in`.project.enroute.data.repository

import `in`.project.enroute.data.model.CampusMetadata
import `in`.project.enroute.data.model.FloorPlanData
import `in`.project.enroute.data.model.FloorPlanMetadata

/**
 * Repository interface for loading campus and floor plan data.
 * Abstracts the data source — can be local assets or a remote backend.
 *
 * Design notes for backend implementations:
 * - Campus metadata is loaded once and cached for the session lifetime.
 *   It persists until the user explicitly closes or switches campuses.
 * - Building and floor data can be fetched lazily as needed.
 * - All methods are suspend functions to support both local I/O and network calls.
 *
 * Suggested REST mapping:
 *   loadCampusMetadata()              → GET /api/campuses/{campusId}
 *   getAvailableBuildings()           → GET /api/campuses/{campusId}/buildings
 *   loadBuildingMetadata(buildingId)  → GET /api/buildings/{buildingId}
 *   getAvailableFloors(buildingId)    → GET /api/buildings/{buildingId}/floors
 *   loadFloorPlan(buildingId, floor)  → GET /api/buildings/{buildingId}/floors/{floorId}
 */
interface FloorPlanRepository {

    // ── Campus-level ─────────────────────────────────────────────

    /**
     * Loads campus-wide metadata (name, location, north bearing, etc.).
     * Should be called once when a campus is opened and cached for the session.
     * @return CampusMetadata for the current campus
     */
    suspend fun loadCampusMetadata(): CampusMetadata

    /**
     * Gets list of all available building IDs on the campus.
     * @return Sorted list of building identifiers (e.g., ["building_1", "building_2"])
     */
    suspend fun getAvailableBuildings(): List<String>

    // ── Building-level ───────────────────────────────────────────

    /**
     * Loads building metadata (scale, rotation, relative position, label, etc.).
     * @param buildingId Identifier for the building (e.g., "building_1")
     * @return FloorPlanMetadata for the building
     */
    suspend fun loadBuildingMetadata(buildingId: String): FloorPlanMetadata

    /**
     * Gets list of available floor IDs for a building, sorted bottom-to-top.
     * @param buildingId Identifier for the building
     * @return Sorted list of floor identifiers (e.g., ["floor_1", "floor_1.5", "floor_2"])
     */
    suspend fun getAvailableFloors(buildingId: String): List<String>

    // ── Floor-level ──────────────────────────────────────────────

    /**
     * Loads complete floor plan data for the specified floor within a building.
     * @param buildingId Identifier for the building (e.g., "building_1")
     * @param floorId Identifier for the floor (e.g., "floor_1")
     * @return FloorPlanData containing walls, stairwells, entrances, rooms, and boundary
     */
    suspend fun loadFloorPlan(buildingId: String, floorId: String): FloorPlanData
}
