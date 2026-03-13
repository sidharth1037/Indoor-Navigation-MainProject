package `in`.project.enroute.feature.navigation.data

/**
 * Serialized distance-transform grid for a single floor.
 * Stored as a Firestore document under precalculated_nav/grid_{floorId}.
 *
 * The gridData field contains the entire distanceGrid array encoded as:
 *   float[][] → flat float[] → ByteArray (via ByteBuffer) → GZip → Base64 String
 */
data class PrecalculatedFloorGrid(
    val floorId: String,
    val originX: Float,
    val originY: Float,
    val maxGridX: Int,
    val maxGridY: Int,
    val gridSize: Float,
    val gridData: String  // Base64-encoded GZip-compressed float array
)

/**
 * Metadata about the precalculated navigation data for a campus.
 * Stored as a Firestore document under precalculated_nav/metadata.
 */
data class PrecalculatedNavMetadata(
    val version: Int = 1,
    val computedAt: Long = 0L,
    val gridSize: Float = 15f,
    val floorIds: List<String> = emptyList()
)
