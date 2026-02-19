package `in`.project.enroute.feature.home.utils

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import `in`.project.enroute.data.model.Room
import java.io.InputStreamReader

/**
 * Represents a single search result.
 * Contains the location (x, y coordinates), the label (room name/number), and the Room object.
 */
data class SearchResult(
    val x: Float,
    val y: Float,
    val label: String?,
    val roomNo: Int?,
    val room: Room
)

/**
 * Singleton cache for loaded room data.
 * Stores rooms by "buildingId/floorId" key to avoid reloading JSON files on subsequent searches.
 * Rooms are loaded on-demand from context.assets using the campus directory structure.
 */
object SearchCache {
    private val cachedRooms = mutableMapOf<String, List<Room>>()
    
    /**
     * Gets rooms for a specific floor in a building, loading from assets if not cached.
     * @param context Android context for asset access
     * @param buildingId Building identifier (e.g., "building_1")
     * @param floorId Floor identifier (e.g., "floor_1", "floor_1.5")
     * @return List of Room objects, empty list if load fails
     */
    fun getRooms(context: Context, buildingId: String, floorId: String): List<Room> {
        val key = "$buildingId/$floorId"
        return cachedRooms.getOrPut(key) {
            loadRoomsFromAssets(context, buildingId, floorId).map {
                it.copy(floorId = floorId, buildingId = buildingId)
            }
        }
    }
    
    /**
     * Clears the cache, forcing fresh loads on next getRooms() calls.
     * Useful for testing or if floor data changes.
     */
    @Suppress("unused")
    fun clearCache() {
        cachedRooms.clear()
    }
    
    /**
     * Loads room data from JSON file in assets.
     * Uses the campus directory structure: campus/{buildingId}/{floorId}/{floorId}_rooms.json
     */
    private fun loadRoomsFromAssets(context: Context, buildingId: String, floorId: String): List<Room> {
        return try {
            val filePath = "campus/$buildingId/$floorId/${floorId}_rooms.json"
            val inputStream = context.assets.open(filePath)
            val reader = InputStreamReader(inputStream)
            
            val gson = Gson()
            val jsonObject = gson.fromJson(reader, JsonObject::class.java)
            val roomsArray = jsonObject.getAsJsonArray("rooms")
            
            val roomListType = object : TypeToken<List<Room>>() {}.type
            val rooms: List<Room> = gson.fromJson(roomsArray, roomListType)
            
            reader.close()
            rooms
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

/**
 * Searches for rooms across all available buildings and floors using prefix matching.
 * Results are cached on first load to avoid re-reading JSON files on subsequent calls.
 * Automatically discovers all buildings under campus/ and their floors.
 * If query is numeric, searches by room number. If query is string, searches by label.
 * 
 * @param context Android context for asset access
 * @param query Search query string - performs case-insensitive prefix match on room labels or room numbers
 * @return List of SearchResult objects matching the query from all buildings/floors, sorted by room number or label
 */
fun searchMultiFloor(context: Context, query: String): List<SearchResult> {
    if (query.isBlank()) return emptyList()
    
    val normalizedQuery = query.trim()
    val isNumericQuery = normalizedQuery.all { it.isDigit() }
    val allResults = mutableListOf<SearchResult>()
    
    // Discover all buildings under campus/
    val buildings = context.assets.list("campus") ?: emptyArray()
    
    for (buildingId in buildings) {
        // Discover all floors under this building
        val floors = context.assets.list("campus/$buildingId") ?: emptyArray()
        val floorDirs = floors.filter { it.startsWith("floor_") }
        
        for (floorId in floorDirs) {
            val rooms = SearchCache.getRooms(context, buildingId, floorId)
        
            if (isNumericQuery) {
                // Search and sort by room number
                rooms
                    .filter { room ->
                        room.number?.toString()?.startsWith(normalizedQuery) ?: false
                    }
                    .forEach { room ->
                        allResults.add(
                            SearchResult(
                                x = room.x,
                                y = room.y,
                                label = room.name,
                                roomNo = room.number,
                                room = room
                            )
                        )
                    }
            } else {
                // Search and sort by label (name)
                rooms
                    .filter { room ->
                        val label = (room.name ?: "").lowercase()
                        label.startsWith(normalizedQuery.lowercase())
                    }
                    .forEach { room ->
                        allResults.add(
                            SearchResult(
                                x = room.x,
                                y = room.y,
                                label = room.name,
                                roomNo = room.number,
                                room = room
                            )
                        )
                    }
            }
        }
    }
    
    return if (isNumericQuery) {
        allResults.sortedBy { it.roomNo ?: Int.MAX_VALUE }
    } else {
        allResults.sortedBy { it.label ?: "" }
    }
}

/**
 * A button composable that performs an action based on destination coordinates.
 * Typically used to navigate to or highlight a specific location on the floor plan.
 * Displays both room number and label if available.
 * 
 * @param coordinates Pair of (x, y) coordinates in floor plan space
 * @param onNavigate Callback function called when button is clicked with the coordinates
 * @param label Display text for the button (room name/label)
 * @param roomNumber Room number to display (optional)
 * @param modifier Modifier for the button
 */
@Composable
fun DestinationButton(
    coordinates: Pair<Float, Float>,
    onNavigate: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    roomNumber: Int? = null
) {
    val displayText = buildString {
        roomNumber?.let { append(" $it") }
        if (roomNumber != null && label != null) append(" : ")
        label?.let { append(it) }
        if (isEmpty()) append("Navigate")
    }
    
    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() }
        ) {
            onNavigate(coordinates.first, coordinates.second)
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = "Navigate to destination",
                modifier = Modifier
                    .size(25.dp)
                    .padding(end = 8.dp)
            )
            Text(text = displayText)
        }
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth(fraction = 0.85f)
                .align(Alignment.BottomCenter)
        )
    }
}
