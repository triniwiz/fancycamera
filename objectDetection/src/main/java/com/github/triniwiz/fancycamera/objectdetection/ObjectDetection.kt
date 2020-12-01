package com.github.triniwiz.fancycamera.objectdetection

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
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
                    val result = mutableListOf<String>()
                    for (detected in it) {
                        val json = gson.toJson(Result(detected))
                        result.add(json)
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

    fun processBytes(bytes: ByteArray, width: Int, height: Int, rotation: Int, format: Int, options: Options): Task<String> {
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
    }

}