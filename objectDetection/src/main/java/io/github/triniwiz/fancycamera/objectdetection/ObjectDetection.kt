package io.github.triniwiz.fancycamera.objectdetection

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ObjectDetection {
    private var executor: ExecutorService = Executors.newSingleThreadExecutor()
    fun processImage(image: InputImage, options: Options): Task<String> {
        val task = TaskCompletionSource<String>()
        val opts = ObjectDetectorOptions.Builder()
        if (options.multiple) {
            opts.enableMultipleObjects()
        }
        if (options.classification) {
            opts.enableClassification()
        }
        if (options.singleMode) {
            opts.setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
        } else {
            opts.setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        }

        val client = com.google.mlkit.vision.objects.ObjectDetection.getClient(opts.build())
        val gson = Gson()
        client.process(image)
            .addOnSuccessListener(executor, {
                val result = mutableListOf<Result>()
                for (detected in it) {
                    result.add(Result(detected))
                }

                val json = if (result.isNotEmpty()) {
                    gson.toJson(result)
                } else {
                    ""
                }
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
        options.singleMode = true
        val input = InputImage.fromBitmap(bitmap, rotation)
        return processImage(input, options)
    }

    class Options {
        var multiple = false
        var classification = false
        internal var singleMode = false

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
                    default.multiple = value.optBoolean("multiple", default.multiple)
                    default.classification =
                        value.optBoolean("classification", default.classification)

                    default.singleMode = value.optBoolean(
                        "singleMode",
                        default.singleMode
                    )

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