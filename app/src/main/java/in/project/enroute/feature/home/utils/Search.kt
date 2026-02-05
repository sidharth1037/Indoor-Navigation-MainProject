package `in`.project.enroute.feature.home.utils

import android.content.Context
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import `in`.project.enroute.data.model.Room
import java.io.InputStreamReader

/**
 * Represents a single search result.
 * Contains the location (x, y coordinates) and the label (room name/number) of the result.
 */
data class SearchResult(
    val x: Float,
    val y: Float,
    val label: String
)

/**
 * Singleton cache for loaded room data.
 * Stores rooms by floor ID to avoid reloading JSON files on subsequent searches.
 * Rooms are loaded on-demand from context.assets.
 */
object SearchCache {
    private val cachedRooms = mutableMapOf<String, List<Room>>()
    
    /**
     * Gets rooms for a specific floor, loading from assets if not cached.
     * @param context Android context for asset access
     * @param floorId Floor identifier (e.g., "floor_1", "floor_1.5")
     * @return List of Room objects, empty list if load fails
     */
    fun getRooms(context: Context, floorId: String): List<Room> {
        return cachedRooms.getOrPut(floorId) {
            loadRoomsFromAssets(context, floorId)
        }
    }
    
    /**
     * Clears the cache, forcing fresh loads on next getRooms() calls.
     * Useful for testing or if floor data changes.
     */
    fun clearCache() {
        cachedRooms.clear()
    }
    
    /**
     * Loads room data from JSON file in assets.
     * Follows the LocalFloorPlanRepository pattern.
     */
    private fun loadRoomsFromAssets(context: Context, floorId: String): List<Room> {
        return try {
            val fileName = "${floorId}_rooms.json"
            val inputStream = context.assets.open(fileName)
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
 * Searches for rooms by a query string using prefix matching on room labels.
 * Results are cached on first load to avoid re-reading JSON files on subsequent calls.
 * 
 * @param context Android context for asset access
 * @param floorId Floor identifier to search (e.g., "floor_1")
 * @param query Search query string - performs case-insensitive prefix match on room labels
 * @return List of SearchResult objects matching the query, sorted by label
 */
fun search(context: Context, floorId: String, query: String): List<SearchResult> {
    if (query.isBlank()) return emptyList()
    
    val rooms = SearchCache.getRooms(context, floorId)
    val normalizedQuery = query.trim().lowercase()
    
    return rooms
        .filter { room ->
            val label = (room.name ?: room.number?.toString() ?: "").lowercase()
            label.startsWith(normalizedQuery)
        }
        .map { room ->
            SearchResult(
                x = room.x,
                y = room.y,
                label = room.name ?: room.number?.toString() ?: "Unknown"
            )
        }
        .sortedBy { it.label }
}

/**
 * Searches for rooms across all available floors using prefix matching on room labels.
 * Results are cached on first load to avoid re-reading JSON files on subsequent calls.
 * Automatically discovers all room JSON files in assets (files ending with _rooms.json).
 * 
 * @param context Android context for asset access
 * @param query Search query string - performs case-insensitive prefix match on room labels
 * @return List of SearchResult objects matching the query from all floors, sorted by label
 */
fun searchMultiFloor(context: Context, query: String): List<SearchResult> {
    if (query.isBlank()) return emptyList()
    
    val normalizedQuery = query.trim().lowercase()
    val allResults = mutableListOf<SearchResult>()
    
    // Dynamically discover all room JSON files in assets
    val assetFiles = context.assets.list("") ?: emptyArray()
    val roomFiles = assetFiles.filter { it.endsWith("_rooms.json") }
    
    for (fileName in roomFiles) {
        // Extract floor ID from filename (e.g., "floor_1_rooms.json" -> "floor_1")
        val floorId = fileName.replace("_rooms.json", "")
        
        val rooms = SearchCache.getRooms(context, floorId)
        rooms
            .filter { room ->
                val label = (room.name ?: room.number?.toString() ?: "").lowercase()
                label.startsWith(normalizedQuery)
            }
            .forEach { room ->
                allResults.add(
                    SearchResult(
                        x = room.x,
                        y = room.y,
                        label = room.name ?: room.number?.toString() ?: "Unknown"
                    )
                )
            }
    }
    
    return allResults.sortedBy { it.label }
}

/**
 * A button composable that performs an action based on destination coordinates.
 * Typically used to navigate to or highlight a specific location on the floor plan.
 * 
 * @param coordinates Pair of (x, y) coordinates in floor plan space
 * @param onNavigate Callback function called when button is clicked with the coordinates
 * @param label Display text for the button (default: room label or coordinates)
 * @param modifier Modifier for the button
 */
@Composable
fun DestinationButton(
    coordinates: Pair<Float, Float>,
    onNavigate: (x: Float, y: Float) -> Unit,
    label: String = "Navigate",
    modifier: Modifier = Modifier
) {
    Button(
        onClick = {
            onNavigate(coordinates.first, coordinates.second)
        },
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        interactionSource = remember { MutableInteractionSource() }
    ) {
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = "Navigate to destination",
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(text = label)
    }
}
