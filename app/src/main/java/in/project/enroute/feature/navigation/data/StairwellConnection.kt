package `in`.project.enroute.feature.navigation.data

import androidx.compose.ui.geometry.Offset

/**
 * A stairwell cross-floor connection point for navigation pathfinding.
 *
 * Built from stairwell polygon edge midpoints, transformed to campus-wide
 * coordinates. Replaces the old approach of using stair entrance points
 * for multi-floor path stitching.
 *
 * The [bottomMidpoint] is the midpoint of the "bottom" edge (entry when
 * going **up**), and [topMidpoint] is the midpoint of the "top" edge
 * (entry when going **down**).
 *
 * Because all floors share the same coordinate space, the exit midpoint
 * from one floor is a valid start position on the adjacent floor — no
 * extra "pairing" step is needed.
 *
 * @param polygonId       Stairwell polygon ID from the floor data.
 * @param buildingId      Building this stairwell belongs to.
 * @param floorId         Floor ID where this geometry was loaded from.
 * @param bottomMidpoint  Midpoint of the "bottom" edge in campus coords.
 * @param topMidpoint     Midpoint of the "top" edge in campus coords.
 * @param floorsConnected Numeric floors this stairwell links (e.g. [1.0, 1.5]).
 * @param bottomFloorId   Resolved floor ID for the lower floor.
 * @param topFloorId      Resolved floor ID for the upper floor.
 * @param bottomFloorNumber Numeric floor value for the lower end.
 * @param topFloorNumber    Numeric floor value for the upper end.
 */
data class StairwellConnection(
    val polygonId: Int,
    val buildingId: String,
    val floorId: String,
    val bottomMidpoint: Offset,
    val topMidpoint: Offset,
    val floorsConnected: List<Float>,
    val bottomFloorId: String,
    val topFloorId: String,
    val bottomFloorNumber: Float,
    val topFloorNumber: Float
)
