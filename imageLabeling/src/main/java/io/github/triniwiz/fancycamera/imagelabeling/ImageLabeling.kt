package io.github.triniwiz.fancycamera.imagelabeling

import com.google.android.gms.tasks.Tasks
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import io.github.triniwiz.fancycamera.ImageProcessor
import org.json.JSONObject
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

class ImageLabeling : ImageProcessor<String> {
    var options = Options()
    override val type: Int
        get() = 2

    private val gson = Gson()
    override fun process(image: InputImage): FutureTask<String> {
        val client = com.google.mlkit.vision.label.ImageLabeling.getClient(
            ImageLabelerOptions.Builder().setConfidenceThreshold(options.confidenceThreshold)
                .build()
        )

        return FutureTask {
            try {
                val results = Tasks.await(client.process(image))
                val result = mutableListOf<Result>()
                for (label in results) {
                    result.add(Result(label))
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