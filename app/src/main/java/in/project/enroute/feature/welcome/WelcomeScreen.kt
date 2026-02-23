package `in`.project.enroute.feature.welcome

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.project.enroute.data.cache.FloorPlanCache
import `in`.project.enroute.data.repository.FirebaseFloorPlanRepository
import `in`.project.enroute.feature.settings.data.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── ViewModel ────────────────────────────────────────────────────

data class CampusItem(val id: String, val name: String)

data class WelcomeUiState(
    val query: String = "",
    val allCampuses: List<CampusItem> = emptyList(),
    val filteredCampuses: List<CampusItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** True when there's already a saved campus → auto-navigate to Home. */
    val savedCampusId: String? = null
)

class WelcomeViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application.applicationContext)
    private val cache = FloorPlanCache(application.applicationContext)

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    init {
        // Check if a campus was previously selected
        viewModelScope.launch {
            val saved = settings.selectedCampusId.first()
            if (saved != null) {
                _uiState.update { it.copy(savedCampusId = saved) }
            }
        }
        // Pre-load the campus list from Firestore
        loadCampuses()
    }

    private fun loadCampuses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val campuses = FirebaseFloorPlanRepository.getAvailableCampuses()
                val items = campuses.map { CampusItem(it.first, it.second) }
                _uiState.update {
                    it.copy(
                        allCampuses = items,
                        filteredCampuses = items,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    fun updateQuery(query: String) {
        _uiState.update { state ->
            val filtered = if (query.isBlank()) {
                state.allCampuses
            } else {
                state.allCampuses.filter { it.name.contains(query, ignoreCase = true) }
            }
            state.copy(query = query, filteredCampuses = filtered)
        }
    }

    /** Persist the selection so later launches skip Welcome. */
    fun selectCampus(campusId: String) {
        viewModelScope.launch {
            settings.saveSelectedCampusId(campusId)
        }
    }

    fun isCached(campusId: String): Boolean = cache.hasCachedCampus(campusId)
}

// ── Composables ──────────────────────────────────────────────────

/**
 * Welcome screen: app title + search-bar button.
 *
 * If a campus was previously selected (persisted in DataStore) the screen
 * auto-navigates to Home immediately, so the user only sees this once or
 * after clearing the selection.
 */
@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel = viewModel(),
    onCampusSelected: (campusId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var isMorphing by remember { mutableStateOf(false) }

    // Auto-navigate if there's a saved campus
    LaunchedEffect(uiState.savedCampusId) {
        uiState.savedCampusId?.let { onCampusSelected(it) }
    }

    // Don't render anything while auto-navigating
    if (uiState.savedCampusId != null) return

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Background content ───────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(120.dp))

            // App name
            Text(
                text = "Enroute",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Campus Navigation",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Search bar button (morphing)
            WelcomeSearchButton(
                isMorphing = isMorphing,
                onAnimationFinished = { showSearch = true },
                onClick = { isMorphing = true }
            )
        }

        // ── Search overlay ───────────────────────────────────────
        AnimatedVisibility(
            visible = showSearch,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            WelcomeSearchOverlay(
                viewModel = viewModel,
                onBack = {
                    showSearch = false
                    isMorphing = false
                },
                onCampusSelected = { id ->
                    viewModel.selectCampus(id)
                    onCampusSelected(id)
                }
            )
        }
    }
}

// ── Search bar button (morphing) ─────────────────────────────────

@Composable
private fun WelcomeSearchButton(
    isMorphing: Boolean,
    onAnimationFinished: () -> Unit,
    onClick: () -> Unit
) {
    val targetWidth = if (isMorphing) 1000.dp else 320.dp // fill width when morphing
    val targetHeight = 52.dp
    val targetCorner = if (isMorphing) 24.dp else 26.dp

    val width by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = tween(300),
        label = "welcome_search_w"
    )
    val corner by animateDpAsState(
        targetValue = targetCorner,
        animationSpec = tween(300),
        label = "welcome_search_c"
    )

    LaunchedEffect(isMorphing) {
        if (isMorphing) {
            delay(250)
            onAnimationFinished()
        }
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(targetHeight)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(enabled = !isMorphing) { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Search for your campus...",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

// ── Full-screen search overlay ───────────────────────────────────

@Composable
private fun WelcomeSearchOverlay(
    viewModel: WelcomeViewModel,
    onBack: () -> Unit,
    onCampusSelected: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    BackHandler { focusManager.clearFocus(); onBack() }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* consume */ }
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar: back + search field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { focusManager.clearFocus(); onBack() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, end = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (uiState.query.isEmpty()) {
                            Text(
                                text = "Search for your campus...",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        BasicTextField(
                            value = uiState.query,
                            onValueChange = { viewModel.updateQuery(it) },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            }
        }

        // Body
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Text(
                    text = "Error: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            uiState.filteredCampuses.isEmpty() -> {
                Text(
                    text = "No campuses found",
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(16.dp)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(uiState.filteredCampuses) { campus ->
                        CampusResultItem(
                            campus = campus,
                            isCached = viewModel.isCached(campus.id),
                            onClick = {
                                focusManager.clearFocus()
                                onCampusSelected(campus.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Result item row ──────────────────────────────────────────────

@Composable
private fun CampusResultItem(
    campus: CampusItem,
    isCached: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = campus.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = campus.id,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isCached) {
            Text(
                text = "cached",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
