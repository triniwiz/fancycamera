package io.github.triniwiz.fancycamera.imagelabeling

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ImageLabeling {
    private var executor: ExecutorService = Executors.newSingleThreadExecutor()
    fun processImage(image: InputImage, options: Options): Task<String> {
        val task = TaskCompletionSource<String>()
        val client = com.google.mlkit.vision.label.ImageLabeling.getClient(
            ImageLabelerOptions.Builder().setConfidenceThreshold(options.confidenceThreshold)
                .build()
        )
        val gson = Gson()
        client.process(image)
            .addOnSuccessListener(executor, {
                val result = mutableListOf<Result>()
                for (label in it) {
                    result.add(Result(label))
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

    fun processBytes(
        bytes: ByteArray,
        width: Int,
        height: Int,
        rotation: Int,
        format: Int,
        options: Options
    ): Task<String> {
        val input = InputImage.fromByteArray(bytes, width, height, rotation, format)
        return processImage(input, options)
    }

    fun processBitmap(bitmap: Bitmap, rotation: Int, options: Options): Task<String> {
        val input = InputImage.fromBitmap(bitmap, rotation)
        return processImage(input, options)
    }

    class Options {
        var confidenceThreshold = 0.5f

        companion object {
            @JvmStatic
            fun fromJson(value: String): Options? {
                return fromJson(value, false)
            }

            @JvmStatic
            fun fromJson(value: String, returnDefault: Boolean): Options? {
                return try {
                    val json = JSONObject(value)
                    fromJson(json, returnDefault)
                } catch (e: Exception) {
                    if (returnDefault) {
                        Options()
                    } else {
                        null
                    }
                }
            }

            @JvmStatic
            fun fromJson(value: JSONObject, returnDefault: Boolean): Options? {
                return try {
                    val default = Options()
                    default.confidenceThreshold =
                        value.optDouble(
                            "confidenceThreshold",
                            default.confidenceThreshold.toDouble()
                        ).toFloat()
                    default
                } catch (e: Exception) {
                    if (returnDefault) {
                        Options()
                    } else {
                        null
                    }
                }
            }
        }
    }
}