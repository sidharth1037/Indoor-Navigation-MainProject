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
        val SHOW_MOTION_LABEL = booleanPreferencesKey("show_motion_label")
        val STRIDE_K = floatPreferencesKey("stride_k_value")
        val STRIDE_C = floatPreferencesKey("stride_c_value")
        // v2 keys: filtered z domain (0.5–5.0 m/s²), not compatible with old unfiltered values
        val STEP_THRESHOLD    = floatPreferencesKey("step_threshold_v2")
        val HIGH_PASS_ALPHA   = floatPreferencesKey("high_pass_alpha")
        val COMPENSATION_STEPS = intPreferencesKey("compensation_steps")
        // Stair detection / model
        val ML_MODEL          = stringPreferencesKey("ml_model")
        val STAIR_ENTRY_THRESHOLD  = intPreferencesKey("stair_entry_threshold")
        val STAIR_MIN_CONFIDENCE   = floatPreferencesKey("stair_min_confidence")
        val STAIR_PROXIMITY_RADIUS = floatPreferencesKey("stair_proximity_radius")
        val STAIR_LOOKBACK         = intPreferencesKey("stair_lookback")
        val STAIR_REPLAY_COUNT     = intPreferencesKey("stair_replay_count")
        val STAIR_ARRIVAL_LOOKBACK = intPreferencesKey("stair_arrival_lookback")
        val STAIR_MIN_PROGRESS_FOR_ARRIVAL = floatPreferencesKey("stair_min_progress_for_arrival")
        val STAIR_WALKING_ARRIVAL_COUNT = intPreferencesKey("stair_walking_arrival_count")
        val STAIR_OPPOSITE_CANCEL_COUNT = intPreferencesKey("stair_opposite_cancel_count")
        val STAIR_MIN_STEPS_BEFORE_CANCEL = intPreferencesKey("stair_min_steps_before_cancel")
        val STAIR_ARRIVAL_HEADING_DEG = floatPreferencesKey("stair_arrival_heading_deg")
        val STAIR_RETURN_HEADING_DEG = floatPreferencesKey("stair_return_heading_deg")
        val STAIR_IDLE_THRESHOLD = intPreferencesKey("stair_idle_threshold")
        // Stride tuning — height & turn
        val HEIGHT_K_INFLUENCE = floatPreferencesKey("height_k_influence")
        val TURN_WINDOW        = intPreferencesKey("turn_window")
        val TURN_THRESHOLD     = floatPreferencesKey("turn_threshold")
        val TURN_SENSITIVITY   = floatPreferencesKey("turn_sensitivity")
        val SHOW_ORIGIN_INSTRUCTIONS = booleanPreferencesKey("show_origin_instructions")
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

    /** Whether the motion label badge is shown while PDR tracking. Defaults to true. */
    val showMotionLabel: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SHOW_MOTION_LABEL] ?: true
    }
    suspend fun saveShowMotionLabel(v: Boolean) = context.dataStore.edit { it[PreferencesKeys.SHOW_MOTION_LABEL] = v }

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

    /** How many buffered peaks to replay when ML confirms activity. Null = default 4. */
    val compensationSteps: Flow<Int?> = context.dataStore.data.map { it[PreferencesKeys.COMPENSATION_STEPS] }
    suspend fun saveCompensationSteps(v: Int) = context.dataStore.edit { it[PreferencesKeys.COMPENSATION_STEPS] = v }

    // ── ML model & stair detection ───────────────────────────────────────

    /** ML model key: "v6" (96-window) or "v6_64" (64-window). Null = default "v6". */
    val mlModel: Flow<String?> = context.dataStore.data.map { it[PreferencesKeys.ML_MODEL] }
    suspend fun saveMlModel(v: String) = context.dataStore.edit { it[PreferencesKeys.ML_MODEL] = v }

    /** Consecutive stair labels needed to enter a stairwell. Null = default 2. */
    val stairEntryThreshold: Flow<Int?> = context.dataStore.data.map { it[PreferencesKeys.STAIR_ENTRY_THRESHOLD] }
    suspend fun saveStairEntryThreshold(v: Int) = context.dataStore.edit { it[PreferencesKeys.STAIR_ENTRY_THRESHOLD] = v }

    /** Minimum confidence to accept upstairs/downstairs labels. Null = default 0.45. */
    val stairMinConfidence: Flow<Float?> = context.dataStore.data.map { it[PreferencesKeys.STAIR_MIN_CONFIDENCE] }
    suspend fun saveStairMinConfidence(v: Float) = context.dataStore.edit { it[PreferencesKeys.STAIR_MIN_CONFIDENCE] = v }

    /** Max distance (campus units) from a stairwell edge to trigger proximity detection. Null = default 150. */
    val stairProximityRadius: Flow<Float?> = context.dataStore.data.map { it[PreferencesKeys.STAIR_PROXIMITY_RADIUS] }
    suspend fun saveStairProximityRadius(v: Float) = context.dataStore.edit { it[PreferencesKeys.STAIR_PROXIMITY_RADIUS] = v }

    /** How many steps back from arrival to find the first compensation step. Null = default 3. */
    val stairLookback: Flow<Int?> = context.dataStore.data.map { it[PreferencesKeys.STAIR_LOOKBACK] }
    suspend fun saveStairLookback(v: Int) = context.dataStore.edit { it[PreferencesKeys.STAIR_LOOKBACK] = v }

    /** How many buffered steps to replay on the new floor. Null = default 3. */
    val stairReplayCount: Flow<Int?> = context.dataStore.data.map { it[PreferencesKeys.STAIR_REPLAY_COUNT] }
    suspend fun saveStairReplayCount(v: Int) = context.dataStore.edit { it[PreferencesKeys.STAIR_REPLAY_COUNT] = v }

    /** Steps back from current stair position used to estimate destination-floor exit point. Null = default 3. */
    val stairArrivalLookback: Flow<Int?> = context.dataStore.data.map { it[PreferencesKeys.STAIR_ARRIVAL_LOOKBACK] }
    suspend fun saveStairArrivalLookback(v: Int) = context.dataStore.edit { it[PreferencesKeys.STAIR_ARRIVAL_LOOKBACK] = v }

    /** Minimum climb progress before arrival/cancel checks are allowed. Null = default 0.30. */
    val stairMinProgressForArrival: Flow<Float?> = context.dataStore.data.map { it[PreferencesKeys.STAIR_MIN_PROGRESS_FOR_ARRIVAL] }
    suspend fun saveStairMinProgressForArrival(v: Float) = context.dataStore.edit { it[PreferencesKeys.STAIR_MIN_PROGRESS_FOR_ARRIVAL] = v }

    /** Walking labels required to confirm arrival on the destination floor. Null = default 2. */
    val stairWalkingArrivalCount: Flow<Int?> = context.dataStore.data.map { it[PreferencesKeys.STAIR_WALKING_ARRIVAL_COUNT] }
    suspend fun saveStairWalkingArrivalCount(v: Int) = context.dataStore.edit { it[PreferencesKeys.STAIR_WALKING_ARRIVAL_COUNT] = v }

    /** Opposite-direction stair labels required to cancel as a U-turn. Null = default 3. */
    val stairOppositeCancelCount: Flow<Int?> = context.dataStore.data.map { it[PreferencesKeys.STAIR_OPPOSITE_CANCEL_COUNT] }
    suspend fun saveStairOppositeCancelCount(v: Int) = context.dataStore.edit { it[PreferencesKeys.STAIR_OPPOSITE_CANCEL_COUNT] = v }

    /** Minimum stair steps before cancellation logic is allowed. Null = default 3. */
    val stairMinStepsBeforeCancel: Flow<Int?> = context.dataStore.data.map { it[PreferencesKeys.STAIR_MIN_STEPS_BEFORE_CANCEL] }
    suspend fun saveStairMinStepsBeforeCancel(v: Int) = context.dataStore.edit { it[PreferencesKeys.STAIR_MIN_STEPS_BEFORE_CANCEL] = v }

    /** Heading window around opposite stair axis used for arrival turn detection (degrees). Null = default 45. */
    val stairArrivalHeadingDeg: Flow<Float?> = context.dataStore.data.map { it[PreferencesKeys.STAIR_ARRIVAL_HEADING_DEG] }
    suspend fun saveStairArrivalHeadingDeg(v: Float) = context.dataStore.edit { it[PreferencesKeys.STAIR_ARRIVAL_HEADING_DEG] = v }

    /** Heading difference from stair axis required for return/cancel detection (degrees). Null = default 120. */
    val stairReturnHeadingDeg: Flow<Float?> = context.dataStore.data.map { it[PreferencesKeys.STAIR_RETURN_HEADING_DEG] }
    suspend fun saveStairReturnHeadingDeg(v: Float) = context.dataStore.edit { it[PreferencesKeys.STAIR_RETURN_HEADING_DEG] = v }

    /** Consecutive idle labels that count as stair pause before walk-based arrival. Null = default 5. */
    val stairIdleThreshold: Flow<Int?> = context.dataStore.data.map { it[PreferencesKeys.STAIR_IDLE_THRESHOLD] }
    suspend fun saveStairIdleThreshold(v: Int) = context.dataStore.edit { it[PreferencesKeys.STAIR_IDLE_THRESHOLD] = v }

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

    // ── Origin dialog ─────────────────────────────────────────────────────

    /** Whether to show instructions expanded in the origin selection dialog. Defaults to true for first-time users. */
    val showOriginInstructions: Flow<Boolean> = context.dataStore.data.map { it[PreferencesKeys.SHOW_ORIGIN_INSTRUCTIONS] ?: true }
    suspend fun saveShowOriginInstructions(v: Boolean) = context.dataStore.edit { it[PreferencesKeys.SHOW_ORIGIN_INSTRUCTIONS] = v }

}
