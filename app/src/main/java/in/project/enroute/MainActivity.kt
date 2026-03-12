package `in`.project.enroute

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import `in`.project.enroute.core.navigation.NavigationGraph
import `in`.project.enroute.ui.theme.EnrouteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EnrouteTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    Surface(modifier = Modifier.fillMaxSize()) {
        NavigationGraph(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        )
    }
}