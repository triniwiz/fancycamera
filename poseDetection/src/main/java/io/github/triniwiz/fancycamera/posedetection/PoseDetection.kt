package io.github.triniwiz.fancycamera.posedetection

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import io.github.triniwiz.fancycamera.ImageProcessor
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

class PoseDetection : ImageProcessor<String> {
    var singleMode = false
    private val gson = Gson()
    private var onlySingleMode = false
    override val type: Int
        get() = 4
    override fun process(image: InputImage): FutureTask<String> {
        val opts = PoseDetectorOptions.Builder()
        if (singleMode || onlySingleMode) {
            opts.setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
        } else {
            opts.setDetectorMode(PoseDetectorOptions.STREAM_MODE)
        }
        val client = com.google.mlkit.vision.pose.PoseDetection.getClient(opts.build())

        return FutureTask {
            try {
                val results = Tasks.await(client.process(image))
                val result = Result(results)
                if (result.landMarks.isEmpty()) {
                    ""
                } else {
                    gson.toJson(result)
                }
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
    ): FutureTask<String> {
        onlySingleMode = true
        val input = InputImage.fromByteArray(bytes, width, height, rotation, format)
        return process(input)
    }

    override fun process(bitmap: Bitmap, rotation: Int): FutureTask<String> {
        onlySingleMode = true
        val input = InputImage.fromBitmap(bitmap, rotation)
        return process(input)
    }

}