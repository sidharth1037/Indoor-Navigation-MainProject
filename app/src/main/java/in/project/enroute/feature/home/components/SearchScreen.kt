package `in`.project.enroute.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import `in`.project.enroute.feature.home.utils.searchMultiFloor
import `in`.project.enroute.feature.home.utils.DestinationButton
import `in`.project.enroute.feature.home.utils.SearchResult
import `in`.project.enroute.data.model.Room

/**
 * Full-screen search page showing an input box at the top.
 * Uses the same palette as the rest of the app (MaterialTheme.colorScheme).
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onCenterView: (x: Float, y: Float, scale: Float) -> Unit = { _, _, _ -> },
    onRoomTap: (Room) -> Unit = { _ -> }
) {
    val query = remember { mutableStateOf("") }
    val searchResults = remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    // Intercept system Back (gesture/button) and route it to the composable's back handler
    BackHandler(enabled = true) {
        focusManager.clearFocus()
        onBack()
    }
    
    // Track pending navigation to delay it until keyboard is hidden
    val pendingNavigation = remember { mutableStateOf<Pair<Float, Float>?>(null) }
    
    // Search across all available floors when query changes
    LaunchedEffect(query.value) {
        searchResults.value = searchMultiFloor(
            context = context,
            query = query.value
        )
    }

    // Automatically focus the text field and show the keyboard when the screen is shown.
    LaunchedEffect(Unit) {
        // A small delay ensures the transition is underway or finished before requesting focus.
        delay(100)
        focusRequester.requestFocus()
    }

    // Handle delayed navigation after keyboard is hidden
    LaunchedEffect(pendingNavigation.value) {
        if (pendingNavigation.value != null) {
            // Wait for keyboard hide animation (approximately 150ms)
            delay(150)
            val (x, y) = pendingNavigation.value!!
            onCenterView(x, y, 1.2f)
            onBack()
            pendingNavigation.value = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Intercept all pointer events to prevent them from reaching the canvas below
            .pointerInput(Unit) { }
            // Alternatively, use clickable with no indication to consume events
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Consume clicks */ }
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Top search bar: match expanded SearchButton appearance and position.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button to left of the search bar
            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    onBack()
                }, 
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // The search input styled exactly like the expanded SearchButton
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp), contentAlignment = Alignment.CenterStart) {
                            if (query.value.isEmpty()) {
                                Text(
                                    text = "Search for a room or place...",
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }

                            BasicTextField(
                                value = query.value,
                                onValueChange = { query.value = it },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onPrimaryContainer),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                        }

                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                }
            }
        }

        // Search results area
        if (searchResults.value.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text("No results found", color = MaterialTheme.colorScheme.onBackground)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(searchResults.value) { result ->
                    DestinationButton(
                        coordinates = Pair(result.x, result.y),
                        onNavigate = { x, y ->
                            // Pin the room first
                            onRoomTap(result.room)
                            // Hide keyboard immediately
                            focusManager.clearFocus()
                            // Schedule navigation after keyboard hide animation completes
                            pendingNavigation.value = Pair(x, y)
                        },
                        label = result.label,
                        roomNumber = result.roomNo,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
