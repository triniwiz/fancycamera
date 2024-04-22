package io.github.triniwiz.fancycamera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import org.json.JSONObject
import java.util.ArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ML {
    companion object {
        private var executor: ExecutorService = Executors.newCachedThreadPool()

        @JvmStatic
        fun process(
            byteArray: ByteArray,
            width: Int,
            height: Int,
            rotation: Int,
            format: Int,
            imageProcessors: List<ImageProcessor<Any>>,
            callback: ImageAnalysisCallback
        ) {
            val image = InputImage.fromByteArray(byteArray, width, height, rotation, format)
            process(image, imageProcessors, callback)
        }

        @JvmStatic
        fun processImage(
            image: Bitmap,
            rotation: Int,
            imageProcessors: List<ImageProcessor<Any>>,
            callback: ImageAnalysisCallback
        ) {
            val input = InputImage.fromBitmap(image, rotation)
            process(input, imageProcessors, callback)
        }

        private fun process(
            inputImage: InputImage?,
            imageProcessors: List<ImageProcessor<Any>>,
            callback: ImageAnalysisCallback
        ) {

            executor.execute {
                inputImage?.let { image ->
                    val ret = mutableListOf<Array<Any>>()
                    var hasError = false
                    for (processor in imageProcessors) {
                        val process = processor.process(image)
                        try {
                            val result = process.get()
                            when (processor.type) {
                                0 -> {
                                    ret.add(arrayOf("barcode", result))
                                }

                                1 -> {
                                    ret.add(arrayOf("face", result))
                                }

                                2 -> {
                                    ret.add(arrayOf("image", result))
                                }

                                3 -> {
                                    ret.add(arrayOf("object", result))
                                }

                                4 -> {
                                    ret.add(arrayOf("pose", result))
                                }

                                5 -> {
                                    ret.add(arrayOf("selfie", result))
                                }

                                6 -> {
                                    ret.add(arrayOf("text", result))
                                }
                            }
                        } catch (e: Exception) {
                            when (processor.type) {
                                0 -> {
                                    hasError = true
                                    callback.onError(
                                        e.message ?: "Failed to complete barcode scanning.", e
                                    )
                                }

                                1 -> {
                                    hasError = true
                                    callback.onError(
                                        e.message ?: "Failed to complete face detection.", e
                                    )
                                }

                                2 -> {
                                    hasError = true
                                    callback.onError(
                                        e.message
                                            ?: "Failed to complete image label detection.", e
                                    )
                                }

                                3 -> {
                                    hasError = true
                                    callback.onError(
                                        e.message ?: "Failed to complete object detection.", e
                                    )
                                }

                                4 -> {
                                    hasError = true
                                    callback.onError(
                                        e.message ?: "Failed to complete pose detection.", e
                                    )
                                }

                                5 -> {
                                    hasError = true
                                    callback.onError(
                                        e.message
                                            ?: "Failed to complete selfie segmentation detection.",
                                        e
                                    )
                                }

                                6 -> {
                                    hasError = true
                                    callback.onError(
                                        e.message ?: "Failed to complete text recognition.", e
                                    )
                                }
                            }
                            if (hasError) {
                                break
                            }
                        }
                    }
                }
            }
        }
    }
}