package `in`.project.enroute.feature.pdr.data.model

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.InputStreamReader

/**
 * Metadata loaded from model_meta_v5.json.
 * Provides normalization params, class names, and sliding window config.
 */
data class MotionMeta(
    val mean: List<Float>,
    val std: List<Float>,
    @SerializedName("classes")
    val classNames: List<String>,
    @SerializedName("window_size")
    val windowSize: Int,
    @SerializedName("step_size")
    val stepSize: Int
) {
    companion object {
        fun fromAssets(context: Context, fileName: String): MotionMeta {
            context.assets.open(fileName).use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    return Gson().fromJson(reader, MotionMeta::class.java)
                }
            }
        }
    }
}

/**
 * Immutable classification result emitted by MotionRepository.
 */
data class MotionEvent(
    val classificationName: String,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis()
)
