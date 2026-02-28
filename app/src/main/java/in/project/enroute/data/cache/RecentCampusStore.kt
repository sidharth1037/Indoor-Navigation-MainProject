package `in`.project.enroute.data.cache

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.content.edit

/**
 * Persists the last [MAX_RECENT] recently-viewed campuses in SharedPreferences.
 * Each entry is a pair of (campusId, campusName).
 *
 * Thread-safe: reads/writes go through [SharedPreferences] which is already
 * synchronised internally.
 */
class RecentCampusStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "recent_campuses"
        private const val KEY_LIST = "list"
        private const val MAX_RECENT = 3
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns up to [MAX_RECENT] recently-viewed campuses (newest first). */
    fun getRecent(): List<Pair<String, String>> {
        val json = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                obj.getString("id") to obj.getString("name")
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Adds (or promotes) a campus to the top of the recent list.
     * Duplicates are removed before inserting.
     */
    fun add(campusId: String, campusName: String) {
        val current = getRecent().toMutableList()
        // Remove existing entry for this campus
        current.removeAll { it.first == campusId }
        // Insert at front
        current.add(0, campusId to campusName)
        // Keep only MAX_RECENT
        save(current.take(MAX_RECENT))
    }

    /** Removes a single campus from the recent list. */
    fun remove(campusId: String) {
        val current = getRecent().toMutableList()
        current.removeAll { it.first == campusId }
        save(current)
    }

    private fun save(list: List<Pair<String, String>>) {
        val array = JSONArray()
        for ((id, name) in list) {
            val obj = JSONObject()
            obj.put("id", id)
            obj.put("name", name)
            array.put(obj)
        }
        prefs.edit { putString(KEY_LIST, array.toString()) }
    }
}
