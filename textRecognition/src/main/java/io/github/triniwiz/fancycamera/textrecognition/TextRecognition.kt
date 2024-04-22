package io.github.triniwiz.fancycamera.textrecognition

import com.google.android.gms.tasks.Tasks
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.triniwiz.fancycamera.ImageProcessor
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

class TextRecognition : ImageProcessor<String> {
    override val type: Int
        get() = 6
    private val gson = Gson()
    override fun process(image: InputImage): FutureTask<String> {
        val client =
            com.google.mlkit.vision.text.TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return FutureTask {
            try {
                val results = Tasks.await(client.process(image))
                val result = Result(results)
                if (result.text.isEmpty() && result.blocks.isEmpty()) {
                    ""
                } else {
                    gson.toJson(result)
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
}