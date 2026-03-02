package `in`.project.enroute.feature.pdr.correction

/**
 * Centralized configuration for PDR error correction.
 * All distance values are in campus-wide coordinate units (1 unit = 2 cm).
 *
 * Unit conversion helpers:
 *   meters  → units:  meters × 50
 *   cm      → units:  cm × 0.5
 *   units   → meters: units / 50
 */
data class CorrectionConfig(

    // ── Buffer ──────────────────────────────────────────────────────────────
    /** Number of raw steps to hold before committing the oldest one.
     *  Higher → better turn detection, more visual lag. */
    val bufferSize: Int = 3,

    // ── Entrance snapping ──────────────────────────────────────────────────
    /** Search radius for nearby entrances when a turn is detected.
     *  1.5 m = 75 units. */
    val entranceSnapRadius: Float = 75f,

    /** Maximum angle (rad) between pre-turn heading and the direction toward
     *  an entrance for it to be considered a valid snap candidate. ~45°. */
    val entranceDirectionTolerance: Float = 0.785f,

    // ── Turn detection ─────────────────────────────────────────────────────
    /** Minimum heading change (rad) across the buffer window to count as a
     *  turn. ~30°. */
    val turnDetectionThreshold: Float = 0.523f,

    // ── Wall constraint ────────────────────────────────────────────────────
    /** Gap (units) to maintain between the user and a wall after clamping.
     *  1 unit = 2 cm. */
    val wallEpsilon: Float = 1.0f,

    /** Fraction of the wall-slide heading delta applied as a correction to
     *  future steps (0 = none, 1 = full). Tune empirically. */
    val headingCorrectionFactor: Float = 0f,

    /** Radius (units) around the current position in which walls are fetched
     *  for constraint checks. 3 m = 150 units. */
    val wallSearchRadius: Float = 150f,

    /** Maximum iterations of slide-then-recheck per single step to avoid
     *  bouncing between converging walls. */
    val maxWallIterations: Int = 3,

    // ── Retroactive smoothing ──────────────────────────────────────────────
    /** Number of already-committed steps over which an entrance snap
     *  correction is distributed to prevent visual jumps. */
    val retroactiveSmoothSteps: Int = 3,

    // ── Stride calibration ─────────────────────────────────────────────────
    /** Maximum stride adjustment factor per snap event.
     *  0.08 → stride can be scaled by [0.92, 1.08] from a single snap. */
    val maxStrideAdjustment: Float = 0.08f
)
