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

import `in`.project.enroute.feature.pdr.ui.components.PdrPathOverlay
import `in`.project.enroute.feature.pdr.ui.components.HeightRequiredDialog
import `in`.project.enroute.feature.pdr.ui.components.MotionLabel
import `in`.project.enroute.feature.home.components.SetLocationButton
import `in`.project.enroute.feature.home.components.RoomInfoPanel
import `in`.project.enroute.feature.home.components.StopTrackingButton
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween as dpTween
import `in`.project.enroute.feature.navigation.NavigationViewModel
import `in`.project.enroute.feature.navigation.NavigationUiState
import `in`.project.enroute.feature.navigation.ui.NavigationPathOverlay

@Composable
fun HomeScreen(
    campusId: String,
    floorPlanViewModel: FloorPlanViewModel = viewModel(),
    pdrViewModel: PdrViewModel = viewModel(),
    navigationViewModel: NavigationViewModel = viewModel()
) {
    val uiState by floorPlanViewModel.uiState.collectAsState()
    val pdrUiState by pdrViewModel.uiState.collectAsState()
    val navUiState by navigationViewModel.uiState.collectAsState()
    // Heading collected separately for PDR — not used for compass (compass uses canvas rotation)
    val heading by pdrViewModel.heading.collectAsState()
    val view = LocalView.current

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

        // Delegate to content composable
        HomeScreenContent(
            uiState = uiState,
            pdrUiState = pdrUiState,
            navUiState = navUiState,
            heading = heading,
            effectiveCanvasState = effectiveCanvasState,
            maxWidth = maxWidth,
            onCanvasStateChange = {
                // If gesture cancels following mode, switch heading back to compass rate
                if (uiState.isFollowingMode) {
                    pdrViewModel.setHeadingTrackingMode(false)
                }
                floorPlanViewModel.updateCanvasState(it)
            },
            onFloorChange = { floorPlanViewModel.setCurrentFloor(it) },
            onCenterView = { x, y, scale -> floorPlanViewModel.centerOnCoordinate(x, y, scale) },
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
            onSetOriginClick = { pdrViewModel.startOriginSelection() },
            onClearPdrClick = {
                // Commit current following position before clearing PDR
                pdrViewModel.setHeadingTrackingMode(false)
                floorPlanViewModel.disableFollowingMode(effectiveCanvasState)
                pdrViewModel.clearAndStop()
                navigationViewModel.clearPath()
            },
            onOriginSelected = { origin ->
                // Pass current floor and building to PDR origin
                // Origin tap is already in campus-wide coordinates
                // Use findFloorAtPoint so origin works even when zoomed out (no dominant building)
                val currentFloor = floorPlanViewModel.findFloorAtPoint(origin)
                pdrViewModel.setOrigin(origin, currentFloor)
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
            }
        )
    }
}

@Composable
private fun HomeScreenContent(
    uiState: FloorPlanUiState,
    pdrUiState: PdrUiState,
    navUiState: NavigationUiState,
    heading: Float,
    effectiveCanvasState: CanvasState,
    maxWidth: Dp,
    onCanvasStateChange: (CanvasState) -> Unit,
    onFloorChange: (Float) -> Unit,
    onCenterView: (x: Float, y: Float, scale: Float) -> Unit,
    onRoomTap: (Room) -> Unit,
    onBackgroundTap: () -> Unit,
    onEnableTracking: (position: androidx.compose.ui.geometry.Offset, headingRadians: Float) -> Unit,
    onSetOriginClick: () -> Unit,
    onClearPdrClick: () -> Unit,
    onOriginSelected: (androidx.compose.ui.geometry.Offset) -> Unit,
    onCancelOriginSelection: () -> Unit,
    onDismissHeightRequired: () -> Unit,
    onSaveHeight: (Float) -> Unit,
    onDirectionsClick: (Room) -> Unit
) {
    var showSearch by remember { mutableStateOf(false) }
    var isMorphingToSearch by remember { mutableStateOf(false) }
    var showOriginDialog by remember { mutableStateOf(false) }
    var aimPressed by remember { mutableStateOf(false) }
    // When true, means the origin dialog was triggered by "Directions" button
    // and we should request directions once origin is set

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
                    onBackgroundTap = onBackgroundTap,
                    isSelectingOrigin = pdrUiState.isSelectingOrigin,
                    onOriginTap = onOriginSelected,
                    modifier = Modifier.fillMaxSize()
                )

                // A* navigation path overlay — rendered above base but below labels/pin
                // Supports multi-floor rendering: current floor at full opacity,
                // other floors faded+dashed with floor labels
                if (navUiState.hasPath) {
                    NavigationPathOverlay(
                        multiFloorPath = navUiState.multiFloorPath,
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
                        canvasState = effectiveCanvasState,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Motion classification label — small chip when tracking
                if (pdrUiState.pdrState.isTracking && pdrUiState.motionLabel != null) {
                    MotionLabel(
                        label = pdrUiState.motionLabel,
                        confidence = pdrUiState.motionConfidence,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 80.dp, end = 8.dp)
                    )
                }

                // Origin selection overlay with instructions (when in selection mode)
                if (pdrUiState.isSelectingOrigin) {
                    OriginSelectionOverlay(
                        onCancel = onCancelOriginSelection,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }

                // Floor slider and search button positioned at top, layered over canvas
                // Hidden during origin selection mode
                if (!pdrUiState.isSelectingOrigin) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp, end = 8.dp, start = 8.dp)
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
                            )
                        }

                        SearchButton(
                            isSliderVisible = uiState.showFloorSlider && !isMorphingToSearch && !showSearch,
                            isSearching = isMorphingToSearch,
                            containerWidth = maxWidth - 16.dp,
                            modifier = Modifier.align(Alignment.TopEnd),
                            onClick = { isMorphingToSearch = true },
                            onAnimationFinished = { showSearch = true }
                        )

                        CompassButton(
                            campusNorthDegrees = uiState.campusMetadata.north,
                            canvasRotationDegrees = effectiveCanvasState.rotation,
                            onClick = { /* Could reset canvas rotation */ },
                            isSliderVisible = uiState.showFloorSlider && !isMorphingToSearch && !showSearch,
                            isSearching = isMorphingToSearch,
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }
                }

                // Aim button positioned at bottom right
                // Hidden during origin selection mode or when following is enabled
                // Shows origin dialog if origin not set, otherwise enables following mode
                AimButton(
                    isVisible = !pdrUiState.isSelectingOrigin && !uiState.isFollowingMode && !aimPressed,
                    onClick = {
                        if (pdrUiState.pdrState.origin == null) {
                            showOriginDialog = true
                        } else {
                            aimPressed = true
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
                
                // Set My Location / Stop Tracking button positioned at bottom left
                // Shows "Set My Location" before origin is set, "Stop Tracking" after
                // Hides when user is selecting origin
                // Animates position based on whether slider or search is visible
                if (pdrUiState.pdrState.origin == null && !pdrUiState.isSelectingOrigin) {
                    SetLocationButton(
                        isSliderVisible = uiState.showFloorSlider && !isMorphingToSearch && !showSearch,
                        onClick = { showOriginDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = bottomButtonPadding)
                    )
                } else if (pdrUiState.pdrState.origin != null && !pdrUiState.isSelectingOrigin) {
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
                            // TODO: Implement location selection
                            showOriginDialog = false
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
                
                // Animated transition for SearchScreen
                AnimatedVisibility(
                    visible = showSearch,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(500))
                ) {
                    SearchScreen(
                        onBack = { 
                            showSearch = false 
                            isMorphingToSearch = false
                        },
                        onCenterView = onCenterView,
                        onRoomTap = onRoomTap
                    )
                }
            }
        }
    }
}
