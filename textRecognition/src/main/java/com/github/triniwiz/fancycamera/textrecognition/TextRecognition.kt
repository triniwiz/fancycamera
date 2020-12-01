package com.github.triniwiz.fancycamera.textrecognition

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizerOptions
import java.util.concurrent.Executors

class TextRecognition {
    private val executor = Executors.newSingleThreadExecutor()
    fun processImage(image: InputImage): Task<String> {
        val task = TaskCompletionSource<String>()
        val client = com.google.mlkit.vision.text.TextRecognition.getClient()
        val gson = Gson()
        client.process(image)
                .addOnSuccessListener(executor, {
                    val result = Result(it)
                    val json: String
                    json = if (result.text.isEmpty() && result.blocks.isEmpty()) {
                        ""
                    } else {
                        gson.toJson(result)
                    }
                    task.setResult(json)
                })
                .addOnFailureListener(executor, {
                    task.setException(it)
                })
        return task.task
    }

    fun processBytes(bytes: ByteArray, width: Int, height: Int, rotation: Int, format: Int): Task<String> {
        val input = InputImage.fromByteArray(bytes, width, height, rotation, format)
        return processImage(input)
    }

    fun processBitmap(bitmap: Bitmap, rotation: Int): Task<String> {
        val input = InputImage.fromBitmap(bitmap, rotation)
        return processImage(input)
    }
}