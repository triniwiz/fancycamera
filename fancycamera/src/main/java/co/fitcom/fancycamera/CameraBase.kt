/*
 * Created By Osei Fortune on 2/16/18 8:42 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 8:42 PM
 *
 */

package co.fitcom.fancycamera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.view.TextureView

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import java.io.File
import java.util.Timer
import java.util.TimerTask

abstract class CameraBase internal constructor(val holder: TextureView) {
    private var mTimer: Timer? = null
    private var mTimerTask: TimerTask? = null
    internal var duration = 0
        private set
    internal var file: File? = null
    var listener: CameraEventListener? = null
    internal abstract var quality: Int

    private val VIDEO_RECORDER_PERMISSIONS_REQUEST = 868
    private val VIDEO_RECORDER_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)

    var textViewListener: TextViewListener? = null

    abstract val isAudioLevelsEnabled: Boolean

    internal abstract var autoSquareCrop: Boolean

    internal abstract val recorder: MediaRecorder?

    internal abstract var saveToGallery: Boolean

    abstract var autoFocus: Boolean

    internal abstract var maxAudioBitRate: Int

    internal abstract var maxVideoBitrate: Int

    internal abstract var maxVideoFrameRate: Int
    internal abstract var didPauseForPermission: Boolean

    abstract var disableHEVC: Boolean

    abstract val numberOfCameras: Int

    abstract fun setEnableAudioLevels(enable: Boolean)

    internal abstract fun hasCamera(): Boolean

    internal abstract fun hasFlash(): Boolean

    internal abstract fun cameraStarted(): Boolean

    internal abstract fun cameraRecording(): Boolean

    internal abstract fun openCamera(width: Int, height: Int)

    internal abstract fun start()

    internal abstract fun stop()

    internal abstract fun startRecording()

    internal abstract fun takePhoto()

    internal abstract fun stopRecording()

    internal abstract fun toggleCamera()

    internal abstract fun updatePreview()

    internal abstract fun release()

    internal abstract fun setCameraPosition(position: FancyCamera.CameraPosition)

    internal abstract fun setCameraOrientation(orientation: FancyCamera.CameraOrientation)

    internal abstract fun toggleFlash()

    internal abstract fun enableFlash()

    internal abstract fun disableFlash()

    internal abstract fun flashEnabled(): Boolean

    internal fun startDurationTimer() {
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
        } else ContextCompat.checkSelfPermission(holder.context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    fun requestStoragePermission() {
        didPauseForPermission = true
        ActivityCompat.requestPermissions(holder.context as Activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 868)
    }

    fun requestPermission() {
        didPauseForPermission = true
        ActivityCompat.requestPermissions(holder.context as Activity, VIDEO_RECORDER_PERMISSIONS, VIDEO_RECORDER_PERMISSIONS_REQUEST)
    }

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT < 23) {
            true
        } else ContextCompat.checkSelfPermission(holder.context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(holder.context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    companion object {

        val CameraThread = "CameraThread"
    }

}