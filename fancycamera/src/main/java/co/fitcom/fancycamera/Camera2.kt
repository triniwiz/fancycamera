/*
 * Created By Osei Fortune on 2/16/18 8:42 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 8:42 PM
 *
 */

package co.fitcom.fancycamera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.hardware.display.DisplayManager
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.*
import androidx.camera.view.CameraView
import androidx.core.app.ActivityCompat

import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale
import android.view.*
import androidx.camera.core.*
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import androidx.camera.view.TextureViewMeteringPointFactory
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.google.common.util.concurrent.ListenableFuture
import java.io.*
import java.lang.ref.WeakReference
import java.nio.Buffer
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.text.ParseException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

@androidx.annotation.RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
@SuppressLint("RestrictedApi")
internal class Camera2(private val mContext: Context, private val textureView: TextureView, private val position: FancyCamera.CameraPosition?, private val orientation: FancyCamera.CameraOrientation?) : CameraBase(textureView) {
    override fun updatePreview() {

    }

    private var mManager: CameraManager? = null
    internal override var recorder: MediaRecorder? = null
    private var mPosition: FancyCamera.CameraPosition? = null
    private var mOrientation: FancyCamera.CameraOrientation? = null
    private var backgroundHandler: Handler? = null
    private var backgroundHandlerThread: HandlerThread? = null
    private var characteristics: CameraCharacteristics? = null
    private var previewSize: Size? = null
    private var videoSize: Size? = null
    private var mPreviewBuilder: CaptureRequest.Builder? = null
    private var mCameraDevice: CameraDevice? = null
    private var mPreviewSession: CameraCaptureSession? = null
    private var isRecording = false
    private var isStarted = false
    private var isFlashEnabled = false
    private var mSensorOrientation: Int? = null
    private var cameraIdToOpen = "0"
    override var autoFocus = true
        set(focus) {
            synchronized(lock) {
                field = focus
            }
        }
    override var disableHEVC = false
    override var maxVideoBitrate = -1
    override var maxAudioBitRate = -1
    override var maxVideoFrameRate = -1
    override var saveToGallery = false
    override var autoSquareCrop = false
    override var isAudioLevelsEnabled = false
        private set
    private var reader: ImageReader? = null

    private val readOnImageAvailableListener = object : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(imageReader: ImageReader) {
            val image = imageReader.acquireLatestImage()
            val bitmap = imageToBitmap(image)
            try {
                save(bitmap)
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                reader = null
                val contentUri = Uri.fromFile(file)
                val mediaScanIntent = android.content.Intent(
                        "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                        contentUri
                )
                mContext.sendBroadcast(mediaScanIntent)
                if (listener != null) {
                    val event = PhotoEvent(EventType.INFO, file, PhotoEvent.EventInfo.PHOTO_TAKEN.toString())
                    listener?.onPhotoEvent(event)
                } else {
                    Log.w(TAG, "No listener found")
                }
            }
        }

        private fun imageToBitmap(image: Image): Bitmap {
            // NV21 is a plane of 8 bit Y values followed by interleaved  Cb Cr
            val ib = ByteBuffer.allocate(image.height * image.width * 2)

            val y = image.planes[0].buffer
            val cr = image.planes[1].buffer
            val cb = image.planes[2].buffer
            ib.put(y)
            ib.put(cb)
            ib.put(cr)

            val yuvImage = YuvImage(ib.array(),
                    ImageFormat.NV21, image.width, image.height, null)

            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0,
                    image.width, image.height), 50, out)
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }

        @Throws(IOException::class)
        private fun save(bm: Bitmap) {
            var originalWidth = bm.width
            var originalHeight = bm.height
            var offsetWidth = 0
            var offsetHeight = 0
            if (autoSquareCrop) {
                if (originalWidth < originalHeight) {
                    offsetHeight = (originalHeight - originalWidth) / 2
                    originalHeight = originalWidth
                } else {
                    offsetWidth = (originalWidth - originalHeight) / 2
                    originalWidth = originalHeight
                }
            }

            // this flips the front camera image to not be 'mirrored effect' for selfies
            // does not flip if using the back camera
            val matrix = Matrix()
            if (mPosition == FancyCamera.CameraPosition.FRONT) {
                val mirrorY = floatArrayOf(-1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
                val matrixMirrorY = Matrix()
                matrixMirrorY.setValues(mirrorY)
                matrix.postConcat(matrixMirrorY)
                matrix.postRotate(90f)
            } else {
                matrix.postRotate(mSensorOrientation!!.toFloat())
            }

            val rotated = Bitmap.createBitmap(bm, offsetWidth, offsetHeight, originalWidth, originalHeight, matrix, false)

            val cameraDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
            if (!cameraDir.exists()) {
                val mkdirs = cameraDir.mkdirs()
            }
            FileOutputStream(file!!).use { outputStream -> rotated.compress(Bitmap.CompressFormat.JPEG, 100, outputStream) }
            bm.recycle()
            rotated.recycle()
        }
    }

    override val numberOfCameras: Int
        get() {
            return CameraX.getCameraFactory().availableCameraIds.size
        }

    private var isCapturingPhoto: Boolean = false
    private lateinit var previewConfig: PreviewConfig
    private lateinit var videoCaptureConfig: VideoCaptureConfig
    private lateinit var imageCaptureConfig: ImageCaptureConfig
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture? = null
    private lateinit var displayManager: DisplayManager

    private var textureViewDisplay: Int = -1


    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == textureViewDisplay) {
                val display = displayManager.getDisplay(displayId)
                preview?.setTargetRotation(display.rotation)
                imageCapture?.setTargetRotation(display.rotation)
                videoCapture?.setTargetRotation(display.rotation)
            }
        }
    }


    private var mQuality = 0
    override var quality: Int
        set(value) {
            mQuality = value
            refreshCamera()
        }
        get() {
            return mQuality
        }

    override var didPauseForPermission = false
    var didCreateBefore = false

    init {
        mManager = mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        displayManager = mContext
                .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager


        (mContext as LifecycleOwner).lifecycle.addObserver(object : LifecycleObserver {

            @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
            fun onCreate() {
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            fun onPause() {
                displayManager.unregisterDisplayListener(displayListener)
                didCreateBefore = true
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
            fun onStopped() {
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
            fun onResume() {
                displayManager.registerDisplayListener(displayListener, null)
                if (didPauseForPermission) {
                    refreshCamera()
                    didPauseForPermission = false
                }

                if (!didPauseForPermission && didCreateBefore) {
                 //   refreshCamera()
                }
            }
        })

        val lensFacing = when (position) {
            FancyCamera.CameraPosition.FRONT -> {
                mPosition = FancyCamera.CameraPosition.FRONT
                CameraX.LensFacing.FRONT
            }
            FancyCamera.CameraPosition.BACK -> {
                mPosition = FancyCamera.CameraPosition.BACK
                CameraX.LensFacing.BACK
            }
            else -> {
                mPosition = FancyCamera.CameraPosition.BACK
                CameraX.LensFacing.BACK
            }
        }


        previewConfig = PreviewConfig.Builder()
                .apply {
                    setBackgroundExecutor(executorService)
                    setLensFacing(lensFacing)
                }.build()
        videoCaptureConfig = VideoCaptureConfig.Builder()
                .apply {
                    setBackgroundExecutor(executorService)
                    setLensFacing(lensFacing)
                }.build()
        imageCaptureConfig = ImageCaptureConfig.Builder()
                .apply {
                    setBackgroundExecutor(executorService)
                    setLensFacing(lensFacing)
                }.build()

        textureView.post {
            textureViewDisplay = textureView.display.displayId
            refreshCamera()
        }
    }

    private fun currentLens(): CameraX.LensFacing {
        return when (mPosition) {
            FancyCamera.CameraPosition.FRONT -> CameraX.LensFacing.FRONT
            FancyCamera.CameraPosition.BACK -> CameraX.LensFacing.BACK
            else -> CameraX.LensFacing.BACK
        }
    }

    private fun rotationToSurfaceRotation(rotation: Int): Int {
        return when (rotation) {
            270 -> Surface.ROTATION_270
            180 -> Surface.ROTATION_180
            90 -> Surface.ROTATION_90
            else -> Surface.ROTATION_0
        }
    }

    private var didOpen = false

    private lateinit var autoFitPreview: AutoFitPreviewBuilder

    @SuppressLint("ClickableViewAccessibility")
    private fun refreshCamera() {
        CameraX.unbindAll()
        if (!textureView.isAvailable || !hasPermission()) {
            return
        }

        val cameraOrientation = when (orientation) {
            FancyCamera.CameraOrientation.PORTRAIT_UPSIDE_DOWN -> Surface.ROTATION_270
            FancyCamera.CameraOrientation.PORTRAIT -> Surface.ROTATION_90
            FancyCamera.CameraOrientation.LANDSCAPE_LEFT -> Surface.ROTATION_0
            FancyCamera.CameraOrientation.LANDSCAPE_RIGHT -> Surface.ROTATION_180
            else -> textureView.display.rotation
        }


        val currentLens = currentLens()


        val config = PreviewConfig.Builder.fromConfig(previewConfig)
                .apply {
                    setTargetRotation(cameraOrientation)
                    setLensFacing(currentLens)
                }
        val imageConfig = ImageCaptureConfig.Builder.fromConfig(imageCaptureConfig)
                .apply {
                    setTargetRotation(cameraOrientation)
                    setLensFacing(currentLens)
                    setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
                }

        val profile = getCamcorderProfile(FancyCamera.Quality.values()[quality])

        config.setTargetResolution(Size(profile.videoFrameWidth, profile.videoFrameHeight))

        videoCaptureConfig = VideoCaptureConfig.Builder.fromConfig(videoCaptureConfig)
                .apply {
                    setTargetRotation(cameraOrientation)
                    setLensFacing(currentLens)
                    setTargetResolution(Size(profile.videoFrameWidth, profile.videoFrameHeight))
                    setMaxResolution(Size(profile.videoFrameWidth, profile.videoFrameHeight))

                    var _maxVideoBitrate = profile.videoBitRate
                    if (maxVideoBitrate > -1) {
                        _maxVideoBitrate = maxVideoBitrate
                    }
                    var _maxVideoFrameRate = profile.videoFrameRate
                    if (maxVideoFrameRate > -1) {
                        _maxVideoFrameRate = maxVideoFrameRate
                    }
                    var _maxAudioBitRate = profile.audioBitRate
                    if (maxAudioBitRate > -1) {
                        _maxAudioBitRate = maxAudioBitRate
                    }

                    setAudioBitRate(min(profile.audioBitRate, _maxAudioBitRate))
                    setBitRate(min(profile.videoBitRate, _maxVideoBitrate))
                    setVideoFrameRate(min(profile.videoFrameRate, _maxVideoFrameRate))
                }.build()

        previewConfig = config.build()
        autoFitPreview = AutoFitPreviewBuilder.build(previewConfig, textureView, listener) //Preview(previewConfig)
        preview = autoFitPreview.useCase
        val cameraControl = CameraX.getCameraControl(currentLens)

        textureView.setOnTouchListener { _, event ->
            if (event.action != MotionEvent.ACTION_UP) {
                return@setOnTouchListener false
            }

            val factory = TextureViewMeteringPointFactory(textureView)
            val point = factory.createPoint(event.x, event.y)
            val action = FocusMeteringAction.Builder.from(point).build()
            cameraControl.startFocusAndMetering(action)
            return@setOnTouchListener true
        }
        videoCapture = VideoCapture(videoCaptureConfig)
        imageCaptureConfig = imageConfig.build()
        imageCapture = ImageCapture(imageCaptureConfig)
        CameraX.bindToLifecycle(mContext as LifecycleOwner, preview!!, imageCapture!!, videoCapture!!)

    }

    override fun setEnableAudioLevels(enable: Boolean) {
        isAudioLevelsEnabled = enable
    }

    override fun hasCamera(): Boolean {
        if (mManager == null) return false
        try {
            return mManager!!.cameraIdList.isNotEmpty()
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        return false
    }

    override fun cameraStarted(): Boolean {
        return isStarted
    }

    override fun cameraRecording(): Boolean {
        return isRecording
    }


    override fun openCamera(width: Int, height: Int) {

    }


    @SuppressLint("InlinedApi")
    @Throws(IOException::class)
    private fun setUpMediaRecorder() {
        val activity = mContext as Activity ?: return

        synchronized(lock) {
            recorder = MediaRecorder()
            recorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            val df = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            val today = Calendar.getInstance().time
            if (saveToGallery && hasStoragePermission()) {
                val cameraDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
                if (!cameraDir.exists()) {
                    val mkdirs = cameraDir.mkdirs()
                }
                file = File(cameraDir, "VID_" + df.format(today) + ".mp4")
            } else {
                file = File(mContext.getExternalFilesDir(null), "VID_" + df.format(today) + ".mp4")
            }
            val profile = getCamcorderProfile(FancyCamera.Quality.values()[quality])
            recorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder!!.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight)
            recorder!!.setAudioChannels(profile.audioChannels)

            if (mOrientation == null || mOrientation == FancyCamera.CameraOrientation.UNKNOWN) {
                val rotation = activity.windowManager.defaultDisplay.rotation

                val orientation = activity.resources.configuration.orientation

                if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                    if (mSensorOrientation != null) {
                        when (mSensorOrientation) {
                            SENSOR_ORIENTATION_DEFAULT_DEGREES -> recorder!!.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation))
                            SENSOR_ORIENTATION_INVERSE_DEGREES -> recorder!!.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation))
                        }
                    }
                } else if (orientation == Configuration.ORIENTATION_LANDSCAPE && Surface.ROTATION_90 == rotation) {
                    recorder!!.setOrientationHint(0)
                } else if (orientation == Configuration.ORIENTATION_LANDSCAPE && Surface.ROTATION_270 == rotation) {
                    recorder!!.setOrientationHint(0)
                }
            } else {
                when (mOrientation) {
                    FancyCamera.CameraOrientation.PORTRAIT_UPSIDE_DOWN -> recorder!!.setOrientationHint(270)
                    FancyCamera.CameraOrientation.LANDSCAPE_LEFT -> recorder!!.setOrientationHint(0)
                    FancyCamera.CameraOrientation.LANDSCAPE_RIGHT -> recorder!!.setOrientationHint(180)
                    else -> recorder!!.setOrientationHint(90)
                }
            }

            val isHEVCSupported = !this.disableHEVC && android.os.Build.VERSION.SDK_INT >= 24

            // Use half bit rate for HEVC
            val videoBitRate = if (isHEVCSupported) profile.videoBitRate / 2 else profile.videoBitRate
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

            recorder!!.setVideoFrameRate(Math.min(profile.videoFrameRate, maxVideoFrameRate))
            recorder!!.setVideoEncodingBitRate(Math.min(videoBitRate, maxVideoBitrate))
            recorder!!.setAudioEncodingBitRate(Math.min(profile.audioBitRate, maxAudioBitRate))

            if (isHEVCSupported) {
                var h265 = Camera2.getIntFieldIfExists(MediaRecorder.VideoEncoder::class.java,
                        "HEVC", null, MediaRecorder.VideoEncoder.DEFAULT)
                if (h265 == MediaRecorder.VideoEncoder.DEFAULT) {
                    h265 = Camera2.getIntFieldIfExists(MediaRecorder.VideoEncoder::class.java,
                            "H265", null, MediaRecorder.VideoEncoder.DEFAULT)
                }
                // Emulator seems to dislike H264/HEVC
                if (isEmulator) {
                    recorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                } else {
                    recorder!!.setVideoEncoder(h265)
                }
            } else {
                recorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            }
            recorder!!.setAudioEncoder(profile.audioCodec)
            recorder!!.setOutputFile(file!!.path)
            recorder!!.setOnInfoListener { mr, what, extra ->
                if (listener != null) {
                    when (what) {
                        MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED -> listener?.onVideoEvent(VideoEvent(EventType.INFO, null, VideoEvent.EventInfo.MAX_DURATION_REACHED.toString()))
                        MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_APPROACHING -> listener?.onVideoEvent(VideoEvent(EventType.INFO, null, VideoEvent.EventInfo.MAX_FILESIZE_APPROACHING.toString()))
                        MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> listener?.onVideoEvent(VideoEvent(EventType.INFO, null, VideoEvent.EventInfo.MAX_FILESIZE_REACHED.toString()))
                        MediaRecorder.MEDIA_RECORDER_INFO_NEXT_OUTPUT_FILE_STARTED -> listener?.onVideoEvent(VideoEvent(EventType.INFO, null, VideoEvent.EventInfo.NEXT_OUTPUT_FILE_STARTED.toString()))
                        MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN -> listener?.onVideoEvent(VideoEvent(EventType.INFO, null, VideoEvent.EventInfo.UNKNOWN.toString()))
                    }
                }
            }

            recorder!!.setOnErrorListener { mr, what, extra ->
                if (listener != null) {
                    when (what) {
                        MediaRecorder.MEDIA_ERROR_SERVER_DIED -> listener?.onVideoEvent(VideoEvent(EventType.ERROR, null, VideoEvent.EventError.SERVER_DIED.toString()))
                        MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN -> listener?.onVideoEvent(VideoEvent(EventType.ERROR, null, VideoEvent.EventError.UNKNOWN.toString()))
                    }
                }
            }

            recorder!!.prepare()
        }
    }


    private fun updateAutoFocus(isVideo: Boolean) {
        if (autoFocus) {
            val modes = characteristics!!.get(
                    CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
            // Auto focus is not supported
            if (modes == null || modes.size == 0 ||
                    modes.size == 1 && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF) {
                mPreviewBuilder!!.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_OFF)
            } else {
                var hasVideoSupport = false
                var hasPhotoSupport = false
                var hasAutoSupport = false
                for (mode in modes) {
                    if (mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) {
                        hasVideoSupport = true
                    }
                    if (mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) {
                        hasPhotoSupport = true
                    }
                    if (mode == CaptureRequest.CONTROL_AF_MODE_AUTO) {
                        hasAutoSupport = true
                    }
                }
                if (isVideo) {
                    if (hasVideoSupport) {
                        mPreviewBuilder!!.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                    } else if (hasAutoSupport) {
                        mPreviewBuilder!!.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_AUTO)
                    }
                } else {
                    if (hasPhotoSupport) {
                        mPreviewBuilder!!.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    } else if (hasAutoSupport) {
                        mPreviewBuilder!!.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_AUTO)
                    }

                }
            }
        } else {
            mPreviewBuilder!!.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF)
        }
    }

    private fun getPreviewSize(sizes: Array<Size>): Size? {
        return getSupportedSize(sizes, quality)
    }

    private fun getSupportedSize(sizes: Array<Size>, quality: Int): Size? {
        val wm = mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val deviceSize = Point()
        display.getSize(deviceSize)
        val width = deviceSize.x
        val height = deviceSize.y

        var optimalSize: Size? = null
        var count = 0
        for (size in sizes) {
            count++
            if (quality == FancyCamera.Quality.LOWEST.value) {
                return sizes[sizes.size - 1]
            } else if (quality == FancyCamera.Quality.HIGHEST.value) {
                if (size.height <= height && size.width <= width) {
                    optimalSize = size
                    break
                } else {
                    if (count == sizes.size - 1) {
                        optimalSize = sizes[sizes.size - 1]
                        break
                    }
                }
            } else if (quality == FancyCamera.Quality.MAX_480P.value) {
                if (size.height == 480 && size.width <= width) {
                    optimalSize = size
                    break
                } else {
                    if (count == sizes.size - 1) {
                        optimalSize = getSupportedSize(sizes, FancyCamera.Quality.QVGA.value)
                        break
                    }
                }
            } else if (quality == FancyCamera.Quality.MAX_720P.value) {
                if (size.height == 720 && size.width <= width) {
                    optimalSize = size
                    break
                } else {
                    if (count == sizes.size - 1) {
                        optimalSize = getSupportedSize(sizes, FancyCamera.Quality.MAX_480P.value)
                        break
                    }
                }
            } else if (quality == FancyCamera.Quality.MAX_1080P.value) {
                if (size.height == 1080 && size.width <= width) {
                    optimalSize = size
                    break
                } else {
                    if (count == sizes.size - 1) {
                        optimalSize = getSupportedSize(sizes, FancyCamera.Quality.MAX_720P.value)
                        break
                    }
                }
            } else if (quality == FancyCamera.Quality.MAX_2160P.value) {
                if (size.height == 2160 && size.width <= width) {
                    optimalSize = size
                    break
                } else {
                    if (count == sizes.size - 1) {
                        optimalSize = getSupportedSize(sizes, FancyCamera.Quality.MAX_1080P.value)
                        break
                    }
                }
            } else if (quality == FancyCamera.Quality.QVGA.value) {
                if (size.height == 240 && size.width <= width) {
                    optimalSize = size
                    break
                } else {
                    if (count == sizes.size - 1) {
                        optimalSize = sizes[sizes.size - 1]
                        break
                    }
                }
            }
        }
        return optimalSize
    }


    private fun startPreview() {

    }

    private fun closePreviewSession() {

    }

    override fun start() {
        refreshCamera()
    }


    override fun stop() {
        CameraX.unbindAll()
    }


    override fun startRecording() {
        val df = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        val today = Calendar.getInstance().time
        if (saveToGallery && hasStoragePermission()) {
            val cameraDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
            if (!cameraDir.exists()) {
                cameraDir.mkdirs()
            }
            file = File(cameraDir, "VID_" + df.format(today) + ".mp4")
        } else {
            file = File(mContext.getExternalFilesDir(null), "VID_" + df.format(today) + ".mp4")
        }

        videoCapture?.startRecording(file!!, executorService, object : VideoCapture.OnVideoSavedListener {
            override fun onVideoSaved(videoFile: File) {
                isStarted = false
                isRecording = false
                stopDurationTimer()

                if (file != null && saveToGallery && hasStoragePermission()) {
                    val contentUri = Uri.fromFile(file)
                    val mediaScanIntent = Intent(
                            "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                            contentUri
                    )
                    mContext.sendBroadcast(mediaScanIntent)
                }

                if (listener != null) {
                    listener?.onVideoEvent(VideoEvent(EventType.INFO, videoFile, VideoEvent.EventInfo.RECORDING_FINISHED.toString()))
                }
                file = null
            }

            override fun onError(videoCaptureError: VideoCapture.VideoCaptureError, message: String, cause: Throwable?) {
                isStarted = false
                isRecording = false
                stopDurationTimer()
                file = null
                listener?.onVideoEvent(VideoEvent(EventType.ERROR, null, VideoEvent.EventError.UNKNOWN.toString()))
            }
        })
        isRecording = true
        startDurationTimer()
        listener?.onVideoEvent(VideoEvent(EventType.INFO, null, VideoEvent.EventInfo.RECORDING_STARTED.toString()))
    }


    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {

            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    override fun takePhoto() {
        val df = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        val today = Calendar.getInstance().time
        if (saveToGallery && hasStoragePermission()) {
            val cameraDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
            if (!cameraDir.exists()) {
                cameraDir.mkdirs()
            }
            file = File(cameraDir, "PIC_" + df.format(today) + ".jpg")
        } else {
            file = File(mContext.getExternalFilesDir(null), "PIC_" + df.format(today) + ".jpg")
        }

        val meta = ImageCapture.Metadata().apply {
            isReversedHorizontal = imageCaptureConfig.lensFacing == CameraX.LensFacing.FRONT
        }

        if (autoSquareCrop) {
            imageCapture?.takePicture(executorService, object : ImageCapture.OnImageCapturedListener() {
                override fun onCaptureSuccess(image: ImageProxy, rotationDegrees: Int) {
                    CameraXExecutors.ioExecutor().execute {
                        var isError = false
                        try {
                            val buffer = image.planes.first().buffer
                            val bytes = ByteArray(buffer!!.remaining())
                            buffer.get(bytes)

                            val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            val matrix = Matrix()

                            matrix.postRotate(rotationDegrees.toFloat())

                            if (imageCaptureConfig.lensFacing == CameraX.LensFacing.FRONT) {
                                matrix.postScale(-1f, 1f);
                            }

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
                            val rotated = Bitmap.createBitmap(bm, offsetWidth, offsetHeight, originalWidth, originalHeight, matrix, false);

                            val cameraDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
                            if (!cameraDir.exists()) {
                                cameraDir.mkdirs();
                            }
                            val outputStream = FileOutputStream(file!!, false)
                            rotated.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)


                            /*
                            val exif = ExifInterface(file!!.absolutePath)

                            val now = System.currentTimeMillis()
                            val datetime = convertToExifDateTime(now)

                            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, datetime)
                            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, datetime)

                            try {
                                val subsec = java.lang.Long.toString(now - convertFromExifDateTime(datetime).getTime())
                                exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, subsec)
                                exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, subsec)
                            } catch (e: ParseException) {
                            }

                            exif.rotate(rotationDegrees)
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

                            */

                            bm.recycle()
                            rotated.recycle()
                        } catch (e: Exception) {
                            isError = true
                            val event = PhotoEvent(EventType.ERROR, null, PhotoEvent.EventError.UNKNOWN.toString())
                            listener?.onPhotoEvent(event)
                        } finally {
                            try {
                                image.close()
                            } catch (e: Exception) {

                            }
                            if (!isError) {
                                if (saveToGallery && hasStoragePermission()) {
                                    val contentUri = Uri.fromFile(file)
                                    val mediaScanIntent = Intent(
                                            "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                                            contentUri
                                    )
                                    mContext.sendBroadcast(mediaScanIntent)
                                }
                                val event = PhotoEvent(EventType.INFO, file, PhotoEvent.EventInfo.PHOTO_TAKEN.toString())
                                listener?.onPhotoEvent(event)
                            }
                        }
                    }

                }

                override fun onError(imageCaptureError: ImageCapture.ImageCaptureError, message: String, cause: Throwable?) {
                    val event = PhotoEvent(EventType.ERROR, null, PhotoEvent.EventError.UNKNOWN.toString())
                    listener?.onPhotoEvent(event)
                }
            })
        } else {
            imageCapture?.takePicture(file!!, meta, executorService, object : ImageCapture.OnImageSavedListener {
                override fun onImageSaved(file: File) {
                    val event = PhotoEvent(EventType.INFO, file, PhotoEvent.EventInfo.PHOTO_TAKEN.toString())
                    listener?.onPhotoEvent(event)
                }

                override fun onError(imageCaptureError: ImageCapture.ImageCaptureError, message: String, cause: Throwable?) {
                    val event = PhotoEvent(EventType.ERROR, null, PhotoEvent.EventError.UNKNOWN.toString())
                    listener?.onPhotoEvent(event)
                }
            })
        }
    }


    private val DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
        public override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy:MM:dd", Locale.US)
        }
    }
    private val TIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
        public override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("HH:mm:ss", Locale.US)
        }
    }
    private val DATETIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
        public override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        }
    }

    private fun convertToExifDateTime(timestamp: Long): String {
        return DATETIME_FORMAT.get()!!.format(Date(timestamp))
    }

    @Throws(ParseException::class)
    private fun convertFromExifDateTime(dateTime: String): Date {
        return DATETIME_FORMAT.get()!!.parse(dateTime)
    }

    private fun stopRecord() {
        videoCapture?.stopRecording()
    }

    override fun stopRecording() {
        stopRecord()
    }


    private fun configureTransform(viewWidth: Int, viewHeight: Int) {

    }


    override fun toggleCamera() {
        if (mPosition == FancyCamera.CameraPosition.BACK) {
            mPosition = FancyCamera.CameraPosition.FRONT
        } else {
            mPosition = FancyCamera.CameraPosition.BACK
        }
        refreshCamera()
    }


    override fun release() {
        CameraX.unbindAll()
    }


    override fun setCameraPosition(position: FancyCamera.CameraPosition) {
        if (position != mPosition) {
            mPosition = position
            refreshCamera()
        }
    }

    override fun setCameraOrientation(orientation: FancyCamera.CameraOrientation) {
        mOrientation = orientation
        refreshCamera()
    }

    override fun hasFlash(): Boolean {
        return try {
            val cameraInfo = CameraX.getCameraInfo(currentLens())
            cameraInfo.isFlashAvailable.value ?: false
        } catch (e: Exception) {
            false
        }
    }

    override fun toggleFlash() {
        if (!hasFlash()) {
            return
        }
        isFlashEnabled = !isFlashEnabled

        imageCapture?.flashMode = when (isFlashEnabled) {
            true -> FlashMode.ON
            else -> FlashMode.OFF
        }

    }

    override fun enableFlash() {
        if (!hasFlash()) {
            return
        }
        isFlashEnabled = true
        imageCapture?.flashMode = FlashMode.ON
    }

    override fun disableFlash() {
        if (!hasFlash()) {
            return
        }
        isFlashEnabled = false
        imageCapture?.flashMode = FlashMode.OFF

    }

    override fun flashEnabled(): Boolean {
        return isFlashEnabled
    }

    private fun getCamcorderProfile(quality: FancyCamera.Quality): CamcorderProfile {
        var profile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW)
        when (quality) {
            FancyCamera.Quality.MAX_480P -> if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_480P)) {
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P)
            } else {
                profile = getCamcorderProfile(FancyCamera.Quality.QVGA)
            }
            FancyCamera.Quality.MAX_720P -> if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P)) {
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P)
            } else {
                profile = getCamcorderProfile(FancyCamera.Quality.MAX_480P)
            }
            FancyCamera.Quality.MAX_1080P -> if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_1080P)) {
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P)
            } else {
                profile = getCamcorderProfile(FancyCamera.Quality.MAX_720P)
            }
            FancyCamera.Quality.MAX_2160P -> try {
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_2160P)
            } catch (e: Exception) {
                profile = getCamcorderProfile(FancyCamera.Quality.HIGHEST)
            }

            FancyCamera.Quality.HIGHEST -> profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH)
            FancyCamera.Quality.LOWEST -> profile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW)
            FancyCamera.Quality.QVGA -> if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_QVGA)) {
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_QVGA)
            } else {
                profile = getCamcorderProfile(FancyCamera.Quality.LOWEST)
            }
        }
        return profile
    }

    companion object {
        private val lock = Any()
        private val TAG = "Camera2.Fancy"
        private val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
        private val SENSOR_ORIENTATION_INVERSE_DEGREES = 270
        private val DEFAULT_ORIENTATIONS = SparseIntArray()
        private val INVERSE_ORIENTATIONS = SparseIntArray()

        @JvmStatic
        val executorService = Executors.newSingleThreadExecutor()


        init {
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        init {
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0)
        }

        private fun getIntFieldIfExists(klass: Class<*>, fieldName: String,
                                        obj: Class<*>?, defaultVal: Int): Int {
            try {
                val f = klass.getDeclaredField(fieldName)
                return f.getInt(obj)
            } catch (e: Exception) {
                return defaultVal
            }

        }

        private val isEmulator: Boolean
            get() = (Build.FINGERPRINT.startsWith("generic")
                    || Build.FINGERPRINT.startsWith("unknown")
                    || Build.MODEL.contains("google_sdk")
                    || Build.MODEL.contains("Emulator")
                    || Build.MODEL.contains("Android SDK built for x86")
                    || Build.MANUFACTURER.contains("Genymotion")
                    || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                    || "google_sdk" == Build.PRODUCT)
    }

}
