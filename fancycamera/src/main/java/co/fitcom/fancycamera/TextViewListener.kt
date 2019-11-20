/*
 * Created By Osei Fortune on 3/24/18 5:18 PM
 * Copyright (c) 2018
 * Last modified 3/24/18 5:18 PM
 *
 */

package co.fitcom.fancycamera

import android.graphics.SurfaceTexture

/**
 * Created by triniwiz on 3/24/18
 */
interface TextViewListener {
    fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int)
    fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int)
    fun onSurfaceTextureDestroyed(surface: SurfaceTexture)
    fun onSurfaceTextureUpdated(surface: SurfaceTexture)
}
