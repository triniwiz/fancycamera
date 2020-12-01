package com.github.triniwiz.videorecorder

import android.content.Intent
import android.net.Uri
import android.os.Bundle

import androidx.appcompat.app.AppCompatActivity

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.triniwiz.fancycamera.*
import java.io.File
import java.lang.Exception

import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity() {
    internal lateinit var container: RelativeLayout
    internal lateinit var cameraView: FancyCamera
    internal lateinit var videoPlayer: VideoView
    internal lateinit var durationView: TextView
    internal lateinit var timer: Timer
    internal var timerTask: TimerTask? = null
    internal var levelsTask: Timer? = null
    internal var level = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FancyCamera.forceV1 = true
        setContentView(R.layout.activity_main)
        videoPlayer = findViewById(R.id.videoPlayer)
        durationView = findViewById(R.id.durationView)
        container = findViewById(R.id.container)
        cameraView = FancyCamera(this)
        cameraView.ratio = "16:9"
        cameraView.autoFocus = true
        cameraView.position = CameraPosition.BACK
        cameraView.setListener(object : CameraEventListenerUI() {
            override fun onReadyUI() {
            }

            override fun onCameraOpenUI() {
                Log.d("co.fitcom.test", "Camera Opened")

                // cameraView.getAvailablePictureSizes

                Log.d("com.fitcom.test", " support " + cameraView.getSupportedRatios.toList())

                Log.d("com.fitcom.test", " support " + cameraView.getAvailablePictureSizes("4:3").toList())
            }

            override fun onCameraCloseUI() {
                Log.d("co.fitcom.test", "Camera Close")
            }

            override fun onCameraPhotoUI(file: File?) {

            }

            override fun onCameraVideoUI(file: File?) {
                timerTask!!.cancel()
                timer.cancel()
                videoPlayer.setVideoURI(Uri.fromFile(file))
                videoPlayer.start()
            }

            override fun onCameraVideoStartUI() {
                println("Recording Started")
                timer = Timer()
                timerTask = object : TimerTask() {
                    override fun run() {
                        println("Recording duration " + cameraView.duration.toString())
                        runOnUiThread { durationView.text = cameraView.duration.toString() }
                    }
                }
                timer.schedule(timerTask, 0, 1000)
            }

            override fun onCameraAnalysisUI(analysis: ImageAnalysis) {
                TODO("Not yet implemented")
            }

            override fun onCameraErrorUI(message: String, ex: Exception) {
                println(message)
            }

        })
        cameraView.detectorType = DetectorType.All
        cameraView.setOnBarcodeScanningListener(object : ImageAnalysisCallback {
            override fun onSuccess(result: String) {
                println("setOnBarcodeScanningListener: Success $result")
            }

            override fun onError(message: String, exception: Exception) {
                println("setOnBarcodeScanningListener: Error $message")
                exception.printStackTrace()
            }
        })
        cameraView.setOnFacesDetectedListener(object : ImageAnalysisCallback {
            override fun onSuccess(result: String) {
                println("setOnFacesDetectedListener: Success ${result}")
            }

            override fun onError(message: String, exception: Exception) {
                println("setOnFacesDetectedListener: Error $message")
                exception.printStackTrace()
            }
        })
        cameraView.setOnImageLabelingListener(object : ImageAnalysisCallback {
            override fun onSuccess(result: String) {
                println("setOnImageLabelingListener: Success ${result}")
            }

            override fun onError(message: String, exception: Exception) {
                println("setOnImageLabelingListener: Error $message")
                exception.printStackTrace()
            }
        })
        cameraView.setOnObjectDetectedListener(object : ImageAnalysisCallback {
            override fun onSuccess(result: String) {
                println("setOnObjectDetectedListener: Success ${result}")
            }

            override fun onError(message: String, exception: Exception) {
                println("setOnObjectDetectedListener: Error $message")
                exception.printStackTrace()
            }
        })
        cameraView.setOnPoseDetectedListener(object : ImageAnalysisCallback {
            override fun onSuccess(result: String) {
                println("setOnPoseDetectedListener: Success $result")
            }

            override fun onError(message: String, exception: Exception) {
                println("setOnPoseDetectedListener: Error $message")
                exception.printStackTrace()
            }
        })
        cameraView.setOnTextRecognitionListener(object : ImageAnalysisCallback {
            override fun onSuccess(result: String) {
                println("setOnTextRecognitionListener: Success $result")
            }

            override fun onError(message: String, exception: Exception) {
                println("setOnTextRecognitionListener: Error $message")
                exception.printStackTrace()
            }
        })
        cameraView.saveToGallery = true
    }

    fun startRecording(view: View) {
        cameraView.quality = Quality.HIGHEST
        cameraView.startRecording()
    }

    fun stopRecording(view: View) {
        cameraView.stopRecording()
    }

    fun startPreview(view: View) {
        cameraView.startPreview()
    }

    fun stopPreview(view: View) {
        cameraView.stopPreview()
    }

    fun toggleCamera(view: View) {
        cameraView.toggleCamera()
    }


    fun goToVideo(view: View) {
        val i = Intent(this, MainActivity::class.java)
        startActivity(i)
    }

    fun goToPhoto(view: View) {
        val i = Intent(this, Photo::class.java)
        startActivity(i)
    }

    fun toggleFlash(view: View) {
        if (cameraView.flashMode != CameraFlashMode.OFF) {
            cameraView.flashMode = CameraFlashMode.OFF
        } else {
            cameraView.flashMode = CameraFlashMode.ON
        }
    }

    override fun onPause() {
        cameraView.stop()
        super.onPause()
        if (levelsTask != null) {
            levelsTask!!.cancel()
        }
        if (timerTask != null) {
            timerTask!!.cancel()
        }
        timerTask = null
        levelsTask = null
    }


    override fun onResume() {
        super.onResume()
        container.addView(cameraView)
        if (!cameraView.hasPermission()) {
            cameraView.requestPermission()
        } else {
       //     cameraView.startPreview()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        cameraView.onPermissionHandler(requestCode, permissions as Array<String>, grantResults)
    }

    internal fun start() {
        if (cameraView.isAudioLevelsEnabled) {
            if (levelsTask == null) {
                levelsTask = Timer()
                levelsTask!!.scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        runOnUiThread {
                            level = Math.pow(10.0, 0.02 * cameraView.db)
                            Log.d("co.test", "Audio Levels$level")
                        }
                    }
                }, 0, 1000)
            }
        }
    }

}
