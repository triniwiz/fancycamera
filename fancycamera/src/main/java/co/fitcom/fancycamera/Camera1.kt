/*
 * Created By Osei Fortune on 2/16/18 8:42 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 7:39 PM
 *
 */

package co.fitcom.fancycamera

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView

import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask


class Camera1 internal constructor(private val mContext: Context, textureView: TextureView, position: FancyCamera.CameraPosition?) : CameraBase(textureView) {
    override var didPauseForPermission: Boolean = false
    override var quality: Int = 0
    private val lock = Any()
    private var mCamera: Camera? = null
    private var mPosition: FancyCamera.CameraPosition? = null
    private var mOrientation: FancyCamera.CameraOrientation? = null
    private var backgroundHandler: Handler? = null
    private var backgroundHandlerThread: HandlerThread? = null
    private var isRecording: Boolean = false
    internal override var recorder: MediaRecorder? = null
        private set
    private var isStarted: Boolean = false
    private val autoStart: Boolean = false
    private var profile: CamcorderProfile? = null
        set(profile) {
            synchronized(lock) {
                field = profile
            }
        }
    private val mTimer: Timer? = null
    private val mTimerTask: TimerTask? = null
    private val mDuration = 0
    private var isFlashEnabled = false
    override var autoFocus = true
        set(focus) {
            synchronized(lock) {
                field = focus
            }
        }
    override var disableHEVC = false
        set(disableHEVC) {
            synchronized(lock) {
                field = disableHEVC
            }
        }
    override var maxVideoBitrate = -1
        set(maxVideoBitrate) {
            synchronized(lock) {
                field = maxVideoBitrate
            }
        }
    override var maxAudioBitRate = -1
        set(maxAudioBitRate) {
            synchronized(lock) {
                field = maxAudioBitRate
            }
        }
    override var maxVideoFrameRate = -1
        set(maxVideoFrameRate) {
            synchronized(lock) {
                field = maxVideoFrameRate
            }
        }
    override var saveToGallery = false
        set(saveToGallery) {
            synchronized(lock) {
                field = saveToGallery
            }
        }
    internal override var autoSquareCrop = false
        set(autoSquareCrop) {
            synchronized(lock) {
                field = autoSquareCrop
            }
        }
    override var isAudioLevelsEnabled = false
        private set

    override val numberOfCameras: Int
        get() = Camera.getNumberOfCameras()

    init {
        if (position == null) {
            mPosition = FancyCamera.CameraPosition.BACK
        } else {
            mPosition = position
        }
        startBackgroundThread()
        textViewListener = object : TextViewListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {

            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {

            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) {

            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {

            }
        }
    }

    override fun setEnableAudioLevels(enable: Boolean) {
        synchronized(lock) {
            isAudioLevelsEnabled = enable
        }
    }

    internal override fun hasCamera(): Boolean {
        return Camera.getNumberOfCameras() > 0
    }

    internal override fun cameraStarted(): Boolean {
        return isStarted
    }

    internal override fun cameraRecording(): Boolean {
        return isRecording
    }

    private fun startBackgroundThread() {
        synchronized(lock) {
            backgroundHandlerThread = HandlerThread(CameraBase.CameraThread)
            backgroundHandlerThread!!.start()
            backgroundHandler = Handler(backgroundHandlerThread!!.looper)
        }
    }

    private fun stopBackgroundThread() {
        synchronized(lock) {
            backgroundHandlerThread!!.interrupt()
            try {
                backgroundHandlerThread!!.join()
                backgroundHandlerThread = null
                backgroundHandler = null
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

        }
    }

    internal override fun openCamera(width: Int, height: Int) {
        try {
            backgroundHandler!!.post {
                synchronized(lock) {
                    mCamera = Camera.open(mPosition!!.value)
                    listener?.onCameraOpen()
                    updatePreview()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun setupPreview() {
        if (mCamera == null) return
        backgroundHandler!!.post {
            synchronized(lock) {
                try {
                    mCamera!!.reconnect()
                    mCamera!!.setPreviewTexture(holder.surfaceTexture)
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
        }
    }

    internal override fun start() {
        backgroundHandler!!.post {
            synchronized(lock) {
                if (holder.isAvailable) {
                    updatePreview()
                }
            }
        }
    }

    internal override fun stop() {
        backgroundHandler!!.post(Runnable {
            synchronized(lock) {
                if (mCamera == null) return@Runnable
                mCamera!!.stopPreview()
                try {
                    mCamera!!.setPreviewTexture(null)
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                mCamera!!.release()
                mCamera = null
                isStarted = false
                listener?.onCameraClose()
            }
        })
    }

    internal override fun startRecording() {
        synchronized(lock) {
            if (isRecording) {
                return
            }
            val params = mCamera!!.parameters
            var profile = getCamcorderProfile(FancyCamera.Quality.values()[quality])
            val mSupportedPreviewSizes = params.supportedPreviewSizes
            val mSupportedVideoSizes = params.supportedVideoSizes
            val optimalSize = getOptimalVideoSize(mSupportedVideoSizes,
                    mSupportedPreviewSizes, holder.width, holder.height)

            profile.videoFrameWidth = optimalSize!!.width
            profile.videoFrameHeight = optimalSize.height
            params.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight)
            if (autoFocus) {
                val supportedFocusModes = params.supportedFocusModes
                if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    params.focusMode = android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                } else if (supportedFocusModes.contains(android.hardware.Camera.Parameters.FOCUS_MODE_AUTO)) {
                    params.focusMode = android.hardware.Camera.Parameters.FOCUS_MODE_AUTO
                }
            } else {
                val supportedFocusModes = params.supportedFocusModes
                if (supportedFocusModes.contains(android.hardware.Camera.Parameters.FOCUS_MODE_FIXED)) {
                    params.focusMode = android.hardware.Camera.Parameters.FOCUS_MODE_FIXED
                }
            }

            profile = profile
            mCamera!!.parameters = params
            if (recorder == null) {
                recorder = MediaRecorder()
            } else {
                recorder!!.reset()
            }
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

            val df = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            val today = Calendar.getInstance().time
            if (saveToGallery) {
                val cameraDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
                if (!cameraDir.exists()) {
                    val mkdirs = cameraDir.mkdirs()
                }
                file = File(cameraDir, "VID_" + df.format(today) + ".mp4")
            } else {
                file = File(mContext.getExternalFilesDir(null), "VID_" + df.format(today) + ".mp4")
            }
            mCamera!!.unlock()
            try {
                val camcorderProfile = profile
                recorder!!.setCamera(mCamera)
                recorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
                recorder!!.setVideoSource(MediaRecorder.VideoSource.CAMERA)
                recorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder!!.setVideoSize(camcorderProfile.videoFrameWidth, camcorderProfile.videoFrameHeight)
                recorder!!.setAudioChannels(camcorderProfile.audioChannels)
                val videoBitRate = camcorderProfile.videoBitRate
                var maxVideoBitrate = camcorderProfile.videoBitRate
                if (this.maxVideoBitrate > -1) {
                    maxVideoBitrate = this.maxVideoBitrate
                }
                var maxVideoFrameRate = camcorderProfile.videoFrameRate
                if (this.maxVideoFrameRate > -1) {
                    maxVideoFrameRate = this.maxVideoFrameRate
                }
                var maxAudioBitRate = camcorderProfile.audioBitRate
                if (this.maxAudioBitRate > -1) {
                    maxAudioBitRate = this.maxAudioBitRate
                }
                recorder!!.setVideoFrameRate(Math.min(camcorderProfile.videoFrameRate, maxVideoFrameRate))
                recorder!!.setVideoEncodingBitRate(Math.min(camcorderProfile.videoBitRate, maxVideoBitrate))
                recorder!!.setAudioEncodingBitRate(Math.min(camcorderProfile.audioBitRate, maxAudioBitRate))
                recorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                recorder!!.setAudioEncoder(camcorderProfile.audioCodec)
                recorder!!.setOutputFile(file!!.path)
                recorder!!.prepare()
                recorder!!.start()
                isRecording = true
                startDurationTimer()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (listener != null) {
                listener?.onVideoEvent(VideoEvent(EventType.INFO, null, VideoEvent.EventInfo.RECORDING_STARTED.toString()))
            }
        }
    }

    internal override fun takePhoto() {
        synchronized(lock) {
            if (isRecording) {
                return
            }
            val params = mCamera!!.parameters
            var profile = getCamcorderProfile(FancyCamera.Quality.values()[quality])
            val mSupportedPreviewSizes = params.supportedPreviewSizes
            val mSupportedVideoSizes = params.supportedVideoSizes
            val optimalSize = getOptimalVideoSize(mSupportedVideoSizes,
                    mSupportedPreviewSizes, holder.width, holder.height)

            var width = optimalSize!!.width
            var height = optimalSize.height
            if (autoSquareCrop) {
                val offsetWidth: Int
                val offsetHeight: Int
                if (width < height) {
                    offsetHeight = (height - width) / 2
                    height = width - offsetHeight
                } else {
                    offsetWidth = (width - height) / 2
                    width = height - offsetWidth
                }
            }

            profile.videoFrameWidth = width
            profile.videoFrameHeight = height


            params.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight)
            profile = profile
            mCamera!!.parameters = params
            val df = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            val today = Calendar.getInstance().time
            if (saveToGallery) {
                val cameraDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
                if (!cameraDir.exists()) {
                    val mkdirs = cameraDir.mkdirs()
                }
                file = File(cameraDir, "PIC_" + df.format(today) + ".jpg")
            } else {
                file = File(mContext.getExternalFilesDir(null), "PIC_" + df.format(today) + ".jpg")
            }
            mCamera!!.takePicture(null, null, Camera.PictureCallback { data, camera ->
                var fos: FileOutputStream? = null
                try {
                    fos = FileOutputStream(file!!)
                    fos.write(data)
                    val contentUri = Uri.fromFile(file)
                    val mediaScanIntent = android.content.Intent(
                            "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                            contentUri
                    )
                    mContext.sendBroadcast(mediaScanIntent)
                    val event = PhotoEvent(EventType.INFO, file, PhotoEvent.EventInfo.PHOTO_TAKEN.toString())
                    listener?.onPhotoEvent(event)
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    if (fos != null) {
                        try {
                            fos.close()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }

                    }
                }
            })
        }
    }

    internal override fun stopRecording() {
        synchronized(lock) {
            if (!isRecording) {
                return
            }
            try {
                recorder!!.stop()
                stopDurationTimer()
                recorder!!.reset()
                recorder!!.release()
                recorder = null
                val contentUri = Uri.fromFile(file)
                val mediaScanIntent = android.content.Intent(
                        "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                        contentUri
                )
                mContext.sendBroadcast(mediaScanIntent)
                listener?.onVideoEvent(VideoEvent(EventType.INFO, file, VideoEvent.EventInfo.RECORDING_FINISHED.toString()))
            } catch (e: Exception) {
                e.printStackTrace()
                val delete = file!!.delete()
                stopDurationTimer()
            } finally {
                isRecording = false
                mCamera!!.lock()
            }
        }
    }

    internal override fun toggleCamera() {
        synchronized(lock) {
            stop()
            if (mPosition == FancyCamera.CameraPosition.BACK) {
                setCameraPosition(FancyCamera.CameraPosition.FRONT)
            } else {
                setCameraPosition(FancyCamera.CameraPosition.BACK)
            }
            openCamera(holder.width, holder.height)
            // updatePreview();
        }
    }

    internal override fun updatePreview() {
        backgroundHandler!!.post {
            synchronized(lock) {
                setupPreview()
                if (mCamera != null) {
                    if (isStarted) {
                        mCamera!!.stopPreview()
                        isStarted = false
                    }
                    updatePreviewSize()
                    updateCameraDisplayOrientation(mContext as Activity, mPosition!!.value, mCamera)
                    setupPreview()
                    if (!isStarted) {
                        mCamera!!.startPreview()
                        isStarted = true
                    }
                    isStarted = true
                }
            }
        }
    }

    internal override fun release() {
        synchronized(lock) {
            if (isRecording) {
                stopRecording()
            }
            stop()
        }
    }

    internal override fun setCameraPosition(position: FancyCamera.CameraPosition) {
        synchronized(lock) {
            stop()

            if (Camera.getNumberOfCameras() < 2) {
                mPosition = FancyCamera.CameraPosition.BACK
            } else {
                mPosition = position
            }
            if (isStarted) {
                start()
            }
        }
    }

    internal override fun setCameraOrientation(orientation: FancyCamera.CameraOrientation) {
        synchronized(lock) {
            mOrientation = orientation
        }
    }


    internal override fun hasFlash(): Boolean {
        if (mCamera != null) {
            val parameters = mCamera!!.parameters
            var hasFlash = false
            for (mode in parameters.supportedFlashModes) {
                if (mode == "on" || mode == "auto") {
                    hasFlash = true
                    break
                }
            }
            return hasFlash
        }

        return false
    }


    internal override fun toggleFlash() {
        synchronized(lock) {
            if (!hasFlash()) {
                return
            }
            isFlashEnabled = !isFlashEnabled
            if (mCamera != null) {
                val parameters = mCamera!!.parameters
                parameters.flashMode = if (isFlashEnabled) Camera.Parameters.FLASH_MODE_ON else Camera.Parameters.FLASH_MODE_OFF
                mCamera!!.parameters = parameters
            }
        }
    }

    internal override fun enableFlash() {
        synchronized(lock) {
            if (!hasFlash()) {
                return
            }
            isFlashEnabled = true
            if (mCamera != null) {
                val parameters = mCamera!!.parameters
                parameters.flashMode = Camera.Parameters.FLASH_MODE_ON
                mCamera!!.parameters = parameters
            }
        }
    }

    internal override fun disableFlash() {
        synchronized(lock) {
            if (!hasFlash()) {
                return
            }
            isFlashEnabled = false
            if (mCamera != null) {
                val parameters = mCamera!!.parameters
                parameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
                mCamera!!.parameters = parameters
            }
        }
    }

    internal override fun flashEnabled(): Boolean {
        return isFlashEnabled
    }

    private fun updatePreviewSize() {
        synchronized(lock) {
            val params = mCamera!!.parameters
            val mSupportedPreviewSizes = params.supportedPreviewSizes
            val mSupportedVideoSizes = params.supportedVideoSizes
            val optimalSize = getOptimalVideoSize(mSupportedVideoSizes,
                    mSupportedPreviewSizes, holder.width, holder.height)
            params.setPreviewSize(optimalSize!!.width, optimalSize.height)
            mCamera!!.parameters = params
        }
    }

    private fun updateCameraDisplayOrientation(activity: Activity,
                                               cameraId: Int, camera: Camera?) {
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

            var result: Int
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360
                result = (360 - result) % 360
            } else {
                result = (info.orientation - degrees + 360) % 360
            }
            camera!!.setDisplayOrientation(result)
        }
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

    private fun getOptimalVideoSize(supportedVideoSizes: List<Camera.Size>?,
                                    previewSizes: List<Camera.Size>, w: Int, h: Int): Camera.Size? {
        // Use a very small tolerance because we want an exact match.
        val ASPECT_TOLERANCE = 0.1
        val targetRatio = w.toDouble() / h

        // Supported video sizes list might be null, it means that we are allowed to use the preview
        // sizes
        val videoSizes: List<Camera.Size>
        if (supportedVideoSizes != null) {
            videoSizes = supportedVideoSizes
        } else {
            videoSizes = previewSizes
        }
        var optimalSize: Camera.Size? = null

        // Start with max value and refine as we iterate over available video sizes. This is the
        // minimum difference between view and camera height.
        var minDiff = java.lang.Double.MAX_VALUE

        // Target view height

        // Try to find a video size that matches aspect ratio and the target view size.
        // Iterate over all available sizes and pick the largest size that can fit in the view and
        // still maintain the aspect ratio.
        for (size in videoSizes) {
            val ratio = size.width.toDouble() / size.height
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue
            if (Math.abs(size.height - h) < minDiff && previewSizes.contains(size)) {
                optimalSize = size
                minDiff = Math.abs(size.height - h).toDouble()
            }
        }

        // Cannot find video size that matches the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = java.lang.Double.MAX_VALUE
            for (size in videoSizes) {
                if (Math.abs(size.height - h) < minDiff && previewSizes.contains(size)) {
                    optimalSize = size
                    minDiff = Math.abs(size.height - h).toDouble()
                }
            }
        }
        return optimalSize
    }
}
