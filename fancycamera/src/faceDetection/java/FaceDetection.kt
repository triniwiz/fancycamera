import android.graphics.ImageFormat
import android.media.Image
import com.google.mlkit.vision.common.InputImage

class FaceDetection {

    fun processImage() {}

    fun processBytes(bytes: ByteArray, width: Int, height: Int, rotation: Int, format: ImageFormat) {
        val client = com.google.mlkit.vision.face.FaceDetection.getClient()
        val input = InputImage.fromByteArray(bytes, width, height, rotation, InputImage.IMAGE_FORMAT_NV21)
        client.process(input)
    }
}