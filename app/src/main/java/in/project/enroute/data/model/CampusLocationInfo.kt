package `in`.project.enroute.data.model

/**
 * Lightweight campus representation with GPS coordinates,
 * used for nearby campus detection.
 */
data class CampusLocationInfo(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)
