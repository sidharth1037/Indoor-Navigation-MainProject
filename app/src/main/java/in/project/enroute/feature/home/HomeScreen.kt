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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import `in`.project.enroute.feature.floorplan.rendering.FloorPlanCanvas

@Composable
fun HomeScreen(
    floorPlanViewModel: FloorPlanViewModel = viewModel()
) {
    val uiState by floorPlanViewModel.uiState.collectAsState()

    // Load all floors on first composition
    LaunchedEffect(Unit) {
        floorPlanViewModel.loadAllFloors(
            "building_1", 
            listOf("floor_1", "floor_1.5", "floor_2", "floor_2.5")
        )
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

        // Delegate to content composable
        HomeScreenContent(
            uiState = uiState,
            maxWidth = maxWidth,
            onCanvasStateChange = { floorPlanViewModel.updateCanvasState(it) },
            onFloorChange = { floorPlanViewModel.setCurrentFloor(it) },
            onCenterView = { x, y, scale -> floorPlanViewModel.centerOnCoordinate(x, y, scale) }
        )
    }
}

@Composable
private fun HomeScreenContent(
    uiState: FloorPlanUiState,
    maxWidth: Dp,
    onCanvasStateChange: (CanvasState) -> Unit,
    onFloorChange: (Float) -> Unit,
    onCenterView: (x: Float, y: Float, scale: Float) -> Unit
) {
    var showSearch by remember { mutableStateOf(false) }
    var isMorphingToSearch by remember { mutableStateOf(false) }

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
            uiState.allFloorsToRender.isNotEmpty() -> {
                // Floor plan canvas filling entire screen
                FloorPlanCanvas(
                    floorsToRender = uiState.allFloorsToRender,
                    canvasState = uiState.canvasState,
                    onCanvasStateChange = onCanvasStateChange,
                    displayConfig = uiState.displayConfig,
                    modifier = Modifier.fillMaxSize()
                )

                // Floor slider and search button positioned at top, layered over canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp, end = 8.dp, start = 8.dp)
                ) {
                    // Floor slider - animated exit when search button is pressed
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
                            isVisible = true, // Visibility managed by AnimatedVisibility
                        )
                    }
                    
                    // Search button
                    SearchButton(
                        isSliderVisible = uiState.showFloorSlider && !isMorphingToSearch && !showSearch,
                        isSearching = isMorphingToSearch,
                        containerWidth = maxWidth - 16.dp,
                        modifier = Modifier.align(Alignment.TopEnd),
                        onClick = { isMorphingToSearch = true },
                        onAnimationFinished = {
                            showSearch = true
                        }
                    )
                }
                
                // Aim button positioned at bottom right
                AimButton(
                    onClick = {
                        // Center view on hardcoded coordinate
                        onCenterView(1338f, 1328f, 0.48f)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 16.dp, end = 16.dp)
                )
                
                // Animated transition for SearchScreen
                AnimatedVisibility(
                    visible = showSearch,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(500))
                ) {
                    SearchScreen(onBack = { 
                        showSearch = false 
                        isMorphingToSearch = false
                    })
                }
            }
        }
    }
}
