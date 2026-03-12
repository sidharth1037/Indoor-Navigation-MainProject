package `in`.project.enroute.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.google.android.gms.location.LocationServices
import `in`.project.enroute.data.model.CampusLocationInfo
import `in`.project.enroute.feature.campussearch.CampusItem
import kotlinx.coroutines.tasks.await

/**
 * Handles GPS location retrieval and nearby campus filtering.
 * Uses FusedLocationProviderClient for efficient location access.
 */
class NearbyManager(context: Context) {

    private val appContext = context.applicationContext
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)

    /** Maximum distance in meters to consider a campus "nearby". */
    companion object {
        const val NEARBY_RADIUS_METERS = 5000f // 5 km
    }

    /** Returns true if GPS (or network location) is currently enabled on the device. */
    fun isLocationEnabled(): Boolean {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /** Returns the device's last known location, or null if unavailable. */
    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): Location? {
        return try {
            fusedClient.lastLocation.await()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Filters and sorts [campuses] by distance from [userLocation].
     * Only returns campuses within [NEARBY_RADIUS_METERS].
     */
    fun filterNearby(
        campuses: List<CampusLocationInfo>,
        userLocation: Location
    ): List<CampusItem> {
        return campuses
            .map { campus ->
                val distance = FloatArray(1)
                Location.distanceBetween(
                    userLocation.latitude, userLocation.longitude,
                    campus.latitude, campus.longitude,
                    distance
                )
                campus to distance[0]
            }
            .filter { it.second <= NEARBY_RADIUS_METERS }
            .sortedBy { it.second }
            .map { CampusItem(it.first.id, it.first.name) }
    }
}
