package `in`.project.enroute.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.project.enroute.feature.admin.auth.AdminAuthRepository
import `in`.project.enroute.feature.settings.components.HeightSettingItem

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onAdminLogin: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAdminLoggedIn by AdminAuthRepository.isLoggedIn.collectAsState()
    
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
        
        // Show entrances toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = "Show entrances on map",
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = uiState.showEntrances,
                onCheckedChange = { viewModel.toggleShowEntrances() },
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        // Clear cache button
        var cacheCleared by remember { mutableStateOf(false) }

        Button(
            onClick = {
                viewModel.clearBackendCache()
                cacheCleared = true
            },
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text(if (cacheCleared) "Cache cleared \u2713" else "Clear cached map data")
        }
        Text(
            text = "Maps will reload from Firebase on next visit",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(24.dp))

        // Admin login / logout section
        if (isAdminLoggedIn) {
            Text(
                text = "Signed in as ${AdminAuthRepository.currentUser?.email ?: "admin"}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedButton(
                onClick = { AdminAuthRepository.logout() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Logout Admin")
            }
        } else {
            OutlinedButton(
                onClick = onAdminLogin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Admin Login")
            }
        }
    }
}


