package `in`.project.enroute.feature.welcome

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.project.enroute.data.cache.RecentCampusStore
import `in`.project.enroute.data.location.NearbyManager
import `in`.project.enroute.data.repository.FirebaseFloorPlanRepository
import `in`.project.enroute.feature.campussearch.CampusItem
import `in`.project.enroute.feature.campussearch.CampusSearchButton
import `in`.project.enroute.feature.campussearch.CampusSearchOverlay
import `in`.project.enroute.feature.campussearch.MORPH_DURATION_MS
import `in`.project.enroute.feature.home.components.OverlayNavButtons
import `in`.project.enroute.feature.admin.auth.AdminAuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── ViewModel ────────────────────────────────────────────────────

data class WelcomeUiState(
    val query: String = "",
    val searchResults: List<CampusItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** True once the first non-blank search has been triggered. */
    val hasSearched: Boolean = false,
    /** Up to 3 recently-viewed campuses (newest first). */
    val recentCampuses: List<CampusItem> = emptyList(),
    /** Nearby buildings based on GPS location. */
    val nearbyBuildings: List<CampusItem> = emptyList(),
    /** True while nearby buildings are being fetched. */
    val isLoadingNearby: Boolean = false,
    /** True once a nearby search has completed at least once (persists across nav). */
    val hasLoadedNearby: Boolean = false,
    /** True if GPS/location is disabled on the device. */
    val locationDisabled: Boolean = false,
    /** True if location permission has not been granted. */
    val locationPermissionNeeded: Boolean = false
)

class WelcomeViewModel(application: Application) : AndroidViewModel(application) {
    private val recentStore = RecentCampusStore(application.applicationContext)
    private val nearbyManager = NearbyManager(application.applicationContext)

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadRecent()
    }

    private fun loadRecent() {
        val recent = recentStore.getRecent().map { CampusItem(it.first, it.second) }
        _uiState.update { it.copy(recentCampuses = recent) }
    }

    /**
     * Called on every keystroke. Debounces 250 ms, then queries the cached
     * campus list via [FirebaseFloorPlanRepository.searchCampuses].
     * Blank queries immediately clear results.
     */
    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), hasSearched = false, error = null, isLoading = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(250) // debounce
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val results = FirebaseFloorPlanRepository.searchCampuses(query)
                val items = results.map { CampusItem(it.first, it.second) }
                _uiState.update {
                    it.copy(searchResults = items, isLoading = false, hasSearched = true)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message, hasSearched = true)
                }
            }
        }
    }

    /** Retry the current query after a network error. */
    fun retrySearch() {
        val q = _uiState.value.query
        if (q.isNotBlank()) {
            _uiState.update { it.copy(query = "") }
            updateQuery(q)
        }
    }

    /** Records a campus as recently viewed and refreshes the UI list. */
    fun addRecent(campusId: String, campusName: String) {
        recentStore.add(campusId, campusName)
        loadRecent()
    }

    /** Removes a campus from the recently-viewed list. */
    fun removeRecent(campusId: String) {
        recentStore.remove(campusId)
        loadRecent()
    }

    /**
     * Loads nearby buildings if not already loaded.
     * Skips the fetch when results are already cached in state
     * (survives navigation within the same app session).
     */
    fun loadNearbyBuildingsIfNeeded() {
        if (_uiState.value.hasLoadedNearby) return
        fetchNearbyBuildings()
    }

    /** Force-refreshes the nearby buildings list (user tapped refresh). */
    fun refreshNearbyBuildings() {
        fetchNearbyBuildings()
    }

    private fun fetchNearbyBuildings() {
        if (!nearbyManager.isLocationEnabled()) {
            _uiState.update {
                it.copy(locationDisabled = true, nearbyBuildings = emptyList(),
                    isLoadingNearby = false, hasLoadedNearby = true,
                    locationPermissionNeeded = false)
            }
            return
        }
        _uiState.update {
            it.copy(isLoadingNearby = true, locationDisabled = false, locationPermissionNeeded = false)
        }
        viewModelScope.launch {
            try {
                val location = nearbyManager.getLastLocation()
                if (location == null) {
                    _uiState.update {
                        it.copy(isLoadingNearby = false, nearbyBuildings = emptyList(), hasLoadedNearby = true)
                    }
                    return@launch
                }
                val allCampuses = FirebaseFloorPlanRepository.getAllCampusesWithLocation()
                val nearby = nearbyManager.filterNearby(allCampuses, location)
                _uiState.update {
                    it.copy(nearbyBuildings = nearby, isLoadingNearby = false, hasLoadedNearby = true)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingNearby = false, hasLoadedNearby = true) }
            }
        }
    }

    /** Signals that location permission is needed. */
    fun markPermissionNeeded() {
        _uiState.update { it.copy(locationPermissionNeeded = true) }
    }

    /** Called after the user grants location permission. */
    fun onPermissionGranted() {
        _uiState.update { it.copy(locationPermissionNeeded = false) }
        fetchNearbyBuildings()
    }
}

// ── Composables ──────────────────────────────────────────────────

private val TITLE_TOP_SPACER = 80.dp
private val TITLE_HEIGHT = 48.dp
private val SUBTITLE_SPACER = 8.dp
private val SUBTITLE_HEIGHT = 20.dp
private val BUTTON_SPACER = 32.dp

/** Resting Y for the search button on the Welcome screen. */
private val WELCOME_BUTTON_RESTING_Y =
    TITLE_TOP_SPACER + TITLE_HEIGHT + SUBTITLE_SPACER + SUBTITLE_HEIGHT + BUTTON_SPACER

/**
 * Welcome screen: app title + morphing search-bar button.
 */
@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel = viewModel(),
    onCampusSelected: (campusId: String) -> Unit,
    onSettingsClick: () -> Unit = {},
    onAdminClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var isMorphing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // ── Location permission launcher ─────────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) viewModel.onPermissionGranted()
    }

    // ── Check permission & load nearby on first composition ──────
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.loadNearbyBuildingsIfNeeded()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Background content (title + subtitle) ────────────────
        val bgAlpha by animateFloatAsState(
            targetValue = if (isMorphing) 0f else 1f,
            animationSpec = tween(200),
            label = "bg_alpha"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .alpha(bgAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(TITLE_TOP_SPACER))

            Text(
                text = "Enroute",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(SUBTITLE_SPACER))

            Text(
                text = "Indoor Navigation",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Space for the search button (it's rendered as an overlay)
            Spacer(modifier = Modifier.height(BUTTON_SPACER + 48.dp + 32.dp))

            // ── Recently viewed campuses ─────────────────────────
            if (uiState.recentCampuses.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // ── Header row ───────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recently viewed",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            uiState.recentCampuses.forEachIndexed { index, campus ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addRecent(campus.id, campus.name)
                                            viewModel.updateQuery("")
                                            onCampusSelected(campus.id)
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = campus.name,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.removeRecent(campus.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (index < uiState.recentCampuses.lastIndex) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Nearby buildings section ──────────────────────────
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // ── Header row ───────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NearMe,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Nearby Buildings",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (!uiState.isLoadingNearby) {
                            IconButton(
                                onClick = { viewModel.refreshNearbyBuildings() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh nearby",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when {
                        // GPS is off
                        uiState.locationDisabled -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Turn on GPS to see nearby buildings",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // Loading
                        uiState.isLoadingNearby -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Finding nearby buildings\u2026",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Results found
                        uiState.nearbyBuildings.isNotEmpty() -> {
                            uiState.nearbyBuildings.forEachIndexed { index, campus ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addRecent(campus.id, campus.name)
                                            onCampusSelected(campus.id)
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = campus.name,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (index < uiState.nearbyBuildings.lastIndex) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }

                        // No results
                        uiState.hasLoadedNearby -> {
                            Text(
                                text = "No nearby buildings found",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Bottom spacing to avoid overlapping the nav buttons
            Spacer(modifier = Modifier.height(80.dp))
        }

        // ── Settings & Admin overlay buttons (bottom left) ───────
        val isAdminLoggedIn by AdminAuthRepository.isLoggedIn.collectAsState()
        OverlayNavButtons(
            isAdminVisible = isAdminLoggedIn,
            onSettingsClick = onSettingsClick,
            onAdminClick = onAdminClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 8.dp, bottom = 16.dp)
        )

        // ── Morphing search button ───────────────────────────────
        CampusSearchButton(
            isMorphing = isMorphing,
            restingY = WELCOME_BUTTON_RESTING_Y,
            onAnimationFinished = { showSearch = true },
            onClick = { isMorphing = true }
        )

        // ── Search screen overlay ────────────────────────────────
        AnimatedVisibility(
            visible = showSearch,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(200))
        ) {
            CampusSearchOverlay(
                query = uiState.query,
                onQueryChange = { viewModel.updateQuery(it) },
                results = uiState.searchResults,
                isLoading = uiState.isLoading,
                error = uiState.error,
                hasSearched = uiState.hasSearched,
                onBack = {
                    showSearch = false
                    coroutineScope.launch {
                        delay(100)
                        isMorphing = false
                        delay(MORPH_DURATION_MS.toLong())
                        viewModel.updateQuery("")
                    }
                },
                onCampusSelected = { id ->
                    val name = uiState.searchResults
                        .firstOrNull { it.id == id }?.name ?: id
                    viewModel.addRecent(id, name)
                    viewModel.updateQuery("")
                    onCampusSelected(id)
                },
                onRetry = { viewModel.retrySearch() }
            )
        }
    }
}
