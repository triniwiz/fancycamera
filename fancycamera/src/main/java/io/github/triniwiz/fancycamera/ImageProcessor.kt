package io.github.triniwiz.fancycamera

import android.graphics.Bitmap
import android.media.Image
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

interface ImageProcessor<T> {
    val type: Int
        get() = -1

    fun process(image: InputImage): FutureTask<T>

    fun process(
        bytes: ByteArray,
        width: Int,
        height: Int,
        rotation: Int,
        format: Int
    ): FutureTask<T> {
        val input = InputImage.fromByteArray(bytes, width, height, rotation, format)
        return process(input)
    }

    fun process(image: Image, rotation: Int): FutureTask<T> {
        return process(InputImage.fromMediaImage(image, rotation))
    }

    fun process(bitmap: Bitmap, rotation: Int): FutureTask<T> {
        val input = InputImage.fromBitmap(bitmap, rotation)
        return process(input)
    }

}