package `in`.project.enroute.feature.navigation.data

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Encodes/decodes a 2D `Array<FloatArray>` distance grid to/from a compact
 * Base64+GZip string suitable for Firestore storage.
 */
object GridSerializer {

    /**
     * Encodes a 2D distanceGrid into a Base64 string.
     * Flow: float[][] → flat bytes (little-endian) → GZip → Base64
     */
    fun encode(grid: Array<FloatArray>, maxGridX: Int, maxGridY: Int): String {
        val flatSize = maxGridX * maxGridY
        val buffer = ByteBuffer.allocate(flatSize * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (x in 0 until maxGridX) {
            for (y in 0 until maxGridY) {
                buffer.putFloat(grid[x][y])
            }
        }

        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { gzip ->
            gzip.write(buffer.array())
        }

        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Decodes a Base64 string back into a 2D distanceGrid.
     * Flow: Base64 → GZip decompress → flat bytes → float[][]
     */
    fun decode(encoded: String, maxGridX: Int, maxGridY: Int): Array<FloatArray> {
        val compressed = Base64.decode(encoded, Base64.NO_WRAP)

        val decompressed = GZIPInputStream(ByteArrayInputStream(compressed)).use {
            it.readBytes()
        }

        val buffer = ByteBuffer.wrap(decompressed).order(ByteOrder.LITTLE_ENDIAN)
        val grid = Array(maxGridX) { FloatArray(maxGridY) }
        for (x in 0 until maxGridX) {
            for (y in 0 until maxGridY) {
                grid[x][y] = buffer.getFloat()
            }
        }
        return grid
    }
}
