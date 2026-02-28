package `in`.project.enroute.feature.navigation.data

import android.util.Log
import androidx.compose.ui.geometry.Offset
import `in`.project.enroute.data.model.Wall
import `in`.project.enroute.feature.navigation.NavigationRepository
import kotlin.math.sqrt

/**
 * Computes multi-floor A* paths by stitching per-floor segments together.
 *
 * Algorithm overview:
 *  1. Determine whether the user needs to go **up** or **down** to reach
 *     the destination floor.
 *  2. On the current floor, find all stair entrances that connect toward
 *     the destination direction.
 *  3. For every candidate stairwell, compute the full multi-floor path
 *     (current floor → stair → next floor → … → destination entrance).
 *  4. Pick the route with the shortest total distance.
 *
 * This class is stateless — it receives all required data via method
 * parameters and can be called from any coroutine context.
 */
class MultiFloorPathfinder {

    companion object {
        private const val TAG = "MultiFloorPathfinder"
    }

    /**
     * Finds the shortest multi-floor path from [start] on [startFloorId] to
     * [goalEntrance] (which lives on goalEntrance.floorId).
     *
     * @param start            User's campus-wide position.
     * @param startFloorId     Floor the user is currently on (e.g. "floor_1").
     * @param goalEntrance     The destination room's entrance (campus-wide).
     * @param allEntrances     Every entrance across all buildings/floors (campus-wide).
     * @param wallsByFloor     Campus-wide walls grouped by floorId.
     * @param repoByFloor      Lazily-built [NavigationRepository] per floor.
     * @return A [MultiFloorPath] with per-floor segments, or empty if no route exists.
     */
    fun findMultiFloorPath(
        start: Offset,
        startFloorId: String,
        goalEntrance: CampusEntrance,
        allEntrances: List<CampusEntrance>,
        wallsByFloor: Map<String, List<Wall>>,
        boundaryByFloor: Map<String, List<CampusBoundaryPolygon>>,
        repoByFloor: MutableMap<String, NavigationRepository>
    ): MultiFloorPath {
        val goalFloorId = goalEntrance.floorId

        // ── Same-floor fast path ─────────────────────────
        if (startFloorId == goalFloorId) {
            val repo = getOrBuildRepo(startFloorId, wallsByFloor, boundaryByFloor, repoByFloor, allEntrances) ?: return MultiFloorPath.EMPTY
            val path = repo.findPath(start, goalEntrance.campusOffset)
            if (path.isEmpty()) return MultiFloorPath.EMPTY

            val segment = FloorPathSegment(
                floorId = startFloorId,
                floorNumber = extractFloorNumber(startFloorId),
                buildingId = goalEntrance.buildingId,
                points = path
            )
            return MultiFloorPath(
                segments = listOf(segment),
                totalFloors = 1,
                isMultiFloor = false
            )
        }

        // ── Multi-floor path ─────────────────────────────────────
        val startFloorNum = extractFloorNumber(startFloorId)
        val goalFloorNum = extractFloorNumber(goalFloorId)
        val goingUp = goalFloorNum > startFloorNum

        Log.d(TAG, "Multi-floor: $startFloorId ($startFloorNum) → $goalFloorId ($goalFloorNum), " +
                "direction=${if (goingUp) "UP" else "DOWN"}")

        // Build the sequence of floors to traverse
        val floorSequence = buildFloorSequence(
            startFloorNum, goalFloorNum, goingUp, allEntrances
        )
        if (floorSequence.isEmpty()) {
            Log.e(TAG, "Could not build floor sequence from $startFloorId to $goalFloorId")
            return MultiFloorPath.EMPTY
        }
        Log.d(TAG, "Floor sequence: ${floorSequence.joinToString(" → ")}")

        // For the first floor, find all candidate stair entrances
        val firstFloorStairs = findCandidateStairs(
            floorId = startFloorId,
            goingUp = goingUp,
            allEntrances = allEntrances
        )
        if (firstFloorStairs.isEmpty()) {
            Log.e(TAG, "No stair entrances found on $startFloorId going ${if (goingUp) "up" else "down"}")
            return MultiFloorPath.EMPTY
        }
        Log.d(TAG, "Found ${firstFloorStairs.size} candidate stair(s) on $startFloorId")

        // Try each candidate stairwell on the first floor and compute full route
        var bestPath: MultiFloorPath = MultiFloorPath.EMPTY
        var bestDistance = Float.MAX_VALUE

        for (stairEntrance in firstFloorStairs) {
            val candidate = computeCandidateRoute(
                start = start,
                floorSequence = floorSequence,
                firstStair = stairEntrance,
                goalEntrance = goalEntrance,
                goingUp = goingUp,
                allEntrances = allEntrances,
                wallsByFloor = wallsByFloor,
                boundaryByFloor = boundaryByFloor,
                repoByFloor = repoByFloor
            )
            if (candidate.isEmpty) continue

            val distance = totalPathDistance(candidate)
            if (distance < bestDistance) {
                bestDistance = distance
                bestPath = candidate
            }
        }

        if (bestPath.isEmpty) {
            Log.e(TAG, "No valid multi-floor route found")
        } else {
            Log.d(TAG, "Best route: ${bestPath.segments.size} segments, " +
                    "${bestPath.totalFloors} floors, distance=${"%.1f".format(bestDistance)}")
        }
        return bestPath
    }

    // ── Internal helpers ─────────────────────────────────────────

    /**
     * Builds the ordered list of floor IDs the user must traverse.
     * e.g. ["floor_1", "floor_1.5", "floor_2"] when going from floor_1 to floor_2.
     */
    private fun buildFloorSequence(
        startFloorNum: Float,
        goalFloorNum: Float,
        goingUp: Boolean,
        allEntrances: List<CampusEntrance>
    ): List<String> {
        // Collect all known floor IDs and their numbers
        val knownFloors = allEntrances
            .map { it.floorId }
            .distinct()
            .map { it to extractFloorNumber(it) }
            .sortedBy { it.second }

        // Filter to floors in the range [start, goal] (inclusive)
        val inRange = if (goingUp) {
            knownFloors.filter { it.second in startFloorNum..goalFloorNum }
        } else {
            knownFloors.filter { it.second in goalFloorNum..startFloorNum }.reversed()
        }

        return inRange.map { it.first }
    }

    /**
     * Finds stair entrances on [floorId] suitable for the given direction.
     *
     * **Going up**: Find entrances with `stairs != null` whose `floor` value
     * is higher than the current floor (i.e. the stairwell leads up).
     *
     * **Going down**: Find entrances with `stairs == "top"` whose `floor` value
     * equals the current floor number (i.e. this is where a lower stairwell
     * arrives, so we can descend from here).
     */
    private fun findCandidateStairs(
        floorId: String,
        goingUp: Boolean,
        allEntrances: List<CampusEntrance>
    ): List<CampusEntrance> {
        val floorNum = extractFloorNumber(floorId)
        return allEntrances.filter { ce ->
            ce.floorId == floorId && ce.original.isStairs && run {
                val stairFloor = ce.original.floor ?: return@run false
                if (goingUp) {
                    // Going up: find stairs whose connected floor is above current
                    stairFloor > floorNum
                } else {
                    // Going down: find "top" stairs (arrival from below) with
                    // floor == current floor number, meaning we can descend from here
                    ce.original.isStairsTop && stairFloor == floorNum
                }
            }
        }
    }

    /**
     * Finds the matching stair entrance on the next floor.
     *
     * Stair pairs across floors share (nearly) the same campus-wide coordinates.
     * We match by finding the closest stair entrance on [nextFloorId] within a
     * distance threshold.
     */
    private fun findPairedStairOnFloor(
        stairEntrance: CampusEntrance,
        nextFloorId: String,
        allEntrances: List<CampusEntrance>,
        maxDistance: Float = 50f
    ): CampusEntrance? {
        return allEntrances
            .filter { it.floorId == nextFloorId && it.original.isStairs }
            .minByOrNull { distance(stairEntrance.campusOffset, it.campusOffset) }
            ?.takeIf { distance(stairEntrance.campusOffset, it.campusOffset) < maxDistance }
    }

    /**
     * Computes the full route through the floor sequence starting from [firstStair].
     */
    private fun computeCandidateRoute(
        start: Offset,
        floorSequence: List<String>,
        firstStair: CampusEntrance,
        goalEntrance: CampusEntrance,
        goingUp: Boolean,
        allEntrances: List<CampusEntrance>,
        wallsByFloor: Map<String, List<Wall>>,
        boundaryByFloor: Map<String, List<CampusBoundaryPolygon>>,
        repoByFloor: MutableMap<String, NavigationRepository>
    ): MultiFloorPath {
        val segments = mutableListOf<FloorPathSegment>()
        val visitedFloors = mutableSetOf<String>()

        var currentPosition = start
        var currentStairTarget = firstStair

        for (i in floorSequence.indices) {
            val floorId = floorSequence[i]
            val floorNum = extractFloorNumber(floorId)
            val isLastFloor = (i == floorSequence.size - 1)
            visitedFloors.add(floorId)

            val repo = getOrBuildRepo(floorId, wallsByFloor, boundaryByFloor, repoByFloor, allEntrances) ?: return MultiFloorPath.EMPTY

            if (isLastFloor) {
                // Last floor: pathfind to the destination entrance
                val path = repo.findPath(currentPosition, goalEntrance.campusOffset)
                if (path.isEmpty()) return MultiFloorPath.EMPTY

                segments.add(FloorPathSegment(
                    floorId = floorId,
                    floorNumber = floorNum,
                    buildingId = goalEntrance.buildingId,
                    points = path
                ))
            } else {
                // Intermediate floor: pathfind to the stair entrance
                val stairGoal = currentStairTarget.campusOffset
                val path = repo.findPath(currentPosition, stairGoal)
                if (path.isEmpty()) return MultiFloorPath.EMPTY

                segments.add(FloorPathSegment(
                    floorId = floorId,
                    floorNumber = floorNum,
                    buildingId = currentStairTarget.buildingId,
                    points = path
                ))

                // Find the paired stair entrance on the next floor
                val nextFloorId = floorSequence[i + 1]
                val pairedStair = findPairedStairOnFloor(
                    currentStairTarget, nextFloorId, allEntrances
                )
                if (pairedStair == null) {
                    Log.e(TAG, "No paired stair found on $nextFloorId for stair at " +
                            "(${currentStairTarget.campusX}, ${currentStairTarget.campusY})")
                    return MultiFloorPath.EMPTY
                }

                // Update position to the paired stair's location on the next floor
                currentPosition = pairedStair.campusOffset

                // Find the next stair to use on the next floor (unless next is last)
                if (i + 1 < floorSequence.size - 1) {
                    val nextFloorStairs = findCandidateStairs(
                        floorId = nextFloorId,
                        goingUp = goingUp,
                        allEntrances = allEntrances
                    )
                    // Pick the stair on the next floor closest to where we arrive
                    val nextStair = nextFloorStairs.minByOrNull {
                        distance(currentPosition, it.campusOffset)
                    }
                    if (nextStair == null) {
                        Log.e(TAG, "No onward stair found on $nextFloorId")
                        return MultiFloorPath.EMPTY
                    }
                    currentStairTarget = nextStair
                }
            }
        }

        return MultiFloorPath(
            segments = segments,
            totalFloors = visitedFloors.size,
            isMultiFloor = visitedFloors.size > 1
        )
    }

    // ── Utility ──────────────────────────────────────────────────

    private fun getOrBuildRepo(
        floorId: String,
        wallsByFloor: Map<String, List<Wall>>,
        boundaryByFloor: Map<String, List<CampusBoundaryPolygon>>,
        repoByFloor: MutableMap<String, NavigationRepository>,
        allEntrances: List<CampusEntrance>
    ): NavigationRepository? {
        return repoByFloor[floorId] ?: run {
            val walls = wallsByFloor[floorId]
            if (walls.isNullOrEmpty()) {
                Log.e(TAG, "No walls for floor $floorId — cannot pathfind")
                return null
            }
            // Collect entrance positions on this floor so the grid covers them
            val entrancePoints = allEntrances
                .filter { it.floorId == floorId }
                .map { it.campusOffset }
            // Skip boundary blocking on ground floors (floor_1) so that
            // cross-building outdoor navigation remains possible.
            val floorNum = extractFloorNumber(floorId)
            val boundaries = if (floorNum <= 1f) {
                emptyList()
            } else {
                boundaryByFloor[floorId] ?: emptyList()
            }
            Log.d(TAG, "Building distance transform for floor $floorId " +
                    "(${walls.size} walls, ${entrancePoints.size} entrance bounds, " +
                    "${boundaries.size} boundary polygons, ground=${floorNum <= 1f})")
            NavigationRepository(walls, entrancePoints, boundaries).also { repoByFloor[floorId] = it }
        }
    }

    private fun distance(a: Offset, b: Offset): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun totalPathDistance(path: MultiFloorPath): Float {
        return path.segments.sumOf { segment ->
            var d = 0.0
            for (i in 1 until segment.points.size) {
                d += distance(segment.points[i - 1], segment.points[i]).toDouble()
            }
            d
        }.toFloat()
    }

    private fun extractFloorNumber(floorId: String): Float =
        floorId.removePrefix("floor_").toFloatOrNull() ?: 1f
}
