package `in`.project.enroute.feature.pdr.data.repository

import android.content.Context
import `in`.project.enroute.feature.pdr.data.model.MotionEvent
import `in`.project.enroute.feature.pdr.sensor.MotionClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Buffers accelerometer samples, runs TFLite inference on a background thread
 * via a sliding window, and exposes the latest classification through a StateFlow.
 *
 * Lifecycle is tied to PDR tracking — call [start] / [stop] accordingly.
 */
class MotionRepository(private val appContext: Context) {

    private var classifier: MotionClassifier? = null
    private val sensorBuffer = ArrayDeque<FloatArray>()

    // Dedicated scope with SupervisorJob so a single failed inference doesn't cancel everything
    private var scope: CoroutineScope? = null

    private val _motionEvent = MutableStateFlow<MotionEvent?>(null)
    val motionEvent: StateFlow<MotionEvent?> = _motionEvent.asStateFlow()

    // Dedup: avoid emitting identical / near-identical events
    private var lastLabel: String? = null
    private var lastConfidence = 0f
    private val inferenceInFlight = AtomicBoolean(false)

    /**
     * Lazily initializes the classifier and resets buffers.
     * Safe to call multiple times — will no-op if already running.
     *
     * @param modelKey  Preference key such as "v6" or "v6_64".
     */
    fun start(modelKey: String = "v6") {
        if (classifier != null) return
        val (modelFile, metaFile) = modelFilesFor(modelKey)
        classifier = MotionClassifier(appContext, modelFile, metaFile)
        sensorBuffer.clear()
        lastLabel = null
        lastConfidence = 0f
        inferenceInFlight.set(false)
        _motionEvent.value = null
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    /** Maps a short key to the asset paths. */
    private fun modelFilesFor(key: String): Pair<String, String> = when (key) {
        "v6_64" -> "model/model_v6_64.tflite" to "model/model_meta_v6_64.json"
        else    -> "model/model_v6.2.tflite"     to "model/model_meta_v6.2.json"
    }

    /**
     * Releases the TFLite interpreter and cancels pending work.
     */
    fun stop() {
        scope?.cancel()
        scope = null
        classifier?.close()
        classifier = null
        sensorBuffer.clear()
        inferenceInFlight.set(false)
        _motionEvent.value = null
    }

    /**
     * Called from the accelerometer listener (StepDetector) on every sample.
     * Buffers until the window is full, then launches inference on [Dispatchers.Default].
     */
    fun onAccelerometerSample(x: Float, y: Float, z: Float) {
        val cls = classifier ?: return // Not started yet
        val mag = sqrt(x * x + y * y + z * z)
        sensorBuffer.add(floatArrayOf(x, y, z, mag))

        if (sensorBuffer.size >= cls.meta.windowSize) {
            val window = sensorBuffer.toTypedArray()

            // Keep only one active inference; drop stale windows under load
            // so labels stay near real-time (important for stair transitions).
            val acquired = inferenceInFlight.compareAndSet(false, true)
            if (acquired) {
                scope?.launch {
                    try {
                        val probs = cls.predict(window)
                        val bestIdx = probs.indices.maxByOrNull { probs[it] } ?: return@launch
                        val label = cls.meta.classNames[bestIdx].lowercase()
                        val conf = probs[bestIdx]

                        // Emit every inference so stair transition logic receives
                        // the full temporal sequence of labels.
                        lastLabel = label
                        lastConfidence = conf
                        _motionEvent.value = MotionEvent(label, conf)
                    } finally {
                        inferenceInFlight.set(false)
                    }
                }
            }

            // Slide the window forward by stepSize
            val removeCount = cls.meta.stepSize.coerceAtMost(sensorBuffer.size)
            repeat(removeCount) { sensorBuffer.removeFirst() }
        }
    }
}
