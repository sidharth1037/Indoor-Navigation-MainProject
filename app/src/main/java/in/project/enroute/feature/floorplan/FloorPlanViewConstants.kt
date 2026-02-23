package `in`.project.enroute.feature.floorplan

/**
 * Central source of truth for all zoom-level thresholds, size constants,
 * and visibility rules used across the floor plan rendering system.
 *
 * Change a value here and it takes effect everywhere — no need to hunt
 * across multiple files.
 *
 *  ┌──────────────────────────────────────────────────────────┐
 *  │  MIN_ZOOM          0.15                                  │
 *  │  BUILDING_NAME_MIN 0.18  ─┐ building names visible      │
 *  │  BUILDING_NAME_MAX 0.48  ─┘                              │
 *  │  ROOM_LABELS_MIN   0.48  ─┐ room labels visible          │
 *  │  ROOM_LABELS_CONST 0.76  ─┘ (constant size above here)  │
 *  │  SLIDER_MIN        0.48     floor slider visible         │
 *  │  PIN_FULL_SIZE     1.00     pin keeps full screen size   │
 *  │  MAX_ZOOM          2.20                                  │
 *  └──────────────────────────────────────────────────────────┘
 */
object FloorPlanViewConstants {

    // ── Canvas zoom limits ────────────────────────────────────────
    /** Minimum canvas scale the user can pinch to. */
    const val MIN_ZOOM = 0.15f
    /** Maximum canvas scale the user can pinch to. */
    const val MAX_ZOOM = 2.2f

    // ── Room labels ───────────────────────────────────────────────
    /**
     * Room labels are hidden entirely below this zoom level.
     * Also the threshold at which the floor slider becomes visible.
     */
    const val ROOM_LABELS_MIN_ZOOM = 0.48f

    /**
     * Above this zoom, room label text is kept at a constant *screen* size
     * (i.e. it stops growing with further zoom-in).
     * Below this, text size is scaled inversely with zoom.
     */
    const val ROOM_LABELS_CONSTANT_SIZE_ZOOM = 0.76f

    /** Base canvas-space text size (px) for room labels. */
    const val ROOM_LABEL_TEXT_SIZE = 30f

    /** Starting max characters per line for room labels (before overlap reduction). */
    const val ROOM_LABEL_MAX_CHARS = 15

    // ── Building name labels ──────────────────────────────────────
    /**
     * Building names become visible above this zoom level.
     * Below this level (very zoomed out) even building names are hidden.
     */
    const val BUILDING_NAME_MIN_ZOOM = 0.18f

    /**
     * Building names are replaced by room labels above this zoom level.
     * Must equal [ROOM_LABELS_MIN_ZOOM] so the two regimes are contiguous.
     */
    const val BUILDING_NAME_MAX_ZOOM = ROOM_LABELS_MIN_ZOOM   // 0.48f

    /** Base canvas-space text size (px) for building name labels. */
    const val BUILDING_NAME_TEXT_SIZE = 44f

    // ── Floor slider ──────────────────────────────────────────────
    /**
     * The floor slider is shown only when zoom >= this level AND a dominant
     * building is visible.  Intentionally equal to [ROOM_LABELS_MIN_ZOOM].
     */
    const val SLIDER_MIN_ZOOM = ROOM_LABELS_MIN_ZOOM           // 0.48f

    /**
     * A building must cover at least this fraction of the screen area to
     * be considered the "dominant" building for the slider.
     */
    const val SLIDER_MIN_SCREEN_COVERAGE = 0.10f

    // ── Search pin ────────────────────────────────────────────────
    /** Base size of the pin drawable in canvas-space pixels. */
    const val PIN_SIZE_DP = 140f

    /**
     * At zoom >= this level the pin keeps its full [PIN_SIZE_DP].
     * Below it the pin scales down proportionally with zoom.
     */
    const val PIN_MIN_ZOOM_FOR_FULL_SIZE = 1f

    /**
     * At zoom <= this level the pin's upward offset grows linearly with zoom.
     * Above it the offset is constant.
     */
    const val PIN_MAX_ZOOM_FOR_OFFSET = 1f

    /** Multiplied by canvasScale (up to [PIN_MAX_ZOOM_FOR_OFFSET]) for pin offset. */
    const val PIN_VERTICAL_OFFSET_BASE = 70f
}
