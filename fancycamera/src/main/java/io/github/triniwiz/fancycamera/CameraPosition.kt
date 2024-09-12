package io.github.triniwiz.fancycamera

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector

enum class CameraPosition constructor(val value: Int) {
    BACK(0),
    FRONT(1);

    @get:RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    val lenFacing: Int
        get() {
            return when (this) {
                FRONT -> CameraSelector.LENS_FACING_FRONT
                else -> CameraSelector.LENS_FACING_BACK
            }
        }

    companion object {
        fun from(value: Int): CameraPosition = values().first { it.value == value }
    }
}