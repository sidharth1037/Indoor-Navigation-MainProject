package `in`.project.enroute.feature.settings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    
    private object PreferencesKeys {
        val HEIGHT = floatPreferencesKey("user_height")
    }
    
    /**
     * Flow of the stored height value in centimeters.
     * Null if the user has not set a height yet.
     */
    val height: Flow<Float?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.HEIGHT]
    }
    
    /**
     * Saves the user's height in centimeters.
     */
    suspend fun saveHeight(heightCm: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HEIGHT] = heightCm
        }
    }
}
