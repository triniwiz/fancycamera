package co.fitcom.videorecorder

import android.content.Intent
import android.net.Uri
import android.os.Bundle

import androidx.appcompat.app.AppCompatActivity

import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.VideoView

import java.util.Timer
import java.util.TimerTask

import co.fitcom.fancycamera.CameraEventListenerUI
import co.fitcom.fancycamera.EventType
import co.fitcom.fancycamera.FancyCamera
import co.fitcom.fancycamera.PhotoEvent
import co.fitcom.fancycamera.VideoEvent

class MainActivity : AppCompatActivity() {
    internal lateinit var cameraView: FancyCamera
    internal lateinit var videoPlayer: VideoView
    internal lateinit var durationView: TextView
    internal lateinit var timer: Timer
    internal var timerTask: TimerTask? = null
    internal var levelsTask: Timer? = null
    internal var level = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        videoPlayer = findViewById(R.id.videoPlayer)
        durationView = findViewById(R.id.durationView)
        cameraView = findViewById(R.id.cameraView)
        cameraView.setCameraPosition(FancyCamera.CameraPosition.BACK)
        cameraView.setQuality(FancyCamera.Quality.HIGHEST.value)
        cameraView.setListener(object : CameraEventListenerUI() {
            override fun onCameraOpenUI() {
                Log.d("co.fitcom.test", "Camera Opened")
            }

            override fun onCameraCloseUI() {
                Log.d("co.fitcom.test", "Camera Close")
            }

            override fun onPhotoEventUI(event: PhotoEvent) {

            }

            override fun onVideoEventUI(event: VideoEvent) {
                if (event.type === EventType.INFO && event.message == VideoEvent.EventInfo.RECORDING_FINISHED.toString()) {
                    timerTask!!.cancel()
                    timer.cancel()
                    videoPlayer.setVideoURI(Uri.fromFile(event.file))
                    videoPlayer.start()
                } else if (event.type === EventType.INFO && event.message == VideoEvent.EventInfo.RECORDING_STARTED.toString()) {
                    println("Recording Started")
                    timer = Timer()
                    timerTask = object : TimerTask() {
                        override fun run() {
                            runOnUiThread { durationView.text = cameraView.duration.toString() }
                        }
                    }
                    timer.schedule(timerTask, 0, 1000)

                } else {
                    println(event.message)
                }
            }

        })
        cameraView.saveToGallery = true
    }

    fun startRecording(view: View) {
        cameraView.setQuality(FancyCamera.Quality.MAX_720P.value)
        cameraView.startRecording()
    }

    fun stopRecording(view: View) {
        cameraView.stopRecording()
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
        cameraView.toggleFlash()
    }

    override fun onPause() {
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
