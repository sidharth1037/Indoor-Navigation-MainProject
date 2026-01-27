package `in`.project.enroute.feature.home

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
            onFloorChange = { floorPlanViewModel.setCurrentFloor(it) }
        )
    }
}

@Composable
private fun HomeScreenContent(
    uiState: FloorPlanUiState,
    maxWidth: Dp,
    onCanvasStateChange: (CanvasState) -> Unit,
    onFloorChange: (Float) -> Unit
) {
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
                // Use Box to position them independently so SearchButton stays in place
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp, end = 8.dp, start = 8.dp)
                ) {
                    // Floor slider - takes available width except for search button space
                    FloorSlider(
                        buildingName = uiState.sliderBuildingName,
                        availableFloors = uiState.sliderFloorNumbers,
                        currentFloor = uiState.sliderCurrentFloor,
                        onFloorChange = onFloorChange,
                        isVisible = uiState.showFloorSlider,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(end = 56.dp) // Leave space for search button when visible
                    )
                    
                    // Search button - fixed at top end
                    // When slider hides, it expands to fill the full width (maxWidth - horizontal padding)
                    SearchButton(
                        isSliderVisible = uiState.showFloorSlider,
                        containerWidth = maxWidth - 16.dp, // Accounting for 8dp horizontal padding on Box
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }
        }
    }
}
