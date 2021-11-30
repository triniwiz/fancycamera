package io.github.triniwiz.fancycamera.facedetection

import android.graphics.Bitmap
import com.google.android.gms.tasks.*
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceDetection() {
    private var executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun processImage(image: InputImage, options: Options): Task<String> {
        val task = TaskCompletionSource<String>()
        val opts = FaceDetectorOptions.Builder()
        if (options.faceTracking) {
            opts.enableTracking()
        }
        opts.setPerformanceMode(options.detectionMode.toNative())
        opts.setMinFaceSize(options.minimumFaceSize)
        opts.setLandmarkMode(options.landmarkMode.toNative())
        opts.setContourMode(options.contourMode.toNative())
        opts.setClassificationMode(options.classificationMode.toNative())
        val client = com.google.mlkit.vision.face.FaceDetection.getClient(opts.build())
        val gson = Gson()
        client.process(image)
                .addOnSuccessListener(executor, {
                    val result = mutableListOf<String>()
                    for (face in it) {
                        val json = gson.toJson(Result(face))
                        result.add(json)
                    }

                    val json = if (result.isNotEmpty()) {
                        gson.toJson(result)
                    } else {
                        ""
                    }
                    client.close()
                    task.setResult(json)
                })
                .addOnFailureListener(executor, {
                    task.setException(it)
                })
        return task.task
    }

    fun processBytes(bytes: ByteArray, width: Int, height: Int, rotation: Int, format: Int, options: Options): Task<String> {
        val input = InputImage.fromByteArray(bytes, width, height, rotation, format)
        return processImage(input, options)
    }

    fun processBitmap(bitmap: Bitmap, rotation: Int, options: Options): Task<String> {
        val input = InputImage.fromBitmap(bitmap, rotation)
        return processImage(input, options)
    }

    enum class DetectionMode(private val mode: String) {
        Accurate("accurate"),
        Fast("fast");

        companion object {
            @JvmStatic
            fun fromString(value: String): DetectionMode? {
                return when (value) {
                    "accurate" -> Accurate
                    "fast" -> Fast
                    else -> null
                }
            }
        }

        internal fun toNative(): Int {
            return when (this) {
                Accurate -> FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
                Fast -> FaceDetectorOptions.PERFORMANCE_MODE_FAST
            }
        }

        override fun toString(): String {
            return mode
        }
    }

    enum class LandMarkMode(private val mode: String) {
        None("none"),
        All("all");

        companion object {
            @JvmStatic
            fun fromString(value: String): LandMarkMode? {
                return when (value) {
                    "all" -> All
                    "none" -> None
                    else -> null
                }
            }
        }

        override fun toString(): String {
            return mode
        }

        internal fun toNative(): Int {
            return when (this) {
                None -> FaceDetectorOptions.LANDMARK_MODE_NONE
                All -> FaceDetectorOptions.LANDMARK_MODE_ALL
            }
        }
    }

    enum class ContourMode(private val mode: String) {
        None("none"),
        All("all");

        companion object {
            @JvmStatic
            fun fromString(value: String): ContourMode? {
                return when (value) {
                    "all" -> All
                    "none" -> None
                    else -> null
                }
            }
        }

        override fun toString(): String {
            return mode
        }

        internal fun toNative(): Int {
            return when (this) {
                None -> FaceDetectorOptions.CONTOUR_MODE_NONE
                All -> FaceDetectorOptions.CONTOUR_MODE_ALL
            }
        }
    }

    enum class ClassificationMode(private val mode: String) {
        None("none"),
        All("all");

        companion object {
            @JvmStatic
            fun fromString(value: String): ClassificationMode? {
                return when (value) {
                    "all" -> All
                    "none" -> None
                    else -> null
                }
            }
        }

        override fun toString(): String {
            return mode
        }

        internal fun toNative(): Int {
            return when (this) {
                None -> FaceDetectorOptions.CLASSIFICATION_MODE_NONE
                All -> FaceDetectorOptions.CLASSIFICATION_MODE_ALL
            }
        }
    }

    class Options {
        var faceTracking: Boolean = false
        var minimumFaceSize: Float = 0.1F
        var detectionMode = DetectionMode.Fast
        var landmarkMode = LandMarkMode.All
        var contourMode = ContourMode.All
        var classificationMode = ClassificationMode.All

        fun setDetectionMode(mode: String) {
            val mMode = DetectionMode.fromString(mode) ?: return
            detectionMode = mMode
        }

        fun setLandMarkMode(mode: String) {
            val mMode = LandMarkMode.fromString(mode) ?: return
            landmarkMode = mMode
        }

        fun setContourMode(mode: String) {
            val mMode = ContourMode.fromString(mode) ?: return
            contourMode = mMode
        }

        fun setClassificationMode(mode: String) {
            val mMode = ClassificationMode.fromString(mode) ?: return
            classificationMode = mMode
        }

        override fun toString(): String {
            return "faceTracking: $faceTracking, minimumFaceSize: $minimumFaceSize, detectionMode: $detectionMode, landmarkMode: $landmarkMode, contourMode: $contourMode, classificationMode: $classificationMode"
        }
    }
}