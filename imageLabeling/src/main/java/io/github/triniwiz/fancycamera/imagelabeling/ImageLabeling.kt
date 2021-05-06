package io.github.triniwiz.fancycamera.imagelabeling

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ImageLabeling {
    private var executor: ExecutorService = Executors.newSingleThreadExecutor()
    fun processImage(image: InputImage, options: Options): Task<String> {
        val task = TaskCompletionSource<String>()
        val client = com.google.mlkit.vision.label.ImageLabeling.getClient(
                ImageLabelerOptions.Builder().setConfidenceThreshold(options.confidenceThreshold).build()
        )
        val gson = Gson()
        client.process(image)
                .addOnSuccessListener(executor, {
                    val result = mutableListOf<String>()
                    for (label in it) {
                        val json = gson.toJson(Result(label))
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

    class Options {
        var confidenceThreshold = 0.5f
    }
}