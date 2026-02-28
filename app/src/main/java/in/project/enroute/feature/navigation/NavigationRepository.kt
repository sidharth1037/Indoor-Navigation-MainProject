package `in`.project.enroute.feature.navigation

import androidx.compose.ui.geometry.Offset
import `in`.project.enroute.data.model.Wall
import `in`.project.enroute.feature.navigation.data.CampusBoundaryPolygon
import kotlin.math.abs
import kotlin.math.sqrt
import android.util.Log
import java.util.PriorityQueue

/**
 * Pathfinding using A* with distance-transform wall avoidance.
 *
 * Design priorities (in order):
 *  1. **Never cross walls** — cells on/touching a wall are hard-blocked; diagonal
 *     moves that would cut through a wall corner are disallowed.
 *  2. **Always pick the shortest passable path** — wall-proximity penalties are
 *     minimal so the algorithm prefers the shortest route through narrow gaps
 *     (doorways, corridors) rather than a long detour through open space.
 *  3. **Smooth output** — after finding the grid path, a line-of-sight pass
 *     ("string pulling") removes unnecessary waypoints without re-introducing
 *     wall crossings.
 *
 * Supports campus-wide coordinates including negative values via a bounding-box
 * origin offset ([originX], [originY]).
 */
class NavigationRepository(
    walls: List<Wall>,
    /** Optional extra points (campus-wide) that must be inside the grid.
     *  Used by multi-floor pathfinding to guarantee stair / goal entrances
     *  fall within the distance-transform grid. */
    additionalBoundaryPoints: List<Offset> = emptyList(),
    /** Optional boundary polygons (campus-wide) defining the floor outline.
     *  Grid cells outside ALL polygons are hard-blocked so A* stays indoors. */
    boundaryPolygons: List<CampusBoundaryPolygon> = emptyList()
) {

    companion object {
        private const val TAG = "NavigationRepository"

        /**
         * Cells closer than this many grid-units to any wall are impassable.
         * 0.55 means the cell centre must be > 0.55 × gridSize world-units
         * away from every wall segment.  This is thick enough to block
         * diagonal moves that would clip through a wall while still leaving
         * standard doorways (≥ 2 cells wide) passable.
         */
        private const val WALL_BLOCK_THRESHOLD = 0.55f

        /**
         * Cells closer than this to a wall are considered "buffer zone".
         * A* penalises them, and line-of-sight smoothing refuses to pass
         * through them so that corner rounding is preserved after smoothing.
         */
        private const val WALL_BUFFER_THRESHOLD = 1.5f
    }

    private val gridSize = 15f   // finer grid → better narrow-gap resolution

    // Grid origin offset (allows negative world coordinates)
    private val originX: Float
    private val originY: Float

    // Grid dimensions in cells
    private val maxGridX: Int
    private val maxGridY: Int

    // Distance transform: minimum distance (in grid-units) to any wall for each cell
    private val distanceGrid: Array<FloatArray>

    init {
        // Compute bounding box from wall extents (with padding)
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE
        for (wall in walls) {
            minX = minOf(minX, wall.x1, wall.x2)
            minY = minOf(minY, wall.y1, wall.y2)
            maxX = maxOf(maxX, wall.x1, wall.x2)
            maxY = maxOf(maxY, wall.y1, wall.y2)
        }
        // Also include any additional boundary points (stair / goal entrances)
        for (pt in additionalBoundaryPoints) {
            minX = minOf(minX, pt.x)
            minY = minOf(minY, pt.y)
            maxX = maxOf(maxX, pt.x)
            maxY = maxOf(maxY, pt.y)
        }
        // Floor the origin to grid boundary, with padding
        originX = (minX / gridSize - 2).toInt() * gridSize
        originY = (minY / gridSize - 2).toInt() * gridSize
        maxGridX = ((maxX - originX) / gridSize + 4).toInt()
        maxGridY = ((maxY - originY) / gridSize + 4).toInt()
        distanceGrid = Array(maxGridX) { FloatArray(maxGridY) }

        Log.d(TAG, "Grid: origin($originX, $originY), size ${maxGridX}x$maxGridY, " +
                "cells=${maxGridX * maxGridY}, gridSize=$gridSize")
        computeDistanceTransform(walls)

        // Block cells outside the floor boundary so A* stays inside the building.
        // Uses point-in-polygon on the actual boundary data from the backend.
        if (boundaryPolygons.isNotEmpty()) {
            markExteriorCells(boundaryPolygons)
        }
    }

    // ── coordinate helpers ──────────────────────────────────────────

    /** World → grid cell index. */
    private fun worldToGridX(wx: Float): Int = ((wx - originX) / gridSize).toInt()
    private fun worldToGridY(wy: Float): Int = ((wy - originY) / gridSize).toInt()

    /** Grid cell centre → world coordinate. */
    private fun gridToWorldX(gx: Int): Float = (gx + 0.5f) * gridSize + originX
    private fun gridToWorldY(gy: Int): Float = (gy + 0.5f) * gridSize + originY

    // ── distance transform ──────────────────────────────────────────

    /**
     * Populates [distanceGrid]: for every cell, the minimum distance (in
     * grid-units) to the nearest wall segment.
     */
    private fun computeDistanceTransform(walls: List<Wall>) {
        for (x in 0 until maxGridX) for (y in 0 until maxGridY) {
            distanceGrid[x][y] = Float.MAX_VALUE
        }

        walls.forEach { wall ->
            val x1 = (wall.x1 - originX) / gridSize
            val y1 = (wall.y1 - originY) / gridSize
            val x2 = (wall.x2 - originX) / gridSize
            val y2 = (wall.y2 - originY) / gridSize
            for (x in 0 until maxGridX) for (y in 0 until maxGridY) {
                val d = distanceToSegment(x.toFloat(), y.toFloat(), x1, y1, x2, y2)
                if (d < distanceGrid[x][y]) distanceGrid[x][y] = d
            }
        }
    }

    /**
     * Blocks every grid cell whose centre falls outside ALL [polygons].
     *
     * Uses the ray-casting point-in-polygon test from [CampusBoundaryPolygon].
     * A cell only needs to be inside at least one polygon to be considered
     * interior (supports multi-section buildings).
     */
    private fun markExteriorCells(polygons: List<CampusBoundaryPolygon>) {
        var blocked = 0
        for (x in 0 until maxGridX) for (y in 0 until maxGridY) {
            // Skip cells already blocked by walls
            if (distanceGrid[x][y] < WALL_BLOCK_THRESHOLD) continue

            val wx = gridToWorldX(x)
            val wy = gridToWorldY(y)
            val point = Offset(wx, wy)
            val inside = polygons.any { it.contains(point) }
            if (!inside) {
                distanceGrid[x][y] = 0f  // hard-block exterior
                blocked++
            }
        }
        Log.d(TAG, "markExteriorCells: blocked $blocked exterior cells " +
                "using ${polygons.size} boundary polygon(s)")
    }

    /** Point-to-segment distance (grid-unit space). */
    private fun distanceToSegment(
        px: Float, py: Float,
        x1: Float, y1: Float, x2: Float, y2: Float
    ): Float {
        val dx = x2 - x1; val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0f) return sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1))
        val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0f, 1f)
        val cx = x1 + t * dx; val cy = y1 + t * dy
        return sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
    }

    // ── cell queries ────────────────────────────────────────────────

    /** True if the cell index is inside the grid bounds. */
    private fun inBounds(x: Int, y: Int): Boolean =
        x in 0 until maxGridX && y in 0 until maxGridY

    /** True if the cell is on or clipping through a wall. */
    private fun isBlocked(x: Int, y: Int): Boolean =
        !inBounds(x, y) || distanceGrid[x][y] < WALL_BLOCK_THRESHOLD

    /**
     * Movement cost for stepping onto cell ([gx], [gy]).
     *
     * Blocked cells return MAX_VALUE. Near-wall cells are penalised
     * enough to produce aesthetic corner-rounding without blocking
     * narrow doorways (≥ 4 cells ≈ 60 world-units).
     *
     * A typical doorway is ~4-5 cells wide. The middle 2-3 cells sit
     * at dist ≈ 1.5–2.5, costing 2–3× base, which adds ~6 extra cost
     * for a doorway vs. hundreds of cells for a detour → doorways win.
     */
    private fun getMovementCost(gx: Int, gy: Int): Float {
        if (!inBounds(gx, gy)) return Float.MAX_VALUE
        val dist = distanceGrid[gx][gy]
        return when {
            dist < WALL_BLOCK_THRESHOLD -> Float.MAX_VALUE   // hard-blocked
            dist < WALL_BUFFER_THRESHOLD -> 5.0f             // buffer zone — passable but costly (rounds corners)
            dist < 3.0f -> 2.0f                              // near wall — moderate nudge
            else        -> 1.0f                              // open space
        }
    }

    // ── A* pathfinding ──────────────────────────────────────────────

    fun findPath(start: Offset, goal: Offset): List<Offset> {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "=== PATHFINDING START ===")
        Log.d(TAG, "Start: (${start.x}, ${start.y}), Goal: (${goal.x}, ${goal.y})")

        // Snap start/goal to closest passable cell (rooms & origins are near walls)
        val startGrid = findNearestPassable(worldToGridX(start.x), worldToGridY(start.y))
        val goalGrid  = findNearestPassable(worldToGridX(goal.x),  worldToGridY(goal.y))

        if (startGrid == null || goalGrid == null) {
            Log.e(TAG, "Cannot find passable cell near start/goal!")
            return emptyList()
        }

        Log.d(TAG, "Start grid: $startGrid, Goal grid: $goalGrid")

        val closedSet  = HashSet<Long>(4096)
        val cameFrom   = HashMap<Long, Long>(4096)
        val gScore     = HashMap<Long, Float>(4096)
        val fScoreMap  = HashMap<Long, Float>(4096)

        val openSet = PriorityQueue<Long>(256) { a, b ->
            (fScoreMap[a] ?: Float.MAX_VALUE).compareTo(fScoreMap[b] ?: Float.MAX_VALUE)
        }

        val startKey = packKey(startGrid.first, startGrid.second)
        val goalKey  = packKey(goalGrid.first, goalGrid.second)

        openSet.add(startKey)
        gScore[startKey] = 0f
        fScoreMap[startKey] = heuristic(startGrid.first, startGrid.second,
            goalGrid.first, goalGrid.second)

        var iterations = 0
        val maxIterations = 80_000

        while (openSet.isNotEmpty() && iterations < maxIterations) {
            iterations++
            val currentKey = openSet.poll()!!

            if (currentKey == goalKey) {
                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "✓ PATH FOUND in $iterations iters, ${elapsed}ms")
                val gridPath = reconstructGridPath(cameFrom, currentKey)
                val worldPath = gridPath.map { Offset(gridToWorldX(unpackX(it)), gridToWorldY(unpackY(it))) }
                return smoothPath(worldPath).also { Log.d(TAG, "Smoothed: ${it.size} waypoints") }
            }

            if (!closedSet.add(currentKey)) continue  // already expanded

            val cx = unpackX(currentKey); val cy = unpackY(currentKey)
            val curG = gScore[currentKey] ?: continue

            // Cardinal neighbours
            for (i in CARD_DX.indices) {
                val nx = cx + CARD_DX[i]; val ny = cy + CARD_DY[i]
                expandNeighbour(nx, ny, curG, 1f, currentKey, goalGrid, closedSet, cameFrom, gScore, fScoreMap, openSet)
            }
            // Diagonal neighbours — blocked if either adjacent cardinal is blocked
            for (i in DIAG_DX.indices) {
                val nx = cx + DIAG_DX[i]; val ny = cy + DIAG_DY[i]
                if (isBlocked(cx + DIAG_DX[i], cy) || isBlocked(cx, cy + DIAG_DY[i])) continue
                expandNeighbour(nx, ny, curG, 1.414f, currentKey, goalGrid, closedSet, cameFrom, gScore, fScoreMap, openSet)
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.e(TAG, "✗ NO PATH after $iterations iters, ${elapsed}ms")
        return emptyList()
    }

    /** Try to relax the edge to ([nx], [ny]). */
    private fun expandNeighbour(
        nx: Int, ny: Int,
        curG: Float, stepScale: Float,
        currentKey: Long,
        goalGrid: Pair<Int, Int>,
        closedSet: HashSet<Long>,
        cameFrom: HashMap<Long, Long>,
        gScore: HashMap<Long, Float>,
        fScoreMap: HashMap<Long, Float>,
        openSet: PriorityQueue<Long>
    ) {
        val cost = getMovementCost(nx, ny)
        if (cost == Float.MAX_VALUE) return
        val nk = packKey(nx, ny)
        if (nk in closedSet) return
        val tentG = curG + cost * stepScale
        if (tentG < (gScore[nk] ?: Float.MAX_VALUE)) {
            cameFrom[nk] = currentKey
            gScore[nk] = tentG
            fScoreMap[nk] = tentG + heuristic(nx, ny, goalGrid.first, goalGrid.second)
            openSet.add(nk)
        }
    }

    // ── key packing (avoids Pair allocation on hot path) ────────────

    private fun packKey(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
    private fun unpackX(key: Long): Int = (key shr 32).toInt()
    private fun unpackY(key: Long): Int = key.toInt()

    // ── direction tables ────────────────────────────────────────────

    private val CARD_DX = intArrayOf(1, -1,  0, 0)
    private val CARD_DY = intArrayOf(0,  0,  1, -1)
    private val DIAG_DX = intArrayOf(1, -1,  1, -1)
    private val DIAG_DY = intArrayOf(1, -1, -1,  1)

    // ── heuristic ───────────────────────────────────────────────────

    /**
     * Octile distance — the tightest admissible heuristic for 8-directional
     * movement with uniform base cost 1.  Greatly reduces expanded nodes
     * compared to the previous Manhattan × 0.5.
     */
    private fun heuristic(ax: Int, ay: Int, bx: Int, by: Int): Float {
        val dx = abs(ax - bx); val dy = abs(ay - by)
        return maxOf(dx, dy) + 0.414f * minOf(dx, dy)
    }

    // ── start / goal snapping ───────────────────────────────────────

    /**
     * If ([gx],[gy]) is passable, return it directly. Otherwise search
     * expanding rings (up to radius 12) for the nearest passable cell.
     */
    private fun findNearestPassable(gx: Int, gy: Int): Pair<Int, Int>? {
        // Clamp to grid bounds first — if the world coordinate was outside the
        // grid, searching outward from an out-of-bounds cell will never hit it.
        val cx = gx.coerceIn(0, maxGridX - 1)
        val cy = gy.coerceIn(0, maxGridY - 1)

        if (inBounds(cx, cy) && !isBlocked(cx, cy)) return cx to cy
        for (r in 1..20) {
            for (dx in -r..r) for (dy in -r..r) {
                if (abs(dx) != r && abs(dy) != r) continue  // ring only
                val nx = cx + dx; val ny = cy + dy
                if (inBounds(nx, ny) && !isBlocked(nx, ny)) return nx to ny
            }
        }
        Log.e(TAG, "findNearestPassable FAILED: original=($gx,$gy) clamped=($cx,$cy) " +
                "grid=${maxGridX}x$maxGridY origin=($originX,$originY)")
        return null
    }

    // ── path reconstruction ─────────────────────────────────────────

    private fun reconstructGridPath(cameFrom: Map<Long, Long>, end: Long): List<Long> {
        val path = mutableListOf<Long>()
        var cur = end
        while (cur in cameFrom) { path.add(cur); cur = cameFrom[cur]!! }
        path.add(cur)
        path.reverse()
        return path
    }

    // ── line-of-sight smoothing ("string pulling") ──────────────────

    /**
     * Removes unnecessary waypoints while guaranteeing the simplified line
     * segments never cross a blocked cell.
     *
     * Algorithm: from the current anchor, try to "see" the furthest waypoint
     * directly (scan from end backwards). The first visible one becomes the
     * next anchor. Repeat until the goal is reached.
     */
    private fun smoothPath(waypoints: List<Offset>): List<Offset> {
        if (waypoints.size <= 2) return waypoints
        val result = mutableListOf(waypoints[0])
        var i = 0
        while (i < waypoints.size - 1) {
            var furthest = i + 1
            for (j in waypoints.size - 1 downTo i + 2) {
                if (hasLineOfSight(waypoints[i], waypoints[j])) { furthest = j; break }
            }
            result.add(waypoints[furthest])
            i = furthest
        }
        return result
    }

    /** True if a cell is in the buffer zone or blocked — used by smoothing. */
    private fun isBuffered(x: Int, y: Int): Boolean =
        !inBounds(x, y) || distanceGrid[x][y] < WALL_BUFFER_THRESHOLD

    /**
     * Walks along the segment at sub-grid steps and checks that every
     * sampled cell clears the buffer zone.  This preserves the aesthetic
     * corner-rounding that A* produced — smoothing won't shortcut through
     * near-wall cells.
     */
    private fun hasLineOfSight(from: Offset, to: Offset): Boolean {
        val dx = to.x - from.x; val dy = to.y - from.y
        val dist = sqrt(dx * dx + dy * dy)
        val steps = (dist / (gridSize * 0.4f)).toInt().coerceAtLeast(1)
        for (s in 0..steps) {
            val t = s.toFloat() / steps
            val gx = worldToGridX(from.x + dx * t)
            val gy = worldToGridY(from.y + dy * t)
            if (isBuffered(gx, gy)) return false
        }
        return true
    }
}
