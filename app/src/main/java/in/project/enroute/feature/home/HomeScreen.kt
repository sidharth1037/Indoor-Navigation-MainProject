package `in`.project.enroute.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.project.enroute.feature.floorplan.FloorPlanViewModel
import `in`.project.enroute.feature.floorplan.components.FloorSlider
import `in`.project.enroute.feature.floorplan.rendering.FloorPlanCanvas

@Composable
fun HomeScreen(
    floorPlanViewModel: FloorPlanViewModel = viewModel()
) {
    val uiState by floorPlanViewModel.uiState.collectAsState()

    // Load all floors on first composition
    LaunchedEffect(Unit) {
        floorPlanViewModel.loadAllFloors("building_1", listOf("floor_1", "floor_1.5","floor_2","floor_2.5"))
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
                Text(text = uiState.error ?: "Unknown error")
            }
            uiState.floorsToRender.isNotEmpty() -> {
                // Floor plan canvas filling entire screen
                FloorPlanCanvas(
                    floorsToRender = uiState.floorsToRender,
                    canvasState = uiState.canvasState,
                    onCanvasStateChange = { floorPlanViewModel.updateCanvasState(it) },
                    displayConfig = uiState.displayConfig,
                    modifier = Modifier.fillMaxSize()
                )

                // Floor slider positioned at top center, layered over canvas
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                ) {
                    FloorSlider(
                        availableFloors = uiState.availableFloorNumbers,
                        currentFloor = uiState.currentFloorNumber,
                        onFloorChange = { floorPlanViewModel.setCurrentFloor(it) }
                    )
                }
            }
        }
    }
}
