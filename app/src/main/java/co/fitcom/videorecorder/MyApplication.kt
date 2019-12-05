package co.fitcom.videorecorder

import android.app.Application
import android.content.Context
import androidx.camera.core.CameraXConfig
import co.fitcom.fancycamera.CameraProvider
import co.fitcom.fancycamera.FancyCamera

/**
 * Created by triniwiz on 12/4/19
 */
class MyApplication : Application(), CameraProvider {
    override fun getCameraXConfig(): CameraXConfig {
        return FancyCamera.defaultConfig(this)
    }
}