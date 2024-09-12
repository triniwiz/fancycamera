package io.github.triniwiz.fancycamera.objectdetection


import com.google.android.gms.tasks.Tasks
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import io.github.triniwiz.fancycamera.ImageProcessor
import org.json.JSONObject
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask


class ObjectDetection : ImageProcessor<String> {
    var options = Options()
    override val type: Int
        get() = 3

    private val gson = Gson()
    override fun process(image: InputImage): FutureTask<String> {
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

        return FutureTask {
            try {
                val results = Tasks.await(client.process(image))
                val result = mutableListOf<Result>()
                for (detected in results) {
                    result.add(Result(detected))
                }
                if (result.isNotEmpty()) {
                    gson.toJson(result)
                } else {
                    ""
                }
            } catch (e: ExecutionException) {
                throw e
            } catch (e: InterruptedException) {
                throw e
            } finally {
                client.close()
            }
        }
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