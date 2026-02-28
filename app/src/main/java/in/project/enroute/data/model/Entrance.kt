package `in`.project.enroute.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents an entrance point in the floor plan.
 * Can be a regular room entrance or a stairwell entry/exit.
 *
 * For stair entrances the backend sends:
 *  - `stairs`: `"top"` or `"bottom"` (null / absent for regular entrances)
 *  - `floor`: the floor number this stair connects to
 *
 * **`"bottom"`** means this entrance is at the bottom of a stairwell — use it
 * to go **up** to `floor`.
 * **`"top"`** means this entrance is where a stairwell from a lower floor
 * arrives — use it to go **down**.
 *
 * A stair entrance whose `floor` equals the file's own floor number is the
 * *start* of a stairwell on that floor; one whose `floor` differs indicates the
 * *destination* floor the stairwell leads to.
 */
data class Entrance(
    val id: Int,
    val x: Float,
    val y: Float,
    val name: String? = null,
    @SerializedName("room_no")
    val roomNo: String? = null,
    /**
     * Stair position: `"top"`, `"bottom"`, or null for regular entrances.
     * Deserialized from either a JSON string or a boolean for backwards compatibility.
     */
    val stairs: String? = null,
    /**
     * The floor number this stair entrance connects to (e.g. 1.5).
     * Null for regular (non-stair) entrances.
     */
    val floor: Float? = null,
    val available: Boolean = true,
    /** Floor this entrance belongs to (e.g. "floor_1"). Set after loading, not from JSON. */
    @Transient
    val floorId: String? = null
) {
    /** True when this entrance is part of a stairwell. */
    val isStairs: Boolean get() = stairs != null

    /** True when this is a "top" stair entrance (arrival from lower floor). */
    val isStairsTop: Boolean get() = stairs == "top"

    /** True when this is a "bottom" stair entrance (departure to upper floor). */
    val isStairsBottom: Boolean get() = stairs == "bottom"
}
