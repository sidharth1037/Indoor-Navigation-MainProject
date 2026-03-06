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
import `in`.project.enroute.feature.pdr.ui.components.OriginSelectionOverlay
import `in`.project.enroute.feature.pdr.ui.components.OriginLocationErrorSnackbar

import `in`.project.enroute.feature.pdr.ui.components.PdrPathOverlay
import `in`.project.enroute.feature.pdr.ui.components.HeightRequiredDialog
import `in`.project.enroute.feature.pdr.ui.components.MotionLabel

import `in`.project.enroute.feature.home.components.RoomInfoPanel
import `in`.project.enroute.feature.home.components.StopTrackingButton
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
import android.widget.Toast
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf

@Composable
fun HomeScreen(
    campusId: String,
    floorPlanViewModel: FloorPlanViewModel = viewModel(),
    pdrViewModel: PdrViewModel = viewModel(),
    navigationViewModel: NavigationViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
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
    // and auto-rerouting. Triggers on every PDR step when a navigation path exists.
    val pdrCurrentPosition = pdrUiState.pdrState.currentPosition
    LaunchedEffect(pdrCurrentPosition, pdrCurrentFloor) {
        if (pdrCurrentPosition != null && pdrCurrentFloor != null && navUiState.hasPath) {
            navigationViewModel.updateUserPosition(pdrCurrentPosition, pdrCurrentFloor)
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
            onBackgroundTap = {
                floorPlanViewModel.clearPin()
                navigationViewModel.clearPath()
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
                // Validate origin location before setting
                android.util.Log.d("HomeScreen", "Origin tapped at: (${origin.x}, ${origin.y})")
                val validation = floorPlanViewModel.validateOriginLocation(origin)
                android.util.Log.d("HomeScreen", "Validation result: isValid=${validation.isValid}, errorType=${validation.errorType}")
                if (validation.isValid) {
                    // Pass current floor and building to PDR origin
                    // Origin tap is already in campus-wide coordinates
                    // Use findFloorAtPoint so origin works even when zoomed out (no dominant building)
                    val currentFloor = floorPlanViewModel.findFloorAtPoint(origin)
                    val constraintData = floorPlanViewModel.getFloorConstraintData(origin)
                    pdrViewModel.setOrigin(origin, currentFloor, constraintData)
                } else {
                    // Show error snackbar with specific message
                    android.util.Log.d("HomeScreen", "Setting error type: ${validation.errorType}")
                    originErrorType = validation.errorType
                }
            },
            onCancelOriginSelection = { pdrViewModel.cancelOriginSelection() },
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
            onSwitchToFloorById = { floorId -> floorPlanViewModel.switchToFloorById(floorId) },
            showMotionLabel = settingsUiState.showMotionLabel,
            onFindCorridorPoints = { room -> floorPlanViewModel.findCorridorPointsForRoom(room) },
            onCenterOnCampus = { x, y, scale -> floorPlanViewModel.centerOnCampusCoordinate(x, y, scale) },
            originErrorType = originErrorType,
            onDismissOriginError = { originErrorType = null }
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
    onBackgroundTap: () -> Unit,
    onEnableTracking: (position: androidx.compose.ui.geometry.Offset, headingRadians: Float) -> Unit,
    onAwaitFreshHeading: suspend () -> Float?,
    onSetOriginClick: () -> Unit,
    onClearPdrClick: () -> Unit,
    onOriginSelected: (androidx.compose.ui.geometry.Offset) -> Unit,
    onCancelOriginSelection: () -> Unit,
    onDismissHeightRequired: () -> Unit,
    onSaveHeight: (Float) -> Unit,
    onDirectionsClick: (Room) -> Unit,
    onSwitchToFloorById: (String) -> Unit = {},
    showMotionLabel: Boolean = true,
    onFindCorridorPoints: (Room) -> List<CorridorPoint> = { emptyList() },
    onCenterOnCampus: (x: Float, y: Float, scale: Float) -> Unit = { _, _, _ -> },
    originErrorType: FloorPlanViewModel.OriginErrorType? = null,
    onDismissOriginError: () -> Unit = {}
) {
    var showSearch by remember { mutableStateOf(false) }
    var isMorphingToSearch by remember { mutableStateOf(false) }
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

    val density = LocalDensity.current
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

    // Animate bottom button offset when room info panel is visible
    val panelVisible = uiState.pinnedRoom != null
    val bottomButtonPadding by animateDpAsState(
        targetValue = if (panelVisible) 16.dp + 130.dp else 16.dp,
        animationSpec = dpTween(durationMillis = 350),
        label = "bottomButtonPadding"
    )

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
                    onCanvasStateChange = onCanvasStateChange,
                    displayConfig = uiState.displayConfig,
                    onRoomTap = onRoomTap,
                    onBackgroundTap = {},
                    isSelectingOrigin = pdrUiState.isSelectingOrigin,
                    onOriginTap = onOriginSelected,
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
                    pinnedRoom = uiState.pinnedRoom,
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

                // Floor slider positioned at top, visible during origin selection
                // Takes full width during origin selection to allow floor switching
                if (pdrUiState.isSelectingOrigin) {
                    // Calculate animated top padding for cancel overlay based on slider visibility
                    val isSliderVisible = uiState.showFloorSlider
                    val cancelTopPadding by animateDpAsState(
                        targetValue = if (isSliderVisible) sliderHeightDp + 14.dp else 8.dp,
                        animationSpec = dpTween(durationMillis = 300),
                        label = "CancelOverlayTopPadding"
                    )
                    
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
                            onFloorChange = onFloorChange,
                            isVisible = true,
                            onHeightMeasured = { px ->
                                sliderHeightDp = with(density) { px.toDp() }
                            }
                        )
                    }
                    
                    // Origin selection overlay - animated position based on slider visibility
                    OriginSelectionOverlay(
                        onCancel = onCancelOriginSelection,
                        topPadding = cancelTopPadding,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                } else {
                    // Floor slider and search button positioned at top, layered over canvas
                    // Hidden during origin selection mode
                    val isSliderVisible = uiState.showFloorSlider && !isMorphingToSearch && !showSearch
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
                            visible = uiState.showFloorSlider && !isMorphingToSearch && !showSearch,
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
                                onFloorChange = onFloorChange,
                                isVisible = true,
                                onHeightMeasured = { px ->
                                    sliderHeightDp = with(density) { px.toDp() }
                                }
                            )
                        }

                        SearchButton(
                            isSliderVisible = uiState.showFloorSlider && !isMorphingToSearch && !showSearch,
                            isSearching = isMorphingToSearch,
                            containerWidth = maxWidth - 16.dp,
                            sliderHeightDp = sliderHeightDp,
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
                            sliderHeightDp = sliderHeightDp,
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
                        .padding(bottom = bottomButtonPadding, end = 8.dp)
                )
                
                // Stop Tracking button positioned at bottom left
                // Shows when origin is set and not selecting origin
                if (pdrUiState.pdrState.origin != null && !pdrUiState.isSelectingOrigin) {
                    StopTrackingButton(
                        isSliderVisible = uiState.showFloorSlider && !isMorphingToSearch && !showSearch,
                        onClick = onClearPdrClick,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = bottomButtonPadding)
                    )
                }

                // Room info panel slides up from bottom when a room label is tapped
                RoomInfoPanel(
                    room = uiState.pinnedRoom,
                    isCalculatingPath = navUiState.isCalculating,
                    onDismiss = onBackgroundTap,
                    onDirectionsClick = { room ->
                        if (pdrUiState.pdrState.origin == null) {
                            // Origin not set → show dialog, remember room for later
                            pendingDirectionsRoom = room
                            showOriginDialog = true
                        } else {
                            onDirectionsClick(room)
                        }
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
                
                // Height required dialog
                if (pdrUiState.showHeightRequired) {
                    HeightRequiredDialog(
                        onDismiss = onDismissHeightRequired,
                        onSave = onSaveHeight
                    )
                }
                
                // Origin location error snackbar at bottom
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
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

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
                        },
                        onCenterView = onCenterView,
                        onRoomTap = onRoomTap
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
