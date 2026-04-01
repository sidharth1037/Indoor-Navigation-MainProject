package `in`.project.enroute.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import `in`.project.enroute.feature.admin.AdminScreen
import `in`.project.enroute.feature.admin.auth.AdminAuthRepository
import `in`.project.enroute.feature.admin.auth.AdminLoginScreen
import `in`.project.enroute.feature.floorplan.FloorPlanViewModel
import `in`.project.enroute.feature.home.HomeScreen
import `in`.project.enroute.feature.home.elevator.ElevatorViewModel
import `in`.project.enroute.feature.landmark.LandmarkViewModel
import `in`.project.enroute.feature.landmark.ui.AddLandmarkScreen
import `in`.project.enroute.feature.navigation.NavigationViewModel
import `in`.project.enroute.feature.pdr.PdrViewModel
import `in`.project.enroute.feature.roominfo.RoomInfoViewModel
import `in`.project.enroute.feature.roominfo.ui.RoomInfoScreen
import `in`.project.enroute.feature.settings.SettingsScreen
import `in`.project.enroute.feature.welcome.WelcomeScreen
import android.widget.Toast
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

sealed class Screen(val route: String) {
    data object Welcome : Screen("welcome")
    data object Home : Screen("home/{campusId}") {
        fun createRoute(campusId: String) = "home/$campusId"
    }
    data object Settings : Screen("settings")
    data object AdminLogin : Screen("admin_login")
    data object Admin : Screen("admin")
    data object AddLandmark : Screen("add_landmark/{campusId}") {
        fun createRoute(campusId: String) = "add_landmark/$campusId"
    }
    data object RoomInfo : Screen("room_info/{campusId}/{buildingId}/{floorId}/{roomId}/{roomNumber}/{roomName}") {
        fun createRoute(
            campusId: String,
            buildingId: String,
            floorId: String,
            roomId: Int,
            roomNumber: Int?,
            roomName: String?
        ) = "room_info/$campusId/$buildingId/$floorId/$roomId/${roomNumber ?: "null"}/${roomName ?: "null"}"
    }
}

@Composable
fun NavigationGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Welcome.route,
        modifier = modifier
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onCampusSelected = { campusId ->
                    // Push Home on top of Welcome so back gesture returns here
                    navController.navigate(Screen.Home.createRoute(campusId)) {
                        launchSingleTop = true
                    }
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route) {
                        launchSingleTop = true
                    }
                },
                onAdminClick = {
                    val adminRoute = if (AdminAuthRepository.isLoggedIn.value) {
                        Screen.Admin.route
                    } else {
                        Screen.AdminLogin.route
                    }
                    navController.navigate(adminRoute) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = Screen.Home.route,
            arguments = listOf(navArgument("campusId") { type = NavType.StringType })
        ) { backStackEntry ->
            val campusId = backStackEntry.arguments?.getString("campusId") ?: ""
            // Scope ViewModels to this navigation destination's backstack entry
            val floorPlanViewModel: FloorPlanViewModel = viewModel(backStackEntry)
            val pdrViewModel: PdrViewModel = viewModel(backStackEntry)
            val navigationViewModel: NavigationViewModel = viewModel(backStackEntry)
            val elevatorViewModel: ElevatorViewModel = viewModel(backStackEntry)
            val landmarkViewModel: LandmarkViewModel = viewModel(backStackEntry)
            val roomInfoViewModel: RoomInfoViewModel = viewModel(backStackEntry)
            HomeScreen(
                campusId = campusId,
                floorPlanViewModel = floorPlanViewModel,
                pdrViewModel = pdrViewModel,
                navigationViewModel = navigationViewModel,
                elevatorViewModel = elevatorViewModel,
                landmarkViewModel = landmarkViewModel,
                roomInfoViewModel = roomInfoViewModel,
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route) {
                        launchSingleTop = true
                    }
                },
                onAdminClick = {
                    val adminRoute = if (AdminAuthRepository.isLoggedIn.value) {
                        Screen.Admin.route
                    } else {
                        Screen.AdminLogin.route
                    }
                    navController.navigate(adminRoute) {
                        launchSingleTop = true
                    }
                },
                onNavigateToAddLandmark = {
                    navController.navigate(Screen.AddLandmark.createRoute(campusId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToRoomInfo = { buildingId, floorId, roomId, roomNumber, roomName ->
                    navController.navigate(
                        Screen.RoomInfo.createRoute(
                            campusId,
                            buildingId,
                            floorId,
                            roomId,
                            roomNumber,
                            roomName
                        )
                    ) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.AdminLogin.route) {
            AdminLoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Admin.route) {
                        popUpTo(Screen.AdminLogin.route) { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.Admin.route) {
            AdminScreen(
                onBack = {
                    navController.popBackStack()
                },
                onLogout = {
                    navController.navigate(Screen.AdminLogin.route) {
                        popUpTo(Screen.Admin.route) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Screen.AddLandmark.route,
            arguments = listOf(navArgument("campusId") { type = NavType.StringType })
        ) { backStackEntry ->
            val campusId = backStackEntry.arguments?.getString("campusId") ?: ""
            // Share the LandmarkViewModel from the Home backstack entry
            val homeEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.Home.createRoute(campusId))
            }
            val landmarkViewModel: LandmarkViewModel = viewModel(homeEntry)
            val context = LocalContext.current
            val landmarkUiState = landmarkViewModel.uiState.collectAsState().value
            val editingLandmark = landmarkUiState.selectedLandmark

            AddLandmarkScreen(
                isSaving = landmarkUiState.isSaving,
                initialName = editingLandmark?.name ?: "",
                initialIconId = editingLandmark?.icon,
                title = if (editingLandmark != null) "Edit Landmark" else "Add Landmark",
                saveButtonLabel = if (editingLandmark != null) "Save Changes" else "Save Landmark",
                onSave = { name, iconId ->
                    if (editingLandmark != null) {
                        landmarkViewModel.updateLandmark(
                            editingLandmark.copy(name = name, icon = iconId),
                            onResult = { success ->
                                if (success) {
                                    Toast.makeText(context, "Landmark updated", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                } else {
                                    Toast.makeText(context, "Failed to update landmark", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    } else {
                        landmarkViewModel.saveLandmark(
                            name = name,
                            icon = iconId,
                            campusX = landmarkUiState.pendingLandmarkLocation?.x ?: 0f,
                            campusY = landmarkUiState.pendingLandmarkLocation?.y ?: 0f,
                            onResult = { success ->
                                if (success) {
                                    Toast.makeText(context, "Landmark added", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                } else {
                                    Toast.makeText(context, "Failed to add landmark", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.RoomInfo.route,
            arguments = listOf(
                navArgument("campusId") { type = NavType.StringType },
                navArgument("buildingId") { type = NavType.StringType },
                navArgument("floorId") { type = NavType.StringType },
                navArgument("roomId") { type = NavType.IntType },
                navArgument("roomNumber") { type = NavType.StringType },
                navArgument("roomName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val campusId = backStackEntry.arguments?.getString("campusId") ?: ""
            val buildingId = backStackEntry.arguments?.getString("buildingId") ?: ""
            val buildingName = remember(backStackEntry) {
                val homeEntry = navController.getBackStackEntry(Screen.Home.createRoute(campusId))
                val floorPlanViewModel: FloorPlanViewModel = viewModel(homeEntry)
                val uiState = floorPlanViewModel.uiState.value
                uiState.buildingStates[buildingId]?.building?.buildingName ?: ""
            }
            val floorId = backStackEntry.arguments?.getString("floorId") ?: ""
            val roomId = backStackEntry.arguments?.getInt("roomId") ?: 0
            val roomNumberStr = backStackEntry.arguments?.getString("roomNumber") ?: "null"
            val roomNameStr = backStackEntry.arguments?.getString("roomName") ?: "null"
            val roomNumber = if (roomNumberStr == "null") null else roomNumberStr.toIntOrNull()
            val roomName = if (roomNameStr == "null") null else roomNameStr

            // Share the RoomInfoViewModel from the Home backstack entry
            val homeEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.Home.createRoute(campusId))
            }
            val roomInfoViewModel: RoomInfoViewModel = viewModel(homeEntry)
            val isAdmin = AdminAuthRepository.isLoggedIn.value && 
                remember(backStackEntry) {
                    val floorPlanViewModel: FloorPlanViewModel = viewModel(homeEntry)
                    val uiState = floorPlanViewModel.uiState.value
                    uiState.campusMetadata.createdBy == AdminAuthRepository.currentUser?.uid
                }

            RoomInfoScreen(
                campusId = campusId,
                buildingId = buildingId,
                buildingName = buildingName,
                floorId = floorId,
                roomId = roomId,
                roomNumber = roomNumber,
                roomName = roomName,
                isAdmin = isAdmin,
                viewModel = roomInfoViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
