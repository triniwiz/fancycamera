/*
 * Created By Osei Fortune on 3/24/18 5:18 PM
 * Copyright (c) 2018
 * Last modified 3/24/18 5:18 PM
 *
 */

package co.fitcom.fancycamera;

import android.graphics.SurfaceTexture;

/**
 * Created by triniwiz on 3/24/18
 */
public interface TextViewListener {
    void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height);
    void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height);
    void onSurfaceTextureDestroyed(SurfaceTexture surface);
    void onSurfaceTextureUpdated(SurfaceTexture surface);
}
