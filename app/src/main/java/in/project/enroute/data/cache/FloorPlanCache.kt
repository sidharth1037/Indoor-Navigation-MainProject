package `in`.project.enroute.data.cache

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import `in`.project.enroute.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.Type

/**
 * Cached snapshot of an entire campus: metadata + all buildings + all floor data.
 * Serialised as a single JSON file per campus.
 */
data class CachedCampusData(
    val campusMetadata: CampusMetadata,
    val buildings: List<CachedBuilding>,
    val cachedAt: Long = System.currentTimeMillis()
)

/**
 * Cached building: its [Building] descriptor plus every [FloorPlanData] that
 * was loaded from the backend.
 */
data class CachedBuilding(
    val building: Building,
    val floorDataList: List<FloorPlanData>
)

/**
 * File-based cache for floor plan data.
 *
 * Stores one JSON file per campus under `floor_plan_cache/{campusId}.json`
 * in the app's internal storage.  The file contains the complete campus
 * data (metadata, buildings, floors) so that the app can work fully offline
 * after the first successful backend fetch.
 *
 * Notes:
 * - Uses a custom Gson instance that preserves `@Transient` fields
 *   (e.g. `Room.floorId`, `Entrance.floorId`) in the cache JSON.
 * - Registers a type adapter for `Pair<Float, Float>` used in [Stairwell.points].
 */
class FloorPlanCache(context: Context) {

    private val cacheDir = File(context.cacheDir, "floor_plan_cache")

    /**
     * Gson configured to include transient fields and handle Pair<Float,Float>.
     */
    private val gson = GsonBuilder()
        // Default Gson excludes transient AND static fields.  We only want to
        // exclude static, so transient fields (floorId, buildingId on Room/Entrance)
        // survive the round-trip.
        .excludeFieldsWithModifiers(java.lang.reflect.Modifier.STATIC)
        .registerTypeAdapter(
            object : TypeToken<Pair<Float, Float>>() {}.type,
            PairFloatAdapter()
        )
        .create()

    // ── Public API ───────────────────────────────────────────────

    /** Returns `true` if a cache file exists for the given campus. */
    fun hasCachedCampus(campusId: String): Boolean =
        File(cacheDir, "$campusId.json").exists()

    /** Saves the full campus snapshot to disk. */
    suspend fun saveCampusData(campusId: String, data: CachedCampusData) =
        withContext(Dispatchers.IO) {
            cacheDir.mkdirs()
            val file = File(cacheDir, "$campusId.json")
            file.writeText(gson.toJson(data))
        }

    /** Loads a cached campus snapshot, or `null` if none exists or it is corrupt. */
    suspend fun loadCampusData(campusId: String): CachedCampusData? =
        withContext(Dispatchers.IO) {
            val file = File(cacheDir, "$campusId.json")
            if (!file.exists()) return@withContext null
            try {
                gson.fromJson(file.readText(), CachedCampusData::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                // Corrupt cache → delete and return null so the next load fetches fresh data.
                file.delete()
                null
            }
        }

    /** Deletes the cache for a single campus. */
    suspend fun clearCache(campusId: String) = withContext(Dispatchers.IO) {
        File(cacheDir, "$campusId.json").delete()
    }

    /** Deletes all cached campus data. */
    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        cacheDir.deleteRecursively()
    }

    // ── Pair<Float, Float> adapter ───────────────────────────────

    /**
     * Gson adapter for `kotlin.Pair<Float, Float>`.
     * Kotlin's Pair has no no-arg constructor, so Gson cannot deserialise it
     * without help.
     */
    private class PairFloatAdapter :
        JsonSerializer<Pair<Float, Float>>,
        JsonDeserializer<Pair<Float, Float>> {

        override fun serialize(
            src: Pair<Float, Float>,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            val obj = JsonObject()
            obj.addProperty("first", src.first)
            obj.addProperty("second", src.second)
            return obj
        }

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): Pair<Float, Float> {
            val obj = json.asJsonObject
            return Pair(obj.get("first").asFloat, obj.get("second").asFloat)
        }
    }
}
