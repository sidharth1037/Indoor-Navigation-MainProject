package `in`.project.enroute.feature.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// ── Admin route definitions ────────────────────────────────────
sealed class AdminRoute(val route: String) {
    data object SelectCampus : AdminRoute("admin/select_campus")
    data object CampusHome : AdminRoute("admin/campus_home")
    data object AddBuilding : AdminRoute("admin/add_building")
    data object AddFloor : AdminRoute("admin/add_floor")
    data object EditCampus : AdminRoute("admin/edit_campus")
    data object EditBuilding : AdminRoute("admin/edit_building")
}

// ── Admin screen entry point ───────────────────────────────────

@Composable
fun AdminScreen(viewModel: AdminViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()

    Column(modifier = Modifier.fillMaxSize()) {
        // Status message bar (always visible across all admin pages)
        uiState.statusMessage?.let { message ->
            StatusBar(
                message = message,
                isError = uiState.isError,
                onDismiss = { viewModel.clearStatus() }
            )
        }

        NavHost(
            navController = navController,
            startDestination = AdminRoute.SelectCampus.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(AdminRoute.SelectCampus.route) {
                SelectCampusScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onCampusSelected = { navController.navigate(AdminRoute.CampusHome.route) }
                )
            }

            composable(AdminRoute.CampusHome.route) {
                CampusHomeScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onAddBuilding = {
                        viewModel.prepareAddBuilding()
                        navController.navigate(AdminRoute.AddBuilding.route)
                    },
                    onAddFloor = {
                        viewModel.prepareAddFloor()
                        navController.navigate(AdminRoute.AddFloor.route)
                    },
                    onEditCampus = {
                        viewModel.prepareEditCampus()
                        navController.navigate(AdminRoute.EditCampus.route)
                    },
                    onEditBuilding = { buildingId ->
                        viewModel.prepareEditBuilding(buildingId)
                        navController.navigate(AdminRoute.EditBuilding.route)
                    },
                    onCampusDeleted = {
                        navController.popBackStack(AdminRoute.SelectCampus.route, inclusive = false)
                    }
                )
            }

            composable(AdminRoute.AddBuilding.route) {
                AddBuildingScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(AdminRoute.AddFloor.route) {
                AddFloorScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(AdminRoute.EditCampus.route) {
                EditCampusScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(AdminRoute.EditBuilding.route) {
                EditBuildingScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
