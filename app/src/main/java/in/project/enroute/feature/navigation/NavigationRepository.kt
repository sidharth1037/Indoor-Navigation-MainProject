package `in`.project.enroute.feature.navigation

import androidx.compose.ui.geometry.Offset
import `in`.project.enroute.data.model.Wall
import kotlin.math.sqrt
import android.util.Log
import java.util.PriorityQueue

/**
 * Pathfinding using A* with distance transform-based cost heuristic.
 * Paths naturally stay away from walls by treating wall proximity as movement cost.
 */
class NavigationRepository(walls: List<Wall>) {
    companion object {
        private const val TAG = "NavigationRepository"
    }

    private val gridSize = 20f
    // Grid dimensions calculated from floor plan image (4188 × 4329 pixels at 20px/cell)
    private val maxGridX = 210  // 4188 / 20 = 209.4 ≈ 210
    private val maxGridY = 217  // 4329 / 20 = 216.45 ≈ 217
    
    // Distance transform: minimum distance to any wall for each cell
    private val distanceGrid = Array(maxGridX) { FloatArray(maxGridY) }

    init {
        computeDistanceTransform(walls)
    }

    /**
     * Compute distance from each grid cell to nearest wall.
     */
    private fun computeDistanceTransform(walls: List<Wall>) {
        // Initialize all distances to infinity
        for (x in 0 until maxGridX) {
            for (y in 0 until maxGridY) {
                distanceGrid[x][y] = Float.MAX_VALUE
            }
        }

        // For each wall, update distances to cells near it
        walls.forEach { wall ->
            val x1 = wall.x1 / gridSize
            val y1 = wall.y1 / gridSize
            val x2 = wall.x2 / gridSize
            val y2 = wall.y2 / gridSize

            // For each grid cell, compute distance to this wall segment
            for (x in 0 until maxGridX) {
                for (y in 0 until maxGridY) {
                    val dist = distanceToSegment(
                        x.toFloat(), y.toFloat(),
                        x1, y1, x2, y2
                    )
                    distanceGrid[x][y] = minOf(distanceGrid[x][y], dist)
                }
            }
        }
    }

    /**
     * Distance from point (px, py) to line segment from (x1, y1) to (x2, y2).
     */
    private fun distanceToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        
        if (lenSq == 0f) return sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1))
        
        val t = ((px - x1) * dx + (py - y1) * dy) / lenSq
        val clampedT = t.coerceIn(0f, 1f)
        val closestX = x1 + clampedT * dx
        val closestY = y1 + clampedT * dy
        
        return sqrt((px - closestX) * (px - closestX) + (py - closestY) * (py - closestY))
    }

    /**
     * Movement cost based on distance to walls: closer to walls = higher cost.
     * Allows destinations near walls but heavily penalizes wall proximity.
     */
    private fun getMovementCost(gridX: Int, gridY: Int): Float {
        if (gridX !in 0 until maxGridX || gridY !in 0 until maxGridY) return Float.MAX_VALUE
        
        val dist = distanceGrid[gridX][gridY]
        return when {
            dist < 0.3f -> Float.MAX_VALUE  // Block cells clipping through walls
            dist < 1f -> 500f      // Very close to walls - high penalty but reachable
            dist < 3f -> 150f      // Close to walls - elevated cost
            dist < 5f -> 50f       // Moderately close - moderate cost
            else -> maxOf(1f, 10f / dist)  // Open space - minimal cost
        }
    }

    fun findPath(start: Offset, goal: Offset): List<Offset> {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "=== PATHFINDING START ===")
        Log.d(TAG, "Start: (${start.x}, ${start.y}), Goal: (${goal.x}, ${goal.y})")
        
        val startGrid = Pair((start.x / gridSize).toInt(), (start.y / gridSize).toInt())
        val goalGrid = Pair((goal.x / gridSize).toInt(), (goal.y / gridSize).toInt())
        
        Log.d(TAG, "Start grid: $startGrid, Goal grid: $goalGrid")
        Log.d(TAG, "Grid dimensions: $maxGridX x $maxGridY, Grid size: $gridSize")
        
        // Debug: Check distances at start and goal
        val startDist = if (isValid(startGrid)) distanceGrid[startGrid.first][startGrid.second] else Float.MAX_VALUE
        val goalDist = if (isValid(goalGrid)) distanceGrid[goalGrid.first][goalGrid.second] else Float.MAX_VALUE
        Log.d(TAG, "Start distance to wall: $startDist, Goal distance to wall: $goalDist")
        Log.d(TAG, "Start cost: ${getMovementCost(startGrid.first, startGrid.second)}, Goal cost: ${getMovementCost(goalGrid.first, goalGrid.second)}")

        if (!isValid(startGrid) || !isValid(goalGrid)) {
            Log.e(TAG, "Invalid start or goal grid positions!")
            return emptyList()
        }

        val closedSet = mutableSetOf<Pair<Int, Int>>()
        val cameFrom = mutableMapOf<Pair<Int, Int>, Pair<Int, Int>>()
        val gScore = mutableMapOf<Pair<Int, Int>, Float>()
        val fScoreMap = mutableMapOf<Pair<Int, Int>, Float>()

        // Priority queue: sorts by fScore automatically (no manual sorting needed)
        val openSet = PriorityQueue<Pair<Int, Int>> { a, b ->
            (fScoreMap[a] ?: Float.MAX_VALUE).compareTo(fScoreMap[b] ?: Float.MAX_VALUE)
        }

        openSet.add(startGrid)
        gScore[startGrid] = 0f
        fScoreMap[startGrid] = heuristic(startGrid, goalGrid)

        var iterations = 0
        val maxIterations = 50000 // Increased back to find longer paths
        
        while (openSet.isNotEmpty() && iterations < maxIterations) {
            iterations++
            
            if (iterations % 5000 == 0) {
                Log.d(TAG, "Iteration $iterations: openSet size=${openSet.size}, closedSet size=${closedSet.size}")
            }
            
            val current = openSet.poll()  // Get lowest fScore in O(log n)

            if (current == goalGrid) {
                val elapsedTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "✓ PATH FOUND in $iterations iterations, ${elapsedTime}ms")
                return reconstructPath(cameFrom, current!!).also {
                    Log.d(TAG, "Path length: ${it.size} waypoints")
                }
            }

            closedSet.add(current!!)

            for (neighbor in getNeighbors(current)) {
                if (neighbor in closedSet) continue

                var cost = getMovementCost(neighbor.first, neighbor.second)
                if (cost == Float.MAX_VALUE) continue
                if (current.first != neighbor.first && current.second != neighbor.second) {
                    cost *= 1.414f
                }
                val tentativeG = (gScore[current] ?: Float.MAX_VALUE) + cost

                if (tentativeG < (gScore[neighbor] ?: Float.MAX_VALUE)) {
                    cameFrom[neighbor] = current
                    gScore[neighbor] = tentativeG
                    fScoreMap[neighbor] = tentativeG + heuristic(neighbor, goalGrid)

                    if (neighbor !in openSet) openSet.add(neighbor)
                }
            }
        }

        val elapsedTime = System.currentTimeMillis() - startTime
        Log.e(TAG, "✗ NO PATH FOUND after $iterations iterations, ${elapsedTime}ms")
        Log.e(TAG, "Final state - openSet: ${openSet.size}, closedSet: ${closedSet.size}")
        return emptyList()
    }

    private fun getNeighbors(pos: Pair<Int, Int>): List<Pair<Int, Int>> {
        val (x, y) = pos
        return listOf(
            Pair(x + 1, y), Pair(x - 1, y), Pair(x, y + 1), Pair(x, y - 1),
            Pair(x + 1, y + 1), Pair(x - 1, y - 1), Pair(x + 1, y - 1), Pair(x - 1, y + 1)
        ).filter { isValid(it) }
    }

    private fun isValid(pos: Pair<Int, Int>): Boolean =
        pos.first in 0 until maxGridX && pos.second in 0 until maxGridY

    private fun heuristic(a: Pair<Int, Int>, b: Pair<Int, Int>): Float =
        (kotlin.math.abs(a.first - b.first) + kotlin.math.abs(a.second - b.second)).toFloat() * 0.5f

    private fun reconstructPath(
        cameFrom: Map<Pair<Int, Int>, Pair<Int, Int>>,
        current: Pair<Int, Int>
    ): List<Offset> {
        // First, reconstruct raw grid path
        val gridPath = mutableListOf<Pair<Int, Int>>()
        var curr = current
        while (curr in cameFrom) {
            gridPath.add(curr)
            curr = cameFrom[curr]!!
        }
        gridPath.add(curr)
        gridPath.reverse()
        
        // Convert to world coordinates and smooth
        val waypoints = gridPath.map { Offset((it.first + 0.5f) * gridSize, (it.second + 0.5f) * gridSize) }
        return smoothPath(waypoints)
    }
    
    /**
     * Minimal smoothing: only remove obvious zigzags, keep wall-following waypoints.
     */
    private fun smoothPath(waypoints: List<Offset>): List<Offset> {
        if (waypoints.size <= 2) return waypoints
        
        val smoothed = mutableListOf<Offset>(waypoints[0])
        
        for (i in 1 until waypoints.size - 1) {
            val p1 = waypoints[i - 1]
            val p2 = waypoints[i]
            val p3 = waypoints[i + 1]
            
            // Perpendicular distance from p2 to line p1-p3
            val dist = perpDistanceToLine(p2, p1, p3)
            
            // Only remove points that are nearly on the line (< 5 pixels deviation)
            if (dist >= 5f) {
                smoothed.add(p2)
            }
        }
        
        smoothed.add(waypoints.last())
        return smoothed
    }
    
    /**
     * Calculate perpendicular distance from point to line defined by two points.
     */
    private fun perpDistanceToLine(point: Offset, lineStart: Offset, lineEnd: Offset): Float {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        val lenSq = dx * dx + dy * dy
        
        if (lenSq == 0f) return sqrt((point.x - lineStart.x) * (point.x - lineStart.x) + (point.y - lineStart.y) * (point.y - lineStart.y))
        
        val t = ((point.x - lineStart.x) * dx + (point.y - lineStart.y) * dy) / lenSq
        val clampedT = t.coerceIn(0f, 1f)
        val closestX = lineStart.x + clampedT * dx
        val closestY = lineStart.y + clampedT * dy
        
        return sqrt((point.x - closestX) * (point.x - closestX) + (point.y - closestY) * (point.y - closestY))
    }
}
