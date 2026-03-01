package `in`.project.enroute.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import `in`.project.enroute.feature.admin.AdminScreen
import `in`.project.enroute.feature.admin.auth.AdminLoginScreen
import `in`.project.enroute.feature.floorplan.FloorPlanViewModel
import `in`.project.enroute.feature.home.HomeScreen
import `in`.project.enroute.feature.navigation.NavigationViewModel
import `in`.project.enroute.feature.pdr.PdrViewModel
import `in`.project.enroute.feature.settings.SettingsScreen
import `in`.project.enroute.feature.welcome.WelcomeScreen

sealed class Screen(val route: String) {
    data object Welcome : Screen("welcome")
    data object Home : Screen("home/{campusId}") {
        fun createRoute(campusId: String) = "home/$campusId"
    }
    data object Settings : Screen("settings")
    data object AdminLogin : Screen("admin_login")
    data object Admin : Screen("admin")
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
            HomeScreen(
                campusId = campusId,
                floorPlanViewModel = floorPlanViewModel,
                pdrViewModel = pdrViewModel,
                navigationViewModel = navigationViewModel
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onAdminLogin = {
                    navController.navigate(Screen.AdminLogin.route)
                }
            )
        }
        composable(Screen.AdminLogin.route) {
            AdminLoginScreen(
                onLoginSuccess = {
                    navController.popBackStack()
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.Admin.route) {
            AdminScreen()
        }
    }
}
