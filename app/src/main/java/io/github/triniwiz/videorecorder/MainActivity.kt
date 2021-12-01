package io.github.triniwiz.videorecorder

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle

import androidx.appcompat.app.AppCompatActivity

import android.util.Log
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.VideoView
import io.github.triniwiz.fancycamera.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception
import java.net.URL

import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    internal lateinit var container: RelativeLayout
    internal lateinit var cameraView: FancyCamera
    internal lateinit var videoPlayer: VideoView
    internal lateinit var durationView: TextView
    internal lateinit var timer: Timer
    internal var timerTask: TimerTask? = null
    internal var levelsTask: Timer? = null
    internal var level = 0.0
    var executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //FancyCamera.forceV1 = true
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

                Log.d(
                    "com.fitcom.test",
                    " support " + cameraView.getAvailablePictureSizes("4:3").toList()
                )
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
                Log.d("co.fitcom.test", "Recording Started")
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
        cameraView.detectorType = DetectorType.None // disable to use recorder
        cameraView.setOnBarcodeScanningListener(object : ImageAnalysisCallback {
            override fun onSuccess(result: Any) {
                println("setOnBarcodeScanningListener: Success $result")
            }

            override fun onError(message: String, exception: Exception) {
                println("setOnBarcodeScanningListener: Error $message")
                exception.printStackTrace()
            }
        })
        cameraView.setOnFacesDetectedListener(object : ImageAnalysisCallback {
            override fun onSuccess(result: Any) {
                println("setOnFacesDetectedListener: Success ${result}")
            }

            override fun onError(message: String, exception: Exception) {
                println("setOnFacesDetectedListener: Error $message")
                exception.printStackTrace()
            }
        })
        cameraView.setOnImageLabelingListener(object : ImageAnalysisCallback {
            override fun onSuccess(result: Any) {
                println("setOnImageLabelingListener: Success ${result}")
            }

            override fun onError(message: String, exception: Exception) {
                println("setOnImageLabelingListener: Error $message")
                exception.printStackTrace()
            }
        })
        cameraView.setOnObjectDetectedListener(object : ImageAnalysisCallback {
            override fun onSuccess(result: Any) {
                println("setOnObjectDetectedListener: Success ${result}")
            }

            override fun onError(message: String, exception: Exception) {
                println("setOnObjectDetectedListener: Error $message")
                exception.printStackTrace()
            }
        })
        cameraView.setOnPoseDetectedListener(object : ImageAnalysisCallback {
            override fun onSuccess(result: Any) {
                println("setOnPoseDetectedListener: Success $result")
            }

            override fun onError(message: String, exception: Exception) {
                println("setOnPoseDetectedListener: Error $message")
                exception.printStackTrace()
            }
        })
        cameraView.setOnTextRecognitionListener(object : ImageAnalysisCallback {
            override fun onSuccess(result: Any) {
                println("setOnTextRecognitionListener: Success $result")
            }

            override fun onError(message: String, exception: Exception) {
                println("setOnTextRecognitionListener: Error $message")
                exception.printStackTrace()
            }
        })
        cameraView.setOnSelfieSegmentationListener(object : ImageAnalysisCallback {
            override fun onSuccess(result: Any) {
                println("setOnSelfieSegmentationListener: Success $result")
            }

            override fun onError(message: String, exception: Exception) {
                println("setOnSelfieSegmentationListener: Error $message")
                exception.printStackTrace()
            }
        })
        cameraView.saveToGallery = true
        container.addView(cameraView)
        processBarcodeBitmap()

    }

    fun processBarcodeBitmap() {
        executor.execute {
            try {
                val barcodeFile = File(filesDir, "barcodes.png")
                if (barcodeFile.exists()) {
                    barcodeFile.delete()
                }

                val url =
                    URL("https://www.jqueryscript.net/images/jQuery-Plugin-To-Generate-International-Article-Number-Barcode-EAN13.jpg")
                val fs = FileOutputStream(barcodeFile)
                url.openStream().use { input ->
                    fs.use { output ->
                        input.copyTo(output)
                        input.close()
                    }
                    fs.close()
                }

                val bm = BitmapFactory.decodeFile(barcodeFile.absolutePath)
                val json = JSONObject()
                json.put("detectorType", 0)
                val barcode = JSONObject()
                val formats = JSONArray()
                formats.put(0)
                barcode.put("barcodeFormat", formats)
                json.put("barcodeScanning", barcode)
                ML.processImage(bm, 0, json.toString(), object : ImageAnalysisCallback {
                    override fun onSuccess(result: Any) {
                        (result as? List<Array<Any>>)?.let { values ->
                            for (value in values) {
                                val type = value[0]
                                val data = value[1]
                                Log.d("com.test", "barcode processImage $type:  $data")
                            }
                        }
                    }

                    override fun onError(message: String, exception: Exception) {}
                })
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
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
        if (!cameraView.hasPermission()) {
            cameraView.requestPermission()
        } else {
            cameraView.startPreview()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
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
