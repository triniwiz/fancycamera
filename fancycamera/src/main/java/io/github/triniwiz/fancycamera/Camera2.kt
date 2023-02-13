package io.github.triniwiz.fancycamera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.media.CamcorderProfile
import android.media.Image
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Log
import android.view.ScaleGestureDetector
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class Camera2 @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CameraBase(context, attrs, defStyleAttr) {
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: androidx.camera.core.ImageAnalysis? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageAnalysisExecutor = Executors.newSingleThreadExecutor()
    private var imageCaptureExecutor = Executors.newSingleThreadExecutor()
    private var videoCaptureExecutor = Executors.newSingleThreadExecutor()
    private var camera: androidx.camera.core.Camera? = null
    private var preview: Preview? = null
    private var surfaceRequest: SurfaceRequest? = null
    private var isStarted = false
    private var isRecording = false
    private var file: File? = null
    private var isForceStopping = false
    private var mLock = Any()
    private var cameraManager: CameraManager? = null
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var recording: Recording? = null
    override var retrieveLatestImage: Boolean = false
        set(value) {
            field = value
            if (!value && latestImage != null) {
                latestImage = null
            }
        }

    override var pause: Boolean = false
        set(value) {
            field = value
            if (value) {
                stopPreview()
            } else {
                startPreview()
            }
        }

    private fun handleZoom() {
        camera?.cameraControl?.setLinearZoom(
            zoom
        )
    }


    override val previewSurface: Any
        get() {
            return previewView
        }
    override var zoom: Float = 0.0F
        set(value) {
            field = when {
                value > 1 -> {
                    1f
                }
                value < 0 -> {
                    0f
                }
                else -> {
                    value
                }
            }
            handleZoom()
        }
    override var whiteBalance: WhiteBalance = WhiteBalance.Auto
        set(value) {
            if (!isRecording) {
                field = value
                refreshCamera()
            }
        }
    override var displayRatio = "4:3"
        set(value) {
            if (value == field) return
            field = when (value) {
                "16:9" -> {
                    value
                }
                "4:3" -> value
                else -> return
            }
            if (!isRecording) {
                refreshCamera()
            }
        }
    override var pictureSize: String = "0x0"
        get() {
            if (field == "0x0") {
                val size = cachedPictureRatioSizeMap[displayRatio]?.get(0)
                if (size != null) {
                    return when (resources.configuration.orientation) {
                        Configuration.ORIENTATION_LANDSCAPE -> "${size.width}x${size.height}"
                        Configuration.ORIENTATION_PORTRAIT -> "${size.height}x${size.width}"
                        else -> field
                    }
                }
            }
            return field
        }
        set(value) {
            val size = stringSizeToSize(value)
            if (cachedPictureRatioSizeMap[displayRatio]?.contains(size) == true) {
                field = value
            }
        }

    private var previewView: PreviewView = PreviewView(context, attrs, defStyleAttr)

    @SuppressLint("UnsafeOptInUsageError")
    private fun handleBarcodeScanning(proxy: ImageProxy): Task<Boolean>? {
        if (!isBarcodeScanningSupported || !(detectorType == DetectorType.Barcode || detectorType == DetectorType.All)) {
            return null
        }
        val image = proxy.image ?: return null
        val rotationAngle = proxy.imageInfo.rotationDegrees
        val InputImageClazz = Class.forName("com.google.mlkit.vision.common.InputImage")
        val BarcodeScannerClazz =
            Class.forName("io.github.triniwiz.fancycamera.barcodescanning.BarcodeScanner")
        val barcodeScanner = BarcodeScannerClazz.newInstance()
        val fromMediaImage =
            InputImageClazz.getMethod("fromMediaImage", Image::class.java, Int::class.java)
        val inputImage = fromMediaImage.invoke(null, image, rotationAngle)
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
            barcodeScannerOptions!!
        ) as Task<String>
        task.addOnSuccessListener(imageAnalysisExecutor) {
            if (it.isNotEmpty()) {
                mainHandler.post {
                    onBarcodeScanningListener?.onSuccess(it)
                }
            }
        }.addOnFailureListener(imageAnalysisExecutor) {
            mainHandler.post {
                onBarcodeScanningListener?.onError(
                    it.message
                        ?: "Failed to complete face detection.", it
                )
            }
        }.addOnCompleteListener(imageAnalysisExecutor) {
            returnTask.setResult(true)
        }
        return returnTask.task
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun handleFaceDetection(proxy: ImageProxy): Task<Boolean>? {
        if (!isFaceDetectionSupported || !(detectorType == DetectorType.Face || detectorType == DetectorType.All)) {
            return null
        }
        val image = proxy.image ?: return null
        val rotationAngle = proxy.imageInfo.rotationDegrees
        val InputImageClazz = Class.forName("com.google.mlkit.vision.common.InputImage")
        val FaceDetectionClazz =
            Class.forName("io.github.triniwiz.fancycamera.facedetection.FaceDetection")
        val faceDetection = FaceDetectionClazz.newInstance()
        val fromMediaImage =
            InputImageClazz.getMethod("fromMediaImage", Image::class.java, Int::class.java)
        val inputImage = fromMediaImage.invoke(null, image, rotationAngle)
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
            faceDetectionOptions!!
        ) as Task<String>
        task.addOnSuccessListener(imageAnalysisExecutor) {
            if (it.isNotEmpty()) {
                mainHandler.post {
                    onFacesDetectedListener?.onSuccess(it)
                }
            }
        }.addOnFailureListener(imageAnalysisExecutor) {
            mainHandler.post {
                onFacesDetectedListener?.onError(
                    it.message
                        ?: "Failed to complete face detection.", it
                )
            }
        }.addOnCompleteListener(imageAnalysisExecutor) {
            returnTask.setResult(true)
        }
        return returnTask.task
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun handleImageLabeling(proxy: ImageProxy): Task<Boolean>? {
        if (!isImageLabelingSupported || !(detectorType == DetectorType.Image || detectorType == DetectorType.All)) {
            return null
        }
        val image = proxy.image ?: return null
        val rotationAngle = proxy.imageInfo.rotationDegrees
        val InputImageClazz = Class.forName("com.google.mlkit.vision.common.InputImage")
        val ImageLabelingClazz =
            Class.forName("io.github.triniwiz.fancycamera.imagelabeling.ImageLabeling")
        val imageLabeling = ImageLabelingClazz.newInstance()
        val fromMediaImage =
            InputImageClazz.getMethod("fromMediaImage", Image::class.java, Int::class.java)
        val inputImage = fromMediaImage.invoke(null, image, rotationAngle)
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
            imageLabelingOptions!!
        ) as Task<String>
        task.addOnSuccessListener(imageAnalysisExecutor) {
            if (it.isNotEmpty()) {
                mainHandler.post {
                    onImageLabelingListener?.onSuccess(it)
                }
            }
        }.addOnFailureListener(imageAnalysisExecutor) {
            mainHandler.post {
                onImageLabelingListener?.onError(
                    it.message
                        ?: "Failed to complete face detection.", it
                )
            }
        }.addOnCompleteListener(imageAnalysisExecutor) {
            returnTask.setResult(true)
        }
        return returnTask.task
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun handleObjectDetection(proxy: ImageProxy): Task<Boolean>? {
        if (!isObjectDetectionSupported || !(detectorType == DetectorType.Object || detectorType == DetectorType.All)) {
            return null
        }
        val image = proxy.image ?: return null
        val rotationAngle = proxy.imageInfo.rotationDegrees
        val InputImageClazz = Class.forName("com.google.mlkit.vision.common.InputImage")
        val ObjectDetectionClazz =
            Class.forName("io.github.triniwiz.fancycamera.objectdetection.ObjectDetection")
        val objectDetection = ObjectDetectionClazz.newInstance()
        val fromMediaImage =
            InputImageClazz.getMethod("fromMediaImage", Image::class.java, Int::class.java)
        val inputImage = fromMediaImage.invoke(null, image, rotationAngle)
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
            objectDetectionOptions!!
        ) as Task<String>
        task.addOnSuccessListener(imageAnalysisExecutor) {
            if (it.isNotEmpty()) {
                mainHandler.post {
                    onObjectDetectedListener?.onSuccess(it)
                }
            }
        }.addOnFailureListener(imageAnalysisExecutor) {
            mainHandler.post {
                onObjectDetectedListener?.onError(
                    it.message
                        ?: "Failed to complete face detection.", it
                )
            }
        }.addOnCompleteListener(imageAnalysisExecutor) {
            returnTask.setResult(true)
        }
        return returnTask.task
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun handlePoseDetection(proxy: ImageProxy): Task<Boolean>? {
        if (!isPoseDetectionSupported || !(detectorType == DetectorType.Pose || detectorType == DetectorType.All)) {
            return null
        }
        val image = proxy.image ?: return null
        val rotationAngle = proxy.imageInfo.rotationDegrees
        val InputImageClazz = Class.forName("com.google.mlkit.vision.common.InputImage")
        val PoseDetectionClazz =
            Class.forName("io.github.triniwiz.fancycamera.posedetection.PoseDetection")
        val poseDetection = PoseDetectionClazz.newInstance()
        val fromMediaImage =
            InputImageClazz.getMethod("fromMediaImage", Image::class.java, Int::class.java)
        val inputImage = fromMediaImage.invoke(null, image, rotationAngle)
        val processImageMethod =
            PoseDetectionClazz.getDeclaredMethod("processImage", InputImageClazz)
        val returnTask = TaskCompletionSource<Boolean>()
        val task = processImageMethod.invoke(poseDetection, inputImage) as Task<String>
        task.addOnSuccessListener(imageAnalysisExecutor) {
            if (it.isNotEmpty()) {
                mainHandler.post {
                    onPoseDetectedListener?.onSuccess(it)
                }
            }
        }.addOnFailureListener(imageAnalysisExecutor) {
            mainHandler.post {
                onPoseDetectedListener?.onError(
                    it.message
                        ?: "Failed to complete text recognition.", it
                )
            }
        }.addOnCompleteListener(imageAnalysisExecutor) {
            returnTask.setResult(true)
        }
        return returnTask.task
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun handleTextRecognition(proxy: ImageProxy): Task<Boolean>? {
        if (!isTextRecognitionSupported || !(detectorType == DetectorType.Text || detectorType == DetectorType.All)) {
            return null
        }
        val image = proxy.image ?: return null
        val rotationAngle = proxy.imageInfo.rotationDegrees
        val InputImageClazz = Class.forName("com.google.mlkit.vision.common.InputImage")
        val TextRecognitionClazz =
            Class.forName("io.github.triniwiz.fancycamera.textrecognition.TextRecognition")
        val textRecognition = TextRecognitionClazz.newInstance()
        val fromMediaImage =
            InputImageClazz.getMethod("fromMediaImage", Image::class.java, Int::class.java)
        val inputImage = fromMediaImage.invoke(null, image, rotationAngle)
        val processImageMethod =
            TextRecognitionClazz.getDeclaredMethod("processImage", InputImageClazz)
        val returnTask = TaskCompletionSource<Boolean>()
        val task = processImageMethod.invoke(textRecognition, inputImage) as Task<String>
        task.addOnSuccessListener(imageAnalysisExecutor) {
            if (it.isNotEmpty()) {
                mainHandler.post {
                    onTextRecognitionListener?.onSuccess(it)
                }
            }
        }.addOnFailureListener(imageAnalysisExecutor) {
            mainHandler.post {
                onTextRecognitionListener?.onError(
                    it.message
                        ?: "Failed to complete text recognition.", it
                )
            }
        }.addOnCompleteListener(imageAnalysisExecutor) {
            returnTask.setResult(true)
        }
        return returnTask.task
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun handleSelfieSegmentation(proxy: ImageProxy): Task<Boolean>? {
        if (!isSelfieSegmentationSupported || !(detectorType == DetectorType.Selfie || detectorType == DetectorType.All)) {
            return null
        }
        val image = proxy.image ?: return null
        val rotationAngle = proxy.imageInfo.rotationDegrees
        val InputImageClazz = Class.forName("com.google.mlkit.vision.common.InputImage")
        val SelfieSegmentationClazz =
            Class.forName("io.github.triniwiz.fancycamera.selfiesegmentation.SelfieSegmentation")
        val selfieSegmentation = SelfieSegmentationClazz.newInstance()
        val fromMediaImage =
            InputImageClazz.getMethod("fromMediaImage", Image::class.java, Int::class.java)
        val inputImage = fromMediaImage.invoke(null, image, rotationAngle)

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
            selfieSegmentationOptions
        ) as Task<Any>
        task.addOnSuccessListener(imageAnalysisExecutor) {
            if (it != null) {
                mainHandler.post {
                    onSelfieSegmentationListener?.onSuccess(it)
                }
            }
        }.addOnFailureListener(imageAnalysisExecutor) {
            mainHandler.post {
                onSelfieSegmentationListener?.onError(
                    it.message
                        ?: "Failed to complete text recognition.", it
                )
            }
        }.addOnCompleteListener(imageAnalysisExecutor) {
            returnTask.setResult(true)
        }
        return returnTask.task
    }

    private fun handlePinchZoom() {
        if (!enablePinchZoom) {
            return
        }
        val listener: ScaleGestureDetector.SimpleOnScaleGestureListener =
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    camera?.cameraInfo?.zoomState?.value?.let { zoomState ->
                        camera?.cameraControl?.setZoomRatio(
                            detector.scaleFactor * zoomState.zoomRatio
                        )
                    }
                    return true
                }
            }
        scaleGestureDetector = ScaleGestureDetector(context, listener)
        previewView.setOnTouchListener { view, event ->
            scaleGestureDetector?.onTouchEvent(event)
            view.performClick()
            true
        }
    }

    override var enablePinchZoom: Boolean = true
        set(value) {
            field = value
            if (value) {
                handlePinchZoom()
            } else {
                scaleGestureDetector = null
            }
        }


    init {
        handlePinchZoom()
        previewView.afterMeasured {
            if (autoFocus) {
                val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
                    previewView.width.toFloat(), previewView.height.toFloat()
                )
                val centerWidth = previewView.width.toFloat() / 2
                val centerHeight = previewView.height.toFloat() / 2
                val autoFocusPoint = factory.createPoint(centerWidth, centerHeight)
                try {
                    camera?.cameraControl?.startFocusAndMetering(
                        FocusMeteringAction.Builder(
                            autoFocusPoint,
                            FocusMeteringAction.FLAG_AF
                        ).apply {
                            setAutoCancelDuration(2, TimeUnit.SECONDS)
                        }.build()
                    )
                } catch (_: CameraInfoUnavailableException) {
                }
            }
        }
        addView(previewView)
        detectSupport()
        initOptions()

        // TODO: Bind this to the view's onCreate method
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider?.unbindAll()
                cameraProvider = cameraProviderFuture.get()
                refreshCamera() // or just initPreview() ?
            } catch (e: Exception) {
                e.printStackTrace()
                listener?.onCameraError("Failed to get camera", e)
                isStarted = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    override var allowExifRotation: Boolean = true
    override var autoSquareCrop: Boolean = false
    override var autoFocus: Boolean = false
    override var saveToGallery: Boolean = false
    override var maxAudioBitRate: Int = -1
    override var maxVideoBitrate: Int = -1
    override var maxVideoFrameRate: Int = -1
    override var disableHEVC: Boolean = false


    override var detectorType: DetectorType = DetectorType.None
        @SuppressLint("UnsafeOptInUsageError")
        set(value) {
            field = value
            if (!isRecording) {
                if (imageAnalysis == null) {
                    setUpAnalysis()
                }
                if (value == DetectorType.None) {
                    if (cameraProvider?.isBound(imageAnalysis!!) == true) {
                        cameraProvider?.unbind(imageAnalysis!!)
                    }
                } else {
                    videoCapture?.let {
                        if (cameraProvider?.isBound(it) == true) {
                            cameraProvider?.unbind(it)
                        }
                    }

                    if (cameraProvider?.isBound(imageAnalysis!!) == false) {
                        camera = cameraProvider?.bindToLifecycle(
                            context as LifecycleOwner,
                            selectorFromPosition(),
                            imageAnalysis
                        )
                    }
                }
            }
        }

    override val numberOfCameras: Int
        get() {
            if (cameraManager == null) {
                cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager?
            }
            var count = 0
            try {
                count = cameraManager?.cameraIdList?.size ?: 0
            } catch (_: CameraAccessException) {
            }
            return count
        }

    private fun getFlashMode(): Int {
        return when (flashMode) {
            CameraFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            CameraFlashMode.ON -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    override var position: CameraPosition = CameraPosition.BACK

    private fun selectorFromPosition(): CameraSelector {
        return CameraSelector.Builder()
            .apply {
                if (position == CameraPosition.FRONT) {
                    requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                } else {
                    requireLensFacing(CameraSelector.LENS_FACING_BACK)
                }
            }
            .build()
    }

    /** Rotation specified by client (external code)
     * TODO: link this to the code, overriding or affecting targetRotation logic */
    override var rotation: CameraOrientation = CameraOrientation.UNKNOWN

    @SuppressLint("RestrictedApi", "UnsafeExperimentalUsageError")
    override fun orientationUpdated() {
        val rotation = when (currentOrientation) {
            270 -> Surface.ROTATION_270
            180 -> Surface.ROTATION_180
            90 -> Surface.ROTATION_90
            else -> Surface.ROTATION_0
        }
        imageCapture?.targetRotation = rotation
        videoCapture?.targetRotation = rotation
        imageAnalysis?.targetRotation = rotation
    }

    private fun getDeviceRotation(): Int {
        return when (this.rotation) {
            CameraOrientation.PORTRAIT_UPSIDE_DOWN -> Surface.ROTATION_270
            CameraOrientation.PORTRAIT -> Surface.ROTATION_90
            CameraOrientation.LANDSCAPE_LEFT -> Surface.ROTATION_0
            CameraOrientation.LANDSCAPE_RIGHT -> Surface.ROTATION_180
            else -> -1
        }
    }

    private fun safeUnbindAll() {
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        } finally {
            if (isStarted) {
                listener?.onCameraClose()
            }
            isStarted = false
        }
    }

    override var quality: Quality = Quality.MAX_480P
        set(value) {
            if (!isRecording && field != value) {
                field = value
                videoCapture?.let {
                    cameraProvider?.let {
                        var wasBound = false
                        if (it.isBound(videoCapture!!)) {
                            wasBound = true
                            it.unbind(imageCapture!!)
                        }

                        videoCapture = null
                        initVideoCapture()

                        if (wasBound) {
                            if (!it.isBound(videoCapture!!)) {
                                it.bindToLifecycle(
                                    context as LifecycleOwner,
                                    selectorFromPosition(),
                                    videoCapture!!
                                )
                            }
                        }
                    }
                }
            }
        }
    override var db: Double
        get() {
            return 0.0
        }
        set(value) {}
    override var amplitude: Double
        get() {
            return 0.0
        }
        set(value) {}
    override var amplitudeEMA: Double
        get() {
            return 0.0
        }
        set(value) {}
    override var isAudioLevelsEnabled: Boolean
        get() {
            return false
        }
        set(value) {}


    @SuppressLint("UnsafeOptInUsageError")
    private fun setUpAnalysis() {
        val builder = androidx.camera.core.ImageAnalysis.Builder()
            .apply {
                if (getDeviceRotation() > -1) {
                    setTargetRotation(getDeviceRotation())
                }
                setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
            }
        val extender = Camera2Interop.Extender(builder)
        imageAnalysis = builder.build()
        imageAnalysis?.setAnalyzer(imageAnalysisExecutor) {

            if (it.image != null && currentFrame != processEveryNthFrame) {
                incrementCurrentFrame()
                return@setAnalyzer
            }

            if (retrieveLatestImage) {
                latestImage = BitmapUtils.getBitmap(it)
            }

            if (it.image != null) {
                val tasks = mutableListOf<Task<*>>()
                //BarcodeScanning
                val barcodeTask = handleBarcodeScanning(it)
                if (barcodeTask != null) {
                    tasks.add(barcodeTask)
                }

                // FaceDetection
                val faceTask = handleFaceDetection(it)
                if (faceTask != null) {
                    tasks.add(faceTask)
                }

                //PoseDetection
                val poseTask = handlePoseDetection(it)
                if (poseTask != null) {
                    tasks.add(poseTask)
                }

                //ImageLabeling
                val imageTask = handleImageLabeling(it)
                if (imageTask != null) {
                    tasks.add(imageTask)
                }

                //ObjectDetection
                val objectTask = handleObjectDetection(it)
                if (objectTask != null) {
                    tasks.add(objectTask)
                }

                // TextRecognition
                val textTask = handleTextRecognition(it)
                if (textTask != null) {
                    tasks.add(textTask)
                }

                // SelfieSegmentation
                val selfieTask = handleSelfieSegmentation(it)
                if (selfieTask != null) {
                    tasks.add(selfieTask)
                }

                if (tasks.isNotEmpty()) {
                    val proxy = it
                    Tasks.whenAllComplete(tasks).addOnCompleteListener {
                        proxy.close()
                        resetCurrentFrame()
                    }
                }
            }
        }
    }

    private var cachedPictureRatioSizeMap: MutableMap<String, MutableList<Size>> = HashMap()
    private var cachedPreviewRatioSizeMap: MutableMap<String, MutableList<Size>> = HashMap()

    @SuppressLint("UnsafeOptInUsageError")
    private fun updateImageCapture(autoBound: Boolean = true) {
        var wasBounded = false
        if (imageCapture != null) {
            wasBounded = cameraProvider?.isBound(imageCapture!!) ?: false
            if (wasBounded) {
                cameraProvider?.unbind(imageCapture)
                imageCapture = null
            }
        }

        val builder = ImageCapture.Builder().apply {

            if (getDeviceRotation() > -1) {
                setTargetRotation(getDeviceRotation())
            }
            if (pictureSize == "0x0") {
                setTargetAspectRatio(
                    when (displayRatio) {
                        "16:9" -> AspectRatio.RATIO_16_9
                        else -> AspectRatio.RATIO_4_3
                    }
                )
            } else {
                try {
                    setTargetResolution(
                        android.util.Size.parseSize(pictureSize)
                    )
                } catch (e: Exception) {
                    setTargetAspectRatio(
                        when (displayRatio) {
                            "16:9" -> AspectRatio.RATIO_16_9
                            else -> AspectRatio.RATIO_4_3
                        }
                    )
                }
            }
            setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            setFlashMode(getFlashMode())
        }

        val extender = Camera2Interop.Extender(builder)

        when (whiteBalance) {
            WhiteBalance.Auto -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO
                )
            }
            WhiteBalance.Sunny -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
                )
            }
            WhiteBalance.Cloudy -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                )
            }
            WhiteBalance.Shadow -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_SHADE
                )
            }
            WhiteBalance.Twilight -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_TWILIGHT
                )
            }
            WhiteBalance.Fluorescent -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
                )
            }
            WhiteBalance.Incandescent -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
                )
            }
            WhiteBalance.WarmFluorescent -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE,
                    CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT
                )
            }
        }

        imageCapture = builder.build()

        if (wasBounded || autoBound) {
            cameraProvider?.let { cameraProvider ->
                if (cameraProvider.isBound(imageCapture!!)) {
                    cameraProvider.bindToLifecycle(
                        context as LifecycleOwner,
                        selectorFromPosition(),
                        imageCapture!!,
                        preview!!
                    )
                }
            }
        }
    }

    private fun initPreview() {
        preview = Preview.Builder()
            .apply {
                setTargetAspectRatio(
                    when (displayRatio) {
                        "16:9" -> AspectRatio.RATIO_16_9
                        else -> AspectRatio.RATIO_4_3
                    }
                )
            }
            .build()
            .also {
                it.setSurfaceProvider(this.previewView.surfaceProvider)
            }

        camera = if (detectorType != DetectorType.None && isMLSupported) {
            cameraProvider?.bindToLifecycle(
                context as LifecycleOwner,
                selectorFromPosition(),
                preview
            )
        } else {
            cameraProvider?.bindToLifecycle(
                context as LifecycleOwner,
                selectorFromPosition(),
                preview
            )
        }

        listener?.onReady()
    }

    private fun getRecorderQuality(quality: Quality): androidx.camera.video.Quality {
        return when (quality) {
            Quality.MAX_480P -> androidx.camera.video.Quality.SD
            Quality.MAX_720P -> androidx.camera.video.Quality.HD
            Quality.MAX_1080P -> androidx.camera.video.Quality.FHD
            Quality.MAX_2160P -> androidx.camera.video.Quality.UHD
            Quality.HIGHEST -> androidx.camera.video.Quality.HIGHEST
            Quality.LOWEST -> androidx.camera.video.Quality.LOWEST
            Quality.QVGA -> androidx.camera.video.Quality.LOWEST
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initVideoCapture() {
        if (pause) {
            return
        }
        if (hasCameraPermission() && hasAudioPermission()) {

            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        getRecorderQuality(quality),
                        FallbackStrategy.lowerQualityOrHigherThan(androidx.camera.video.Quality.SD)
                    )
                ).setExecutor(videoCaptureExecutor)
                .build()


            videoCapture = VideoCapture.withOutput(recorder).apply {
                if (getDeviceRotation() > -1) {
                    targetRotation = getDeviceRotation()
                }
            }
        }
    }

    @SuppressLint("RestrictedApi", "UnsafeOptInUsageError")
    private fun refreshCamera() {
        if (pause) {
            return
        }
        if (!hasCameraPermission()) return
        cachedPictureRatioSizeMap.clear()
        cachedPreviewRatioSizeMap.clear()

        videoCapture = null
        imageCapture = null
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        camera = null
        preview?.setSurfaceProvider(null)
        preview = null

        if (detectorType != DetectorType.None) {
            setUpAnalysis()
        }

        initPreview()

        initVideoCapture()

        handleZoom()

        camera?.cameraInfo?.let {
            val streamMap = Camera2CameraInfo.from(it)
                .getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            if (streamMap != null) {
                val sizes =
                    streamMap.getOutputSizes(ImageFormat.JPEG) +
                            streamMap.getOutputSizes(SurfaceTexture::class.java)
                for (size in sizes) {
                    val aspect = size.width.toFloat() / size.height.toFloat()
                    var key: String? = null
                    val value = Size(size.width, size.height)
                    when (aspect) {
                        1.0F -> key = "1:1"
                        in 1.2F..1.2222222F -> key = "6:5"
                        in 1.3F..1.3333334F -> key = "4:3"
                        in 1.77F..1.7777778F -> key = "16:9"
                        1.5F -> key = "3:2"
                    }

                    if (key != null) {
                        val list = cachedPictureRatioSizeMap[key]
                        list?.let {
                            list.add(value)
                        } ?: run {
                            cachedPictureRatioSizeMap[key] = mutableListOf(value)
                        }
                    }
                }
            }
        }

        updateImageCapture(true)

        if (flashMode == CameraFlashMode.TORCH && camera?.cameraInfo?.hasFlashUnit() == true) {
            camera?.cameraControl?.enableTorch(true)
        }

        isStarted = true
        resetCurrentFrame()
        listener?.onCameraOpen()
    }

    override fun startPreview() {
        if (!isStarted) {
            refreshCamera()
        }
    }

    override fun stopPreview() {
        if (isStarted) {
            safeUnbindAll()
        }
    }

    override var flashMode: CameraFlashMode = CameraFlashMode.OFF
        set(value) {
            field = value
            camera?.let {
                when (value) {
                    CameraFlashMode.OFF -> {
                        it.cameraControl.enableTorch(false)
                        imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
                    }
                    CameraFlashMode.ON, CameraFlashMode.RED_EYE -> imageCapture?.flashMode =
                        ImageCapture.FLASH_MODE_ON
                    CameraFlashMode.AUTO -> imageCapture?.flashMode = ImageCapture.FLASH_MODE_AUTO
                    CameraFlashMode.TORCH -> it.cameraControl.enableTorch(true)
                }
            }
        }

    @SuppressLint("RestrictedApi")
    override fun startRecording() {
        if (!hasAudioPermission() || !hasCameraPermission()) {
            return
        }
        deInitListener()
        val df = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        val today = Calendar.getInstance().time
        val fileName = "VID_" + df.format(today) + ".mp4"
        file = if (saveToGallery && hasStoragePermission()) {
            val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_DCIM)
            if (externalDir == null) {
                listener?.onCameraError(
                    "Cannot save video to gallery",
                    Exception("Failed to create uri")
                )
                return
            } else {
                if (!externalDir.exists()) {
                    externalDir.mkdirs()
                }
                File(externalDir, fileName)
            }

        } else {
            File(context.getExternalFilesDir(null), fileName)
        }

        try {
            if (videoCapture == null) {
                initVideoCapture()
            }
            cameraProvider?.let {
                if (it.isBound(imageCapture!!)) {
                    it.unbind(imageCapture!!)
                }

                if (!it.isBound(videoCapture!!)) {
                    it.bindToLifecycle(
                        context as LifecycleOwner,
                        selectorFromPosition(),
                        videoCapture!!
                    )
                }
            }

            val opts = FileOutputOptions.Builder(file!!).build()

            val pending = videoCapture?.output?.prepareRecording(
                context, opts
            )

            if (enableAudio) {
                pending?.withAudioEnabled()
            }

            recording = pending?.start(
                ContextCompat.getMainExecutor(context)
            ) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        if (flashMode == CameraFlashMode.ON) {
                            camera?.cameraControl?.enableTorch(true)
                        }
                        startDurationTimer()
                        listener?.onCameraVideoStart()
                    }
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        stopDurationTimer()

                        if (event.hasError()) {
                            file = null
                            val e = if (event.cause != null) {
                                Exception(event.cause)
                            } else {
                                Exception()
                            }
                            listener?.onCameraError("${event.error}", e)
                            if (isForceStopping) {
                                ContextCompat.getMainExecutor(context).execute {
                                    safeUnbindAll()
                                }

                                synchronized(mLock) {
                                    isForceStopping = false
                                }
                            }
                        } else {
                            if (isForceStopping) {
                                if (file != null) {
                                    file!!.delete()
                                }
                                ContextCompat.getMainExecutor(context).execute {
                                    safeUnbindAll()
                                }
                                synchronized(mLock) {
                                    isForceStopping = false
                                }
                            } else {
                                if (saveToGallery && hasStoragePermission()) {
                                    val values = ContentValues().apply {
                                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                        put(
                                            MediaStore.Video.Media.DATE_ADDED,
                                            System.currentTimeMillis()
                                        )
                                        // hardcoded video/avc
                                        put(MediaStore.MediaColumns.MIME_TYPE, "video/avc")
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { //this one
                                            put(
                                                MediaStore.MediaColumns.RELATIVE_PATH,
                                                Environment.DIRECTORY_DCIM
                                            )
                                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                                            put(
                                                MediaStore.Video.Media.DATE_TAKEN,
                                                System.currentTimeMillis()
                                            )
                                        }

                                    }

                                    val uri = context.contentResolver.insert(
                                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                        values
                                    )
                                    if (uri == null) {
                                        listener?.onCameraError(
                                            "Failed to add video to gallery",
                                            Exception("Failed to create uri")
                                        )
                                    } else {
                                        val fos = context.contentResolver.openOutputStream(uri)
                                        val fis = FileInputStream(file!!)
                                        fos.use {
                                            if (it != null) {
                                                fis.copyTo(it)
                                                it.flush()
                                                it.close()
                                                fis.close()
                                            }
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { //this one
                                            values.clear();
                                            values.put(MediaStore.Video.Media.IS_PENDING, 0);
                                            context.contentResolver.update(uri, values, null, null);
                                        }
                                        listener?.onCameraVideo(file)
                                    }

                                } else {
                                    listener?.onCameraVideo(file)
                                }
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            isRecording = false
            stopDurationTimer()
            if (file != null) {
                file!!.delete()
            }
            cameraProvider?.let {
                if (it.isBound(videoCapture!!)) {
                    it.unbind(videoCapture!!)
                }
                if (it.isBound(imageCapture!!)) {
                    it.unbind(imageCapture!!)
                }
            }
            isForceStopping = false
            listener?.onCameraError("Failed to record video.", e)
        }
    }

    @SuppressLint("RestrictedApi")
    override fun stopRecording() {
        if (flashMode == CameraFlashMode.ON) {
            camera?.cameraControl?.enableTorch(false)
        }
        recording?.stop()
    }

    override fun takePhoto() {
        val df = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        val today = Calendar.getInstance().time
        val fileName = "PIC_" + df.format(today) + ".jpg"
        file = if (saveToGallery && hasStoragePermission()) {
            val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_DCIM)
            if (externalDir == null) {
                listener?.onCameraError(
                    "Cannot save photo to gallery storage",
                    Exception("Failed to get external directory")
                )
                return
            } else {
                if (!externalDir.exists()) {
                    externalDir.mkdirs()
                }
                File(externalDir, fileName)
            }

        } else {
            File(context.getExternalFilesDir(null), fileName)
        }

        cameraProvider?.let { provider ->
            videoCapture?.let { if (provider.isBound(it)) provider.unbind(it) }

            if (imageCapture == null) {
                updateImageCapture(true)
            }
            imageCapture?.let { capture ->
                if (!provider.isBound(capture)) {
                    provider.bindToLifecycle(
                        context as LifecycleOwner,
                        selectorFromPosition(),
                        capture,
                        preview
                    )
                }
            } ?: run {
                listener?.onCameraError("Cannot take photo", Exception("imageCapture not set"))
                return
            }
        } ?: run {
            listener?.onCameraError("Cannot take photo", Exception("cameraProvider not set"))
            return
        }

        val useImageProxy = autoSquareCrop || !allowExifRotation
        if (useImageProxy) {
            imageCapture?.takePicture(
                imageCaptureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        processImageProxy(image, fileName)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        listener?.onCameraError("Failed to take photo image", exception)
                    }
                })
        } else {
            val meta = ImageCapture.Metadata().apply {
                isReversedHorizontal = position == CameraPosition.FRONT
            }
            val options = ImageCapture.OutputFileOptions.Builder(file!!)
            options.setMetadata(meta)
            imageCapture?.takePicture(
                options.build(),
                imageCaptureExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        processImageFile(fileName) // outputFileResults.savedUri.toString() is null
                    }

                    override fun onError(exception: ImageCaptureException) {
                        listener?.onCameraError("Failed to take photo image", exception)
                    }
                })
        }
    }

    private fun processImageProxy(image: ImageProxy, fileName: String) {
        var isError = false
        var outputStream: FileOutputStream? = null
        try {
            val meta = ImageCapture.Metadata().apply {
                isReversedHorizontal = position == CameraPosition.FRONT
            }

            val buffer = image.planes.first().buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val matrix = Matrix()

            // Registering image's required rotation, provided by Androidx ImageAnalysis
            val imageTargetRotation = image.imageInfo.rotationDegrees
            matrix.postRotate(imageTargetRotation.toFloat())

            // Flipping over the image in case it is the front camera
            if (position == CameraPosition.FRONT)
                matrix.postScale(-1f, 1f)

            var originalWidth = bm.width
            var originalHeight = bm.height
            var offsetWidth = 0
            var offsetHeight = 0
            if (autoSquareCrop) {
                if (originalWidth < originalHeight) {
                    offsetHeight = (originalHeight - originalWidth) / 2;
                    originalHeight = originalWidth;
                } else {
                    offsetWidth = (originalWidth - originalHeight) / 2;
                    originalWidth = originalHeight;
                }
            }
            val rotated = Bitmap.createBitmap(
                bm,
                offsetWidth,
                offsetHeight,
                originalWidth,
                originalHeight,
                matrix,
                false
            )
            outputStream = FileOutputStream(file!!, false)
            var override: Bitmap? = null
            if (overridePhotoHeight > 0 && overridePhotoWidth > 0) {
                override = Bitmap.createScaledBitmap(
                    rotated,
                    overridePhotoWidth,
                    overridePhotoHeight,
                    false
                )
                override.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
            } else {
                rotated.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
            }


            val exif = ExifInterface(file!!.absolutePath)

            val now = System.currentTimeMillis()
            val datetime = convertToExifDateTime(now)

            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, datetime)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, datetime)

            try {
                val subsec = (now - convertFromExifDateTime(datetime).time).toString()
                exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, subsec)
                exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, subsec)
            } catch (_: ParseException) {
            }

            exif.rotate(image.imageInfo.rotationDegrees)
            if (meta.isReversedHorizontal) {
                exif.flipHorizontally()
            }
            if (meta.isReversedVertical) {
                exif.flipVertically()
            }
            if (meta.location != null) {
                exif.setGpsInfo(meta.location!!)
            }
            exif.saveAttributes()

            bm.recycle()
            rotated.recycle()
            override?.recycle()
        } catch (e: Exception) {
            isError = true
            listener?.onCameraError("Failed to save photo.", e)
        } finally {
            try {
                outputStream?.close()
            } catch (e: IOException) {
                //NOOP
            }
            try {
                image.close()
            } catch (_: Exception) {

            }
            if (!isError) {
                if (saveToGallery && hasStoragePermission()) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis())
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/*")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { //this one
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                        }
                    }

                    val uri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                    )
                    if (uri == null) {
                        listener?.onCameraError(
                            "Failed to add photo to gallery",
                            Exception("Failed to create uri")
                        )
                    } else {
                        val fos = context.contentResolver.openOutputStream(uri)
                        val fis = FileInputStream(file!!)
                        fos.use {
                            if (it != null) {
                                fis.copyTo(it)
                                it.flush()
                                it.close()
                                fis.close()
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { //this one
                            values.clear();
                            values.put(MediaStore.Images.Media.IS_PENDING, 0);
                            context.contentResolver.update(uri, values, null, null);
                        }
                        listener?.onCameraPhoto(file)
                    }

                } else {
                    listener?.onCameraPhoto(file)
                }

            }
        }
    }

    private fun processImageFile(fileName: String) {
        // Saving image to user gallery
        if (saveToGallery && hasStoragePermission()) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis())

                put(MediaStore.MediaColumns.MIME_TYPE, "image/*")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { //this one
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                    put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                }
            }

            val uri =
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                listener?.onCameraError(
                    "Failed to add photo to gallery",
                    Exception("Failed to create uri")
                )
            } else {
                val fos = context.contentResolver.openOutputStream(uri)
                val fis = FileInputStream(file!!)
                fos.use {
                    if (it != null) {
                        fis.copyTo(it)
                        it.flush()
                        it.close()
                        fis.close()
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { //this one
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
                listener?.onCameraPhoto(file)
            }

        } else {
            listener?.onCameraPhoto(file)
        }
    }

    override fun hasFlash(): Boolean {
        return camera?.cameraInfo?.hasFlashUnit() ?: false
    }

    override fun cameraRecording(): Boolean {
        return isRecording
    }

    override fun toggleCamera() {
        if (!isRecording) {
            position = when (position) {
                CameraPosition.BACK -> CameraPosition.FRONT
                CameraPosition.FRONT -> CameraPosition.BACK
            }
            safeUnbindAll()
            refreshCamera()
        }
    }

    override fun getSupportedRatios(): Array<String> {
        return cachedPreviewRatioSizeMap.keys.toTypedArray()
    }

    override fun getAvailablePictureSizes(ratio: String): Array<Size> {
        return cachedPictureRatioSizeMap[ratio]?.toTypedArray() ?: arrayOf()
    }

    override fun stop() {
        if (!isForceStopping) {
            if (isRecording) {
                isForceStopping = true
                stopRecording()
            } else {
                safeUnbindAll()
            }
        }
    }


    override fun release() {
        if (!isForceStopping) {
            if (isRecording) {
                isForceStopping = true
                stopRecording()
            }

            safeUnbindAll()

            preview?.setSurfaceProvider(null)
            preview = null
            imageCapture = null
            videoCapture = null
            imageAnalysis = null
            camera = null
        }
        deInitListener()
    }
}