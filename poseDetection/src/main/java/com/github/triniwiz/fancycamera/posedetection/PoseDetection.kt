package com.github.triniwiz.fancycamera.posedetection

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PoseDetection {
    private var executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var singleMode = false
    fun processImage(image: InputImage): Task<String> {
        val task = TaskCompletionSource<String>()
        val opts = PoseDetectorOptions.Builder()
        if (singleMode) {
            opts.setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
        } else {
            opts.setDetectorMode(PoseDetectorOptions.STREAM_MODE)
        }
        val client = com.google.mlkit.vision.pose.PoseDetection.getClient(opts.build())
        val gson = Gson()
        client.process(image)
                .addOnSuccessListener(executor, {
                    val result = Result(it)
                    val json: String
                    json = if (result.landMarks.isEmpty()) {
                        ""
                    } else {
                        gson.toJson(result)
                    }
                    client.close()
                    singleMode = false
                    task.setResult(json)
                })
                .addOnFailureListener(executor, {
                    singleMode = false
                    task.setException(it)
                })
        return task.task
    }

    fun processBytes(bytes: ByteArray, width: Int, height: Int, rotation: Int, format: Int): Task<String> {
        val input = InputImage.fromByteArray(bytes, width, height, rotation, format)
        return processImage(input)
    }

    fun processBitmap(bitmap: Bitmap, rotation: Int): Task<String> {
        singleMode = true
        val input = InputImage.fromBitmap(bitmap, rotation)
        return processImage(input)
    }

}