package `in`.project.enroute.feature.home.utils

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.project.enroute.data.model.Room
import `in`.project.enroute.feature.floorplan.state.BuildingState

/**
 * Represents a single search result.
 * Contains the location (x, y coordinates), the label (room name/number), building name, and the Room object.
 */
data class SearchResult(
    val x: Float,
    val y: Float,
    val label: String?,
    val roomNo: Int?,
    val buildingName: String,
    val room: Room
)

/**
 * Searches for rooms across all loaded buildings and floors using prefix matching.
 * Uses data already loaded from Firebase via the ViewModel's BuildingState map.
 * If query is numeric, searches by room number. If query is string, searches by label.
 *
 * @param buildingStates Map of building ID to BuildingState from the ViewModel (Firebase data)
 * @param query Search query string - performs case-insensitive prefix match on room labels or room numbers
 * @return List of SearchResult objects matching the query, sorted by room number or label
 */
fun searchRooms(buildingStates: Map<String, BuildingState>, query: String): List<SearchResult> {
    if (query.isBlank()) return emptyList()

    val normalizedQuery = query.trim()
    val isNumericQuery = normalizedQuery.all { it.isDigit() }
    val allResults = mutableListOf<SearchResult>()

    for ((_, buildingState) in buildingStates) {
        val buildingName = buildingState.building.buildingName

        for ((_, floorData) in buildingState.floors) {
            val rooms = floorData.rooms

            if (isNumericQuery) {
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
                                buildingName = buildingName,
                                room = room
                            )
                        )
                    }
            } else {
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
                                buildingName = buildingName,
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
    roomNumber: Int? = null,
    buildingName: String? = null
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
            Column {
                Text(text = displayText)
                if (!buildingName.isNullOrBlank()) {
                    Text(
                        text = buildingName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth(fraction = 0.85f)
                .align(Alignment.BottomCenter)
        )
    }
}
