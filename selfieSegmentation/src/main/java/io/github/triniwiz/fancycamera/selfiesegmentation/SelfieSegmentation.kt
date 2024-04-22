package io.github.triniwiz.fancycamera.selfiesegmentation

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import io.github.triniwiz.fancycamera.ImageProcessor
import org.json.JSONObject
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

class SelfieSegmentation : ImageProcessor<SegmentationMask> {
    var options = Options()
    override val type: Int
        get() = 5
    private var onlySingleMode = false


    override fun process(image: InputImage): FutureTask<SegmentationMask> {
        val opts = SelfieSegmenterOptions.Builder()
        if (options.singleMode) {
            opts.setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
        } else {
            opts.setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
        }
        if (options.enableRawSizeMask) {
            opts.enableRawSizeMask()
        }
        opts.setStreamModeSmoothingRatio(options.smoothingRatio)
        val client = Segmentation.getClient(opts.build())

        return FutureTask {
            try {
                Tasks.await(client.process(image))
            } catch (e: ExecutionException) {
                throw e
            } catch (e: InterruptedException) {
                throw e
            } finally {
                onlySingleMode = false
                client.close()
            }
        }

    }

    override fun process(
        bytes: ByteArray,
        width: Int,
        height: Int,
        rotation: Int,
        format: Int
    ): FutureTask<SegmentationMask> {
        onlySingleMode = true
        val input = InputImage.fromByteArray(bytes, width, height, rotation, format)
        return process(input)
    }

    override fun process(bitmap: Bitmap, rotation: Int): FutureTask<SegmentationMask> {
        onlySingleMode = true
        val input = InputImage.fromBitmap(bitmap, rotation)
        return process(input)
    }

    class Options {
        internal var singleMode = false
        var enableRawSizeMask = false
        var smoothingRatio = 0.7F

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
                    default.enableRawSizeMask =
                        value.optBoolean(
                            "enableRawSizeMask",
                            default.enableRawSizeMask
                        )
                    default.smoothingRatio =
                        value.optDouble("smoothingRatio", default.smoothingRatio.toDouble())
                            .toFloat()

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