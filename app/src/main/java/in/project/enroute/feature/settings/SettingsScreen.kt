package `in`.project.enroute.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.project.enroute.feature.settings.components.HeightSettingItem

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Settings label
        Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Height setting item
        HeightSettingItem(viewModel = viewModel)

        // Clear cache button
        var cacheCleared by remember { mutableStateOf(false) }

        Button(
            onClick = {
                viewModel.clearBackendCache()
                cacheCleared = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        ) {
            Text(if (cacheCleared) "Cache cleared \u2713" else "Clear cached map data")
        }
        Text(
            text = "Maps will reload from Firebase on next visit",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}


