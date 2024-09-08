package io.github.triniwiz.fancycamera

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Log
import android.util.Range
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.impl.utils.Exif
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.net.ParseException
import androidx.core.view.GestureDetectorCompat
import androidx.databinding.ObservableArrayList
import androidx.databinding.ObservableList
import androidx.databinding.ObservableList.OnListChangedCallback
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


@SuppressLint("UnsafeOptInUsageError", "RestrictedApi")
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class Camera2 @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CameraBase(context, attrs, defStyleAttr) {

    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    private var cameraProvider: ProcessCameraProvider? = null

    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: androidx.camera.core.ImageAnalysis? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var executor = Executors.newSingleThreadExecutor()
    private var camera: androidx.camera.core.Camera? = null
    private var preview: Preview? = null
    private var isStarted = false
    private var isRecording = false
    private var file: File? = null
    private var isForceStopping = false
    private var mLock = Any()
    private var cameraManager: CameraManager? = null
    private var recording: Recording? = null
    private var pendingAutoFocus = false
    private var lastZoomRatio = 1.0f
    private var autoFocusTimer: Timer? = null

    override var enablePinchZoom: Boolean = true
    override var enableTapToFocus: Boolean = true

    override var imageProcessors: ObservableList<ImageProcessor<*>> =
        ObservableArrayList<ImageProcessor<*>>()
            .apply {
                val callback: OnListChangedCallback<ObservableList<ImageProcessor<Any>>> =
                    object : OnListChangedCallback<ObservableList<ImageProcessor<Any>>>() {
                        override fun onChanged(sender: ObservableList<ImageProcessor<Any>>?) {}

                        override fun onItemRangeChanged(
                            sender: ObservableList<ImageProcessor<Any>>?,
                            positionStart: Int,
                            itemCount: Int
                        ) {
                            // noop
                        }

                        override fun onItemRangeInserted(
                            sender: ObservableList<ImageProcessor<Any>>?,
                            positionStart: Int,
                            itemCount: Int
                        ) {
                            // noop
                        }

                        override fun onItemRangeMoved(
                            sender: ObservableList<ImageProcessor<Any>>?,
                            fromPosition: Int,
                            toPosition: Int,
                            itemCount: Int
                        ) {
                            // noop
                        }

                        override fun onItemRangeRemoved(
                            sender: ObservableList<ImageProcessor<Any>>?,
                            positionStart: Int,
                            itemCount: Int
                        ) {
                            if (sender?.isEmpty() == true) {

                            }
                        }
                    }
                addOnListChangedCallback(callback)
            }
    override var defaultLens: CameraLens = CameraLens.telephoto
        set(value) {
            field = value
            refreshCamera()
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

    private fun handleZoom() {
        // here we set the zoom once
        // handles the case where: user changes the zoom before camera is ready, apply it when camera ready
        // user changes zoom after camera is ready, this will trigger on the zoom setter
        camera?.cameraControl?.let {
            var zoomChanged = true
            if (storedZoom > 0) {
                it.setLinearZoom(storedZoom)
                storedZoom = -1f
            } else if (storedZoomRatio > 0) {
                it.setZoomRatio(storedZoomRatio)
                storedZoomRatio = -1f
            } else {
                zoomChanged = false
            }
            if (zoomChanged) {
                onZoomChange()
            }
        }
    }

    override val previewSurface: Any
        get() {
            return previewView
        }
    val maxZoomRatio: Float
        get() = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f
    val minZoomRatio: Float
        get() = camera?.cameraInfo?.zoomState?.value?.minZoomRatio ?: 1f

    var storedZoomRatio: Float = -1F
    override var zoomRatio: Float
        get() = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
        set(value) {
            storedZoomRatio = value
            storedZoom = -1f
            handleZoom()
        }

    var storedZoom: Float = -1.0F

    override var zoom: Float
        get() = camera?.cameraInfo?.zoomState?.value?.linearZoom
            ?: (if (storedZoom < 0) 0.0f else storedZoom)
        set(value) {
            storedZoom = when {
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
            storedZoomRatio = -1f
            handleZoom()
        }
    override var whiteBalance: WhiteBalance = WhiteBalance.Auto
        set(value) {
            field = value
            if (!isRecording) {
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

    private fun getFocusMeteringActions(): Int {
        var actions = FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        if (whiteBalance == WhiteBalance.Auto) {
            actions = actions or FocusMeteringAction.FLAG_AWB
        }
        return actions
    }

    private fun cancelAndDisposeFocusTimer() {
        autoFocusTimer?.cancel()
        autoFocusTimer?.purge()
        autoFocusTimer = null
    }

    private var scaleFactor = 1F
    var deviceCameraInfo = mutableListOf<CameraInfo>()
        private set

    val currentCameraInfo: CameraInfo?
        get() {
            if (camera != null) {
                val info = Camera2CameraInfo.from(camera!!.cameraInfo)
                return CameraInfo(
                    info.cameraId,
                    camera!!.cameraInfo.implementationType,
                    CameraxCameraCharacteristicsImpl(info)
                )
            }
            return null
        }

    private var wideCameraInfo: androidx.camera.core.CameraInfo? = null
    private fun setupGestureListeners() {

        val listener =
            object : ScaleGestureDetector.SimpleOnScaleGestureListener(),
                GestureDetector.OnGestureListener {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    camera?.cameraInfo?.zoomState?.value?.let { zoomState ->
                        camera?.cameraControl?.setZoomRatio(
                            detector.scaleFactor * zoomState.zoomRatio
                        )
                        onZoomChange()
                    }
                    return true
                }

                override fun onDown(p0: MotionEvent): Boolean = true

                override fun onShowPress(p0: MotionEvent) = Unit

                override fun onSingleTapUp(event: MotionEvent): Boolean {
                    val factory: MeteringPointFactory = previewView.meteringPointFactory
                    val autoFocusPoint = factory.createPoint(event.x, event.y)
                    try {
                        camera?.cameraControl?.cancelFocusAndMetering()
                        camera?.cameraControl?.startFocusAndMetering(
                            FocusMeteringAction.Builder(
                                autoFocusPoint,
                                getFocusMeteringActions()
                            ).apply {
                                //focus only when the user tap the preview
                                disableAutoCancel()
                            }.build()
                        )
                        cancelAndDisposeFocusTimer()
                        autoFocusTimer = Timer("autoFocusTimer")
                        autoFocusTimer?.schedule(object : TimerTask() {
                            override fun run() {
                                handleAutoFocus()
                            }
                        }, 5000)
                    } catch (e: CameraInfoUnavailableException) {
                        Log.d("ERROR", "cannot access camera", e)
                    }
                    return true
                }

                override fun onScroll(
                    p0: MotionEvent?,
                    p1: MotionEvent,
                    p2: Float,
                    p3: Float
                ): Boolean = false

                override fun onLongPress(p0: MotionEvent) = Unit

                override fun onFling(
                    p0: MotionEvent?,
                    p1: MotionEvent,
                    p2: Float,
                    p3: Float
                ): Boolean = false

            }
        val scaleGestureDetector = ScaleGestureDetector(context, listener)
        val gestureDetectorCompat = GestureDetectorCompat(context, listener)
        previewView.setOnTouchListener { view, event ->
            var consumed = false
            if (enablePinchZoom) {
                consumed = scaleGestureDetector.onTouchEvent(event)
            }
            if (!scaleGestureDetector.isInProgress && enableTapToFocus) {
                consumed = gestureDetectorCompat.onTouchEvent(event)
            }
            view.performClick()
            consumed
        }
    }


    fun setCameraWithId(id: String) {

    }

    init {
        setupGestureListeners()
        previewView.afterMeasured {
            handleAutoFocus()
        }
        addView(previewView)

        // TODO: Bind this to the view's onCreate method
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider?.unbindAll()
                cameraProviderFuture.get()?.let {
                    cameraProvider = it
                    deviceCameraInfo.clear()
                    it.availableCameraInfos.forEach { ci ->
                        val info = Camera2CameraInfo.from((ci))
                        deviceCameraInfo.add(
                            CameraInfo(
                                info.cameraId,
                                ci.implementationType,
                                CameraxCameraCharacteristicsImpl(info)
                            )
                        )
                    }
                    it.availableCameraInfos.minByOrNull { info ->
                        info.intrinsicZoomRatio
                    }?.let { info ->
                        if (!mIsWideAngleSupported) {
                            mIsWideAngleSupported = info.intrinsicZoomRatio < 1.0
                            if (mIsWideAngleSupported) {
                                wideCameraInfo = info
                            }
                        }
                    }
                }
                cameraProvider = cameraProviderFuture.get()
                refreshCamera() // or just initPreview() ?
            } catch (e: Exception) {
                listener?.onCameraError("Failed to get camera", e)
                isStarted = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private var mIsWideAngleSupported = false

    override fun isWideAngleSupported(): Boolean {
        return mIsWideAngleSupported
    }

    private fun handleAutoFocus() {
        if (camera?.cameraControl == null) {
            pendingAutoFocus = true
            return
        }
        pendingAutoFocus = false
        if (autoFocus) {
            val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
                previewView.width.toFloat(), previewView.height.toFloat()
            )
            val centerWidth = previewView.width.toFloat() / 2
            val centerHeight = previewView.height.toFloat() / 2
            val autoFocusPoint = factory.createPoint(centerWidth, centerHeight)
            try {
                val action = FocusMeteringAction.Builder(
                    autoFocusPoint,
                    getFocusMeteringActions()
                ).apply {
                    setAutoCancelDuration(2, TimeUnit.SECONDS)
                }.build();
                val supported = camera?.cameraInfo?.isFocusMeteringSupported(action)
                if (supported == true) {
                    camera?.cameraControl?.startFocusAndMetering(
                        action
                    )
                }

            } catch (_: CameraInfoUnavailableException) {
            }
        }
    }

    override var allowExifRotation: Boolean = true
    override var autoSquareCrop: Boolean = false
    override var autoFocus: Boolean = false
    override var saveToGallery: Boolean = false
    override var maxAudioBitRate: Int = -1
    override var maxVideoBitrate: Int = -1
    override var maxVideoFrameRate: Int = -1
    override var disableHEVC: Boolean = false

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
        val test = camera?.cameraInfo?.hasFlashUnit()
        if (test != null && !test) {
            return ImageCapture.FLASH_MODE_OFF
        }
        return when (flashMode) {
            CameraFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            CameraFlashMode.ON -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    override var position: CameraPosition = CameraPosition.BACK


    var frontCameraId: String? = null
        set(value) {
            field = value
            if (!isRecording && position == CameraPosition.FRONT) {
                if (camera?.cameraInfo != null) {
                    val info = Camera2CameraInfo.from(camera!!.cameraInfo)
                    if (value == info.cameraId) {
                        return
                    }
                }
                refreshCamera()
            }
        }

    var backCameraId: String? = null
        set(value) {
            field = value
            if (!isRecording && position == CameraPosition.BACK) {
                if (camera?.cameraInfo != null) {
                    val info = Camera2CameraInfo.from(camera!!.cameraInfo)
                    if (value == info.cameraId) {
                        return
                    }
                }
                refreshCamera()
            }
        }

    // todo
    var externalCameraId: String? = null

    private fun selectorFromPosition(): CameraSelector {
        return CameraSelector.Builder()
            .apply {
                if (position == CameraPosition.FRONT && frontCameraId != null) {
                    addCameraFilter { infos ->
                        infos.filter {
                            val info = Camera2CameraInfo.from(it)
                            info.cameraId == frontCameraId!!
                        }.toMutableList()
                    }
                } else if (position == CameraPosition.BACK && backCameraId != null) {
                    addCameraFilter { infos ->
                        infos.filter {
                            val info = Camera2CameraInfo.from(it)
                            info.cameraId == backCameraId!!
                        }.toMutableList()
                    }
                } else {
                    if (isWideAngleSupported()) {
                        if (defaultLens == CameraLens.auto) {
                            if (position == CameraPosition.FRONT) {
                                requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                            } else {
                                requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            }
                        } else {
                            addCameraFilter {
                                it.filter { info ->
                                    if (info.lensFacing == CameraSelector.LENS_FACING_EXTERNAL || info.lensFacing == CameraSelector.LENS_FACING_UNKNOWN) {
                                        false
                                    } else if (position.lenFacing != info.lensFacing) {
                                        false
                                    } else {
                                        when (defaultLens) {
                                            CameraLens.telephoto -> {
                                                if (info.intrinsicZoomRatio >= 1.0) {
                                                    true
                                                } else {
                                                    false
                                                }
                                            }

                                            CameraLens.wide, CameraLens.ultrawide -> {
                                                if (info.intrinsicZoomRatio < 1.0) {
                                                    true
                                                } else {
                                                    false
                                                }
                                            }

                                            CameraLens.auto -> true
                                        }
                                    }
                                }
                            }
                        }

                    } else {
                        if (position == CameraPosition.FRONT) {
                            requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        } else {
                            requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        }
                    }
                }

            }
            .build()
    }

    /** Rotation specified by client (external code)
     * TODO: link this to the code, overriding or affecting targetRotation logic */
    override var rotation: CameraOrientation = CameraOrientation.UNKNOWN

    @SuppressLint("UnsafeExperimentalUsageError")
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

    private fun bindDefaultUseCase() {
        if (imageAnalysis != null && imageProcessors.isNotEmpty()) {
            (context as? Activity)?.let {
                it.runOnUiThread {
                    camera = cameraProvider?.bindToLifecycle(
                        context as LifecycleOwner,
                        selectorFromPosition(),
                        imageAnalysis,
                        preview
                    )
                    handleAutoFocus()
                }
            }
            return
        }
        (context as? Activity)?.let {
            it.runOnUiThread {
                camera = cameraProvider?.bindToLifecycle(
                    context as LifecycleOwner,
                    selectorFromPosition(),
                    preview
                )
            }
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
                setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
            }
        imageAnalysis = builder.build()
        imageAnalysis?.setAnalyzer(executor) { proxy ->
            proxy.image?.let { image ->
                if (currentFrame < processEveryNthFrame) {
                    incrementCurrentFrame()
                    proxy.close()
                    return@setAnalyzer
                }

                if (retrieveLatestImage) {
                    latestImage = BitmapUtils.getBitmap(proxy)
                }

                if (imageProcessors.isNotEmpty()) {
                    executor.execute {
                        for (processor in imageProcessors) {
                            val process = processor.process(image, proxy.imageInfo.rotationDegrees)
                            try {
                                process.run()
                                val result = process.get()
                                if (result != null) {
                                    if ((0..6).contains(processor.type) && result is String && result.isEmpty()) {
                                        continue
                                    }
                                    when (processor.type) {
                                        0 -> {
                                            onBarcodeScanningListener?.onSuccess(result)
                                        }

                                        1 -> {
                                            onFacesDetectedListener?.onSuccess(result)
                                        }

                                        2 -> {
                                            onImageLabelingListener?.onSuccess(result)
                                        }

                                        3 -> {
                                            onObjectDetectedListener?.onSuccess(result)
                                        }

                                        4 -> {
                                            onPoseDetectedListener?.onSuccess(result)
                                        }

                                        5 -> {
                                            onSelfieSegmentationListener?.onSuccess(result)
                                        }

                                        6 -> {
                                            onTextRecognitionListener?.onSuccess(result)
                                        }

                                        else -> {}
                                    }
                                }
                            } catch (e: Exception) {
                                when (processor.type) {
                                    0 -> {
                                        onBarcodeScanningListener?.onError(
                                            e.message ?: "Failed to complete barcode scanning.", e
                                        )
                                    }

                                    1 -> {
                                        onFacesDetectedListener?.onError(
                                            e.message ?: "Failed to complete face detection.", e
                                        )
                                    }

                                    2 -> {
                                        onImageLabelingListener?.onError(
                                            e.message
                                                ?: "Failed to complete image label detection.", e
                                        )
                                    }

                                    3 -> {
                                        onObjectDetectedListener?.onError(
                                            e.message ?: "Failed to complete object detection.", e
                                        )
                                    }

                                    4 -> {
                                        onPoseDetectedListener?.onError(
                                            e.message ?: "Failed to complete pose detection.", e
                                        )
                                    }

                                    5 -> {
                                        onSelfieSegmentationListener?.onError(
                                            e.message
                                                ?: "Failed to complete selfie segmentation detection.",
                                            e
                                        )
                                    }

                                    6 -> {
                                        onTextRecognitionListener?.onError(
                                            e.message ?: "Failed to complete text recognition.", e
                                        )
                                    }
                                }
                            }
                        }
                    }

                    executor.execute {
                        proxy.close()
                        resetCurrentFrame()
                    }
                } else {
                    proxy.close()
                    resetCurrentFrame()
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
                setResolutionSelector(
                    ResolutionSelector.Builder()
                        .apply {
                            setAspectRatioStrategy(
                                AspectRatioStrategy(
                                    when (displayRatio) {
                                        "16:9" -> AspectRatio.RATIO_16_9
                                        else -> AspectRatio.RATIO_4_3
                                    }, AspectRatioStrategy.FALLBACK_RULE_AUTO
                                )
                            )
                        }
                        .build()
                )
            } else {
                setResolutionSelector(
                    ResolutionSelector.Builder()
                        .apply {
                            try {
                                setResolutionStrategy(
                                    ResolutionStrategy(
                                        android.util.Size.parseSize(pictureSize),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                                    )
                                )
                            } catch (e: Exception) {
                                setAspectRatioStrategy(
                                    AspectRatioStrategy(
                                        when (displayRatio) {
                                            "16:9" -> AspectRatio.RATIO_16_9
                                            else -> AspectRatio.RATIO_4_3
                                        }, AspectRatioStrategy.FALLBACK_RULE_AUTO
                                    )
                                )
                            }
                        }
                        .build()
                )
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

        // handle ultra wide af mode
        if ((camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1.0f) < 1.0f) {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_OFF
            )
        }


        imageCapture = builder.build()

        if (wasBounded || autoBound) {
            cameraProvider?.let { cameraProvider ->
                camera = cameraProvider.bindToLifecycle(
                    context as LifecycleOwner,
                    selectorFromPosition(),
                    imageCapture!!,
                    preview!!
                )
            }
        }
    }

    interface CameraCharacteristicsImpl {
        fun <T> getCameraCharacteristic(key: CameraCharacteristics.Key<T>): T?
    }

    class CameraxCameraCharacteristicsImpl(private val characteristics: Camera2CameraInfo) :
        CameraCharacteristicsImpl {
        override fun <T> getCameraCharacteristic(key: CameraCharacteristics.Key<T>): T? {
            return characteristics.getCameraCharacteristic(key)
        }
    }


    class Camera2CameraCharacteristicsImpl(private val characteristics: CameraCharacteristics) :
        CameraCharacteristicsImpl {
        override fun <T> getCameraCharacteristic(key: CameraCharacteristics.Key<T>): T? {
            return characteristics.get(key)
        }
    }

    class CameraInfo(
        val id: String,
        val implementationType: String,
        characteristics: CameraCharacteristicsImpl
    ) {

        val lensFacing by lazy {
            when (characteristics.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)!!) {
                CameraCharacteristics.LENS_FACING_BACK -> 0
                CameraCharacteristics.LENS_FACING_FRONT -> 1
                CameraCharacteristics.LENS_FACING_EXTERNAL -> 2
                else -> 2
            }
        }


        val capabilities by lazy {
            characteristics.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        }


        val focalLengths by lazy {
            characteristics.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        }

        val maxDigitalZoom by lazy {
            characteristics.getCameraCharacteristic(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                ?: 1f
        }

        val zoomRange by lazy {
            val range = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                characteristics.getCameraCharacteristic(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            } else {
                null
            }
            return@lazy range ?: Range(1f, maxDigitalZoom)
        }

        val activeSize =
            characteristics.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!

        val minZoom by lazy { zoomRange.lower.toDouble() }
        val maxZoom by lazy { zoomRange.upper.toDouble() }

        val hardwareLevel =
            when (characteristics.getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> 0
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> 1
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> 1
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> 2
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> 3
                else -> 0
            }

        override fun toString(): String {
            return "cameraId $id\n" +
                    "maxDigitalZoom $maxDigitalZoom\n" +
                    "zoomRange $zoomRange\n" +
                    "activeSize $activeSize\n" +
                    "minZoom $minZoom\n" +
                    "maxZoom $maxZoom\n" +
                    "hardwareLevel $hardwareLevel"
        }
    }

    //  private var cameraInfoCache: MutableMap<String, CameraInfo> = mutableMapOf()
    private fun initPreview() {

        safeUnbindAll()

        val previewBuilder = Preview.Builder()
            .apply {
                setResolutionSelector(
                    ResolutionSelector.Builder().apply {
                        setAspectRatioStrategy(
                            AspectRatioStrategy(
                                when (displayRatio) {
                                    "16:9" -> AspectRatio.RATIO_16_9
                                    else -> AspectRatio.RATIO_4_3
                                }, AspectRatioStrategy.FALLBACK_RULE_AUTO
                            )
                        )
                    }.build()
                )

            }

        if (imageAnalysis == null) {
            setUpAnalysis()
        }

        if (defaultLens == CameraLens.auto) {
            if (wideCameraInfo != null) {
                val info = Camera2CameraInfo.from(wideCameraInfo!!)
                val cameraInfo = CameraInfo(
                    info.cameraId,
                    wideCameraInfo!!.implementationType,
                    CameraxCameraCharacteristicsImpl(info)
                )

                val builder = Preview.Builder()
                    .apply {
                        setResolutionSelector(
                            ResolutionSelector.Builder().apply {
                                setAspectRatioStrategy(
                                    AspectRatioStrategy(
                                        when (displayRatio) {
                                            "16:9" -> AspectRatio.RATIO_16_9
                                            else -> AspectRatio.RATIO_4_3
                                        }, AspectRatioStrategy.FALLBACK_RULE_AUTO
                                    )
                                )
                            }.build()
                        )

                    }

                val extender = Camera2Interop.Extender(builder)

                val zoomRange = cameraInfo.zoomRange
                val zoomClamped = zoomRange.clamp(zoom)
                if (cameraInfo.hardwareLevel >= 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    extender.setCaptureRequestOption(CaptureRequest.CONTROL_ZOOM_RATIO, zoomClamped)
                } else {
                    val size = cameraInfo.activeSize
                    val dx = (size.width() / zoomClamped / 2).toInt()
                    val dy = (size.height() / zoomClamped / 2).toInt()
                    val left = size.centerX() - this.left - dx
                    val top = size.centerY() - this.top - dy
                    val right = size.centerX() - this.left + dx
                    val bottom = size.centerY() - this.top + dy

                    val zoomed = Rect(left, top, right, bottom)
                    extender.setCaptureRequestOption(CaptureRequest.SCALER_CROP_REGION, zoomed)
                }


                preview = previewBuilder
                    .build()
                    .also { value ->
                        value.setSurfaceProvider(this.previewView.surfaceProvider)
                    }

                bindDefaultUseCase()
            } else {
                preview = previewBuilder
                    .build()
                    .also {
                        it.setSurfaceProvider(this.previewView.surfaceProvider)
                    }

                bindDefaultUseCase()
            }

        } else {
            preview = previewBuilder
                .build()
                .also {
                    it.setSurfaceProvider(this.previewView.surfaceProvider)
                }

            bindDefaultUseCase()
        }

        if (pendingAutoFocus) {
            handleAutoFocus()
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
                )
                .build()


            videoCapture = VideoCapture.withOutput(recorder).apply {
                if (getDeviceRotation() > -1) {
                    targetRotation = getDeviceRotation()
                }
            }
        }
    }

    private var zoomRange = 1F..1F

    @SuppressLint("UnsafeOptInUsageError")
    private fun refreshCamera() {
        if (pause) {
            return
        }
        cancelAndDisposeFocusTimer()
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

        setUpAnalysis()

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

        updateImageCapture(false)

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
                var test = camera?.cameraInfo?.hasFlashUnit()
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

    private fun onZoomChange() {
        val currentZoomRatio = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1.0f
        if (lastZoomRatio == currentZoomRatio || lastZoomRatio < 1.0f && currentZoomRatio < 1.0f || lastZoomRatio >= 1.0f && currentZoomRatio >= 1.0f) {
            return
        }
        lastZoomRatio = currentZoomRatio
        updateImageCapture()
        return
    }

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
                camera = it.bindToLifecycle(
                    context as LifecycleOwner,
                    selectorFromPosition(),
                    videoCapture!!
                )
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
                            } else {
                                bindDefaultUseCase()
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
                                bindDefaultUseCase()
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
            bindDefaultUseCase()
            isForceStopping = false
            listener?.onCameraError("Failed to record video.", e)
        }
    }

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
            if (imageCapture == null) {
                updateImageCapture(false)
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
                executor,
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
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        processImageFile(fileName) // outputFileResults.savedUri.toString() is null
                    }

                    override fun onError(exception: ImageCaptureException) {
                        listener?.onCameraError("Failed to take photo image", exception)
                        ContextCompat.getMainExecutor(context).execute {
                            cameraProvider?.unbind(imageCapture)
                            cameraProvider?.bindToLifecycle(
                                context as LifecycleOwner,
                                selectorFromPosition(),
                                preview,
                                imageAnalysis
                            )
                        }
                    }
                })
        }
    }

    private fun processImageProxy(image: ImageProxy, fileName: String) {
        var isError = false
        var outputStream: FileOutputStream? = null
        try {

            val buffer: ByteBuffer = image.planes[0].buffer
            buffer.rewind()
            val data = ByteArray(buffer.capacity())
            buffer.get(data)
            buffer.rewind()
            val inputStream: InputStream = ByteArrayInputStream(data)
            val srcExif = ExifInterface(inputStream)

            var bm = image.toBitmap()
            var originalWidth = bm.width
            var originalHeight = bm.height
            var offsetWidth = 0
            var offsetHeight = 0
            var matrix: Matrix? = null

            if (!allowExifRotation) {
                matrix = Matrix()
                // Registering image's required rotation, provided by Androidx ImageAnalysis
                val imageTargetRotation = image.imageInfo.rotationDegrees
                matrix.postRotate(imageTargetRotation.toFloat())

                // Flipping over the image in case it is the front camera
                if (position == CameraPosition.FRONT)
                    matrix.postScale(-1f, 1f)
            }

            if (autoSquareCrop) {
                if (originalWidth < originalHeight) {
                    offsetHeight = (originalHeight - originalWidth) / 2
                    originalHeight = originalWidth
                } else {
                    offsetWidth = (originalWidth - originalHeight) / 2
                    originalWidth = originalHeight
                }
            }

            if (autoSquareCrop || !allowExifRotation) {
                bm = Bitmap.createBitmap(
                    bm,
                    offsetWidth,
                    offsetHeight,
                    originalWidth,
                    originalHeight,
                    matrix,
                    false
                )
            }

            outputStream = FileOutputStream(file!!, false)
            var override: Bitmap? = null
            if (overridePhotoHeight > 0 && overridePhotoWidth > 0) {
                override = Bitmap.createScaledBitmap(
                    bm,
                    overridePhotoWidth,
                    overridePhotoHeight,
                    false
                )
                override.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            } else {
                bm.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }

            val dstExif = ExifInterface(file!!.absolutePath)

            val now = System.currentTimeMillis()
            val datetime = convertToExifDateTime(now)

            dstExif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, datetime)
            dstExif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, datetime)

            try {
                val subsec = (now - convertFromExifDateTime(datetime).time).toString()
                dstExif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, subsec)
                dstExif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, subsec)
            } catch (_: ParseException) {
            }

            if (allowExifRotation) {
                val exifOrientation = srcExif.getAttribute(ExifInterface.TAG_ORIENTATION)

                dstExif.setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation)
            }


            dstExif.setAttribute(
                ExifInterface.TAG_APERTURE_VALUE,
                srcExif.getAttribute(ExifInterface.TAG_APERTURE_VALUE)
            )


            val aperture = srcExif.getAttribute(ExifInterface.TAG_F_NUMBER)

            dstExif.setAttribute(
                ExifInterface.TAG_F_NUMBER,
                aperture
            )

            val exposureTime = srcExif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)

            dstExif.setAttribute(
                ExifInterface.TAG_EXPOSURE_TIME,
                exposureTime
            )

            srcExif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { isoSpeed ->
                dstExif.setAttribute(
                    ExifInterface.TAG_ISO_SPEED_RATINGS,
                    isoSpeed
                )
            }


            srcExif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                ?.let { photographicSensitivity ->
                    dstExif.setAttribute(
                        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                        photographicSensitivity
                    )
                }


            val make = srcExif.getAttribute(ExifInterface.TAG_MAKE)
            dstExif.setAttribute(ExifInterface.TAG_MAKE, make)

            val model = srcExif.getAttribute(ExifInterface.TAG_MODEL)
            dstExif.setAttribute(ExifInterface.TAG_MODEL, model)

            val lensModel = srcExif.getAttribute(ExifInterface.TAG_LENS_MODEL)
            dstExif.setAttribute(ExifInterface.TAG_LENS_MODEL, lensModel)

            val softwareVersion = srcExif.getAttribute(ExifInterface.TAG_SOFTWARE)
            dstExif.setAttribute(ExifInterface.TAG_SOFTWARE, softwareVersion)

            val focalLength = srcExif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
            dstExif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, focalLength)

            try {
                Exif.createFromImageProxy(image).location?.let {
                    dstExif.setGpsInfo(it)
                }
            } catch (_: IOException) {
            }

            dstExif.saveAttributes()


            bm.recycle()
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


            bindDefaultUseCase()
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
        cancelAndDisposeFocusTimer()
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