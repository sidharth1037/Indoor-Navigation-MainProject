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
 *  2. On the current floor, find all [StairwellConnection]s that connect
 *     toward the destination direction.
 *  3. For every candidate connection, compute the full multi-floor path
 *     (current floor → stair midpoint → next floor → … → destination).
 *  4. Pick the route with the shortest total distance.
 *
 * Stairwell connections replace the old entrance-based stair pairing.
 * Since all floors share the same campus-wide coordinate system, the
 * exit midpoint of a stairwell on one floor is a valid start position
 * on the adjacent floor — no cross-floor pairing is needed.
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
     * @param stairConnections All stairwell connections across the campus.
     * @param wallsByFloor     Campus-wide walls grouped by floorId.
     * @param boundaryByFloor  Campus-wide boundary polygons grouped by floorId.
     * @param repoByFloor      Lazily-built [NavigationRepository] per floor.
     * @return A [MultiFloorPath] with per-floor segments, or empty if no route exists.
     */
    fun findMultiFloorPath(
        start: Offset,
        startFloorId: String,
        goalEntrance: CampusEntrance,
        allEntrances: List<CampusEntrance>,
        stairConnections: List<StairwellConnection>,
        wallsByFloor: Map<String, List<Wall>>,
        boundaryByFloor: Map<String, List<CampusBoundaryPolygon>>,
        repoByFloor: MutableMap<String, NavigationRepository>
    ): MultiFloorPath {
        val goalFloorId = goalEntrance.floorId

        // ── Same-floor fast path ─────────────────────────
        if (startFloorId == goalFloorId) {
            val repo = getOrBuildRepo(
                startFloorId, wallsByFloor, boundaryByFloor, repoByFloor,
                allEntrances, stairConnections
            ) ?: return MultiFloorPath.EMPTY
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
            startFloorNum, goalFloorNum, goingUp, allEntrances, stairConnections
        )
        if (floorSequence.isEmpty()) {
            Log.e(TAG, "Could not build floor sequence from $startFloorId to $goalFloorId")
            return MultiFloorPath.EMPTY
        }
        Log.d(TAG, "Floor sequence: ${floorSequence.joinToString(" → ")}")

        // Find all stairwell connections that leave the starting floor
        val firstFloorConnections = findCandidateConnections(
            floorId = startFloorId,
            goingUp = goingUp,
            stairConnections = stairConnections
        )
        if (firstFloorConnections.isEmpty()) {
            Log.e(TAG, "No stairwell connections on $startFloorId going ${if (goingUp) "up" else "down"}")
            return MultiFloorPath.EMPTY
        }
        Log.d(TAG, "Found ${firstFloorConnections.size} candidate connection(s) on $startFloorId")

        // Try each candidate connection and compute full route
        var bestPath: MultiFloorPath = MultiFloorPath.EMPTY
        var bestDistance = Float.MAX_VALUE

        for (connection in firstFloorConnections) {
            val candidate = computeCandidateRoute(
                start = start,
                floorSequence = floorSequence,
                firstConnection = connection,
                goalEntrance = goalEntrance,
                goingUp = goingUp,
                stairConnections = stairConnections,
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
     * Uses both entrance data and stair connections to discover known floors.
     */
    private fun buildFloorSequence(
        startFloorNum: Float,
        goalFloorNum: Float,
        goingUp: Boolean,
        allEntrances: List<CampusEntrance>,
        stairConnections: List<StairwellConnection>
    ): List<String> {
        // Collect known floor IDs from entrances
        val entranceFloors = allEntrances
            .map { it.floorId }
            .distinct()
            .map { it to extractFloorNumber(it) }

        // Also collect floor IDs from stair connections
        val connectionFloors = stairConnections.flatMap { conn ->
            listOf(
                conn.bottomFloorId to conn.bottomFloorNumber,
                conn.topFloorId to conn.topFloorNumber
            )
        }

        // Merge and deduplicate
        val knownFloors = (entranceFloors + connectionFloors)
            .distinctBy { it.first }
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
     * Finds stairwell connections accessible from [floorId] in the given direction.
     *
     * **Going up**: Connections whose [StairwellConnection.bottomFloorId]
     * matches [floorId] (enter from the bottom, exit at the top).
     *
     * **Going down**: Connections whose [StairwellConnection.topFloorId]
     * matches [floorId] (enter from the top, exit at the bottom).
     */
    private fun findCandidateConnections(
        floorId: String,
        goingUp: Boolean,
        stairConnections: List<StairwellConnection>
    ): List<StairwellConnection> {
        return if (goingUp) {
            stairConnections.filter { it.bottomFloorId == floorId }
        } else {
            stairConnections.filter { it.topFloorId == floorId }
        }
    }

    /**
     * Returns the path target and next-floor start position for a [connection].
     *
     * The **far edge** of the stairwell is used as the path target so the A*
     * path physically traverses the stairwell polygon (bottom→top or top→bottom)
     * rather than stopping at the near edge and leaving the stairwell undrawn.
     *
     * Going up:   path targets topMidpoint;    next floor starts at topMidpoint.
     * Going down: path targets bottomMidpoint; next floor starts at bottomMidpoint.
     *
     * Entry and exit are the same point because the far-edge midpoint is valid
     * in the shared campus-wide coordinate space on both floors.
     */
    private fun connectionEndpoints(
        connection: StairwellConnection,
        goingUp: Boolean
    ): Pair<Offset, Offset> {
        return if (goingUp) {
            connection.topMidpoint to connection.topMidpoint
        } else {
            connection.bottomMidpoint to connection.bottomMidpoint
        }
    }

    /**
     * Computes the full route through the floor sequence starting from
     * [firstConnection].
     *
     * Since all floors share the same campus-wide coordinate system,
     * the exit midpoint of a stairwell on one floor is a valid start
     * position on the adjacent floor — no cross-floor pairing is needed.
     */
    private fun computeCandidateRoute(
        start: Offset,
        floorSequence: List<String>,
        firstConnection: StairwellConnection,
        goalEntrance: CampusEntrance,
        goingUp: Boolean,
        stairConnections: List<StairwellConnection>,
        allEntrances: List<CampusEntrance>,
        wallsByFloor: Map<String, List<Wall>>,
        boundaryByFloor: Map<String, List<CampusBoundaryPolygon>>,
        repoByFloor: MutableMap<String, NavigationRepository>
    ): MultiFloorPath {
        val segments = mutableListOf<FloorPathSegment>()
        val visitedFloors = mutableSetOf<String>()

        var currentPosition = start
        var currentConnection = firstConnection

        for (i in floorSequence.indices) {
            val floorId = floorSequence[i]
            val floorNum = extractFloorNumber(floorId)
            val isLastFloor = (i == floorSequence.size - 1)
            visitedFloors.add(floorId)

            val repo = getOrBuildRepo(
                floorId, wallsByFloor, boundaryByFloor, repoByFloor,
                allEntrances, stairConnections
            ) ?: return MultiFloorPath.EMPTY

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
                // Intermediate floor: pathfind to the stairwell entry midpoint
                val (entryMidpoint, exitMidpoint) = connectionEndpoints(currentConnection, goingUp)
                val path = repo.findPath(currentPosition, entryMidpoint)
                if (path.isEmpty()) return MultiFloorPath.EMPTY

                segments.add(FloorPathSegment(
                    floorId = floorId,
                    floorNumber = floorNum,
                    buildingId = currentConnection.buildingId,
                    points = path
                ))

                // Exit midpoint is the start position on the next floor
                // (campus-wide coordinates are shared, so it's already valid)
                currentPosition = exitMidpoint

                // Find the next connection on the next floor (unless next is last)
                if (i + 1 < floorSequence.size - 1) {
                    val nextFloorId = floorSequence[i + 1]
                    val nextConnections = findCandidateConnections(
                        floorId = nextFloorId,
                        goingUp = goingUp,
                        stairConnections = stairConnections
                    )
                    // Pick the connection closest to where we arrive
                    val nextConnection = nextConnections.minByOrNull { conn ->
                        val (entry, _) = connectionEndpoints(conn, goingUp)
                        distance(currentPosition, entry)
                    }
                    if (nextConnection == null) {
                        Log.e(TAG, "No onward stairwell connection found on $nextFloorId")
                        return MultiFloorPath.EMPTY
                    }
                    currentConnection = nextConnection
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

    /**
     * Gets or lazily builds a [NavigationRepository] for the given floor.
     * The grid bounds include both entrance positions and stairwell midpoints.
     */
    private fun getOrBuildRepo(
        floorId: String,
        wallsByFloor: Map<String, List<Wall>>,
        boundaryByFloor: Map<String, List<CampusBoundaryPolygon>>,
        repoByFloor: MutableMap<String, NavigationRepository>,
        allEntrances: List<CampusEntrance>,
        stairConnections: List<StairwellConnection>
    ): NavigationRepository? {
        return repoByFloor[floorId] ?: run {
            val walls = wallsByFloor[floorId]
            if (walls.isNullOrEmpty()) {
                Log.e(TAG, "No walls for floor $floorId — cannot pathfind")
                return null
            }
            // Collect entrance positions + stair midpoints so the grid covers them
            val entrancePoints = allEntrances
                .filter { it.floorId == floorId }
                .map { it.campusOffset }
            val stairPoints = stairConnections.flatMap { conn ->
                buildList {
                    if (conn.bottomFloorId == floorId) add(conn.bottomMidpoint)
                    if (conn.topFloorId == floorId) add(conn.topMidpoint)
                }
            }
            val allBoundPoints = entrancePoints + stairPoints
            // Skip boundary blocking on ground floors (floor_1) so that
            // cross-building outdoor navigation remains possible.
            val floorNum = extractFloorNumber(floorId)
            val boundaries = if (floorNum <= 1f) {
                emptyList()
            } else {
                boundaryByFloor[floorId] ?: emptyList()
            }
            Log.d(TAG, "Building distance transform for floor $floorId " +
                    "(${walls.size} walls, ${allBoundPoints.size} bound points, " +
                    "${boundaries.size} boundary polygons, ground=${floorNum <= 1f})")
            NavigationRepository(walls, allBoundPoints, boundaries).also { repoByFloor[floorId] = it }
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
