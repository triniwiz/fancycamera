package io.github.triniwiz.fancycamera

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.hardware.Camera
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Log
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.TextureView
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import java.io.*
import java.nio.ByteBuffer
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

@Suppress("DEPRECATION")
class Camera @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : CameraBase(context, attrs, defStyleAttr) {
    var lock: Any = Any()
    var camera: Camera? = null
    private var file: File? = null
    var isRecording = false
    var isForceStopping = false
    var isStarted = false
    private fun handleZoom() {
        synchronized(lock) {
            if (camera != null) {
                val params = camera!!.parameters
                params.zoom = (zoom * 100).toInt()
            }
        }
    }

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
    override var zoomRatio: Float = 1.0f

    override fun orientationUpdated() {
        camera?.let {
            updateCameraDisplayOrientation(context as Activity, position.value, it)
        }
    }

    override var whiteBalance: WhiteBalance = WhiteBalance.Auto
        set(value) {
            if (!isRecording || isTakingPhoto) {
                field = value
                refreshCamera()
            }
        }
    private var cameraExecutor = Executors.newSingleThreadExecutor()
    private var imageAnalysisExecutor = Executors.newSingleThreadExecutor()
    override var displayRatio = "4:3"
        set(value) {
            if (cachedPreviewRatioSizeMap.containsKey(value)) {
                field = value
                if (!isTakingPhoto || !isRecording) {
                    refreshCamera()
                }
            }
        }
    override var pictureSize: String = "0x0"
        get() {
            if (field == "0x0") {
                val size = cachedPictureRatioSizeMap[displayRatio]?.get(0)
                if (size != null) {
                    return "${size.width}x${size.height}"
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

    private fun handlePinchZoom() {
        if (!enablePinchZoom) {
            return
        }
        val listener: ScaleGestureDetector.SimpleOnScaleGestureListener =
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    camera?.parameters?.let { parameters ->
                        if (parameters.isZoomSupported) {
                            var currentZoom =
                                (parameters.zoom + parameters.maxZoom * (detector.scaleFactor - 1)).toInt()
                            currentZoom = min(currentZoom, parameters.maxZoom)
                            currentZoom = max(0, currentZoom)
                            parameters.zoom = currentZoom
                            camera?.parameters = parameters

                        }
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

    private var scaleGestureDetector: ScaleGestureDetector? = null
    override var enablePinchZoom: Boolean = true
        set(value) {
            field = value
            if (value) {
                handlePinchZoom()
            } else {
                scaleGestureDetector = null
            }
        }
    override var enableTapToFocus: Boolean = false

    private var previewView: TextureView = TextureView(context, attrs, defStyleAttr)

    private fun handleBarcodeScanning(data: ByteArray, camera: Camera): Task<Boolean>? {
        if (!isBarcodeScanningSupported || !(detectorType == DetectorType.Barcode || detectorType == DetectorType.All)) {
            return null
        }
        val BarcodeScannerClazz =
            Class.forName("io.github.triniwiz.fancycamera.barcodescanning.BarcodeScanner")
        val barcodeScanner = BarcodeScannerClazz.newInstance()
        val BarcodeScannerOptionsClazz =
            Class.forName("io.github.triniwiz.fancycamera.barcodescanning.BarcodeScanner\$Options")

        val processImageMethod = BarcodeScannerClazz.getDeclaredMethod(
            "processBytes",
            ByteArray::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            BarcodeScannerOptionsClazz
        )
        val previewSize = camera.parameters.previewSize
        val returnTask = TaskCompletionSource<Boolean>()

        if (barcodeScannerOptions == null) {
            barcodeScannerOptions = BarcodeScannerOptionsClazz.newInstance()
        }

        val task = processImageMethod.invoke(
            barcodeScanner,
            data,
            previewSize.width,
            previewSize.height,
            rotationAngle,
            ImageFormat.NV21,
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

    private fun handleFaceDetection(data: ByteArray, camera: Camera): Task<Boolean>? {
        if (!isFaceDetectionSupported || !(detectorType == DetectorType.Face || detectorType == DetectorType.All)) {
            return null
        }
        val FaceDetectionClazz =
            Class.forName("io.github.triniwiz.fancycamera.facedetection.FaceDetection")
        val faceDetection = FaceDetectionClazz.newInstance()
        val FaceDetectionOptionsClazz =
            Class.forName("io.github.triniwiz.fancycamera.facedetection.FaceDetection\$Options")
        val processBytesMethod = FaceDetectionClazz.getDeclaredMethod(
            "processBytes",
            ByteArray::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            FaceDetectionOptionsClazz
        )

        if (faceDetectionOptions == null) {
            faceDetectionOptions = FaceDetectionOptionsClazz.newInstance()
        }

        val previewSize = camera.parameters.previewSize
        val returnTask = TaskCompletionSource<Boolean>()
        val task = processBytesMethod.invoke(
            faceDetection,
            data,
            previewSize.width,
            previewSize.height,
            rotationAngle,
            ImageFormat.NV21,
            faceDetectionOptions!!
        ) as Task<String>
        task.addOnSuccessListener(imageAnalysisExecutor) {
            if (it.isNotEmpty()) {
                onFacesDetectedListener?.onSuccess(it)
            }
        }.addOnFailureListener(imageAnalysisExecutor) {
            onFacesDetectedListener?.onError(
                it.message
                    ?: "Failed to complete face detection.", it
            )
        }.addOnCompleteListener {
            returnTask.setResult(true)
        }
        return returnTask.task
    }

    private fun handleImageLabeling(data: ByteArray, camera: Camera): Task<Boolean>? {
        if (!isImageLabelingSupported || !(detectorType == DetectorType.Image || detectorType == DetectorType.All)) {
            return null
        }
        val ImageLabelingClazz =
            Class.forName("io.github.triniwiz.fancycamera.imagelabeling.ImageLabeling")
        val imageLabeling = ImageLabelingClazz.newInstance()
        val ImageLabelingOptionsClazz =
            Class.forName("io.github.triniwiz.fancycamera.imagelabeling.ImageLabeling\$Options")
        val processBytesMethod = ImageLabelingClazz.getDeclaredMethod(
            "processBytes",
            ByteArray::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            ImageLabelingOptionsClazz
        )
        if (imageLabelingOptions == null) {
            imageLabelingOptions = ImageLabelingOptionsClazz.newInstance()
        }

        val previewSize = camera.parameters.previewSize
        val returnTask = TaskCompletionSource<Boolean>()
        val task = processBytesMethod.invoke(
            imageLabeling,
            data,
            previewSize.width,
            previewSize.height,
            rotationAngle,
            ImageFormat.NV21,
            imageLabelingOptions!!
        ) as Task<String>
        task.addOnSuccessListener(imageAnalysisExecutor) {
            if (it.isNotEmpty()) {
                onImageLabelingListener?.onSuccess(it)
            }
        }.addOnFailureListener(imageAnalysisExecutor) {
            onImageLabelingListener?.onError(
                it.message
                    ?: "Failed to complete face detection.", it
            )
        }.addOnCompleteListener {
            returnTask.setResult(true)
        }
        return returnTask.task
    }

    private fun handleObjectDetection(data: ByteArray, camera: Camera): Task<Boolean>? {
        if (!isObjectDetectionSupported || !(detectorType == DetectorType.Object || detectorType == DetectorType.All)) {
            return null
        }
        val ObjectDetectionClazz =
            Class.forName("io.github.triniwiz.fancycamera.objectdetection.ObjectDetection")
        val objectDetection = ObjectDetectionClazz.newInstance()
        val ObjectDetectionOptionsClazz =
            Class.forName("io.github.triniwiz.fancycamera.objectdetection.ObjectDetection\$Options")
        val processBytesMethod = ObjectDetectionClazz.getDeclaredMethod(
            "processBytes",
            ByteArray::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            ObjectDetectionOptionsClazz
        )

        if (objectDetectionOptions == null) {
            objectDetectionOptions = ObjectDetectionOptionsClazz.newInstance()
        }

        val previewSize = camera.parameters.previewSize
        val returnTask = TaskCompletionSource<Boolean>()
        val task = processBytesMethod.invoke(
            objectDetection,
            data,
            previewSize.width,
            previewSize.height,
            rotationAngle,
            ImageFormat.NV21,
            objectDetectionOptions!!
        ) as Task<String>
        task.addOnSuccessListener(imageAnalysisExecutor) {
            if (it.isNotEmpty()) {
                onObjectDetectedListener?.onSuccess(it)
            }
        }.addOnFailureListener(imageAnalysisExecutor) {
            onObjectDetectedListener?.onError(
                it.message
                    ?: "Failed to complete face detection.", it
            )
        }.addOnCompleteListener {
            returnTask.setResult(true)
        }
        return returnTask.task
    }

    private fun handlePoseDetection(data: ByteArray, camera: Camera): Task<Boolean>? {
        if (!isFaceDetectionSupported || !(detectorType == DetectorType.Face || detectorType == DetectorType.All)) {
            return null
        }
        val PoseDetectionClazz =
            Class.forName("io.github.triniwiz.fancycamera.posedetection.PoseDetection")
        val poseDetection = PoseDetectionClazz.newInstance()
        val processBytesMethod = PoseDetectionClazz.getDeclaredMethod(
            "processBytes",
            ByteArray::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java
        )
        val previewSize = camera.parameters.previewSize
        val returnTask = TaskCompletionSource<Boolean>()
        val task = processBytesMethod.invoke(
            poseDetection,
            data,
            previewSize.width,
            previewSize.height,
            rotationAngle,
            ImageFormat.NV21
        ) as Task<String>
        task.addOnSuccessListener(imageAnalysisExecutor) {
            if (it.isNotEmpty()) {
                onPoseDetectedListener?.onSuccess(it)
            }
        }.addOnFailureListener(imageAnalysisExecutor) {
            onPoseDetectedListener?.onError(
                it.message
                    ?: "Failed to complete face detection.", it
            )
        }.addOnCompleteListener {
            returnTask.setResult(true)
        }
        return returnTask.task
    }

    private fun handleTextRecognition(data: ByteArray, camera: Camera): Task<Boolean>? {
        if (!isTextRecognitionSupported || !(detectorType == DetectorType.Text || detectorType == DetectorType.All)) {
            return null
        }
        val TextRecognitionClazz =
            Class.forName("io.github.triniwiz.fancycamera.textrecognition.TextRecognition")
        val textRecognition = TextRecognitionClazz.newInstance()
        val processBytesMethod = TextRecognitionClazz.getDeclaredMethod(
            "processBytes",
            ByteArray::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java
        )
        val previewSize = camera.parameters.previewSize
        val returnTask = TaskCompletionSource<Boolean>()
        val task = processBytesMethod.invoke(
            textRecognition,
            data,
            previewSize.width,
            previewSize.height,
            rotationAngle,
            ImageFormat.NV21
        ) as Task<String>
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
        }.addOnCompleteListener {
            returnTask.setResult(true)
        }
        return returnTask.task
    }

    private fun handleSelfieSegmentation(data: ByteArray, camera: Camera): Task<Boolean>? {
        if (!isSelfieSegmentationSupported || !(detectorType == DetectorType.Selfie || detectorType == DetectorType.All)) {
            return null
        }
        val SelfieSegmentationClazz =
            Class.forName("io.github.triniwiz.fancycamera.selfiesegmentation.SelfieSegmentation")
        val selfieSegmentation = SelfieSegmentationClazz.newInstance()
        val SelfieSegmentationOptionsClazz =
            Class.forName("io.github.triniwiz.fancycamera.selfiesegmentation.SelfieSegmentation\$Options")
        val processBytesMethod = SelfieSegmentationClazz.getDeclaredMethod(
            "processBytes",
            ByteArray::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            SelfieSegmentationOptionsClazz
        )

        if (selfieSegmentationOptions == null) {
            selfieSegmentationOptions = SelfieSegmentationOptionsClazz.newInstance()
        }

        val previewSize = camera.parameters.previewSize
        val returnTask = TaskCompletionSource<Boolean>()
        val task = processBytesMethod.invoke(
            selfieSegmentation,
            data,
            previewSize.width,
            previewSize.height,
            rotationAngle,
            ImageFormat.NV21,
            selfieSegmentationOptions!!
        ) as Task<Any?>
        task.addOnSuccessListener(imageAnalysisExecutor) {
            if (it != null) {
                onImageLabelingListener?.onSuccess(it)
            }
        }.addOnFailureListener(imageAnalysisExecutor) {
            onImageLabelingListener?.onError(
                it.message
                    ?: "Failed to complete face detection.", it
            )
        }.addOnCompleteListener {
            returnTask.setResult(true)
        }
        return returnTask.task
    }

    init {
        handlePinchZoom()
        addView(previewView)
        detectSupport()
        previewView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                cameraExecutor.execute {
                    initCamera()
                    mainHandler.post {
                        listener?.onReady()
                    }
                }
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {

            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                stop()
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                onSurfaceUpdateListener?.onUpdate()
            }
        }
    }

    override var flashMode: CameraFlashMode = CameraFlashMode.OFF
        set(value) {
            synchronized(lock) {
                when (value) {
                    CameraFlashMode.OFF -> camera?.parameters?.flashMode =
                        Camera.Parameters.FLASH_MODE_OFF
                    CameraFlashMode.ON -> camera?.parameters?.flashMode =
                        Camera.Parameters.FLASH_MODE_ON
                    CameraFlashMode.AUTO -> camera?.parameters?.flashMode =
                        Camera.Parameters.FLASH_MODE_AUTO
                    CameraFlashMode.RED_EYE -> camera?.parameters?.flashMode =
                        Camera.Parameters.FLASH_MODE_RED_EYE
                    CameraFlashMode.TORCH -> camera?.parameters?.flashMode =
                        Camera.Parameters.FLASH_MODE_TORCH
                }
                field = value
            }
        }

    override var detectorType: DetectorType = DetectorType.None

    override var allowExifRotation: Boolean = true // TODO( Implement )

    override var autoSquareCrop: Boolean = false

    override var autoFocus: Boolean = false

    override val previewSurface: Any
        get() = previewView

    override var position: CameraPosition = CameraPosition.BACK
        set(value) {
            if (isRecording || isTakingPhoto) {
                return
            }
            field = value
            cameraExecutor.execute {
                synchronized(lock) {
                    if (!isRecording || isTakingPhoto) {
                        camera?.setPreviewCallback(null)
                        camera?.release()
                        camera = null
                        cachedPictureRatioSizeMap.clear()
                        cachedPreviewRatioSizeMap.clear()
                        initCamera()
                    }
                }
            }
        }

    override var rotation: CameraOrientation = CameraOrientation.UNKNOWN

    override fun hasFlash(): Boolean {
        return synchronized(lock) {
            camera?.parameters?.supportedFlashModes?.contains(Camera.Parameters.FLASH_MODE_ON)
                ?: false
        }
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

    private fun getFlashMode(): String {
        return when (flashMode) {
            CameraFlashMode.AUTO -> Camera.Parameters.FLASH_MODE_AUTO
            CameraFlashMode.ON -> Camera.Parameters.FLASH_MODE_ON
            CameraFlashMode.RED_EYE -> Camera.Parameters.FLASH_MODE_RED_EYE
            CameraFlashMode.TORCH -> Camera.Parameters.FLASH_MODE_TORCH
            else -> Camera.Parameters.FLASH_MODE_OFF
        }
    }

    private var cachedPictureRatioSizeMap: MutableMap<String, MutableList<Size>> = HashMap()
    private var cachedPreviewRatioSizeMap: MutableMap<String, MutableList<Size>> = HashMap()
    private var realCameraPosition = 0
    private var previewBuffer: ByteBuffer? = null

    private fun createPreviewBuffer(size: Size): ByteArray {
        val bpp = size.width * size.height * ImageFormat.getBitsPerPixel(ImageFormat.NV21)
        val length = ceil(bpp.toDouble() / 8) + 1
        return ByteArray(length.toInt())
    }

    private fun updateAutoFocus() {
        synchronized(lock) {
            val parameters = camera?.parameters
            if (autoFocus) {
                val supportedFocusModes = parameters?.supportedFocusModes
                if (supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) == true) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                } else if (supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_AUTO) == true) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                }
            } else {
                val supportedFocusModes = parameters?.supportedFocusModes
                if (supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_FIXED) == true) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_FIXED
                }
            }
            camera?.parameters = parameters
        }
    }

    private fun initCamera() {
        if (previewView.surfaceTexture == null) {
            return
        }
        val cameraInfo = Camera.CameraInfo()
        val camerasCount = Camera.getNumberOfCameras()
        var cameraPosition = Camera.CameraInfo.CAMERA_FACING_BACK
        for (i in 0 until camerasCount) {
            Camera.getCameraInfo(i, cameraInfo)
            if (position == CameraPosition.FRONT && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                cameraPosition = i
                break
            }
        }
        realCameraPosition = cameraPosition
        try {
            camera = Camera.open(cameraPosition)
            for (size in camera?.parameters?.supportedPictureSizes ?: arrayListOf()) {
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
                    val list = cachedPictureRatioSizeMap.get(key)
                    if (list == null) {
                        cachedPictureRatioSizeMap.put(key, mutableListOf(value))
                    } else {
                        list.add(value)
                    }
                }
            }
            for (size in camera?.parameters?.supportedPreviewSizes ?: arrayListOf()) {
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
                    val list = cachedPreviewRatioSizeMap.get(key)
                    if (list == null) {
                        cachedPreviewRatioSizeMap.put(key, mutableListOf(value))
                    } else {
                        list.add(value)
                    }
                }

            }
            updateAutoFocus()
            val parameters = camera?.parameters
            parameters?.flashMode = getFlashMode()
            camera?.setPreviewTexture(previewView.surfaceTexture)
            if (parameters?.isAutoWhiteBalanceLockSupported == true && parameters.supportedWhiteBalance != null) {
                val supportedWhiteBalance = parameters.supportedWhiteBalance
                when (whiteBalance) {
                    WhiteBalance.Auto -> {
                        parameters.whiteBalance = WhiteBalance.Auto.value
                    }
                    WhiteBalance.Sunny -> {
                        if (supportedWhiteBalance.contains(WhiteBalance.Sunny.value)) {
                            parameters.whiteBalance = WhiteBalance.Sunny.value
                        }
                    }
                    WhiteBalance.Cloudy -> {
                        if (supportedWhiteBalance.contains(WhiteBalance.Cloudy.value)) {
                            parameters.whiteBalance = WhiteBalance.Cloudy.value
                        }
                    }
                    WhiteBalance.Shadow -> {
                        if (supportedWhiteBalance.contains(WhiteBalance.Shadow.value)) {
                            parameters.whiteBalance = WhiteBalance.Shadow.value
                        }
                    }
                    WhiteBalance.Twilight -> {
                        if (supportedWhiteBalance.contains(WhiteBalance.Twilight.value)) {
                            parameters.whiteBalance = WhiteBalance.Twilight.value
                        }
                    }
                    WhiteBalance.Fluorescent -> {
                        if (supportedWhiteBalance.contains(WhiteBalance.Fluorescent.value)) {
                            parameters.whiteBalance = WhiteBalance.Fluorescent.value
                        }
                    }
                    WhiteBalance.Incandescent -> {
                        if (supportedWhiteBalance.contains(WhiteBalance.Incandescent.value)) {
                            parameters.whiteBalance = WhiteBalance.Incandescent.value
                        }
                    }
                    WhiteBalance.WarmFluorescent -> {
                        if (supportedWhiteBalance.contains(WhiteBalance.WarmFluorescent.value)) {
                            parameters.whiteBalance = WhiteBalance.WarmFluorescent.value
                        }
                    }
                }
            }
            val pictureSize = stringSizeToSize(pictureSize)
            val previewSize = cachedPreviewRatioSizeMap[displayRatio]?.get(0) ?: Size(0, 0)
            if (isMLSupported) {
                parameters?.setPreviewSize(previewSize.width, previewSize.height)
                parameters?.setPictureSize(pictureSize.width, pictureSize.height)
                parameters?.previewFormat = ImageFormat.NV21
                previewBuffer = ByteBuffer.wrap(createPreviewBuffer(previewSize))
                camera?.parameters = parameters
                camera?.addCallbackBuffer(previewBuffer!!.array())
                camera?.setPreviewCallbackWithBuffer { data, camera ->
                    if (data != null) {
                        if (currentFrame != processEveryNthFrame) {
                            incrementCurrentFrame()
                            return@setPreviewCallbackWithBuffer
                        }

                        if (retrieveLatestImage) {
                            latestImage = BitmapUtils.getBitmap(
                                data, FrameMetadata
                                    .Builder()
                                    .setWidth(camera.parameters.previewSize.width)
                                    .setHeight(camera.parameters.previewSize.height)
                                    .build()
                            )
                        }

                        Log.d("com.test", "setPreviewCallbackWithBuffer : ${data.size}")


                        val tasks = mutableListOf<Task<*>>()
                        //BarcodeScanner
                        val barcodeTask = handleBarcodeScanning(data, camera)
                        if (barcodeTask != null) {
                            tasks.add(barcodeTask)
                        }
                        // FaceDetection
                        val faceTask = handleFaceDetection(data, camera)
                        if (faceTask != null) {
                            tasks.add(faceTask)
                        }

                        //ImageLabeling
                        val imageTask = handleImageLabeling(data, camera)
                        if (imageTask != null) {
                            tasks.add(imageTask)
                        }

                        //ObjectDetection
                        val objectTask = handleObjectDetection(data, camera)
                        if (objectTask != null) {
                            tasks.add(objectTask)
                        }

                        //PoseDetection
                        val poseTask = handlePoseDetection(data, camera)
                        if (poseTask != null) {
                            tasks.add(poseTask)
                        }

                        // TextRecognition
                        val textTask = handleTextRecognition(data, camera)
                        if (textTask != null) {
                            tasks.add(textTask)
                        }

                        // SelfieSegmentation
                        val selfieTask = handleSelfieSegmentation(data, camera)
                        if (selfieTask != null) {
                            tasks.add(selfieTask)
                        }

                        if (tasks.isNotEmpty()) {
                            Tasks.whenAllComplete(tasks).addOnCompleteListener {
                                try {
                                    val size = camera.parameters.previewSize
                                    camera.addCallbackBuffer(
                                        createPreviewBuffer(
                                            Size(
                                                size.width,
                                                size.height
                                            )
                                        )
                                    )
                                } catch (e: java.lang.Exception) {
                                } finally {
                                    resetCurrentFrame()
                                }
                            }
                        }
                    }
                }
            }
            updateCameraDisplayOrientation(context as Activity, position.value, camera)
            camera?.startPreview()
            resetCurrentFrame()
            listener?.onCameraOpen()
        } catch (error: RuntimeException) {
            error.printStackTrace()
            listener?.onCameraError(error.message ?: "Camera failed to open.", error)
        }
    }

    private fun startPreviewInternal() {
        if (pause) {
            return
        }
        if (camera != null) {
            if (flashMode == CameraFlashMode.ON) {
                camera?.parameters?.flashMode = Camera.Parameters.FLASH_MODE_TORCH
            }
            camera?.startPreview()
        } else {
            initCamera()
        }
    }

    override fun startPreview() {
        cameraExecutor.execute {
            synchronized(lock) {
                startPreviewInternal()
            }
        }
    }

    private fun stopPreviewInternal() {
        camera?.stopPreview()
        listener?.onCameraClose()
    }

    private fun refreshCamera() {
        cameraExecutor.execute {
            synchronized(lock) {
                stopPreviewInternal()
                startPreviewInternal()
            }
        }
    }

    override fun stopPreview() {
        cameraExecutor.execute {
            synchronized(lock) {
                stopPreviewInternal()
            }
        }
    }

    private var isTakingPhoto = false

    override fun takePhoto() {
        synchronized(lock) {
            if (isRecording || isTakingPhoto) {
                return
            }
            val df = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            val today = Calendar.getInstance().time
            val fileName = "PIC_" + df.format(today) + ".jpg"
            val tmpFileName = "TMP_PIC_" + df.format(today) + ".jpg"
            val tmpFile = File(context.getExternalFilesDir(null), tmpFileName)
            file = if (saveToGallery && hasStoragePermission()) {
                val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_DCIM)
                if (externalDir == null) {
                    listener?.onCameraError(
                        "Cannot save photo to gallery storage",
                        java.lang.Exception("Failed to get external directory")
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

            isTakingPhoto = true
            camera?.takePicture(null, null) { data, camera ->
                cameraExecutor.execute {
                    var fos: FileOutputStream? = null
                    try {
                        if (autoSquareCrop) {
                            val tmpFos = FileOutputStream(tmpFile)
                            tmpFos.write(data)
                            fos = FileOutputStream(file)
                            var bm = BitmapFactory.decodeFile(tmpFile.absolutePath)
                            var originalWidth = bm.width
                            var originalHeight = bm.height
                            var offsetWidth = 0
                            var offsetHeight = 0

                            if (originalWidth < originalHeight) {
                                offsetHeight = (originalHeight - originalWidth) / 2;
                                originalHeight = originalWidth
                            } else {
                                offsetWidth = (originalWidth - originalHeight) / 2;
                                originalWidth = originalHeight
                            }


                            val matrix = Matrix()
                            val tmpExif = ExifInterface(tmpFile)
                            var orientation = tmpExif.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_UNDEFINED
                            )

                            val info = Camera.CameraInfo()
                            Camera.getCameraInfo(realCameraPosition, info)

                            if (realCameraPosition == 1) {
                                when (orientation) {
                                    1 -> {
                                        orientation = 2
                                    }
                                    3 -> {
                                        orientation = 4
                                    }
                                    6 -> {
                                        orientation = 7
                                    }
                                }
                            }
                            when (orientation) {
                                1 -> {
                                }
                                2 -> matrix.postScale(-1f, 1f)
                                3 -> matrix.postRotate(180f)
                                4 -> {
                                    matrix.postRotate(180f)
                                    matrix.postScale(-1f, 1f)
                                }
                                5 -> {
                                    matrix.postRotate(90f)
                                    matrix.postScale(-1f, 1f)
                                }
                                6 -> matrix.postRotate(90f)
                                7 -> {
                                    matrix.postRotate(270f)
                                    matrix.postScale(-1f, 1f)
                                }
                                8 -> matrix.postRotate(270f)
                            }

                            bm = Bitmap.createBitmap(
                                bm,
                                offsetWidth,
                                offsetHeight,
                                originalWidth,
                                originalHeight,
                                matrix,
                                false
                            )


                            var override: Bitmap? = null
                            if (overridePhotoHeight > 0 && overridePhotoWidth > 0) {
                                override = Bitmap.createScaledBitmap(
                                    bm,
                                    overridePhotoWidth,
                                    originalHeight,
                                    false
                                )
                                override.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                            } else {
                                bm.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                            }

                            fos.close()
                            tmpFos.close()
                            bm.recycle()
                            override?.recycle()
                            tmpFile.delete()
                        } else {
                            fos = FileOutputStream(file)
                            fos.write(data)
                        }


                        val exif = ExifInterface(file!!)

                        val now = System.currentTimeMillis()
                        val datetime = convertToExifDateTime(now)

                        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, datetime)
                        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, datetime)

                        try {
                            val subsec = (now - convertFromExifDateTime(datetime).time).toString()
                            exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, subsec)
                            exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, subsec)
                        } catch (e: ParseException) {
                        }

                        exif.saveAttributes()

                        if (saveToGallery && hasStoragePermission()) {
                            val values = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis())
                                put(MediaStore.MediaColumns.MIME_TYPE, "image/*")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { //this one
                                    put(
                                        MediaStore.MediaColumns.RELATIVE_PATH,
                                        Environment.DIRECTORY_DCIM
                                    )
                                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                                    put(
                                        MediaStore.Images.Media.DATE_TAKEN,
                                        System.currentTimeMillis()
                                    )
                                }
                            }

                            val uri = context.contentResolver.insert(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                values
                            )
                            if (uri == null) {
                                listener?.onCameraError(
                                    "Failed to add photo to gallery",
                                    java.lang.Exception("Failed to create uri")
                                )
                                startPreview()
                                synchronized(lock) {
                                    isTakingPhoto = false
                                }
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
                                startPreview()
                                synchronized(lock) {
                                    isTakingPhoto = false
                                }
                            }

                        } else {
                            listener?.onCameraPhoto(file)
                            startPreview()
                            synchronized(lock) {
                                isTakingPhoto = false
                            }
                        }
                    } catch (e: FileNotFoundException) {
                        listener?.onCameraError("File not found", e)
                    } catch (e: IOException) {
                        listener?.onCameraError(e.message ?: "", e)
                    } finally {
                        if (fos != null) {
                            try {
                                fos.close()
                            } catch (e: IOException) {
                            }

                        }
                    }
                }
            }
        }
    }

    override fun startRecording() {
        if (!isRecording) {
            synchronized(lock) {
                if (isRecording) {
                    return
                }
                updateAutoFocus()
                val params = camera!!.parameters
                val profile = getCamcorderProfile(quality)
                //params.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight)
                camera!!.parameters = params
                recorder.reset()
                updateCameraDisplayOrientation(context as Activity, position.value, camera)

                recorder.setOnErrorListener { mr, what, extra ->
                    if (listener != null) {
                        when (what) {
                            MediaRecorder.MEDIA_ERROR_SERVER_DIED -> listener?.onCameraError(
                                "Server died",
                                java.lang.Exception("Server died")
                            )
                            MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN -> listener?.onCameraError(
                                "Unknown",
                                java.lang.Exception("Unknown")
                            )
                        }
                    }
                }


                val df = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                val today = Calendar.getInstance().time
                val fileName = "VID_" + df.format(today) + ".mp4"
                file = if (saveToGallery && hasStoragePermission()) {
                    val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_DCIM)
                    if (externalDir == null) {
                        listener?.onCameraError(
                            "Cannot save video to gallery storage",
                            java.lang.Exception("Failed to get external directory")
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

                camera!!.unlock()
                try {
                    recorder.setCamera(camera)
                    if (enableAudio) {
                        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                    }
                    recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA)
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    recorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight)
                    if (enableAudio) {
                        recorder.setAudioChannels(profile.audioChannels)
                    }
                    var maxVideoBitrate = profile.videoBitRate
                    if (this.maxVideoBitrate > -1) {
                        maxVideoBitrate = this.maxVideoBitrate
                    }
                    var maxVideoFrameRate = profile.videoFrameRate
                    if (this.maxVideoFrameRate > -1) {
                        maxVideoFrameRate = this.maxVideoFrameRate
                    }
                    var maxAudioBitRate = profile.audioBitRate
                    if (this.maxAudioBitRate > -1) {
                        maxAudioBitRate = this.maxAudioBitRate
                    }
                    recorder.setVideoFrameRate(Math.min(profile.videoFrameRate, maxVideoFrameRate))
                    recorder.setVideoEncodingBitRate(
                        Math.min(
                            profile.videoBitRate,
                            maxVideoBitrate
                        )
                    )
                    if (enableAudio) {
                        recorder.setAudioEncodingBitRate(
                            Math.min(
                                profile.audioBitRate,
                                maxAudioBitRate
                            )
                        )
                    }
                    recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    if (enableAudio) {
                        recorder.setAudioEncoder(profile.audioCodec)
                    }
                    recorder.setOutputFile(file!!.path)
                    recorder.prepare()
                    if (flashMode == CameraFlashMode.ON) {
                        camera?.parameters?.flashMode = Camera.Parameters.FLASH_MODE_TORCH
                    }
                    recorder.start()
                    isRecording = true
                    startDurationTimer()
                } catch (e: Exception) {
                    listener?.onCameraError(e.message ?: "", e)
                }

                if (listener != null) {
                    listener?.onCameraVideoStart()
                }
            }
        }
    }

    override fun stopRecording() {
        cameraExecutor.execute {
            synchronized(lock) {
                if (!isRecording) {
                    return@execute
                }
                try {
                    if (flashMode == CameraFlashMode.ON) {
                        camera?.parameters?.flashMode = Camera.Parameters.FLASH_MODE_ON
                    }
                    recorder.stop()
                    stopDurationTimer()
                    recorder.reset()
                    if (isForceStopping) {
                        file?.delete()
                        isForceStopping = false
                        return@execute
                    }
                    if (saveToGallery && hasStoragePermission()) {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, file!!.name)
                            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis())
                            put(MediaStore.MediaColumns.MIME_TYPE, "video/*")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { //this one
                                put(
                                    MediaStore.MediaColumns.RELATIVE_PATH,
                                    Environment.DIRECTORY_DCIM
                                )
                                put(MediaStore.MediaColumns.IS_PENDING, 1)
                                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                            }
                        }

                        val uri = context.contentResolver.insert(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            values
                        )
                        if (uri == null) {
                            listener?.onCameraError(
                                "Failed to add video to gallery",
                                java.lang.Exception("Failed to create uri")
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

                            listener?.onCameraVideo(file)
                        }

                    } else {
                        listener?.onCameraVideo(file)
                    }
                } catch (e: Exception) {
                    file!!.delete()
                    stopDurationTimer()
                } finally {
                    isRecording = false
                    camera?.lock()
                }
            }
        }
    }

    override var saveToGallery: Boolean = false
    override var maxAudioBitRate: Int = -1
    override var maxVideoBitrate: Int = -1
    override var maxVideoFrameRate: Int = -1
    override var disableHEVC: Boolean = false
    override var quality: Quality = Quality.MAX_480P
    override val numberOfCameras: Int
        get() {
            return Camera.getNumberOfCameras()
        }


    override fun stop() {
        cameraExecutor.execute {
            synchronized(lock) {
                if (!isForceStopping) {
                    if (isRecording) {
                        isForceStopping = true
                        stopRecording()
                    } else {
                        stopPreviewInternal()
                    }
                }
            }
        }
    }

    private var isReleasing = false
    private fun releaseInternal() {
        if (!isReleasing) {
            isReleasing = true
            camera?.setPreviewCallback(null)
            camera?.release()
            camera = null
            cachedPictureRatioSizeMap.clear()
            cachedPreviewRatioSizeMap.clear()
            isReleasing = false
        }
    }

    override fun release() {
        cameraExecutor.execute {
            synchronized(lock) {
                releaseInternal()
            }
        }
    }


    override fun toggleCamera() {
        position = if (position == CameraPosition.FRONT) {
            CameraPosition.BACK
        } else {
            CameraPosition.FRONT
        }
    }

    override fun getSupportedRatios(): Array<String> {
        return cachedPreviewRatioSizeMap.keys.toTypedArray()
    }

    override fun getAvailablePictureSizes(ratio: String): Array<Size> {
        return cachedPictureRatioSizeMap.get(ratio)?.toTypedArray() ?: arrayOf()
    }

    override fun cameraRecording(): Boolean {
        return isRecording
    }

    override var isAudioLevelsEnabled: Boolean = false

    override val amplitude: Double
        get() {
            var amp = 0.0
            if (isAudioLevelsEnabled) {
                if (cameraRecording()) {
                    amp = (recorder.maxAmplitude).toDouble()
                    return amp
                }
                amp = try {
                    (recorder.maxAmplitude).toDouble()
                } catch (ignored: Exception) {
                    0.0
                }

            }
            return amp
        }

    override val db: Double
        get() = 20 * log10(amplitude / 32767.0)

    private var mEMA = 0.0
    override val amplitudeEMA: Double
        get() {
            val amp = amplitude
            mEMA = EMA_FILTER * amp + (1.0 - EMA_FILTER) * mEMA
            return mEMA
        }

    private var rotationAngle = 0
    private fun updateCameraDisplayOrientation(
        activity: Activity,
        cameraId: Int, camera: Camera?
    ) {
        synchronized(lock) {
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(cameraId, info)
            val rotation = activity.windowManager.defaultDisplay
                .rotation
            var degrees = 0
            when (rotation) {
                Surface.ROTATION_0 -> degrees = 0
                Surface.ROTATION_90 -> degrees = 90
                Surface.ROTATION_180 -> degrees = 180
                Surface.ROTATION_270 -> degrees = 270
            }

            val params = camera!!.parameters

            var angle: Int
            val displayAngle: Int
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                angle = (info.orientation + degrees) % 360
                displayAngle = (360 - angle) % 360

                val model = Build.MODEL.toLowerCase(Locale.ROOT)
                val isNexus6 = model.contains("nexus") && model.contains("6")

                if (isNexus6) {
                    angle = 90
                }
            } else {
                angle = (info.orientation - degrees + 360) % 360
                displayAngle = angle
            }

            rotationAngle = angle
            params.setRotation(angle)
            camera.parameters = params
            camera.setDisplayOrientation(displayAngle)
            recorder.setOrientationHint(angle)

        }
    }

}