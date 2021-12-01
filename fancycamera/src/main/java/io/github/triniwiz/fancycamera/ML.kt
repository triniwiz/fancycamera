package io.github.triniwiz.fancycamera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ML {
    companion object {
        private var mainHandler = Handler(Looper.getMainLooper())
        private var executor: ExecutorService = Executors.newCachedThreadPool()

        @JvmStatic
        fun processBytes(
            byteArray: ByteArray,
            width: Int,
            height: Int,
            rotation: Int,
            format: Int,
            options: String,
            callback: ImageAnalysisCallback
        ) {
            val InputImageClazz = Class.forName("com.google.mlkit.vision.common.InputImage")
            val fromMediaImage =
                InputImageClazz.getMethod(
                    "fromByteArray",
                    ByteArray::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java
                )
            process(
                fromMediaImage.invoke(null, byteArray, width, height, rotation, format),
                options, callback
            )
        }

        @JvmStatic
        fun processImage(
            image: Bitmap,
            rotation: Int,
            options: String,
            callback: ImageAnalysisCallback
        ) {
            val InputImageClazz = Class.forName("com.google.mlkit.vision.common.InputImage")
            val fromMediaImage =
                InputImageClazz.getMethod(
                    "fromBitmap",
                    Bitmap::class.java,
                    Int::class.java
                )
            process(
                fromMediaImage.invoke(null, image, rotation),
                options, callback
            )
        }

        private fun process(
            inputImage: Any?,
            options: String,
            callback: ImageAnalysisCallback
        ) {
            val json = try {
                JSONObject(options)
            } catch (e: Exception) {
                callback.onError(e.message ?: "", e)
                null
            } ?: return
            executor.execute {
                inputImage?.let { image ->
                    val tasks = mutableListOf<Task<*>>()
                    val detectorType = (json["detectorType"] as Int?)?.let { type ->
                        DetectorType.fromInt(type)
                    } ?: DetectorType.None

                    val results = mutableListOf<Array<Any>>()

                    //BarcodeScanning
                    if (CameraBase.isFaceDetectionSupported && (detectorType == DetectorType.Barcode || detectorType == DetectorType.All)) {
                        json.opt("barcodeScanning")?.let {
                            try {
                                val OptionsClazz =
                                    Class.forName("io.github.triniwiz.fancycamera.barcodescanning.BarcodeScanner\$Options")
                                val fromJson =
                                    OptionsClazz.getMethod(
                                        "fromJson",
                                        JSONObject::class.java,
                                        Boolean::class.java
                                    )

                                val opts = fromJson.invoke(null, it, true)
                                val barcodeTask =
                                    handleBarcodeScanning(image, opts!!, object :
                                        ImageAnalysisCallback {
                                        override fun onSuccess(result: Any) {
                                            results.add(arrayOf("barcode", result))
                                        }

                                        override fun onError(
                                            message: String,
                                            exception: java.lang.Exception
                                        ) {
                                        }
                                    })
                                if (barcodeTask != null) {
                                    tasks.add(barcodeTask)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    // FaceDetection
                    if (CameraBase.isFaceDetectionSupported && (detectorType == DetectorType.Face || detectorType == DetectorType.All)) {
                        json.opt("faceDetection")?.let {
                            try {
                                val OptionsClazz =
                                    Class.forName("io.github.triniwiz.fancycamera.facedetection.FaceDetection\$Options")
                                val fromJson =
                                    OptionsClazz.getMethod(
                                        "fromJson",
                                        JSONObject::class.java,
                                        Boolean::class.java
                                    )

                                val opts = fromJson.invoke(null, it, true)
                                val faceTask = handleFaceDetection(image, opts!!, object :
                                    ImageAnalysisCallback {
                                    override fun onSuccess(result: Any) {
                                        results.add(arrayOf("face", result))
                                    }

                                    override fun onError(
                                        message: String,
                                        exception: java.lang.Exception
                                    ) {
                                    }
                                })
                                if (faceTask != null) {
                                    tasks.add(faceTask)
                                }
                            } catch (e: Exception) {
                            }
                        }
                    }

                    //PoseDetection
                    if (CameraBase.isPoseDetectionSupported || (detectorType == DetectorType.Pose || detectorType == DetectorType.All)) {
                        try {
                            val poseTask = handlePoseDetection(image, object :
                                ImageAnalysisCallback {
                                override fun onSuccess(result: Any) {
                                    results.add(arrayOf("pose", result))
                                }

                                override fun onError(
                                    message: String,
                                    exception: java.lang.Exception
                                ) {
                                }
                            })
                            if (poseTask != null) {
                                tasks.add(poseTask)
                            }
                        } catch (e: Exception) {
                        }
                    }

                    //ImageLabeling
                    if (CameraBase.isImageLabelingSupported && (detectorType == DetectorType.Image || detectorType == DetectorType.All)) {
                        json.opt("imageLabeling")?.let {
                            try {
                                val OptionsClazz =
                                    Class.forName("io.github.triniwiz.fancycamera.imagelabeling.ImageLabeling\$Options")
                                val fromJson =
                                    OptionsClazz.getMethod(
                                        "fromJson",
                                        JSONObject::class.java,
                                        Boolean::class.java
                                    )

                                val opts = fromJson.invoke(null, it, true)
                                val imageTask = handleImageLabeling(image, opts!!, object :
                                    ImageAnalysisCallback {
                                    override fun onSuccess(result: Any) {
                                        results.add(arrayOf("image", result))
                                    }

                                    override fun onError(
                                        message: String,
                                        exception: java.lang.Exception
                                    ) {
                                    }
                                })
                                if (imageTask != null) {
                                    tasks.add(imageTask)
                                }
                            } catch (e: Exception) {
                            }
                        }
                    }

                    //ObjectDetection
                    if (CameraBase.isObjectDetectionSupported && (detectorType == DetectorType.Object || detectorType == DetectorType.All)) {
                        json.opt("objectDetection")?.let {
                            val OptionsClazz =
                                Class.forName("io.github.triniwiz.fancycamera.objectdetection.ObjectDetection\$Options")
                            val fromJson =
                                OptionsClazz.getMethod(
                                    "fromJson",
                                    JSONObject::class.java,
                                    Boolean::class.java
                                )

                            val opts = fromJson.invoke(null, it, true)
                            val objectTask = handleObjectDetection(image, opts!!, object :
                                ImageAnalysisCallback {
                                override fun onSuccess(result: Any) {
                                    results.add(arrayOf("object", result))
                                }

                                override fun onError(
                                    message: String,
                                    exception: java.lang.Exception
                                ) {
                                }
                            })
                            if (objectTask != null) {
                                tasks.add(objectTask)
                            }
                        }
                    }

                    // TextRecognition
                    if (CameraBase.isTextRecognitionSupported || (detectorType == DetectorType.Text || detectorType == DetectorType.All)) {
                        try {
                            val textTask = handleTextRecognition(image, object :
                                ImageAnalysisCallback {
                                override fun onSuccess(result: Any) {
                                    results.add(arrayOf("text", result))
                                }

                                override fun onError(
                                    message: String,
                                    exception: java.lang.Exception
                                ) {
                                }
                            })
                            if (textTask != null) {
                                tasks.add(textTask)
                            }
                        } catch (e: Exception) {
                        }
                    }

                    // SelfieSegmentation
                    if (CameraBase.isSelfieSegmentationSupported || (detectorType == DetectorType.Selfie || detectorType == DetectorType.All)) {
                        json.opt("selfieSegmentation")?.let {
                            try {
                                val OptionsClazz =
                                    Class.forName("io.github.triniwiz.fancycamera.selfiesegmentation.SelfieSegmentation\$Options")
                                val fromJson =
                                    OptionsClazz.getMethod(
                                        "fromJson",
                                        JSONObject::class.java,
                                        Boolean::class.java
                                    )

                                val opts = fromJson.invoke(null, it, true)
                                val selfieTask = handleSelfieSegmentation(image, opts!!, object :
                                    ImageAnalysisCallback {
                                    override fun onSuccess(result: Any) {
                                        results.add(arrayOf("selfie", result))
                                    }

                                    override fun onError(
                                        message: String,
                                        exception: java.lang.Exception
                                    ) {
                                    }
                                })
                                if (selfieTask != null) {
                                    tasks.add(selfieTask)
                                }
                            } catch (e: Exception) {
                            }
                        }
                    }

                    if (tasks.isNotEmpty()) {
                        Tasks.whenAllComplete(tasks).addOnCompleteListener {
                            callback.onSuccess(results)
                        }
                    }
                }
            }
        }


        @SuppressLint("UnsafeOptInUsageError")
        private fun handleBarcodeScanning(
            inputImage: Any,
            options: Any,
            callback: ImageAnalysisCallback
        ): Task<Boolean>? {
            if (!CameraBase.isBarcodeScanningSupported) {
                return null
            }

            val InputImageClazz = Class.forName("com.google.mlkit.vision.common.InputImage")
            val BarcodeScannerClazz =
                Class.forName("io.github.triniwiz.fancycamera.barcodescanning.BarcodeScanner")
            val barcodeScanner = BarcodeScannerClazz.newInstance()

            val BarcodeScannerOptionsClazz =
                Class.forName("io.github.triniwiz.fancycamera.barcodescanning.BarcodeScanner\$Options")


            val processImageMethod = BarcodeScannerClazz.getDeclaredMethod(
                "processImage",
                InputImageClazz,
                BarcodeScannerOptionsClazz
            )

            val returnTask = TaskCompletionSource<Boolean>()
            val task = processImageMethod.invoke(
                barcodeScanner,
                inputImage,
                options
            ) as Task<String>
            task.addOnSuccessListener(executor, {
                if (it.isNotEmpty()) {
                    mainHandler.post {
                        callback.onSuccess(it)
                    }
                }
            }).addOnFailureListener(executor, {
                mainHandler.post {
                    callback.onError(
                        it.message
                            ?: "Failed to complete face detection.", it
                    )
                }
            }).addOnCompleteListener(executor, {
                returnTask.setResult(true)
            })
            return returnTask.task
        }

        @SuppressLint("UnsafeOptInUsageError")
        private fun handleFaceDetection(
            inputImage: Any,
            options: Any,
            callback: ImageAnalysisCallback
        ): Task<Boolean>? {
            if (!CameraBase.isFaceDetectionSupported) {
                return null
            }

            val InputImageClazz = Class.forName("com.google.mlkit.vision.common.InputImage")
            val FaceDetectionClazz =
                Class.forName("io.github.triniwiz.fancycamera.facedetection.FaceDetection")
            val faceDetection = FaceDetectionClazz.newInstance()

            val FaceDetectionOptionsClazz =
                Class.forName("io.github.triniwiz.fancycamera.facedetection.FaceDetection\$Options")

            val processImageMethod = FaceDetectionClazz.getDeclaredMethod(
                "processImage",
                InputImageClazz,
                FaceDetectionOptionsClazz
            )
            val returnTask = TaskCompletionSource<Boolean>()
            val task = processImageMethod.invoke(
                faceDetection,
                inputImage,
                options
            ) as Task<String>
            task.addOnSuccessListener(executor, {
                if (it.isNotEmpty()) {
                    mainHandler.post {
                        callback.onSuccess(it)
                    }
                }
            }).addOnFailureListener(executor, {
                mainHandler.post {
                    callback.onError(
                        it.message
                            ?: "Failed to complete face detection.", it
                    )
                }
            }).addOnCompleteListener(executor, {
                returnTask.setResult(true)
            })
            return returnTask.task
        }

        @SuppressLint("UnsafeOptInUsageError")
        private fun handleImageLabeling(
            inputImage: Any,
            options: Any,
            callback: ImageAnalysisCallback
        ): Task<Boolean>? {
            if (!CameraBase.isImageLabelingSupported) {
                return null
            }

            val InputImageClazz = Class.forName("com.google.mlkit.vision.common.InputImage")

            val ImageLabelingClazz =
                Class.forName("io.github.triniwiz.fancycamera.imagelabeling.ImageLabeling")
            val imageLabeling = ImageLabelingClazz.newInstance()
            val ImageLabelingOptionsClazz =
                Class.forName("io.github.triniwiz.fancycamera.imagelabeling.ImageLabeling\$Options")
            val processImageMethod = ImageLabelingClazz.getDeclaredMethod(
                "processImage",
                InputImageClazz,
                ImageLabelingOptionsClazz
            )
            val returnTask = TaskCompletionSource<Boolean>()
            val task = processImageMethod.invoke(
                imageLabeling,
                inputImage,
                options
            ) as Task<String>
            task.addOnSuccessListener(executor, {
                if (it.isNotEmpty()) {
                    mainHandler.post {
                        callback.onSuccess(it)
                    }
                }
            }).addOnFailureListener(executor, {
                mainHandler.post {
                    callback.onError(
                        it.message
                            ?: "Failed to complete face detection.", it
                    )
                }
            }).addOnCompleteListener(executor, {
                returnTask.setResult(true)
            })
            return returnTask.task
        }

        @SuppressLint("UnsafeOptInUsageError")
        private fun handleObjectDetection(
            inputImage: Any,
            options: Any,
            callback: ImageAnalysisCallback
        ): Task<Boolean>? {
            if (!CameraBase.isObjectDetectionSupported) {
                return null
            }

            val InputImageClazz = Class.forName("com.google.mlkit.vision.common.InputImage")
            val ObjectDetectionClazz =
                Class.forName("io.github.triniwiz.fancycamera.objectdetection.ObjectDetection")
            val objectDetection = ObjectDetectionClazz.newInstance()
            val ObjectDetectionOptionsClazz =
                Class.forName("io.github.triniwiz.fancycamera.objectdetection.ObjectDetection\$Options")
            val processImageMethod = ObjectDetectionClazz.getDeclaredMethod(
                "processImage",
                InputImageClazz,
                ObjectDetectionOptionsClazz
            )
            val returnTask = TaskCompletionSource<Boolean>()
            val task = processImageMethod.invoke(
                objectDetection,
                inputImage,
                options
            ) as Task<String>
            task.addOnSuccessListener(executor, {
                if (it.isNotEmpty()) {
                    mainHandler.post {
                        callback.onSuccess(it)
                    }
                }
            }).addOnFailureListener(executor, {
                mainHandler.post {
                    callback.onError(
                        it.message
                            ?: "Failed to complete face detection.", it
                    )
                }
            }).addOnCompleteListener(executor, {
                returnTask.setResult(true)
            })
            return returnTask.task
        }

        @SuppressLint("UnsafeOptInUsageError")
        private fun handlePoseDetection(
            inputImage: Any,
            callback: ImageAnalysisCallback
        ): Task<Boolean>? {
            if (!CameraBase.isPoseDetectionSupported) {
                return null
            }
            val InputImageClazz = Class.forName("com.google.mlkit.vision.common.InputImage")
            val PoseDetectionClazz =
                Class.forName("io.github.triniwiz.fancycamera.posedetection.PoseDetection")
            val poseDetection = PoseDetectionClazz.newInstance()
            val singleMode = PoseDetectionClazz.getDeclaredField("singleMode")
            singleMode.setBoolean(poseDetection, true)
            val processImageMethod =
                PoseDetectionClazz.getDeclaredMethod("processImage", InputImageClazz)
            val returnTask = TaskCompletionSource<Boolean>()
            val task = processImageMethod.invoke(poseDetection, inputImage) as Task<String>
            task.addOnSuccessListener(executor, {
                if (it.isNotEmpty()) {
                    mainHandler.post {
                        callback.onSuccess(it)
                    }
                }
            }).addOnFailureListener(executor, {
                mainHandler.post {
                    callback.onError(
                        it.message
                            ?: "Failed to complete text recognition.", it
                    )
                }
            }).addOnCompleteListener(executor, {
                returnTask.setResult(true)
            })
            return returnTask.task
        }

        @SuppressLint("UnsafeOptInUsageError")
        private fun handleTextRecognition(
            inputImage: Any,
            callback: ImageAnalysisCallback
        ): Task<Boolean>? {
            if (!CameraBase.isTextRecognitionSupported) {
                return null
            }

            val InputImageClazz = Class.forName("com.google.mlkit.vision.common.InputImage")
            val TextRecognitionClazz =
                Class.forName("io.github.triniwiz.fancycamera.textrecognition.TextRecognition")
            val textRecognition = TextRecognitionClazz.newInstance()
            val processImageMethod =
                TextRecognitionClazz.getDeclaredMethod("processImage", InputImageClazz)
            val returnTask = TaskCompletionSource<Boolean>()
            val task = processImageMethod.invoke(textRecognition, inputImage) as Task<String>
            task.addOnSuccessListener(executor, {
                if (it.isNotEmpty()) {
                    mainHandler.post {
                        callback.onSuccess(it)
                    }
                }
            }).addOnFailureListener(executor, {
                mainHandler.post {
                    callback.onError(
                        it.message
                            ?: "Failed to complete text recognition.", it
                    )
                }
            }).addOnCompleteListener(executor, {
                returnTask.setResult(true)
            })
            return returnTask.task
        }

        @SuppressLint("UnsafeOptInUsageError")
        private fun handleSelfieSegmentation(
            inputImage: Any,
            options: Any,
            callback: ImageAnalysisCallback
        ): Task<Boolean>? {
            if (!CameraBase.isSelfieSegmentationSupported) {
                return null
            }

            val InputImageClazz = Class.forName("com.google.mlkit.vision.common.InputImage")
            val SelfieSegmentationClazz =
                Class.forName("io.github.triniwiz.fancycamera.selfiesegmentation.SelfieSegmentation")
            val selfieSegmentation = SelfieSegmentationClazz.newInstance()

            val SelfieSegmentationOptionsClazz =
                Class.forName("io.github.triniwiz.fancycamera.selfiesegmentation.SelfieSegmentation\$Options")

            val processImageMethod = SelfieSegmentationClazz.getDeclaredMethod(
                "processImage",
                InputImageClazz,
                SelfieSegmentationOptionsClazz
            )

            val returnTask = TaskCompletionSource<Boolean>()
            val task = processImageMethod.invoke(
                selfieSegmentation,
                inputImage,
                options
            ) as Task<Any?>
            task.addOnSuccessListener(executor, {
                if (it != null) {
                    mainHandler.post {
                        callback.onSuccess(it)
                    }
                }
            }).addOnFailureListener(executor, {
                mainHandler.post {
                    callback.onError(
                        it.message
                            ?: "Failed to complete text recognition.", it
                    )
                }
            }).addOnCompleteListener(executor, {
                returnTask.setResult(true)
            })
            return returnTask.task
        }

    }
}