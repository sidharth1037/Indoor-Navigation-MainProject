package `in`.project.enroute.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.project.enroute.feature.admin.auth.AdminAuthRepository
import `in`.project.enroute.feature.settings.components.HeightSettingItem

// ── Reusable helpers ─────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SubsectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

// ── Main screen ───────────────────────────────────────────────────────────────

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
        Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ─────────────────────────────────────────────────────────────────────
        // 1. USER PROFILE
        // ─────────────────────────────────────────────────────────────────────
        SectionHeader(title = "USER PROFILE")

        HeightSettingItem(viewModel = viewModel)

        // ─────────────────────────────────────────────────────────────────────
        // 2. MAP DISPLAY & DATA
        // ─────────────────────────────────────────────────────────────────────
        SectionHeader(title = "MAP DISPLAY & DATA")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Show entrances on map", fontSize = 16.sp)
            }
            Switch(
                checked = uiState.showEntrances,
                onCheckedChange = { viewModel.toggleShowEntrances() },
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        var cacheCleared by remember { mutableStateOf(false) }
        Button(
            onClick = {
                viewModel.clearBackendCache()
                cacheCleared = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (cacheCleared) "Cache cleared ✓" else "Clear cached map data")
        }
        Text(
            text = "Maps will reload from Firebase on next visit",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )

        // ─────────────────────────────────────────────────────────────────────
        // 3. PDR TUNING  (Step Detection + Stride Tuning merged)
        // ─────────────────────────────────────────────────────────────────────
        SectionHeader(title = "PDR TUNING")

        // ── Step Detection subsection ─────────────────────────────────────
        SubsectionHeader("Step Detection")

        // A: Threshold
        Text("Peak threshold: ${"%.2f".format(uiState.stepThreshold)} m/s²", fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Slider(value = uiState.stepThreshold, onValueChange = { viewModel.updateStepThreshold(it) }, valueRange = 0.5f..5.0f, steps = 17, modifier = Modifier.fillMaxWidth())
        Text("Min filtered |z| peak to count as a step. Default 2.00.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

        // A: High-pass alpha
        Text("High-pass α: ${"%.2f".format(uiState.highPassAlpha)}", fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Slider(value = uiState.highPassAlpha, onValueChange = { viewModel.updateHighPassAlpha(it) }, valueRange = 0.70f..0.98f, steps = 27, modifier = Modifier.fillMaxWidth())
        Text("Gravity filter strength. Higher = more responsive, less gravity rejection. Default 0.90.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

        // B: Min prominence
        Text("Min prominence: ${"%.2f".format(uiState.minProminence)} m/s²", fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Slider(value = uiState.minProminence, onValueChange = { viewModel.updateMinProminence(it) }, valueRange = 0.5f..4.0f, steps = 13, modifier = Modifier.fillMaxWidth())
        Text("Peak must rise this much above the preceding valley. Rejects single spikes. Default 1.50.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

        // D: Floor threshold
        Text("Floor threshold: ${"%.2f".format(uiState.floorThreshold)} m/s²", fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Slider(value = uiState.floorThreshold, onValueChange = { viewModel.updateFloorThreshold(it) }, valueRange = 0.2f..2.0f, steps = 17, modifier = Modifier.fillMaxWidth())
        Text("Signal must dip below this between steps (full oscillation required). Default 0.80.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

        // C: Rhythm tolerance
        Text("Rhythm gate — earliest: ${"%.2f".format(uiState.rhythmToleranceLow)}×avg", fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Slider(value = uiState.rhythmToleranceLow, onValueChange = { viewModel.updateRhythmToleranceLow(it) }, valueRange = 0.2f..0.7f, steps = 9, modifier = Modifier.fillMaxWidth())
        Text("Steps arriving earlier than avgInterval × this are rejected. Default 0.40.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

        Text("Rhythm gate — latest: ${"%.2f".format(uiState.rhythmToleranceHigh)}×avg", fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Slider(value = uiState.rhythmToleranceHigh, onValueChange = { viewModel.updateRhythmToleranceHigh(it) }, valueRange = 1.2f..2.5f, steps = 12, modifier = Modifier.fillMaxWidth())
        Text("Steps arriving later than avgInterval × this are rejected. Default 1.80.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

        Spacer(Modifier.height(8.dp))

        // ── Stride Tuning subsection ──────────────────────────────────────
        SubsectionHeader("Stride Tuning")

        Text(
            text = "K (cadence sensitivity): ${"%.3f".format(uiState.strideK)}",
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Slider(
            value = uiState.strideK,
            onValueChange = { viewModel.updateStrideK(it) },
            valueRange = 0.05f..0.30f,
            steps = 24,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "C (base stride): ${"%.3f".format(uiState.strideC)}",
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Slider(
            value = uiState.strideC,
            onValueChange = { viewModel.updateStrideC(it) },
            valueRange = 0.10f..0.40f,
            steps = 29,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Lower C = shorter steps on map",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // ─────────────────────────────────────────────────────────────────────
        // 4. ADMIN
        // ─────────────────────────────────────────────────────────────────────
        SectionHeader(title = "ADMIN")

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

        Spacer(Modifier.height(24.dp))
    }
}


