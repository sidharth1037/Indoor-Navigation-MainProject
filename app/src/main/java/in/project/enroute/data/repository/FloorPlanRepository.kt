package `in`.project.enroute.data.repository

import `in`.project.enroute.data.model.FloorPlanData
import `in`.project.enroute.data.model.FloorPlanMetadata

/**
 * Repository interface for loading floor plan data.
 * Abstracts the data source - can be local assets or remote backend.
 */
interface FloorPlanRepository {
    
    /**
     * Loads complete floor plan data for the specified floor within a building.
     * @param buildingId Identifier for the building (e.g., "building_1")
     * @param floorId Identifier for the floor (e.g., "floor_1")
     * @return FloorPlanData containing walls, stairwells, entrances, rooms, and boundary
     */
    suspend fun loadFloorPlan(buildingId: String, floorId: String): FloorPlanData
    
    /**
     * Loads building metadata (scale, rotation, relative position, etc.).
     * @param buildingId Identifier for the building
     * @return FloorPlanMetadata for the building
     */
    suspend fun loadBuildingMetadata(buildingId: String): FloorPlanMetadata
    
    /**
     * Gets list of available floor IDs for a building.
     * @param buildingId Identifier for the building
     * @return List of floor identifiers
     */
    suspend fun getAvailableFloors(buildingId: String): List<String>
    
    /**
     * Gets list of all available building IDs on the campus.
     * @return List of building identifiers
     */
    suspend fun getAvailableBuildings(): List<String>
}
