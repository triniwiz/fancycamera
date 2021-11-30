package io.github.triniwiz.videorecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import io.github.triniwiz.fancycamera.*
import java.io.File

class Photo : AppCompatActivity() {
    internal lateinit var cameraView: FancyCamera
    internal lateinit var imageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo)

        imageView = findViewById(R.id.imageView)
        cameraView = findViewById(R.id.PhotoView)
        //FancyCamera.forceV1 = true
        cameraView.autoFocus = true
        cameraView.quality = Quality.HIGHEST
        cameraView.setListener(object : CameraEventListenerUI() {
            override fun onReadyUI() {
                
            }

            override fun onCameraOpenUI() {

            }

            override fun onCameraCloseUI() {

            }

            override fun onCameraPhotoUI(file: File?) {
                imageView.setImageURI(Uri.fromFile(file))
            }

            override fun onCameraVideoUI(file: File?) {
                TODO("Not yet implemented")
            }

            override fun onCameraVideoStartUI() {
                TODO("Not yet implemented")
            }

            override fun onCameraAnalysisUI(analysis: ImageAnalysis) {
                TODO("Not yet implemented")
            }

            override fun onCameraErrorUI(message: String, ex: Exception) {
                println(message)
            }

        })
        cameraView.detectorType = DetectorType.Face
        cameraView.setOnFacesDetectedListener(object : ImageAnalysisCallback {
            override fun onSuccess(result: Any) {
                println("setOnFacesDetectedListener: Success ${result}")
            }

            override fun onError(message: String, exception: java.lang.Exception) {
                println("setOnFacesDetectedListener: Error $message")
                exception.printStackTrace()
            }
        })
        cameraView.setOnTextRecognitionListener(object : ImageAnalysisCallback {
            override fun onSuccess(result: Any) {
                println("setOnTextRecognitionListener: Success ${result}")
            }

            override fun onError(message: String, exception: java.lang.Exception) {
                println("setOnTextRecognitionListener: Error $message")
                exception.printStackTrace()
            }
        })
        cameraView.saveToGallery = true
        cameraView.autoSquareCrop = true
    }

    fun takePhoto(view: View) {
        if (cameraView.hasStoragePermission()) {
            cameraView.takePhoto()
        } else {
            cameraView.requestStoragePermission()
        }
    }

    fun toggleFlash(view: View) {
        if (cameraView.flashMode != CameraFlashMode.OFF) {
            cameraView.flashMode = CameraFlashMode.OFF
        } else {
            cameraView.flashMode = CameraFlashMode.ON
        }
    }

    fun toggleCamera(view: View) {
        cameraView.toggleCamera()
    }

    fun goToHome(view: View) {
        val i = Intent(this, Home::class.java)
        startActivity(i)
    }

    override fun onPause() {
        cameraView.stop()
        super.onPause()
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        var count = 0
        for (grant in grantResults) {
            if (permissions[count] == Manifest.permission.WRITE_EXTERNAL_STORAGE && grant == PackageManager.PERMISSION_GRANTED) {
                cameraView.takePhoto()
                break
            }
            count++
        }
    }
}
