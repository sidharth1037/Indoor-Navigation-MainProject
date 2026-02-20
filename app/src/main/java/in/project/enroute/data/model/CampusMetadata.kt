package `in`.project.enroute.data.model

/**
 * Metadata for an entire campus.
 * Contains campus-level configuration that applies to all buildings.
 * This data is loaded once when the campus is opened and persists
 * until the user manually closes or switches campuses.
 *
 * In a backend scenario, this would be fetched from a campus endpoint
 * (e.g., GET /api/campuses/{campusId}/metadata).
 */
data class CampusMetadata(
    /**
     * Display name of the campus (e.g., "SJCET Palai")
     */
    val name: String = "",

    /**
     * Human-readable location string (e.g., "Palai, Kerala")
     */
    val location: String = "",

    /**
     * Geographic latitude of the campus center
     */
    val latitude: Double = 0.0,

    /**
     * Geographic longitude of the campus center
     */
    val longitude: Double = 0.0,

    /**
     * Clockwise rotation in degrees indicating where true north is
     * relative to the default (un-rotated) campus map orientation.
     *
     * When the canvas is at 0° rotation, the compass needle should
     * point at this angle (clockwise from screen-top).
     * As the user rotates the canvas, the compass rotates by the same amount.
     */
    val north: Float = 0f
)
