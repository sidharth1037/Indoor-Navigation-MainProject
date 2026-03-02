package `in`.project.enroute.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
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
            .verticalScroll(rememberScrollState())
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

        Spacer(Modifier.height(8.dp))

        // ── Stride tuning sliders ───────────────────────────────────────
        Text(
            text = "Stride Tuning",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // K value slider (cadence sensitivity)
        Text(
            text = "K (cadence sensitivity): ${"%.3f".format(uiState.strideK)}",
            fontSize = 14.sp
        )
        Slider(
            value = uiState.strideK,
            onValueChange = { viewModel.updateStrideK(it) },
            valueRange = 0.05f..0.30f,
            steps = 24,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(4.dp))

        // C value slider (base stride constant)
        Text(
            text = "C (base stride): ${"%.3f".format(uiState.strideC)}",
            fontSize = 14.sp
        )
        Slider(
            value = uiState.strideC,
            onValueChange = { viewModel.updateStrideC(it) },
            valueRange = 0.10f..0.40f,
            steps = 29,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Lower values = shorter steps on map",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ── Step detection threshold slider ────────────────────────────────────
        Text(
            text = "Step Detection Threshold",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Step threshold slider (acceleration in m/s²)
        Text(
            text = "Threshold: ${"%.1f".format(uiState.stepThreshold)} m/s²",
            fontSize = 14.sp
        )
        Slider(
            value = uiState.stepThreshold,
            onValueChange = { viewModel.updateStepThreshold(it) },
            valueRange = 9.4f..12f,
            steps = 13,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Higher values = fewer false steps, may miss real steps",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Spacer(Modifier.height(8.dp))

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


