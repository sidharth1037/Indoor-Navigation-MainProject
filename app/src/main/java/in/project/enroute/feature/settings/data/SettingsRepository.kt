package `in`.project.enroute.feature.settings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    
    private object PreferencesKeys {
        val HEIGHT = floatPreferencesKey("user_height")
        val SHOW_ENTRANCES = booleanPreferencesKey("show_entrances")
        val STRIDE_K = floatPreferencesKey("stride_k_value")
        val STRIDE_C = floatPreferencesKey("stride_c_value")
        val STEP_THRESHOLD = floatPreferencesKey("step_threshold")
    }
    
    /**
     * Flow of the stored height value in centimeters.
     * Null if the user has not set a height yet.
     */
    val height: Flow<Float?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.HEIGHT]
    }
    
    /**
     * Flow of the entrance visibility preference.
     * Defaults to false (entrances hidden).
     */
    val showEntrances: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SHOW_ENTRANCES] ?: false
    }
    
    /**
     * Saves the user's height in centimeters.
     */
    suspend fun saveHeight(heightCm: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HEIGHT] = heightCm
        }
    }
    
    /**
     * Saves the entrance visibility preference.
     */
    suspend fun saveShowEntrances(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_ENTRANCES] = show
        }
    }

    // ── Stride constants ──────────────────────────────────────────────────────

    /**
     * Flow of the stride K constant (cadence sensitivity).
     * Null = use default from StrideConfig.
     */
    val strideK: Flow<Float?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.STRIDE_K]
    }

    /**
     * Flow of the stride C constant (base stride).
     * Null = use default from StrideConfig.
     */
    val strideC: Flow<Float?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.STRIDE_C]
    }

    suspend fun saveStrideK(k: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.STRIDE_K] = k
        }
    }

    suspend fun saveStrideC(c: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.STRIDE_C] = c
        }
    }

    // ── Step detection threshold ──────────────────────────────────────────────

    /**
     * Flow of the step detection threshold (acceleration in m/s²).
     * Null = use default (12f).
     */
    val stepThreshold: Flow<Float?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.STEP_THRESHOLD]
    }

    suspend fun saveStepThreshold(threshold: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.STEP_THRESHOLD] = threshold
        }
    }

}
