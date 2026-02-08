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
 * Searches for rooms by a query string using prefix matching on room labels or numbers.
 * Results are cached on first load to avoid re-reading JSON files on subsequent calls.
 * If query is numeric, searches by room number. If query is string, searches by label.
 * 
 * @param context Android context for asset access
 * @param floorId Floor identifier to search (e.g., "floor_1")
 * @param query Search query string - performs case-insensitive prefix match on room labels or exact match on room numbers
 * @return List of SearchResult objects matching the query, sorted by room number or label
 */
fun search(context: Context, floorId: String, query: String): List<SearchResult> {
    if (query.isBlank()) return emptyList()
    
    val rooms = SearchCache.getRooms(context, floorId)
    val normalizedQuery = query.trim()
    val isNumericQuery = normalizedQuery.all { it.isDigit() }
    
    return if (isNumericQuery) {
        // Search and sort by room number
        val queryNumber = normalizedQuery.toIntOrNull()
        rooms
            .filter { room ->
                room.number?.toString()?.startsWith(normalizedQuery) ?: false
            }
            .map { room ->
                SearchResult(
                    x = room.x,
                    y = room.y,
                    label = room.name,
                    roomNo = room.number,
                    room = room
                )
            }
            .sortedBy { it.roomNo ?: Int.MAX_VALUE }
    } else {
        // Search and sort by label (name)
        rooms
            .filter { room ->
                val label = (room.name ?: "").lowercase()
                label.startsWith(normalizedQuery.lowercase())
            }
            .map { room ->
                SearchResult(
                    x = room.x,
                    y = room.y,
                    label = room.name,
                    roomNo = room.number,
                    room = room
                )
            }
            .sortedBy { it.label ?: "" }
    }
}

/**
 * Searches for rooms across all available floors using prefix matching on room labels or numbers.
 * Results are cached on first load to avoid re-reading JSON files on subsequent calls.
 * Automatically discovers all room JSON files in assets (files ending with _rooms.json).
 * If query is numeric, searches by room number. If query is string, searches by label.
 * 
 * @param context Android context for asset access
 * @param query Search query string - performs case-insensitive prefix match on room labels or room numbers
 * @return List of SearchResult objects matching the query from all floors, sorted by room number or label
 */
fun searchMultiFloor(context: Context, query: String): List<SearchResult> {
    if (query.isBlank()) return emptyList()
    
    val normalizedQuery = query.trim()
    val isNumericQuery = normalizedQuery.all { it.isDigit() }
    val allResults = mutableListOf<SearchResult>()
    
    // Dynamically discover all room JSON files in assets
    val assetFiles = context.assets.list("") ?: emptyArray()
    val roomFiles = assetFiles.filter { it.endsWith("_rooms.json") }
    
    for (fileName in roomFiles) {
        // Extract floor ID from filename (e.g., "floor_1_rooms.json" -> "floor_1")
        val floorId = fileName.replace("_rooms.json", "")
        
        val rooms = SearchCache.getRooms(context, floorId)
        
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
