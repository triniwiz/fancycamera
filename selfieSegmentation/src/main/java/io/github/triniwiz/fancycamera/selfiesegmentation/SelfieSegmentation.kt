package io.github.triniwiz.fancycamera.selfiesegmentation

import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions;
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SelfieSegmentation {
    private var executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun processImage(image: InputImage, options: Options): Task<SegmentationMask> {
        val task = TaskCompletionSource<SegmentationMask>()
        val opts = SelfieSegmenterOptions.Builder()
        if (options.detectorMode === "single") {
            opts.setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
        } else {
            opts.setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
        }
        if (options.enableRawSizeMask) {
            opts.enableRawSizeMask()
        }
        opts.setStreamModeSmoothingRatio(options.smoothingRatio)
        val client = Segmentation.getClient(opts.build())
        client.process(image)
            .addOnCompleteListener(executor) {
                client.close()
                task.setResult(it.result)
            }.addOnFailureListener(executor) {
                task.setException(it)
            }
        return task.task
    }


    fun processBytes(
        bytes: ByteArray,
        width: Int,
        height: Int,
        rotation: Int,
        format: Int,
        options: Options
    ): Task<SegmentationMask> {
        val input = InputImage.fromByteArray(bytes, width, height, rotation, format)
        options.detectorMode = "single"
        return processImage(input, options)
    }

    fun processBitmap(bitmap: Bitmap, rotation: Int, options: Options): Task<SegmentationMask> {
        options.detectorMode = "single"
        val input = InputImage.fromBitmap(bitmap, rotation)
        return processImage(input, options)
    }

    class Options {
        var detectorMode: String = "stream"
        var enableRawSizeMask = false
        var smoothingRatio = 0.7F

    }
}