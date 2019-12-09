/*
 * Created By Osei Fortune on 2/16/18 8:43 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 7:58 PM
 *
 */

package co.fitcom.fancycamera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.log10
import kotlin.math.min


@SuppressLint("RestrictedApi")
class FancyCamera : PreviewView {
    private val VIDEO_RECORDER_PERMISSIONS_REQUEST = 868
    private val VIDEO_RECORDER_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
    private var mFlashEnabled = false
    private val mLock = Any()
    private var listener: CameraEventListener? = null
    private var isRecording = false
    private var recorder: MediaRecorder? = null
    private var isGettingAudioLvls = false
    private var mEMA = 0.0
    private var processCameraProvider: ProcessCameraProvider? = null
    private var mTimer: Timer? = null
    private var mTimerTask: TimerTask? = null
    var duration = 0
    val numberOfCameras: Int
        get() {
            var count = 0
            try {
                count = CameraX.getCameraFactory().availableCameraIds.size
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return count
        }

    var autoSquareCrop = false

    var autoFocus: Boolean = true

    var saveToGallery: Boolean = false

    private var file: File? = null

    private var mCameraSelector: CameraSelector? = null

    var cameraPosition: CameraPosition = CameraPosition.BACK
        set(position) {
            if (!isRecording) {
                if (position != field) {
                    val lens = when (position) {
                        CameraPosition.BACK -> LensFacing.BACK
                        CameraPosition.FRONT -> LensFacing.FRONT
                    }
                    if (mCameraSelector != null) {
                        if (mCameraSelector!!.lensFacing!! != lens) {
                            field = position
                            safeUnbindAll()
                            listener?.onCameraClose()
                            refreshCamera()
                        }
                    } else {
                        field = position
                    }
                }
            }
        }

    var cameraOrientation: CameraOrientation = CameraOrientation.UNKNOWN
        set(orientation) {
            field = orientation
        }


    var isAudioLevelsEnabled: Boolean = false

    val amplitude: Double
        get() {
            var amp = 0.0
            if (isAudioLevelsEnabled) {
                if (cameraRecording()) {
                    amp = (if (recorder != null) recorder?.maxAmplitude else 0)!!.toDouble()
                    return amp
                }
                amp = try {
                    (if (recorder != null) recorder!!.maxAmplitude else 0).toDouble()
                } catch (ignored: Exception) {
                    0.0
                }

            }
            return amp
        }

    val db: Double
        get() = 20 * log10(amplitude / 32767.0)

    val amplitudeEMA: Double
        get() {
            val amp = amplitude
            mEMA = EMA_FILTER * amp + (1.0 - EMA_FILTER) * mEMA
            return mEMA
        }

    var maxAudioBitRate: Int = -1


    var maxVideoBitrate: Int = -1


    var maxVideoFrameRate: Int = -1


    var disableHEVC: Boolean = false

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context, attrs)
    }

    private fun initListener() {
        if (isAudioLevelsEnabled) {
            if (!hasPermission()) {
                return
            }
            if (recorder != null) deInitListener()
            recorder = MediaRecorder()
            recorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder!!.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            recorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            recorder!!.setOutputFile("/dev/null")
            try {
                recorder!!.prepare()
                recorder!!.start()
                isGettingAudioLvls = true
                mEMA = 0.0
            } catch (e: IOException) {
                // Need this ???
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }

    private fun deInitListener() {
        if (isAudioLevelsEnabled && isGettingAudioLvls) {
            try {
                recorder!!.stop()
                recorder!!.release()
                recorder = null
                isGettingAudioLvls = false
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }

    private lateinit var mCameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var mCamera: Camera? = null
    private var mImageCapture: ImageCapture? = null
    private var mVideoCapture: VideoCapture? = null
    private var mPreview: Preview? = null
    private var currentOrientation: Int = Configuration.ORIENTATION_UNDEFINED

    private fun init(context: Context, attrs: AttributeSet?) {
        mCameraProviderFuture = ProcessCameraProvider.getInstance(context)
        if (attrs != null) {
            val a = context.obtainStyledAttributes(
                    attrs,
                    R.styleable.FancyCamera)

            try {
                mFlashEnabled = a.getBoolean(R.styleable.FancyCamera_enableFlash, false)
                saveToGallery = a.getBoolean(R.styleable.FancyCamera_saveToGallery, false)
                quality = Quality.from(a.getInteger(R.styleable.FancyCamera_quality, Quality.MAX_480P.value))
                cameraPosition = CameraPosition.from(a.getInteger(R.styleable.FancyCamera_cameraPosition, CameraPosition.BACK.value))
                cameraOrientation = CameraOrientation.from(a.getInteger(R.styleable.FancyCamera_cameraOrientation, CameraOrientation.UNKNOWN.value))
                disableHEVC = a.getBoolean(R.styleable.FancyCamera_disableHEVC, false)
                maxAudioBitRate = a.getInteger(R.styleable.FancyCamera_maxAudioBitRate, -1)
                maxVideoBitrate = a.getInteger(R.styleable.FancyCamera_maxVideoBitrate, -1)
                maxVideoFrameRate = a.getInteger(R.styleable.FancyCamera_maxVideoFrameRate, -1)
                setEnableAudioLevels(a.getBoolean(R.styleable.FancyCamera_audioLevels, false))

            } finally {
                a.recycle()
            }
        }

        val orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                currentOrientation = when (orientation) {
                    in 0..20, in 340..359 -> 0
                    in 70..110 -> 90
                    in 250..290 -> 270
                    in 160..200 -> 180
                    else -> currentOrientation
                }
            }
        }.enable()
        Futures.addCallback(mCameraProviderFuture, object : FutureCallback<ProcessCameraProvider> {
            override fun onSuccess(result: ProcessCameraProvider?) {
                processCameraProvider = result
                refreshCamera()
            }

            override fun onFailure(t: Throwable?) {
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun refreshCamera() {
        if (!hasPermission()) {
            return;
        }
        try {
            val lensFacing = when (cameraPosition) {
                CameraPosition.FRONT -> {
                    LensFacing.FRONT
                }
                CameraPosition.BACK -> {
                    LensFacing.BACK
                }
            }
            val profile = getCamcorderProfile(quality)

            val cameraOrientation = when (this.cameraOrientation) {
                CameraOrientation.PORTRAIT_UPSIDE_DOWN -> Surface.ROTATION_270
                CameraOrientation.PORTRAIT -> Surface.ROTATION_90
                CameraOrientation.LANDSCAPE_LEFT -> Surface.ROTATION_0
                CameraOrientation.LANDSCAPE_RIGHT -> Surface.ROTATION_180
                else -> -1
            }
            mPreview = Preview.Builder().build()
            mPreview?.previewSurfaceProvider = previewSurfaceProvider
            mCameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            mImageCapture = ImageCapture.Builder()
                    .apply {
                        setCaptureMode(ImageCapture.CaptureMode.MINIMIZE_LATENCY)
                        if (cameraOrientation > -1) {
                            setTargetRotation(cameraOrientation)
                        }
                        setFlashMode(when (mFlashEnabled) {
                            true -> FlashMode.ON
                            else -> FlashMode.OFF
                        })
                    }.build()

            mVideoCapture = VideoCaptureConfig.Builder()
                    .apply {
                        if (cameraOrientation != -1) {
                            setTargetRotation(cameraOrientation)
                        }
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
                    }
                    .build()

            synchronized(mLock) {
                mCamera = processCameraProvider?.bindToLifecycle(context as LifecycleOwner, mCameraSelector!!, mPreview!!, mImageCapture!!, mVideoCapture!!)
                listener?.onCameraOpen()
                isStarted = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isStarted = false
        }
    }

    private var isStarted = false

    private fun getCamcorderProfile(quality: Quality): CamcorderProfile {
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

    fun hasFlash(): Boolean {
        return mCamera?.cameraInfo?.hasFlashUnit() ?: false
    }

    fun toggleTorch() {
        val state = when (mCamera?.cameraInfo?.torchState?.value ?: TorchState.OFF) {
            TorchState.ON -> false
            else -> true
        }
        mCamera?.cameraControl?.enableTorch(state)
    }

    fun enableTorch() {
        mCamera?.cameraControl?.enableTorch(true)
    }

    fun disableTorch() {
        mCamera?.cameraControl?.enableTorch(false)
    }

    fun torchEnabled(): Boolean {
        return mCamera?.cameraInfo?.torchState?.value ?: TorchState.OFF == TorchState.ON
    }


    fun toggleFlash() {
        if (mFlashEnabled) {
            mImageCapture?.flashMode = FlashMode.OFF
            mFlashEnabled = false
        } else {
            mImageCapture?.flashMode = FlashMode.ON
            mFlashEnabled = true
        }
    }

    fun enableFlash() {
        mImageCapture?.flashMode = FlashMode.ON
        mFlashEnabled = true
    }

    fun disableFlash() {
        mImageCapture?.flashMode = FlashMode.OFF
        mFlashEnabled = false
    }

    fun flashEnabled(): Boolean {
        return mFlashEnabled
    }


    fun cameraRecording(): Boolean {
        return isRecording
    }

    fun takePhoto() {
        val df = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        val today = Calendar.getInstance().time
        if (saveToGallery && hasStoragePermission()) {
            val cameraDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
            if (!cameraDir.exists()) {
                cameraDir.mkdirs()
            }
            file = File(cameraDir, "PIC_" + df.format(today) + ".jpg")
        } else {
            file = File(context.getExternalFilesDir(null), "PIC_" + df.format(today) + ".jpg")
        }

        val meta = ImageCapture.Metadata().apply {
            isReversedHorizontal = mCameraSelector?.lensFacing == LensFacing.FRONT
        }

        if (autoSquareCrop) {
            mImageCapture?.takePicture(executorService, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy, rotationDegrees: Int) {
                    var isError = false
                    var outputStream: FileOutputStream? = null
                    try {
                        val buffer = image.planes.first().buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)

                        val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val matrix = Matrix()

                        matrix.postRotate(rotationDegrees.toFloat())

                        if (mCameraSelector?.lensFacing == LensFacing.BACK) {
                            if (currentOrientation == 90) {
                                matrix.postRotate(90f)
                            }

                            if (currentOrientation == 270) {
                                matrix.postRotate(270f)
                            }

                            if (currentOrientation == 180) {
                                matrix.postRotate(180f)
                            }
                        }

                        if (mCameraSelector?.lensFacing == LensFacing.FRONT) {
                            if (currentOrientation == 90) {
                                matrix.postRotate(270f)
                            }

                            if (currentOrientation == 270) {
                                matrix.postRotate(90f)
                            }

                            if (currentOrientation == 180) {
                                matrix.postRotate(180f)
                            }

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
                        outputStream = FileOutputStream(file!!, false)
                        rotated.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)


                        val exif = ExifInterface(file!!.absolutePath)

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

                        bm.recycle()
                        rotated.recycle()
                    } catch (e: Exception) {
                        isError = true
                        listener?.onEvent(Event(EventType.Photo, null, e.localizedMessage))
                    } finally {
                        try {
                            outputStream?.close()
                        } catch (e: IOException) {
                            //NOOP
                        }
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
                                context.sendBroadcast(mediaScanIntent)
                            }
                            listener?.onEvent(Event(EventType.Photo, file, null))
                        }
                    }

                }

                fun onError(imageCaptureError: ImageCapture.ImageCaptureError, message: String, cause: Throwable?) {
                    listener?.onEvent(Event(EventType.Photo, null, message))
                }
            })
        } else {
            mImageCapture?.takePicture(file!!, meta, executorService, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(file: File) {
                    val exif = ExifInterface(file.absolutePath)
                    if (mCameraSelector?.lensFacing == LensFacing.BACK) {
                        if (currentOrientation == 90) {
                            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_180.toString())
                        }

                        if (currentOrientation == 270) {
                            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                        }

                        if (currentOrientation == 180) {
                            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_270.toString())
                        }
                    }

                    if (mCameraSelector?.lensFacing == LensFacing.FRONT) {
                        if (currentOrientation == 90) {
                            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_180.toString())
                        }

                        if (currentOrientation == 270) {
                            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                        }

                        if (currentOrientation == 180) {
                            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                        }
                    }
                    try {
                        exif.saveAttributes()
                    } catch (e: IOException) {

                    }
                    listener?.onEvent(Event(EventType.Photo, file, null))
                }

                override fun onError(imageCaptureError: Int, message: String, cause: Throwable?) {
                    listener?.onEvent(Event(EventType.Photo, null, message))
                }
            })
        }
    }

    var quality: Quality = Quality.MAX_480P
        set(value) {
            if (!isRecording && field != value) {
                field = value
                safeUnbindAll()
                listener?.onCameraClose()
                refreshCamera()
            }
        }

    fun setListener(listener: CameraEventListener) {
        this.listener = listener
    }

    fun start() {
        if (!isStarted) {
            refreshCamera()
        }
    }

    fun stopRecording() {
        mVideoCapture?.stopRecording()
    }

    fun startRecording() {
        deInitListener()
        val df = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        val today = Calendar.getInstance().time
        if (saveToGallery && hasStoragePermission()) {
            val cameraDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
            if (!cameraDir.exists()) {
                cameraDir.mkdirs()
            }
            file = File(cameraDir, "VID_" + df.format(today) + ".mp4")
        } else {
            file = File(context.getExternalFilesDir(null), "VID_" + df.format(today) + ".mp4")
        }
        try {
            mVideoCapture?.startRecording(file!!, executorService, object : VideoCapture.OnVideoSavedCallback {
                override fun onVideoSaved(videoFile: File) {
                    isRecording = false
                    stopDurationTimer()

                    if (isForceStopping) {
                        if (file != null) {
                            file!!.delete()
                        }
                        ContextCompat.getMainExecutor(context).execute {
                            safeUnbindAll()
                            listener?.onCameraClose()
                        }
                        synchronized(mLock) {
                            isForceStopping = false
                        }
                    }

                    if (file != null && saveToGallery && hasStoragePermission()) {
                        val contentUri = Uri.fromFile(file)
                        val mediaScanIntent = Intent(
                                "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                                contentUri
                        )
                        context.sendBroadcast(mediaScanIntent)
                    }

                    listener?.onEvent(Event(EventType.Video, videoFile, null))
                    file = null
                }


                override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                    isRecording = false
                    stopDurationTimer()
                    file = null
                    listener?.onEvent(Event(EventType.Video, null, message))
                    if (isForceStopping) {
                        ContextCompat.getMainExecutor(context).execute {
                            safeUnbindAll()
                        }

                        synchronized(mLock) {
                            isForceStopping = false
                        }
                    }
                }
            })
            isRecording = true
            startDurationTimer()
            listener?.onEvent(Event(EventType.Video, null, null))
        } catch (e: Exception) {
            isRecording = false
            stopDurationTimer()
            if (file != null) {
                file!!.delete()
            }
            safeUnbindAll()
            mCamera = null
            isForceStopping = false
        }
    }

    private var isForceStopping: Boolean = false

    fun stop() {
        if (!isForceStopping) {
            if (isRecording) {
                isForceStopping = true
                stopRecording()
            } else {
                safeUnbindAll()
                listener?.onCameraClose()
            }
        }
    }

    fun release() {
        stop()
        if (!isForceStopping) {
            safeUnbindAll()
            listener?.onCameraClose()
            mPreview?.previewSurfaceProvider = null
            mPreview = null
            mImageCapture = null
            mVideoCapture = null
            mCamera = null
        }
        deInitListener()
    }

    private fun safeUnbindAll() {
        try {
            processCameraProvider?.unbindAll()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isStarted = false
        }
    }

    enum class Quality constructor(val value: Int) {
        MAX_480P(0),
        MAX_720P(1),
        MAX_1080P(2),
        MAX_2160P(3),
        HIGHEST(4),
        LOWEST(5),
        QVGA(6);

        companion object {
            fun from(value: Int): Quality = values().first { it.value == value }
        }
    }

    enum class CameraOrientation constructor(val value: Int) {
        UNKNOWN(0),
        PORTRAIT(1),
        PORTRAIT_UPSIDE_DOWN(2),
        LANDSCAPE_LEFT(3),
        LANDSCAPE_RIGHT(4);

        companion object {
            fun from(value: Int): CameraOrientation = values().first { it.value == value }
        }
    }


    enum class CameraPosition constructor(val value: Int) {
        BACK(0),
        FRONT(1);

        companion object {
            fun from(value: Int): CameraPosition = values().first { it.value == value }
        }
    }


    private fun startDurationTimer() {
        mTimer = Timer()
        mTimerTask = object : TimerTask() {
            override fun run() {
                duration += 1
            }
        }
        mTimer?.schedule(mTimerTask, 0, 1000)
    }

    internal fun stopDurationTimer() {
        mTimerTask?.cancel()
        mTimer?.cancel()
        duration = 0
    }

    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT < 23) {
            true
        } else ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    fun requestStoragePermission() {
        ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 868)
    }

    fun requestPermission() {
        ActivityCompat.requestPermissions(context as Activity, VIDEO_RECORDER_PERMISSIONS, VIDEO_RECORDER_PERMISSIONS_REQUEST)
    }

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT < 23) {
            true
        } else ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun toggleCamera() {
        cameraPosition = when (cameraPosition) {
            CameraPosition.BACK -> CameraPosition.FRONT
            CameraPosition.FRONT -> CameraPosition.BACK
        }
    }

    fun setEnableAudioLevels(enableAudioLevels: Boolean) {

    }

    companion object {
        private val EMA_FILTER = 0.6
        @JvmStatic
        val executorService = Executors.newSingleThreadExecutor()

        @JvmStatic
        fun defaultConfig(context: Context): CameraXConfig {
            return Camera2Config.defaultConfig(context)
        }

        private val TAG = "FancyCamera"
        private val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
        private val SENSOR_ORIENTATION_INVERSE_DEGREES = 270
        private val DEFAULT_ORIENTATIONS = SparseIntArray()
        private val INVERSE_ORIENTATIONS = SparseIntArray()

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
            return try {
                val f = klass.getDeclaredField(fieldName)
                f.getInt(obj)
            } catch (e: Exception) {
                defaultVal
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
}

