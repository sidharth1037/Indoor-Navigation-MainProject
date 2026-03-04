package `in`.project.enroute.feature.settings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
        // v2 keys: filtered z domain (0.5–5.0 m/s²), not compatible with old unfiltered values
        val STEP_THRESHOLD    = floatPreferencesKey("step_threshold_v2")
        val HIGH_PASS_ALPHA   = floatPreferencesKey("high_pass_alpha")
        val MIN_PROMINENCE    = floatPreferencesKey("min_prominence")
        val RHYTHM_TOL_LOW    = floatPreferencesKey("rhythm_tol_low")
        val RHYTHM_TOL_HIGH   = floatPreferencesKey("rhythm_tol_high")
        val FLOOR_THRESHOLD   = floatPreferencesKey("floor_threshold")
        // Stair detection / model
        val ML_MODEL          = stringPreferencesKey("ml_model")
        val STAIR_ENTRY_THRESHOLD  = intPreferencesKey("stair_entry_threshold")
        val STAIR_PROXIMITY_RADIUS = floatPreferencesKey("stair_proximity_radius")
        val STAIR_LOOKBACK         = intPreferencesKey("stair_lookback")
        val STAIR_REPLAY_COUNT     = intPreferencesKey("stair_replay_count")
        // Stride tuning — height & turn
        val HEIGHT_K_INFLUENCE = floatPreferencesKey("height_k_influence")
        val TURN_WINDOW        = intPreferencesKey("turn_window")
        val TURN_THRESHOLD     = floatPreferencesKey("turn_threshold")
        val TURN_SENSITIVITY   = floatPreferencesKey("turn_sensitivity")
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

    // ── Step detection ──────────────────────────────────────────────────────

    /** Filtered |z| peak threshold (m/s²). Null = default 2.0f. */
    val stepThreshold: Flow<Float?> = context.dataStore.data.map { it[PreferencesKeys.STEP_THRESHOLD] }
    suspend fun saveStepThreshold(v: Float) = context.dataStore.edit { it[PreferencesKeys.STEP_THRESHOLD] = v }

    /** High-pass filter alpha (0–1). Null = default 0.9f. */
    val highPassAlpha: Flow<Float?> = context.dataStore.data.map { it[PreferencesKeys.HIGH_PASS_ALPHA] }
    suspend fun saveHighPassAlpha(v: Float) = context.dataStore.edit { it[PreferencesKeys.HIGH_PASS_ALPHA] = v }

    /** Minimum valley-to-peak height (m/s²). Null = default 1.5f. */
    val minProminence: Flow<Float?> = context.dataStore.data.map { it[PreferencesKeys.MIN_PROMINENCE] }
    suspend fun saveMinProminence(v: Float) = context.dataStore.edit { it[PreferencesKeys.MIN_PROMINENCE] = v }

    /** Rhythm gate lower tolerance multiplier. Null = default 0.4f. */
    val rhythmToleranceLow: Flow<Float?> = context.dataStore.data.map { it[PreferencesKeys.RHYTHM_TOL_LOW] }
    suspend fun saveRhythmToleranceLow(v: Float) = context.dataStore.edit { it[PreferencesKeys.RHYTHM_TOL_LOW] = v }

    /** Rhythm gate upper tolerance multiplier. Null = default 1.8f. */
    val rhythmToleranceHigh: Flow<Float?> = context.dataStore.data.map { it[PreferencesKeys.RHYTHM_TOL_HIGH] }
    suspend fun saveRhythmToleranceHigh(v: Float) = context.dataStore.edit { it[PreferencesKeys.RHYTHM_TOL_HIGH] = v }

    /** Floor threshold: signal must dip below this between steps (m/s²). Null = default 0.8f. */
    val floorThreshold: Flow<Float?> = context.dataStore.data.map { it[PreferencesKeys.FLOOR_THRESHOLD] }
    suspend fun saveFloorThreshold(v: Float) = context.dataStore.edit { it[PreferencesKeys.FLOOR_THRESHOLD] = v }

    // ── ML model & stair detection ───────────────────────────────────────

    /** ML model key: "v6" (96-window) or "v6_64" (64-window). Null = default "v6". */
    val mlModel: Flow<String?> = context.dataStore.data.map { it[PreferencesKeys.ML_MODEL] }
    suspend fun saveMlModel(v: String) = context.dataStore.edit { it[PreferencesKeys.ML_MODEL] = v }

    /** Consecutive stair labels needed to enter a stairwell. Null = default 2. */
    val stairEntryThreshold: Flow<Int?> = context.dataStore.data.map { it[PreferencesKeys.STAIR_ENTRY_THRESHOLD] }
    suspend fun saveStairEntryThreshold(v: Int) = context.dataStore.edit { it[PreferencesKeys.STAIR_ENTRY_THRESHOLD] = v }

    /** Max distance (campus units) from a stairwell edge to trigger proximity detection. Null = default 150. */
    val stairProximityRadius: Flow<Float?> = context.dataStore.data.map { it[PreferencesKeys.STAIR_PROXIMITY_RADIUS] }
    suspend fun saveStairProximityRadius(v: Float) = context.dataStore.edit { it[PreferencesKeys.STAIR_PROXIMITY_RADIUS] = v }

    /** How many steps back from arrival to find the first compensation step. Null = default 3. */
    val stairLookback: Flow<Int?> = context.dataStore.data.map { it[PreferencesKeys.STAIR_LOOKBACK] }
    suspend fun saveStairLookback(v: Int) = context.dataStore.edit { it[PreferencesKeys.STAIR_LOOKBACK] = v }

    /** How many buffered steps to replay on the new floor. Null = default 3. */
    val stairReplayCount: Flow<Int?> = context.dataStore.data.map { it[PreferencesKeys.STAIR_REPLAY_COUNT] }
    suspend fun saveStairReplayCount(v: Int) = context.dataStore.edit { it[PreferencesKeys.STAIR_REPLAY_COUNT] = v }

    // ── Stride tuning ─ height & turn ─────────────────────────────────────

    /** How much height influences K. Null = default 0.05. */
    val heightKInfluence: Flow<Float?> = context.dataStore.data.map { it[PreferencesKeys.HEIGHT_K_INFLUENCE] }
    suspend fun saveHeightKInfluence(v: Float) = context.dataStore.edit { it[PreferencesKeys.HEIGHT_K_INFLUENCE] = v }

    /** Heading window size for turn detection. Null = default 3. */
    val turnWindow: Flow<Int?> = context.dataStore.data.map { it[PreferencesKeys.TURN_WINDOW] }
    suspend fun saveTurnWindow(v: Int) = context.dataStore.edit { it[PreferencesKeys.TURN_WINDOW] = v }

    /** Min cumulative heading change (degrees) to trigger turn reduction. Null = default 60. */
    val turnThreshold: Flow<Float?> = context.dataStore.data.map { it[PreferencesKeys.TURN_THRESHOLD] }
    suspend fun saveTurnThreshold(v: Float) = context.dataStore.edit { it[PreferencesKeys.TURN_THRESHOLD] = v }

    /** Overall strength of turn-based stride reduction. Null = default 0.5. */
    val turnSensitivity: Flow<Float?> = context.dataStore.data.map { it[PreferencesKeys.TURN_SENSITIVITY] }
    suspend fun saveTurnSensitivity(v: Float) = context.dataStore.edit { it[PreferencesKeys.TURN_SENSITIVITY] = v }

}
