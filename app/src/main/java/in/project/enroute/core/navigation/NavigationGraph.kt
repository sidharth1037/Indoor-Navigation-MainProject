package `in`.project.enroute.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import `in`.project.enroute.feature.floorplan.FloorPlanViewModel
import `in`.project.enroute.feature.home.HomeScreen
import `in`.project.enroute.feature.navigation.NavigationViewModel
import `in`.project.enroute.feature.pdr.PdrViewModel
import `in`.project.enroute.feature.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Settings : Screen("settings")
}

@Composable
fun NavigationGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
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
    }
}
