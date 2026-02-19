package `in`.project.enroute.feature.pdr.sensor

import android.content.Context
import `in`.project.enroute.feature.pdr.data.model.MotionMeta
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Thin wrapper around the TFLite model for motion classification.
 * Handles model loading, input normalization, and inference.
 *
 * Kept intentionally minimal — all buffering and threading lives in MotionRepository.
 */
class MotionClassifier(
    context: Context,
    modelFileName: String = "model/model_v5.tflite",
    metaFileName: String = "model/model_meta_v5.json"
) {

    val meta: MotionMeta = MotionMeta.fromAssets(context, metaFileName)

    private val interpreter: Interpreter

    init {
        val modelBuffer = loadModelFile(context, modelFileName)
        interpreter = Interpreter(modelBuffer)
    }

    private fun loadModelFile(context: Context, fileName: String): ByteBuffer {
        val afd = context.assets.openFd(fileName)
        FileInputStream(afd.fileDescriptor).use { fis ->
            return fis.channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
        }
    }

    /**
     * Runs inference on a window of sensor data.
     * @param window 2D array [windowSize][num Features] (acc_x, acc_y, acc_z, acc_mag).
     * @return FloatArray of probabilities for each class.
     */
    fun predict(window: Array<FloatArray>): FloatArray {
        val numFeatures = 4
        val inputBuffer = ByteBuffer.allocateDirect(1 * meta.windowSize * numFeatures * 4)
            .order(ByteOrder.nativeOrder())

        for (i in 0 until meta.windowSize) {
            for (j in 0 until numFeatures) {
                val raw = if (i < window.size && j < window[i].size) window[i][j] else 0f
                inputBuffer.putFloat((raw - meta.mean[j]) / meta.std[j])
            }
        }

        val output = Array(1) { FloatArray(meta.classNames.size) }
        interpreter.run(inputBuffer, output)
        return output[0]
    }

    fun close() {
        interpreter.close()
    }
}
