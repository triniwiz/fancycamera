/*
 * Created By Osei Fortune on 2/16/18 8:43 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 7:58 PM
 *
 */

package co.fitcom.fancycamera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.TypedArray
import android.graphics.SurfaceTexture
import android.media.MediaRecorder
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import android.util.AttributeSet
import android.view.TextureView
import android.view.ViewGroup

import java.io.File
import java.io.IOException


class FancyCamera : TextureView, TextureView.SurfaceTextureListener {
    private var mFlashEnabled = false
    private val isStarted = false
    private var mCameraPosition = 0
    private var mCameraOrientation = 0
    private var mQuality = Quality.MAX_480P.value
    private val mLock = Any()
    private var listener: CameraEventListener? = null
    private val isReady = false
    private var cameraBase: CameraBase? = null
    private var recorder: MediaRecorder? = null
    private var isGettingAudioLvls = false
    private var mEMA = 0.0
    private var internalListener: CameraEventListener? = null

    val numberOfCameras: Int
        get() = cameraBase!!.numberOfCameras

    internal var autoSquareCrop: Boolean
        get() = cameraBase!!.autoSquareCrop
        set(autoSquareCrop) {
            cameraBase!!.autoSquareCrop = autoSquareCrop
        }

    var autoFocus: Boolean
        get() = cameraBase!!.autoFocus
        set(focus) {
            cameraBase!!.autoFocus = focus
        }

    var saveToGallery: Boolean
        get() = cameraBase!!.saveToGallery
        set(saveToGallery) {
            cameraBase!!.saveToGallery = saveToGallery
        }

    internal var file: File?
        get() = cameraBase!!.file
        set(file) {
            cameraBase!!.file = file
        }

    var cameraPosition: Int
        get() = mCameraPosition
        set(position) = cameraBase!!.setCameraPosition(CameraPosition.values()[position])

    var cameraOrientation: Int
        get() = mCameraOrientation
        set(orientation) = cameraBase!!.setCameraOrientation(CameraOrientation.values()[orientation])

    val duration: Int
        get() = cameraBase!!.duration

    val isAudioLevelsEnabled: Boolean
        get() = cameraBase!!.isAudioLevelsEnabled

    val amplitude: Double
        get() {
            var amp = 0.0
            if (isAudioLevelsEnabled) {
                if (cameraRecording()) {
                    amp = (if (cameraBase!!.recorder != null) cameraBase!!.recorder?.maxAmplitude else 0)!!.toDouble()
                    return amp
                }
                try {
                    amp = (if (recorder != null) recorder!!.maxAmplitude else 0).toDouble()
                } catch (ignored: Exception) {
                    amp = 0.0
                }

            }
            return amp
        }

    val db: Double
        get() = 20 * Math.log10(amplitude / 32767.0)

    val amplitudeEMA: Double
        get() {
            val amp = amplitude
            mEMA = EMA_FILTER * amp + (1.0 - EMA_FILTER) * mEMA
            return mEMA
        }

    var maxAudioBitRate: Int
        get() = cameraBase!!.maxAudioBitRate
        set(maxAudioBitRate) {
            cameraBase!!.maxAudioBitRate = maxAudioBitRate
        }


    var maxVideoBitrate: Int
        get() = cameraBase!!.maxVideoBitrate
        set(maxVideoBitrate) {
            cameraBase!!.maxVideoBitrate = maxVideoBitrate
        }


    var maxVideoFrameRate: Int
        get() = cameraBase!!.maxVideoFrameRate
        set(maxVideoFrameRate) {
            cameraBase!!.maxVideoFrameRate = maxVideoFrameRate
        }


    var disableHEVC: Boolean
        get() = cameraBase!!.disableHEVC
        set(disableHEVC) {
            cameraBase!!.disableHEVC = disableHEVC
        }

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

    fun deInitListener() {
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

    private fun init(context: Context, attrs: AttributeSet?) {
        if (Build.VERSION.SDK_INT >= 21) {
            cameraBase = Camera2(getContext(), this, CameraPosition.values()[cameraPosition], CameraOrientation.values()[cameraOrientation])
        } else {
            cameraBase = Camera1(getContext(), this, CameraPosition.values()[cameraPosition])
        }

        internalListener = object : CameraEventListener {
            override fun onCameraOpen() {
                initListener()
                if (listener != null) {
                    listener!!.onCameraOpen()
                }
            }

            override fun onCameraClose() {
                deInitListener()
                if (listener != null) {
                    listener!!.onCameraClose()
                }
            }

            override fun onPhotoEvent(event: PhotoEvent) {
                if (listener != null) {
                    listener!!.onPhotoEvent(event)
                }
            }

            override fun onVideoEvent(event: VideoEvent) {
                if (listener != null) {
                    listener!!.onVideoEvent(event)
                }
            }
        }

        cameraBase!!.listener = internalListener

        if (attrs != null) {
            val a = context.obtainStyledAttributes(
                    attrs,
                    R.styleable.FancyCamera)

            try {
                mFlashEnabled = a.getBoolean(R.styleable.FancyCamera_enableFlash, false)
                saveToGallery = a.getBoolean(R.styleable.FancyCamera_saveToGallery, false)
                mQuality = a.getInteger(R.styleable.FancyCamera_quality, Quality.MAX_480P.value)
                setQuality(mQuality)
                mCameraPosition = a.getInteger(R.styleable.FancyCamera_cameraPosition, 0)
                cameraPosition = mCameraPosition
                mCameraOrientation = a.getInteger(R.styleable.FancyCamera_cameraOrientation, 0)
                cameraOrientation = mCameraOrientation
                disableHEVC = a.getBoolean(R.styleable.FancyCamera_disableHEVC, false)
                maxAudioBitRate = a.getInteger(R.styleable.FancyCamera_maxAudioBitRate, -1)
                maxVideoBitrate = a.getInteger(R.styleable.FancyCamera_maxVideoBitrate, -1)
                maxVideoFrameRate = a.getInteger(R.styleable.FancyCamera_maxVideoFrameRate, -1)
                setEnableAudioLevels(a.getBoolean(R.styleable.FancyCamera_audioLevels, false))

            } finally {
                a.recycle()
            }
        }
        this.surfaceTextureListener = this
    }

    fun hasFlash(): Boolean {
        return cameraBase!!.hasFlash()
    }

    fun toggleFlash() {
        cameraBase!!.toggleFlash()
    }

    fun enableFlash() {
        cameraBase!!.enableFlash()
    }

    fun disableFlash() {
        cameraBase!!.disableFlash()
    }

    fun flashEnabled(): Boolean {
        return cameraBase!!.flashEnabled()
    }

    fun cameraStarted(): Boolean {
        return cameraBase!!.cameraStarted()
    }

    fun cameraRecording(): Boolean {
        return cameraBase!!.cameraRecording()
    }

    fun takePhoto() {
        cameraBase!!.takePhoto()
    }

    fun setQuality(quality: Int) {
        mQuality = Quality.MAX_480P.value
        when (quality) {
            0 -> mQuality = 0
            1 -> mQuality = 1
            2 -> mQuality = 2
            3 -> mQuality = 3
            4 -> mQuality = 4
            5 -> mQuality = 5
            6 -> mQuality = 6
        }
        cameraBase!!.quality = mQuality
    }

    fun setListener(listener: CameraEventListener) {
        this.listener = listener
    }

    fun setCameraPosition(position: FancyCamera.CameraPosition) {
        cameraBase!!.setCameraPosition(position)
    }

    fun setCameraOrientation(orientation: FancyCamera.CameraOrientation) {
        cameraBase!!.setCameraOrientation(orientation)
    }

    fun requestPermission() {
        cameraBase!!.requestPermission()
    }

    fun hasPermission(): Boolean {
        return cameraBase!!.hasPermission()
    }

    fun hasStoragePermission(): Boolean {
        return cameraBase!!.hasStoragePermission()
    }

    fun requestStoragePermission() {
        cameraBase!!.requestStoragePermission()
    }

    fun start() {
        cameraBase!!.start()
    }

    fun stopRecording() {
        cameraBase!!.stopRecording()
    }

    fun startRecording() {
        deInitListener()
        cameraBase!!.startRecording()
    }

    fun stop() {
        cameraBase!!.stop()
    }

    fun release() {
        cameraBase!!.release()
        deInitListener()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (cameraBase!!.textViewListener != null) {
            cameraBase!!.textViewListener!!.onSurfaceTextureAvailable(surface, width, height)
        }
        if (!hasPermission()) {
            requestPermission()
            return
        }
        cameraBase!!.openCamera(width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        if (cameraBase!!.textViewListener != null) {
            cameraBase!!.textViewListener!!.onSurfaceTextureSizeChanged(surface, width, height)
        }
        cameraBase!!.updatePreview()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        if (cameraBase!!.textViewListener != null) {
            cameraBase!!.textViewListener!!.onSurfaceTextureDestroyed(surface)
        }
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        if (cameraBase!!.textViewListener != null) {
            cameraBase!!.textViewListener!!.onSurfaceTextureUpdated(surface)
        }
    }

    enum class Quality private constructor(val value: Int) {
        MAX_480P(0),
        MAX_720P(1),
        MAX_1080P(2),
        MAX_2160P(3),
        HIGHEST(4),
        LOWEST(5),
        QVGA(6)
    }

    enum class CameraOrientation private constructor(val value: Int) {
        UNKNOWN(0),
        PORTRAIT(1),
        PORTRAIT_UPSIDE_DOWN(2),
        LANDSCAPE_LEFT(3),
        LANDSCAPE_RIGHT(4)
    }

    enum class CameraPosition private constructor(val value: Int) {
        BACK(0),
        FRONT(1)
    }

    fun toggleCamera() {
        cameraBase!!.toggleCamera()
    }

    fun setEnableAudioLevels(enableAudioLevels: Boolean) {
        cameraBase!!.setEnableAudioLevels(enableAudioLevels)
    }

    companion object {
        private val EMA_FILTER = 0.6
    }
}
