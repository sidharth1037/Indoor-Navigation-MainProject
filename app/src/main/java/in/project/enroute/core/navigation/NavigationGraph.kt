package `in`.project.enroute.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import `in`.project.enroute.feature.admin.AdminScreen
import `in`.project.enroute.feature.floorplan.FloorPlanViewModel
import `in`.project.enroute.feature.home.HomeScreen
import `in`.project.enroute.feature.navigation.NavigationViewModel
import `in`.project.enroute.feature.pdr.PdrViewModel
import `in`.project.enroute.feature.settings.SettingsScreen
import `in`.project.enroute.feature.welcome.WelcomeScreen

sealed class Screen(val route: String) {
    data object Welcome : Screen("welcome")
    data object Home : Screen("home")
    data object Settings : Screen("settings")
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
                    // Navigate to Home and remove Welcome from back stack
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Screen.Home.route) { backStackEntry ->
            // Scope ViewModels to this navigation destination's backstack entry
            // This preserves state when navigating away and returning
            val floorPlanViewModel: FloorPlanViewModel = viewModel(backStackEntry)
            val pdrViewModel: PdrViewModel = viewModel(backStackEntry)
            val navigationViewModel: NavigationViewModel = viewModel(backStackEntry)
            HomeScreen(
                floorPlanViewModel = floorPlanViewModel,
                pdrViewModel = pdrViewModel,
                navigationViewModel = navigationViewModel
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        composable(Screen.Admin.route) {
            AdminScreen()
        }
    }
}
