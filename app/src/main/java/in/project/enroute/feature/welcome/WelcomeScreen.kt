package `in`.project.enroute.feature.welcome

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.project.enroute.data.cache.FloorPlanCache
import `in`.project.enroute.data.repository.FirebaseFloorPlanRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ── ViewModel ────────────────────────────────────────────────────

data class CampusItem(val id: String, val name: String)

data class WelcomeUiState(
    val query: String = "",
    val allCampuses: List<CampusItem> = emptyList(),
    val filteredCampuses: List<CampusItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class WelcomeViewModel(application: Application) : AndroidViewModel(application) {

    private val cache = FloorPlanCache(application.applicationContext)

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    init {
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
                state.allCampuses.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    it.id.contains(query, ignoreCase = true)
                }
            }
            state.copy(query = query, filteredCampuses = filtered)
        }
    }

    /** Retry loading campuses after an error. */
    fun retryLoadCampuses() {
        loadCampuses()
    }

    fun isCached(campusId: String): Boolean = cache.hasCachedCampus(campusId)
}

// ── Composables ──────────────────────────────────────────────────

// Layout constants (dp) for the search-button morph animation.
// The button starts as a narrow centered pill. When morphing it expands
// and translates up to align with the search bar in WelcomeSearchScreen.
//
// WelcomeSearchScreen Row: padding(start=4, end=16, top=8)
//   back button = 48 dp  →  search box left edge = 4+48 = 52 dp
//   search box right edge = screenWidth − 16 dp
// So morphed: paddingStart=52dp, paddingEnd=16dp (total=68dp removed from width)
// Resting:    paddingStart=60dp, paddingEnd=60dp (narrow pill, 120dp removed)
const val MORPH_DURATION_MS = 300
private val TITLE_TOP_SPACER = 120.dp
private val TITLE_HEIGHT = 48.dp
private val SUBTITLE_SPACER = 8.dp
private val SUBTITLE_HEIGHT = 20.dp
private val BUTTON_SPACER = 48.dp

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
    var showSearch by remember { mutableStateOf(false) }
    var isMorphing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Background content (title + subtitle) ────────────────
        // Fade out when morphing/search is active
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
        }

        // ── Morphing search button ───────────────────────────────
        // Always rendered (even when search is open) so it can reverse-animate
        // back to resting position when the user returns from the search screen.
        WelcomeSearchButton(
            isMorphing = isMorphing,
            onAnimationFinished = { showSearch = true },
            onClick = { isMorphing = true }
        )

        // ── Search screen overlay ────────────────────────────────
        AnimatedVisibility(
            visible = showSearch,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150))
        ) {
            WelcomeSearchScreen(
                viewModel = viewModel,
                onBack = {
                    showSearch = false
                    // Let the search screen fade out (150ms), then start the
                    // reverse morph so the button animates back to resting.
                    coroutineScope.launch {
                        delay(100)
                        isMorphing = false
                        delay(300)
                        viewModel.updateQuery("")   // reset search after animation
                    }
                },
                onCampusSelected = { id ->
                    onCampusSelected(id)
                }
            )
        }
    }
}

// ── Search bar button (morphing) ─────────────────────────────────

/**
 * A pill-shaped button that morphs to match the search bar in [WelcomeSearchScreen].
 *
 * Forward (isMorphing = true):
 *   - Expands horizontally: paddingH 60→(52 start, 16 end)
 *   - Translates up: Y = restingY → 8.dp
 *
 * Reverse (isMorphing = false):
 *   - The same animation in reverse — this works because the button is
 *     always in the composition (not hidden when the search screen is open)
 *     so Compose can animate back to resting from the morphed state.
 */
@Composable
private fun WelcomeSearchButton(
    isMorphing: Boolean,
    onAnimationFinished: () -> Unit,
    onClick: () -> Unit
) {
    // Resting Y: just below title + subtitle
    val restingY = TITLE_TOP_SPACER + TITLE_HEIGHT + SUBTITLE_SPACER + SUBTITLE_HEIGHT + BUTTON_SPACER
    // Morphed Y: aligns with the search bar Row in WelcomeSearchScreen (top=8dp)
    val targetY = 8.dp

    val yOffset by animateDpAsState(
        targetValue = if (isMorphing) targetY else restingY,
        animationSpec = tween(MORPH_DURATION_MS),
        label = "morph_y"
    )

    // Resting: narrow centered pill (60dp each side)
    // Morphed: aligns exactly with WelcomeSearchScreen's search box area
    //          start = 4dp (Row) + 48dp (back btn) = 52dp
    //          end   = 16dp (Row end padding)
    val startPadding by animateDpAsState(
        targetValue = if (isMorphing) 52.dp else 60.dp,
        animationSpec = tween(MORPH_DURATION_MS),
        label = "morph_start"
    )
    val endPadding by animateDpAsState(
        targetValue = if (isMorphing) 16.dp else 60.dp,
        animationSpec = tween(MORPH_DURATION_MS),
        label = "morph_end"
    )

    LaunchedEffect(isMorphing) {
        if (isMorphing) {
            delay((MORPH_DURATION_MS - 20).toLong())
            onAnimationFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPadding, end = endPadding)
            .offset(y = yOffset)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(enabled = !isMorphing) { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Search for your campus...",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
