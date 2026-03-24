package `in`.project.enroute.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import `in`.project.enroute.feature.floorplan.utils.FollowingAnimator
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.project.enroute.feature.floorplan.FloorPlanViewModel
import `in`.project.enroute.feature.floorplan.FloorPlanUiState
import `in`.project.enroute.feature.floorplan.rendering.CanvasState
import `in`.project.enroute.feature.home.components.FloorSlider
import `in`.project.enroute.feature.home.components.SearchButton
import `in`.project.enroute.feature.home.components.SearchScreen
import `in`.project.enroute.feature.home.components.AimButton
import `in`.project.enroute.feature.home.components.CompassButton
import `in`.project.enroute.feature.floorplan.rendering.FloorPlanCanvas
import `in`.project.enroute.feature.floorplan.rendering.FloorPlanLabelsOverlay
import `in`.project.enroute.data.model.Room
import android.graphics.drawable.VectorDrawable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import `in`.project.enroute.feature.pdr.PdrViewModel
import `in`.project.enroute.feature.pdr.PdrUiState
import `in`.project.enroute.feature.pdr.ui.components.OriginSelectionDialog
import `in`.project.enroute.feature.pdr.ui.components.OriginLocationErrorSnackbar

import `in`.project.enroute.feature.pdr.ui.components.PdrPathOverlay
import `in`.project.enroute.feature.pdr.ui.components.HeightRequiredDialog
import `in`.project.enroute.feature.pdr.ui.components.MotionLabel

import `in`.project.enroute.feature.home.components.RoomInfoPanel
import `in`.project.enroute.feature.home.components.StopTrackingButton
import `in`.project.enroute.feature.home.components.StopTrackingConfirmDialog
import `in`.project.enroute.feature.home.components.OverlayNavButtons
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween as dpTween
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.cos
import kotlin.math.sin
import `in`.project.enroute.feature.navigation.NavigationViewModel
import `in`.project.enroute.feature.navigation.NavigationUiState
import `in`.project.enroute.feature.navigation.ui.NavigationPathOverlay
import `in`.project.enroute.feature.settings.SettingsViewModel
import `in`.project.enroute.feature.home.locationselection.CorridorPoint
import `in`.project.enroute.feature.home.locationselection.CorridorPointFinder
import `in`.project.enroute.feature.home.locationselection.EntranceConfirmationPanel
import `in`.project.enroute.feature.home.locationselection.EntranceMarkerOverlay
import `in`.project.enroute.feature.home.locationselection.TapLocationConfirmationPanel
import `in`.project.enroute.feature.home.locationselection.OriginPreviewOverlay
import `in`.project.enroute.feature.home.locationselection.MapViewportUtils
import `in`.project.enroute.feature.home.locationselection.WalkingTutorialDialog
import `in`.project.enroute.feature.home.locationselection.DestinationPromptDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    campusId: String,
    floorPlanViewModel: FloorPlanViewModel = viewModel(),
    pdrViewModel: PdrViewModel = viewModel(),
    navigationViewModel: NavigationViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    onSettingsClick: () -> Unit = {},
    onAdminClick: () -> Unit = {}
) {
    val uiState by floorPlanViewModel.uiState.collectAsState()
    val pdrUiState by pdrViewModel.uiState.collectAsState()
    val navUiState by navigationViewModel.uiState.collectAsState()
    val settingsUiState by settingsViewModel.uiState.collectAsState()
    // Heading collected separately for PDR — not used for compass (compass uses canvas rotation)
    val heading by pdrViewModel.heading.collectAsState()
    val hasFreshHeadingSinceStart by pdrViewModel.hasFreshHeadingSinceStart.collectAsState()
    val view = LocalView.current
    
    // State for origin location error snack bar
    var originErrorType by remember { mutableStateOf<FloorPlanViewModel.OriginErrorType?>(null) }

    // Load all buildings on first composition
    LaunchedEffect(Unit) {
        floorPlanViewModel.loadCampus(campusId)
    }

    // Supply loaded floor data to NavigationViewModel whenever it changes
    LaunchedEffect(uiState.allLoadedFloors) {
        if (uiState.allLoadedFloors.isNotEmpty()) {
            navigationViewModel.supplyFloorData(uiState.allLoadedFloors)
        }
    }

    // Supply stairwell connections for cross-floor navigation
    LaunchedEffect(uiState.buildingStates) {
        if (uiState.buildingStates.isNotEmpty()) {
            val connections = floorPlanViewModel.getStairwellConnections()
            navigationViewModel.supplyStairwellConnections(connections)
        }
    }

    // Load precalculated navigation grids at campus-load time (not when user taps Directions)
    LaunchedEffect(uiState.buildingStates) {
        if (uiState.buildingStates.isNotEmpty()) {
            val grids = floorPlanViewModel.loadPrecalculatedNavData()
            if (grids != null) {
                navigationViewModel.supplyPrecalculatedGrids(grids)
            }
        }
    }

    // Supply building boundary data to PdrViewModel for automatic building detection
    LaunchedEffect(uiState.buildingStates) {
        if (uiState.buildingStates.isNotEmpty()) {
            val campusBuildings = floorPlanViewModel.getCampusBuildings()
            if (campusBuildings.isNotEmpty()) {
                pdrViewModel.loadBuildingData(campusBuildings)
            }

            // Supply stairwell zones and all floor constraint data
            val stairwellZones = floorPlanViewModel.getStairwellZones()
            pdrViewModel.loadStairwellZones(stairwellZones)

            val allFloorData = floorPlanViewModel.getAllFloorConstraintData()
            pdrViewModel.loadAllFloorConstraintData(allFloorData)
        }
    }

    // Auto-switch visible floor when PDR detects a floor change (stairwell transition).
    // Only switch the map if following mode is active — otherwise just update internal state.
    val pdrCurrentFloor = pdrUiState.pdrState.currentFloor
    LaunchedEffect(pdrCurrentFloor) {
        if (pdrCurrentFloor != null && uiState.isFollowingMode) {
            floorPlanViewModel.switchToFloorById(pdrCurrentFloor)
        }
    }

    // Feed user position into NavigationViewModel for progressive path consumption
    // and auto-rerouting. This is intentionally limited to started navigation
    // and step-position updates (not heading ticks) to avoid compute flooding.
    val pdrCurrentPosition = pdrUiState.pdrState.currentPosition
    LaunchedEffect(pdrCurrentPosition, pdrCurrentFloor, navUiState.hasPath, navUiState.isNavigationStarted) {
        if (
            pdrCurrentPosition != null &&
            pdrCurrentFloor != null &&
            navUiState.hasPath &&
            navUiState.isNavigationStarted
        ) {
            navigationViewModel.updateUserPosition(pdrCurrentPosition, pdrCurrentFloor)
        }
    }

    // Turn-by-turn guidance depends on heading, so this pipeline updates on
    // heading ticks, but remains gated behind started navigation.
    LaunchedEffect(pdrCurrentPosition, pdrCurrentFloor, heading, navUiState.hasPath, navUiState.isNavigationStarted) {
        if (
            pdrCurrentPosition != null &&
            pdrCurrentFloor != null &&
            navUiState.hasPath &&
            navUiState.isNavigationStarted
        ) {
            navigationViewModel.updateTurnByTurn(pdrCurrentPosition, pdrCurrentFloor, heading)
        }
    }

    // Keep screen on when PDR tracking is active
    DisposableEffect(pdrUiState.pdrState.isTracking) {
        if (pdrUiState.pdrState.isTracking) {
            view.keepScreenOn = true
        }
        onDispose {
            if (!pdrUiState.pdrState.isTracking) {
                view.keepScreenOn = false
            }
        }
    }

    // Use BoxWithConstraints to get screen dimensions for viewport calculations
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        // Update screen size in ViewModel for viewport calculations
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        
        LaunchedEffect(screenWidth, screenHeight) {
            floorPlanViewModel.updateScreenSize(screenWidth, screenHeight)
        }

        // Compute effective canvas state for following mode inline during composition.
        // This ensures FloorPlanCanvas and PdrPathOverlay see the same state in the same frame,
        // eliminating the brief cone flash at new step positions.
        //
        // IMPORTANT: heading is read INSIDE derivedStateOf (not as a remember key)
        // so that heading changes only re-evaluate the lambda, not recreate it.
        val effectiveCanvasState by remember(
            uiState.isFollowingMode,
            uiState.isFollowingAnimating,
            uiState.canvasState,
            pdrUiState.pdrState.path,
            heading,
            screenWidth,
            screenHeight
        ) {
            derivedStateOf {
                if (uiState.isFollowingMode && !uiState.isFollowingAnimating && pdrUiState.pdrState.path.isNotEmpty()) {
                    // PDR positions are already in campus-wide coordinates
                    val currentPosition = pdrUiState.pdrState.path.last().position
                    FollowingAnimator.calculateFollowingState(
                        worldPosition = currentPosition,
                        headingRadians = heading,
                        scale = uiState.canvasState.scale,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight
                    )
                } else {
                    uiState.canvasState
                }
            }
        }

        // Keep dominant building and floor slider in sync during following mode.
        // The effective canvas state is computed inline (not stored in ViewModel),
        // so the ViewModel doesn't know the viewport changed. Feed it back here.
        LaunchedEffect(effectiveCanvasState) {
            if (uiState.isFollowingMode && !uiState.isFollowingAnimating) {
                floorPlanViewModel.updateCanvasState(effectiveCanvasState, isFromGesture = false)
            }
        }

        // Delegate to content composable
        HomeScreenContent(
            uiState = uiState,
            pdrUiState = pdrUiState,
            navUiState = navUiState,
            heading = heading,
            hasFreshHeadingSinceStart = hasFreshHeadingSinceStart,
            effectiveCanvasState = effectiveCanvasState,
            maxWidth = maxWidth,
            onCanvasStateChange = {
                // If gesture cancels following mode, switch heading back to compass rate
                if (uiState.isFollowingMode) {
                    pdrViewModel.setHeadingTrackingMode(false)
                }
                floorPlanViewModel.updateCanvasState(it)
            },
            onFloorChange = {
                // Disable tracking/following mode when user manually switches floor
                if (uiState.isFollowingMode) {
                    pdrViewModel.setHeadingTrackingMode(false)
                    floorPlanViewModel.disableFollowingMode(effectiveCanvasState)
                }
                floorPlanViewModel.setCurrentFloor(it)
            },
            onCenterView = { x, y, scale, buildingId -> floorPlanViewModel.centerOnCoordinate(x, y, scale, buildingId) },
            onRoomTap = { room ->
                // Switch to the room's floor in the correct building
                room.floorId?.let { fid ->
                    val floorNumber = fid.removePrefix("floor_").toFloatOrNull()
                    if (floorNumber != null) {
                        val buildingId = room.buildingId
                        if (buildingId != null) {
                            floorPlanViewModel.setCurrentFloor(buildingId, floorNumber)
                        } else {
                            floorPlanViewModel.setCurrentFloor(floorNumber)
                        }
                    }
                }
                floorPlanViewModel.pinRoom(room)
            },

            onEnableTracking = { position, heading ->
                // PDR positions are already campus-wide
                pdrViewModel.setHeadingTrackingMode(true)
                floorPlanViewModel.enableFollowingMode(position, heading)
            },
            onAwaitFreshHeading = { pdrViewModel.awaitHeadingAfterSensorsStart() },
            onSetOriginClick = { pdrViewModel.startOriginSelection() },
            onClearPdrClick = {
                // Commit current following position before clearing PDR
                pdrViewModel.setHeadingTrackingMode(false)
                floorPlanViewModel.disableFollowingMode(effectiveCanvasState)
                pdrViewModel.clearAndStop()
                navigationViewModel.clearPath()
            },
            onOriginSelected = { origin ->
                // In HomeScreen, just pass through to validate
                // Actual pending state is managed in HomeScreenContent
                val validation = floorPlanViewModel.validateOriginLocation(origin)
                if (validation.isValid) {
                    val currentFloor = floorPlanViewModel.findFloorAtPoint(origin)
                    val constraintData = floorPlanViewModel.getFloorConstraintData(origin)
                    pdrViewModel.setOrigin(origin, currentFloor, constraintData)
                } else {
                    originErrorType = validation.errorType
                }
            },
            onDismissHeightRequired = { pdrViewModel.dismissHeightRequired() },
            onSaveHeight = { height -> pdrViewModel.saveHeightAndProceed(height) },
            onDirectionsClick = { room ->
                // Positions are all campus-wide
                val currentPosition = pdrUiState.pdrState.path.lastOrNull()?.position
                    ?: pdrUiState.pdrState.origin
                // Use the floor stored in PDR state (set at origin time) so navigation
                // works even when zoomed out with no dominant building.
                val floor = pdrUiState.pdrState.currentFloor ?: uiState.currentFloorId
                if (currentPosition != null && floor != null) {
                    navigationViewModel.requestDirections(room, currentPosition, floor)
                }
            },
            onStartNavigation = {
                navigationViewModel.startNavigation()
            },
            onExitNavigation = {
                navigationViewModel.clearPath()
                pdrViewModel.setHeadingTrackingMode(false)
                floorPlanViewModel.disableFollowingMode(effectiveCanvasState)
                floorPlanViewModel.clearPin()
            },
            onClearOverlay = {
                floorPlanViewModel.clearPin()
            },
            onShowRoomOnMap = { room ->
                floorPlanViewModel.showPinnedRoomOnMap()
                room.floorId?.let { floorPlanViewModel.switchToFloorById(it) }
                floorPlanViewModel.centerOnCoordinate(
                    x = room.x,
                    y = room.y,
                    scale = 1.2f,
                    buildingId = room.buildingId
                )
            },
            onSwitchToFloorById = { floorId -> floorPlanViewModel.switchToFloorById(floorId) },
            showMotionLabel = settingsUiState.showMotionLabel,
            onFindCorridorPoints = { room -> floorPlanViewModel.findCorridorPointsForRoom(room) },
            onCenterOnCampus = { x, y, scale -> floorPlanViewModel.centerOnCampusCoordinate(x, y, scale) },
            originErrorType = originErrorType,
            onDismissOriginError = { originErrorType = null },
            onOriginError = { errorType -> originErrorType = errorType },
            floorPlanViewModel = floorPlanViewModel,
            pdrViewModel = pdrViewModel,
            onSettingsClick = onSettingsClick,
            onAdminClick = onAdminClick
        )
    }
}

@Composable
private fun HomeScreenContent(
    uiState: FloorPlanUiState,
    pdrUiState: PdrUiState,
    navUiState: NavigationUiState,
    heading: Float,
    hasFreshHeadingSinceStart: Boolean,
    effectiveCanvasState: CanvasState,
    maxWidth: Dp,
    onCanvasStateChange: (CanvasState) -> Unit,
    onFloorChange: (Float) -> Unit,
    onCenterView: (x: Float, y: Float, scale: Float, buildingId: String?) -> Unit,
    onRoomTap: (Room) -> Unit,
    onEnableTracking: (position: Offset, headingRadians: Float) -> Unit,
    onAwaitFreshHeading: suspend () -> Float?,
    onSetOriginClick: () -> Unit,
    onClearPdrClick: () -> Unit,
    onOriginSelected: (Offset) -> Unit,
    onDismissHeightRequired: () -> Unit,
    onSaveHeight: (Float) -> Unit,
    onDirectionsClick: (Room) -> Unit,
    onStartNavigation: () -> Unit,
    onExitNavigation: () -> Unit,
    onClearOverlay: () -> Unit = {},
    onShowRoomOnMap: (Room) -> Unit,
    onSwitchToFloorById: (String) -> Unit = {},
    showMotionLabel: Boolean = true,
    onFindCorridorPoints: (Room) -> List<CorridorPoint> = { emptyList() },
    onCenterOnCampus: (x: Float, y: Float, scale: Float) -> Unit = { _, _, _ -> },
    originErrorType: FloorPlanViewModel.OriginErrorType? = null,
    onDismissOriginError: () -> Unit = {},
    onOriginError: (FloorPlanViewModel.OriginErrorType) -> Unit = {},
    floorPlanViewModel: FloorPlanViewModel? = null,
    pdrViewModel: PdrViewModel? = null,
    onSettingsClick: () -> Unit = {},
    onAdminClick: () -> Unit = {}
) {
    var showSearch by remember { mutableStateOf(false) }
    var isMorphingToSearch by remember { mutableStateOf(false) }
    // Track whether search was opened from the destination prompt dialog
    var searchOpenedFromDialog by remember { mutableStateOf(false) }
    var showOriginDialog by remember { mutableStateOf(false) }
    var aimPressed by remember { mutableStateOf(false) }
    // When true, the AimButton was pressed before origin was set.
    // Once origin is set, auto-enable tracking without requiring another press.
    var pendingTrackAfterOrigin by remember { mutableStateOf(false) }
    // When non-null, the Directions button was pressed before origin was set.
    // Once origin is set, auto-request directions to this room.
    var pendingDirectionsRoom by remember { mutableStateOf<Room?>(null) }

    // Location selection flow: set origin by selecting a room entrance
    var showLocationSearch by remember { mutableStateOf(false) }
    var locationCorridorPoints by remember { mutableStateOf<List<CorridorPoint>>(emptyList()) }
    var selectedEntranceIndex by remember { mutableStateOf<Int?>(null) }
    var locationSelectionError by remember { mutableStateOf<String?>(null) }

    // Tap on map to set origin: pending origin before confirmation
    var pendingOriginLocation by remember { mutableStateOf<Offset?>(null) }

    // Show walking tutorial after origin is confirmed, delayed for centering animation
    var showWalkingTutorial by remember { mutableStateOf(false) }
    var pendingWalkingTutorial by remember { mutableStateOf(false) }

    // Show destination prompt after walking tutorial is dismissed
    var showDestinationPrompt by remember { mutableStateOf(false) }

    // Stop tracking confirmation dialog
    var showStopTrackingConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(pendingWalkingTutorial) {
        if (pendingWalkingTutorial) {
            kotlinx.coroutines.delay(900)  // wait for centering animation to finish
            showWalkingTutorial = true
            pendingWalkingTutorial = false
        }
    }

    // Overlay room: shown when user taps a label/searches while a path is active.
    // Stacks a second RoomInfoPanel + pin on top of the existing navigation panel.
    var overlayPinnedRoom by remember { mutableStateOf<Room?>(null) }
    // True after the user presses Directions on the overlay panel.
    // Gates path state so the overlay doesn't inherit the old path's Start button.
    var overlayRequestedDirections by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val sliderAnchorHeight = 77.dp
    val navigationSliderExpandedHeight = sliderAnchorHeight + 59.dp
    var sliderHeightDp by remember { mutableStateOf(77.dp) }

    // North-reset animation: animates canvas rotation so north points up
    var northResetId by remember { mutableIntStateOf(0) }
    val northResetFrom = remember { mutableFloatStateOf(0f) }
    val northResetTo = remember { mutableFloatStateOf(0f) }
    val updatedOnCanvasStateChange = rememberUpdatedState(onCanvasStateChange)
    val updatedCanvasState = rememberUpdatedState(uiState.canvasState)
    LaunchedEffect(northResetId) {
        if (northResetId == 0) return@LaunchedEffect
        // Capture initial state once — do not read per-frame to avoid drift
        val startCanvasState = updatedCanvasState.value
        val r1 = northResetFrom.floatValue
        val screenCx = uiState.screenWidth / 2f
        val screenCy = uiState.screenHeight / 2f
        val anim = Animatable(r1)
        anim.animateTo(
            targetValue = northResetTo.floatValue,
            animationSpec = dpTween(durationMillis = 450, easing = FastOutSlowInEasing)
        ) {
            // Compute offset adjustment so rotation pivots around screen center
            val deltaRad = Math.toRadians((value - r1).toDouble()).toFloat()
            val cosD = cos(deltaRad)
            val sinD = sin(deltaRad)
            val vx = screenCx - startCanvasState.offsetX
            val vy = screenCy - startCanvasState.offsetY
            updatedOnCanvasStateChange.value(
                startCanvasState.copy(
                    rotation = value,
                    offsetX = screenCx - (vx * cosD - vy * sinD),
                    offsetY = screenCy - (vx * sinD + vy * cosD)
                )
            )
        }
    }

    val routePinRoom by remember(navUiState.hasPath, navUiState.targetRoom, navUiState.targetEntrance) {
        derivedStateOf {
            val room = navUiState.targetRoom ?: return@derivedStateOf null
            val entrance = navUiState.targetEntrance ?: return@derivedStateOf room
            room.copy(x = entrance.x, y = entrance.y)
        }
    }
    val activePinnedRoom = if (navUiState.hasPath) routePinRoom ?: uiState.pinnedRoom else uiState.pinnedRoom
    
    // Track if the pin is at an entrance (for rendering without vertical offset)
    val isPinAtEntrance = navUiState.hasPath && navUiState.targetEntrance != null

    // ── Back button handling ─────────────────────────────────────────────────
    // Intercept back press to dismiss overlays before navigating away
    val hasActiveOverlay = showSearch ||
            showLocationSearch ||
            showOriginDialog ||
            showDestinationPrompt ||
            showStopTrackingConfirmDialog ||
            showWalkingTutorial ||
            locationCorridorPoints.isNotEmpty() ||
            pdrUiState.isSelectingOrigin ||
            overlayPinnedRoom != null ||
            activePinnedRoom != null

    BackHandler(enabled = hasActiveOverlay) {
        when {
            // Full-screen overlays first (highest priority)
            showSearch -> {
                showSearch = false
                isMorphingToSearch = false
            }
            showLocationSearch -> {
                showLocationSearch = false
                isMorphingToSearch = false
            }
            // Dialogs
            showOriginDialog -> showOriginDialog = false
            showDestinationPrompt -> showDestinationPrompt = false
            showStopTrackingConfirmDialog -> showStopTrackingConfirmDialog = false
            showWalkingTutorial -> showWalkingTutorial = false
            // Bottom panels
            locationCorridorPoints.isNotEmpty() -> {
                locationCorridorPoints = emptyList()
                selectedEntranceIndex = null
            }
            pdrUiState.isSelectingOrigin -> {
                pdrViewModel?.cancelOriginSelection()
                pendingOriginLocation = null
            }
            // Room info panels
            overlayPinnedRoom != null -> {
                overlayPinnedRoom = null
            }
            activePinnedRoom != null -> {
                onExitNavigation()
            }
        }
    }

    // Animate bottom button offset when room info panel is visible
    val panelVisible = activePinnedRoom != null
    val bottomButtonPadding by animateDpAsState(
        targetValue = if (panelVisible) {
            if (navUiState.isNavigationStarted) 16.dp + 74.dp else 16.dp + 110.dp
        } else {
            16.dp
        },
        animationSpec = dpTween(durationMillis = 350),
        label = "bottomButtonPadding"
    )

    var hasUserMovedCanvasAfterPathFound by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val pinnedRoomOffCurrentFloor by remember(activePinnedRoom, uiState.currentFloorId) {
        derivedStateOf {
            val room = activePinnedRoom ?: return@derivedStateOf false
            val roomFloor = room.floorId ?: return@derivedStateOf false
            val currentFloor = uiState.currentFloorId ?: return@derivedStateOf false
            roomFloor != currentFloor
        }
    }

    val pinnedRoomFarFromCenter by remember(
        activePinnedRoom,
        uiState.buildingStates,
        effectiveCanvasState,
        uiState.screenWidth,
        uiState.screenHeight
    ) {
        derivedStateOf {
            val room = activePinnedRoom ?: return@derivedStateOf false
            val roomWorld = MapViewportUtils.resolveRoomCampusPosition(room, uiState.buildingStates)
                ?: return@derivedStateOf false
            !MapViewportUtils.isWorldPointNearScreenCenter(
                worldPoint = roomWorld,
                canvasState = effectiveCanvasState,
                screenWidth = uiState.screenWidth,
                screenHeight = uiState.screenHeight
            )
        }
    }

    val shouldShowShowOnMapButton by remember(
        navUiState.hasPath,
        hasUserMovedCanvasAfterPathFound,
        pinnedRoomOffCurrentFloor,
        pinnedRoomFarFromCenter
    ) {
        derivedStateOf {
            if (navUiState.hasPath) {
                hasUserMovedCanvasAfterPathFound
            } else {
                pinnedRoomOffCurrentFloor || pinnedRoomFarFromCenter
            }
        }
    }

    // rememberUpdatedState ensures the latest hasPath is always readable,
    // even through FloorPlanCanvas's stale pointerInput(Unit) lambda closure.
    val currentHasPath = rememberUpdatedState(navUiState.hasPath)
    val currentOnRoomTap = rememberUpdatedState(onRoomTap)

    fun requestRoomSelection(room: Room) {
        if (currentHasPath.value) {
            // Path is active — show overlay pin + panel instead of replacing
            overlayPinnedRoom = room
            overlayRequestedDirections = false
        } else {
            currentOnRoomTap.value(room)
        }
    }

    LaunchedEffect(navUiState.multiFloorPath) {
        if (navUiState.hasPath) {
            hasUserMovedCanvasAfterPathFound = false
            floorPlanViewModel?.showPinnedRoomOnMap()

            // Smoothly reset rotation to north-up first so bounds calculation is accurate
            val currentRotation = effectiveCanvasState.rotation
            val targetRotation = -uiState.campusMetadata.north
            
            if (kotlin.math.abs(currentRotation - targetRotation) > 1f) {
                // Trigger smooth rotation animation via shortest arc
                val diff = ((targetRotation - currentRotation + 180f) % 360f + 360f) % 360f - 180f
                northResetFrom.floatValue = currentRotation
                northResetTo.floatValue = currentRotation + diff
                northResetId++
                
                // Wait for rotation animation to fully complete (animation is 450ms)
                kotlinx.coroutines.delay(480)
            }

            val fitPoints = navUiState.multiFloorPath.allPoints.toMutableList()
            val userPosition = pdrUiState.pdrState.path.lastOrNull()?.position
                ?: pdrUiState.pdrState.origin
            if (userPosition != null) fitPoints.add(userPosition)

            if (fitPoints.isNotEmpty()) {
                val (center, zoom) = MapViewportUtils.calculateFitBoundsForWorldPoints(
                    points = fitPoints,
                    screenWidth = uiState.screenWidth,
                    screenHeight = uiState.screenHeight
                )
                onCenterOnCampus(center.x, center.y, zoom)
            }
        }
    }

    // Reset local pressed state when following mode is turned off so button reappears
    LaunchedEffect(uiState.isFollowingMode) {
        if (!uiState.isFollowingMode) aimPressed = false
    }

    // Auto-enable tracking once origin is set after AimButton triggered the dialog
    // Also auto-request directions if Directions was pressed before origin was set
    LaunchedEffect(pdrUiState.pdrState.origin) {
        val origin = pdrUiState.pdrState.origin
        if (origin != null) {
            if (pendingTrackAfterOrigin) {
                pendingTrackAfterOrigin = false
                aimPressed = true
                pdrUiState.pdrState.currentFloor?.let { floor ->
                    onSwitchToFloorById(floor)
                }
                val freshHeading = onAwaitFreshHeading() ?: heading
                onEnableTracking(origin, freshHeading)
            }
            pendingDirectionsRoom?.let { room ->
                pendingDirectionsRoom = null
                onDirectionsClick(room)
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator()
            }
            uiState.error != null -> {
                Text(text = uiState.error)
            }
            uiState.buildingStates.isNotEmpty() -> {
                // Resolve pin drawable and primary tint color
                val context = LocalContext.current
                val pinDrawable = remember {
                    ContextCompat.getDrawable(context, `in`.project.enroute.R.drawable.pin) as? VectorDrawable
                }
                val primaryColor = MaterialTheme.colorScheme.primary.toArgb()

                // Floor plan canvas filling entire screen (base layer: background, walls, entrances)
                FloorPlanCanvas(
                    buildingStates = uiState.buildingStates,
                    campusBounds = uiState.campusBounds,
                    canvasState = effectiveCanvasState,
                    onCanvasStateChange = { newState ->
                        if (navUiState.hasPath) {
                            hasUserMovedCanvasAfterPathFound = true
                        }
                        onCanvasStateChange(newState)
                    },
                    displayConfig = uiState.displayConfig,
                    onRoomTap = { room -> requestRoomSelection(room) },
                    onBackgroundTap = { /* no-op */ },
                    isSelectingOrigin = pdrUiState.isSelectingOrigin,
                    onOriginTap = { origin ->
                        // Store as pending - user must confirm
                        android.util.Log.d("HomeScreen", "Origin tapped at: (${origin.x}, ${origin.y})")
                        floorPlanViewModel?.let { vm ->
                            val validation = vm.validateOriginLocation(origin)
                            android.util.Log.d("HomeScreen", "Validation result: isValid=${validation.isValid}, errorType=${validation.errorType}")
                            if (validation.isValid) {
                                pendingOriginLocation = origin
                            } else {
                                // Show error via callback
                                validation.errorType?.let { onOriginError(it) }
                            }
                        }
                    },
                    corridorPoints = locationCorridorPoints,
                    onMarkerTap = { markerIndex ->
                        selectedEntranceIndex = markerIndex
                        // Animate camera to the selected entrance
                        val point = locationCorridorPoints[markerIndex]
                        onCenterOnCampus(
                            point.campusPosition.x,
                            point.campusPosition.y,
                            CorridorPointFinder.calculateFitBounds(
                                listOf(point),
                                uiState.screenWidth,
                                uiState.screenHeight
                            ).second
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // A* navigation path overlay — rendered above base but below labels/pin
                // Supports multi-floor rendering: current floor at full opacity,
                // other floors faded+dashed with floor labels
                if (navUiState.hasPath) {
                    NavigationPathOverlay(
                        multiFloorPath = navUiState.displayPath,
                        currentVisibleFloor = uiState.currentFloorId,
                        canvasState = effectiveCanvasState,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Room labels, building names, and search pin — above nav path
                FloorPlanLabelsOverlay(
                    buildingStates = uiState.buildingStates,
                    canvasState = effectiveCanvasState,
                    displayConfig = uiState.displayConfig,
                    pinnedRoom = activePinnedRoom,
                    showPinnedRoomMarker = uiState.showPinnedRoomOnMap,
                    isPinAtEntrance = isPinAtEntrance,
                    // Hide overlay pin when directions were requested (route pin takes over)
                    overlayPinnedRoom = if (overlayRequestedDirections) null else overlayPinnedRoom,
                    pinDrawable = pinDrawable,
                    pinTintColor = primaryColor,
                    modifier = Modifier.fillMaxSize()
                )

                // PDR path overlay — user position always on top
                if (pdrUiState.pdrState.path.isNotEmpty()) {
                    PdrPathOverlay(
                        path = pdrUiState.pdrState.path,
                        currentHeading = heading,
                        showDirectionCone = hasFreshHeadingSinceStart,
                        canvasState = effectiveCanvasState,
                        isOnCurrentFloor = pdrUiState.pdrState.currentFloor == null ||
                                pdrUiState.pdrState.currentFloor == uiState.currentFloorId,
                        isOnStairs = pdrUiState.pdrState.isOnStairs,
                        currentPosition = pdrUiState.pdrState.currentPosition,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Entrance marker overlay — shows numbered markers at corridor points
                // Rendered here (before UI buttons) so markers appear behind buttons
                if (locationCorridorPoints.isNotEmpty()) {
                    EntranceMarkerOverlay(
                        corridorPoints = locationCorridorPoints,
                        selectedIndex = selectedEntranceIndex,
                        canvasState = effectiveCanvasState,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Origin preview overlay — shows blue dot at pending origin location
                if (pendingOriginLocation != null) {
                    OriginPreviewOverlay(
                        pendingOrigin = pendingOriginLocation,
                        canvasState = effectiveCanvasState,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Floor slider positioned at top, visible during origin selection
                // Takes full width during origin selection to allow floor switching
                if (pdrUiState.isSelectingOrigin) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = 6.dp, start = 8.dp, end = 8.dp)
                    ) {
                        FloorSlider(
                            buildingName = uiState.sliderBuildingName,
                            availableFloors = uiState.sliderFloorNumbers,
                            currentFloor = uiState.sliderCurrentFloor,
                            onFloorChange = { 
                                // Clear pending origin choices when floor changes
                                pendingOriginLocation = null
                                onDismissOriginError()
                                onFloorChange(it) 
                            },
                            instructionText = "",
                            isNavigationActive = false,
                            hideControlsForNavigation = false,
                            showCurrentFloorLabelOnly = false,
                            currentFloorLabel = "",
                            isVisible = true,
                            disabled = locationCorridorPoints.isNotEmpty(),
                            onHeightMeasured = { px ->
                                sliderHeightDp = with(density) { px.toDp() }
                            }
                        )
                    }
                } else {
                    // Floor slider and search button positioned at top, layered over canvas
                    // Hidden during origin selection mode
                    val turnByTurnActive = navUiState.isNavigationStarted && navUiState.hasPath
                    val isZoomedSliderVisible = uiState.showFloorSlider
                    val isSliderVisible = (isZoomedSliderVisible || turnByTurnActive) && !isMorphingToSearch && !showSearch
                    val hideSliderControlsForNavigation = turnByTurnActive && !isZoomedSliderVisible
                    val floorLabel = pdrUiState.pdrState.currentFloor
                        ?.removePrefix("floor_")
                        ?.let { "Floor $it" }
                        ?: ""

                    val motionLabelTopPadding by animateDpAsState(
                        targetValue = if (isSliderVisible) sliderHeightDp + 11.dp else 52.dp,
                        animationSpec = dpTween(durationMillis = 300),
                        label = "MotionLabelTopPadding"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = 6.dp, end = 8.dp, start = 8.dp)
                    ) {
                        AnimatedVisibility(
                            visible = isSliderVisible,
                            enter = fadeIn(tween(300)) + slideInHorizontally(tween(300)) { -it },
                            exit = fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { -it },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(end = 56.dp)
                        ) {
                            FloorSlider(
                                buildingName = uiState.sliderBuildingName,
                                availableFloors = uiState.sliderFloorNumbers,
                                currentFloor = uiState.sliderCurrentFloor,
                                onFloorChange = {
                                    pendingOriginLocation = null
                                    onDismissOriginError()
                                    onFloorChange(it)
                                },
                                instructionText = navUiState.turnByTurnInstruction,
                                isNavigationActive = turnByTurnActive,
                                hideControlsForNavigation = hideSliderControlsForNavigation,
                                showCurrentFloorLabelOnly = hideSliderControlsForNavigation,
                                currentFloorLabel = floorLabel,
                                navigationExpandedHeight = navigationSliderExpandedHeight,
                                isVisible = true,
                                disabled = locationCorridorPoints.isNotEmpty(),
                                onHeightMeasured = { px ->
                                    sliderHeightDp = with(density) { px.toDp() }
                                }
                            )
                        }

                        SearchButton(
                            isSliderVisible = isSliderVisible,
                            isSearching = isMorphingToSearch,
                            containerWidth = maxWidth - 16.dp,
                            sliderHeightDp = sliderAnchorHeight,
                            modifier = Modifier.align(Alignment.TopEnd),
                            onClick = { isMorphingToSearch = true },
                            onAnimationFinished = { showSearch = true }
                        )

                        CompassButton(
                            campusNorthDegrees = uiState.campusMetadata.north,
                            canvasRotationDegrees = effectiveCanvasState.rotation,
                            onClick = {
                                val from = effectiveCanvasState.rotation
                                val rawTarget = -uiState.campusMetadata.north
                                // Rotate via the shortest arc
                                val diff = ((rawTarget - from + 180f) % 360f + 360f) % 360f - 180f
                                northResetFrom.floatValue = from
                                northResetTo.floatValue = from + diff
                                northResetId++
                            },
                            isSliderVisible = isSliderVisible,
                            isSearching = isMorphingToSearch,
                            sliderHeightDp = sliderAnchorHeight,
                            modifier = Modifier.align(Alignment.TopEnd)
                        )

                        // Motion classification label — left edge, tracks slider visibility
                        if (pdrUiState.pdrState.isTracking && pdrUiState.motionLabel != null && showMotionLabel) {
                            MotionLabel(
                                label = pdrUiState.motionLabel,
                                confidence = pdrUiState.motionConfidence,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(top = motionLabelTopPadding)
                            )
                        }
                    }
                }

                // Origin location error snackbar shown below floor slider.
                val sliderVisible = pdrUiState.isSelectingOrigin ||
                        (((uiState.showFloorSlider || (navUiState.isNavigationStarted && navUiState.hasPath))
                                && !isMorphingToSearch && !showSearch))
                val originErrorTopPadding by animateDpAsState(
                    targetValue = if (sliderVisible) sliderHeightDp + 14.dp else 52.dp,
                    animationSpec = dpTween(durationMillis = 250),
                    label = "OriginErrorTopPadding"
                )
                originErrorType?.let { errorType ->
                    val message = when (errorType) {
                        FloorPlanViewModel.OriginErrorType.OUTSIDE_CAMPUS ->
                            "Location is outside campus area."
                        FloorPlanViewModel.OriginErrorType.INSIDE_BUILDING_ZOOMED_OUT ->
                            "Cannot set location inside building while zoomed out. Zoom in first."
                        FloorPlanViewModel.OriginErrorType.WRONG_FLOOR ->
                            "You can only set location inside the currently viewing floor or outside building."
                    }
                    OriginLocationErrorSnackbar(
                        message = message,
                        onDismiss = onDismissOriginError,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = originErrorTopPadding)
                    )
                }

                // Aim button positioned at bottom right
                // Hidden during origin selection mode or when following is enabled
                // Shows origin dialog if origin not set, otherwise enables following mode
                AimButton(
                    isVisible = !pdrUiState.isSelectingOrigin && !uiState.isFollowingMode && !aimPressed,
                    isPdrActive = pdrUiState.pdrState.origin != null,
                    onClick = {
                        if (pdrUiState.pdrState.origin == null) {
                            pendingTrackAfterOrigin = true
                            showOriginDialog = true
                        } else {
                            aimPressed = true
                            // Switch to the floor the user is currently on (if inside a building)
                            pdrUiState.pdrState.currentFloor?.let { floor ->
                                onSwitchToFloorById(floor)
                            }
                            val currentPosition = if (pdrUiState.pdrState.path.isNotEmpty()) {
                                pdrUiState.pdrState.path.last().position
                            } else {
                                pdrUiState.pdrState.origin
                            }
                            onEnableTracking(currentPosition, heading)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(bottom = bottomButtonPadding, end = 8.dp)
                )
                
                // Stop Tracking button positioned at bottom left
                // Shows when origin is set and not selecting origin
                if (pdrUiState.pdrState.origin != null && !pdrUiState.isSelectingOrigin) {
                    StopTrackingButton(
                        isSliderVisible =
                            ((uiState.showFloorSlider || (navUiState.isNavigationStarted && navUiState.hasPath))
                                && !isMorphingToSearch && !showSearch),
                        onClick = { showStopTrackingConfirmDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .navigationBarsPadding()
                            .padding(start = 8.dp, bottom = bottomButtonPadding)
                    )
                }

                // Settings & Admin overlay buttons — bottom left, above StopTrackingButton
                val stopTrackingVisible = pdrUiState.pdrState.origin != null && !pdrUiState.isSelectingOrigin
                val navButtonsBottomPadding = bottomButtonPadding + if (stopTrackingVisible) 59.dp else 0.dp
                if (!pdrUiState.isSelectingOrigin) {
                    OverlayNavButtons(
                        onSettingsClick = onSettingsClick,
                        onAdminClick = onAdminClick,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .navigationBarsPadding()
                            .padding(start = 8.dp, bottom = navButtonsBottomPadding)
                    )
                }

                // Room info panel slides up from bottom when a room label is tapped
                RoomInfoPanel(
                    room = activePinnedRoom,
                    isCalculatingPath = navUiState.isCalculating,
                    hasPath = navUiState.hasPath,
                    isNavigationStarted = navUiState.isNavigationStarted,
                    showShowOnMapButton = shouldShowShowOnMapButton,
                    onDismiss = {
                        onExitNavigation()
                    },
                    onDirectionsClick = { room ->
                        if (pdrUiState.pdrState.origin == null) {
                            // Origin not set → show dialog, remember room for later
                            pendingDirectionsRoom = room
                            showOriginDialog = true
                        } else {
                            onDirectionsClick(room)
                        }
                    },
                    onShowOnMapClick = { room ->
                        hasUserMovedCanvasAfterPathFound = false
                        floorPlanViewModel?.showPinnedRoomOnMap()
                        
                        // When a path exists, fit the entire route on screen
                        if (navUiState.hasPath) {
                            coroutineScope.launch {
                                // Smoothly reset rotation to north-up first so bounds calculation is accurate
                                val currentRotation = effectiveCanvasState.rotation
                                val targetRotation = -uiState.campusMetadata.north
                                
                                if (kotlin.math.abs(currentRotation - targetRotation) > 1f) {
                                    // Trigger smooth rotation animation via shortest arc
                                    val diff = ((targetRotation - currentRotation + 180f) % 360f + 360f) % 360f - 180f
                                    northResetFrom.floatValue = currentRotation
                                    northResetTo.floatValue = currentRotation + diff
                                    northResetId++
                                    
                                    // Wait for rotation animation to fully complete (animation is 450ms)
                                    kotlinx.coroutines.delay(480)
                                }
                                
                                val fitPoints = navUiState.multiFloorPath.allPoints.toMutableList()
                                val userPosition = pdrUiState.pdrState.path.lastOrNull()?.position
                                    ?: pdrUiState.pdrState.origin
                                if (userPosition != null) fitPoints.add(userPosition)
                                
                                if (fitPoints.isNotEmpty()) {
                                    val (center, zoom) = MapViewportUtils.calculateFitBoundsForWorldPoints(
                                        points = fitPoints,
                                        screenWidth = uiState.screenWidth,
                                        screenHeight = uiState.screenHeight
                                    )
                                    onCenterOnCampus(center.x, center.y, zoom)
                                }
                            }
                        } else {
                            // No path: center on the room as before
                            onShowRoomOnMap(room)
                        }
                    },
                    onStartClick = {
                        pdrUiState.pdrState.currentFloor?.let { floor ->
                            onSwitchToFloorById(floor)
                        }
                        val currentPosition = pdrUiState.pdrState.path.lastOrNull()?.position
                            ?: pdrUiState.pdrState.origin
                        if (currentPosition == null) {
                            onStartNavigation()
                        } else {
                            coroutineScope.launch {
                                aimPressed = true
                                val freshHeading = onAwaitFreshHeading() ?: heading
                                onEnableTracking(currentPosition, freshHeading)
                                onStartNavigation()
                            }
                        }
                    },
                    onExitClick = {
                        overlayPinnedRoom = null
                        onExitNavigation()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // Overlay panel: shown when user taps a label/searches while a path is active.
                // Stacks on top of the path panel; dismissing reveals the path panel below.
                // When Directions is pressed here, the overlay stays visible and updates
                // in-place to show Start / Show full route once the path is computed.
                RoomInfoPanel(
                    room = overlayPinnedRoom,
                    isCalculatingPath = if (overlayRequestedDirections) navUiState.isCalculating else false,
                    hasPath = if (overlayRequestedDirections) navUiState.hasPath else false,
                    isNavigationStarted = if (overlayRequestedDirections) navUiState.isNavigationStarted else false,
                    showShowOnMapButton = if (overlayRequestedDirections) {
                        // After requesting directions, use the route-based check
                        shouldShowShowOnMapButton
                    } else {
                        // Before directions, check if overlay room is off-floor or far from center
                        overlayPinnedRoom != null && run {
                            val room = overlayPinnedRoom!!
                            val roomFloor = room.floorId
                            val currentFloor = uiState.currentFloorId
                            val offFloor = roomFloor != null && currentFloor != null && roomFloor != currentFloor
                            val farFromCenter = run {
                                val world = MapViewportUtils.resolveRoomCampusPosition(room, uiState.buildingStates)
                                world != null && !MapViewportUtils.isWorldPointNearScreenCenter(
                                    worldPoint = world,
                                    canvasState = effectiveCanvasState,
                                    screenWidth = uiState.screenWidth,
                                    screenHeight = uiState.screenHeight
                                )
                            }
                            offFloor || farFromCenter
                        }
                    },
                    onDismiss = {
                        overlayPinnedRoom = null
                        overlayRequestedDirections = false
                        onClearOverlay()
                    },
                    onDirectionsClick = { room ->
                        // Clear old path silently, pin this room, request new directions.
                        // Don't clear overlayPinnedRoom — the panel stays and updates in-place.
                        overlayRequestedDirections = true
                        onExitNavigation()
                        onRoomTap(room)
                        if (pdrUiState.pdrState.origin == null) {
                            pendingDirectionsRoom = room
                            showOriginDialog = true
                        } else {
                            onDirectionsClick(room)
                        }
                    },
                    onShowOnMapClick = { room ->
                        hasUserMovedCanvasAfterPathFound = false
                        floorPlanViewModel?.showPinnedRoomOnMap()

                        if (navUiState.hasPath) {
                            coroutineScope.launch {
                                val currentRotation = effectiveCanvasState.rotation
                                val targetRotation = -uiState.campusMetadata.north

                                if (kotlin.math.abs(currentRotation - targetRotation) > 1f) {
                                    val diff = ((targetRotation - currentRotation + 180f) % 360f + 360f) % 360f - 180f
                                    northResetFrom.floatValue = currentRotation
                                    northResetTo.floatValue = currentRotation + diff
                                    northResetId++
                                    kotlinx.coroutines.delay(480)
                                }

                                val fitPoints = navUiState.multiFloorPath.allPoints.toMutableList()
                                val userPosition = pdrUiState.pdrState.path.lastOrNull()?.position
                                    ?: pdrUiState.pdrState.origin
                                if (userPosition != null) fitPoints.add(userPosition)

                                if (fitPoints.isNotEmpty()) {
                                    val (center, zoom) = MapViewportUtils.calculateFitBoundsForWorldPoints(
                                        points = fitPoints,
                                        screenWidth = uiState.screenWidth,
                                        screenHeight = uiState.screenHeight
                                    )
                                    onCenterOnCampus(center.x, center.y, zoom)
                                }
                            }
                        } else {
                            onShowRoomOnMap(room)
                        }
                    },
                    onStartClick = {
                        // Start navigation from overlay — clear overlay, delegate to main flow
                        overlayPinnedRoom = null
                        pdrUiState.pdrState.currentFloor?.let { floor ->
                            onSwitchToFloorById(floor)
                        }
                        val currentPosition = pdrUiState.pdrState.path.lastOrNull()?.position
                            ?: pdrUiState.pdrState.origin
                        if (currentPosition == null) {
                            onStartNavigation()
                        } else {
                            coroutineScope.launch {
                                aimPressed = true
                                val freshHeading = onAwaitFreshHeading() ?: heading
                                onEnableTracking(currentPosition, freshHeading)
                                onStartNavigation()
                            }
                        }
                    },
                    onExitClick = {
                        overlayPinnedRoom = null
                        onExitNavigation()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
                
                // Origin selection dialog
                if (showOriginDialog) {
                    OriginSelectionDialog(
                        onDismiss = { showOriginDialog = false },
                        onSelectPoint = {
                            showOriginDialog = false
                            onSetOriginClick()
                        },
                        onSelectLocation = {
                            showOriginDialog = false
                            showLocationSearch = true
                        }
                    )
                }

                // Stop tracking confirmation dialog
                if (showStopTrackingConfirmDialog) {
                    StopTrackingConfirmDialog(
                        onConfirm = {
                            showStopTrackingConfirmDialog = false
                            onClearPdrClick()
                        },
                        onDismiss = { showStopTrackingConfirmDialog = false }
                    )
                }

                // Walking tutorial dialog — shown after origin is confirmed
                if (showWalkingTutorial) {
                    WalkingTutorialDialog(
                        onDismiss = {
                            showWalkingTutorial = false
                            showDestinationPrompt = true
                        }
                    )
                }

                // Destination prompt dialog — shown after walking tutorial
                if (showDestinationPrompt) {
                    DestinationPromptDialog(
                        onSearchDestination = {
                            showDestinationPrompt = false
                            searchOpenedFromDialog = true
                            isMorphingToSearch = true
                        },
                        onLater = { showDestinationPrompt = false }
                    )
                }
                
                // Height required dialog
                if (pdrUiState.showHeightRequired) {
                    HeightRequiredDialog(
                        onDismiss = onDismissHeightRequired,
                        onSave = onSaveHeight
                    )
                }
                
                // Tap location confirmation panel — slides up when tap-selecting origin
                TapLocationConfirmationPanel(
                    isVisible = pdrUiState.isSelectingOrigin,
                    hasPendingLocation = pendingOriginLocation != null,
                    onConfirm = {
                        // Confirm the pending origin location
                        pendingOriginLocation?.let { origin ->
                            floorPlanViewModel?.let { vm ->
                                val currentFloor = vm.findFloorAtPoint(origin)
                                val constraintData = vm.getFloorConstraintData(origin)
                                pdrViewModel?.setOrigin(origin, currentFloor, constraintData)
                                pendingOriginLocation = null
                                pendingWalkingTutorial = true
                            }
                        }
                    },
                    onCancel = {
                        pdrViewModel?.cancelOriginSelection()
                        pendingOriginLocation = null
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // Entrance confirmation panel — slides up when corridor points are found
                if (locationCorridorPoints.isNotEmpty()) {
                    val selectedRoom = locationCorridorPoints.firstOrNull()?.entrance
                    val roomLabel = selectedRoom?.name
                        ?: selectedRoom?.roomNo?.let { "Room $it" }
                        ?: "Selected Location"

                    EntranceConfirmationPanel(
                        corridorPoints = locationCorridorPoints,
                        selectedIndex = if (locationCorridorPoints.size == 1) 0 else selectedEntranceIndex,
                        roomLabel = roomLabel,
                        onSelectEntrance = { idx ->
                            selectedEntranceIndex = idx
                            // Animate camera to the selected entrance
                            val point = locationCorridorPoints[idx]
                            onCenterOnCampus(
                                point.campusPosition.x,
                                point.campusPosition.y,
                                CorridorPointFinder.calculateFitBounds(
                                    listOf(point),
                                    uiState.screenWidth,
                                    uiState.screenHeight
                                ).second
                            )
                        },
                        onConfirm = {
                            val idx = if (locationCorridorPoints.size == 1) 0
                                      else selectedEntranceIndex ?: return@EntranceConfirmationPanel
                            val point = locationCorridorPoints[idx]
                            onOriginSelected(point.campusPosition)
                            locationCorridorPoints = emptyList()
                            selectedEntranceIndex = null
                            pendingWalkingTutorial = true
                        },
                        onDismiss = {
                            locationCorridorPoints = emptyList()
                            selectedEntranceIndex = null
                        },
                        onSearchAnother = {
                            locationCorridorPoints = emptyList()
                            selectedEntranceIndex = null
                            showLocationSearch = true
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // Animate camera to show corridor points after they are found
                LaunchedEffect(locationCorridorPoints) {
                    if (locationCorridorPoints.isNotEmpty()) {
                        // Small delay to let floor switch take effect
                        kotlinx.coroutines.delay(250)
                        val (center, zoom) = CorridorPointFinder.calculateFitBounds(
                            points = locationCorridorPoints,
                            screenWidth = uiState.screenWidth,
                            screenHeight = uiState.screenHeight
                        )
                        onCenterOnCampus(center.x, center.y, zoom)
                    }
                }

                // Show error toast when no entrances found for selected room
                if (locationSelectionError != null) {
                    val ctx = LocalContext.current
                    LaunchedEffect(locationSelectionError) {
                        locationSelectionError?.let {
                            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
                            locationSelectionError = null
                        }
                    }
                }

                // Animated transition for SearchScreen
                AnimatedVisibility(
                    visible = showSearch,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(500))
                ) {
                    SearchScreen(
                        buildingStates = uiState.buildingStates,
                        onBack = { 
                            showSearch = false 
                            isMorphingToSearch = false
                            // Only show destination prompt if search was opened from the dialog
                            if (searchOpenedFromDialog) {
                                showDestinationPrompt = true
                                searchOpenedFromDialog = false
                            }
                        },
                        onCenterView = onCenterView,
                        onRoomTap = { room -> requestRoomSelection(room) }
                    )
                }

                // Location selection search screen
                AnimatedVisibility(
                    visible = showLocationSearch,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(500))
                ) {
                    SearchScreen(
                        buildingStates = uiState.buildingStates,
                        onBack = {
                            showLocationSearch = false
                            isMorphingToSearch = false
                        },
                        onCenterView = { _, _, _, _ -> },
                        onRoomTap = { room ->
                            val corridorPoints = onFindCorridorPoints(room)
                            if (corridorPoints.isEmpty()) {
                                locationCorridorPoints = emptyList()
                                locationSelectionError = "No entrance found for ${room.name ?: room.number ?: "this room"}"
                            } else {
                                locationCorridorPoints = corridorPoints
                                selectedEntranceIndex = if (corridorPoints.size == 1) 0 else null
                                // Auto-switch to the entrance's floor
                                onSwitchToFloorById(corridorPoints.first().floorId)
                                showLocationSearch = false
                                isMorphingToSearch = false
                            }
                        }
                    )
                }


            }
        }
    }
}
