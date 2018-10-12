/*
 * Created By Osei Fortune on 2/16/18 8:43 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 7:58 PM
 *
 */

package co.fitcom.fancycamera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.TextureView;

import java.io.File;


public class FancyCamera extends TextureView implements TextureView.SurfaceTextureListener {
    private boolean mFlashEnabled = false;
    private boolean mSaveToGallery = false;
    private boolean isStarted = false;
    private int mCameraPosition = 0;
    private int mQuality = Quality.MAX_480P.getValue();
    private final Object mLock = new Object();
    private CameraEventListener listener;
    private int VIDEO_RECORDER_PERMISSIONS_REQUEST = 868;
    private String[] VIDEO_RECORDER_PERMISSIONS = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};
    private boolean isReady = false;
    private CameraBase cameraBase;

    public FancyCamera(Context context) {
        super(context);
        init(context, null);
    }

    public FancyCamera(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        if (Build.VERSION.SDK_INT >= 21) {
            cameraBase = new Camera2(getContext(), this, CameraPosition.values()[getCameraPosition()]);
        } else {
            cameraBase = new Camera1(getContext(), this, CameraPosition.values()[getCameraPosition()]);
        }
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(
                    attrs,
                    R.styleable.FancyCamera);

            try {
                mFlashEnabled = a.getBoolean(R.styleable.FancyCamera_enableFlash, false);
                mSaveToGallery = a.getBoolean(R.styleable.FancyCamera_saveToGallery, false);
                mQuality = a.getInteger(R.styleable.FancyCamera_quality, Quality.MAX_480P.getValue());
                setQuality(mQuality);
                mCameraPosition = a.getInteger(R.styleable.FancyCamera_cameraPosition, 0);
                cameraBase.setCameraPosition(CameraPosition.values()[mCameraPosition]);
            } finally {
                a.recycle();
            }
        }
        this.setSurfaceTextureListener(this);
    }

    public void setFile(File file) {
        cameraBase.setFile(file);
    }

    File getFile() {
        return cameraBase.getFile();
    }

    public boolean cameraStarted() {
        return cameraBase.cameraStarted();
    }

    public boolean cameraRecording(){
        return cameraBase.cameraRecording();
    }

    public void takePhoto(){
        cameraBase.takePhoto();
    }

    public int getCameraPosition() {
        return mCameraPosition;
    }

    public int getDuration() {
        return cameraBase.getDuration();
    }

    public void setQuality(int quality) {
        mQuality = Quality.MAX_480P.getValue();
        switch (quality) {
            case 0:
                mQuality = 0;
                break;
            case 1:
                mQuality = 1;
                break;
            case 2:
                mQuality = 2;
                break;
            case 3:
                mQuality = 3;
                break;
            case 4:
                mQuality = 4;
                break;
            case 5:
                mQuality = 5;
                break;
            case 6:
                mQuality = 6;
                break;

        }
        cameraBase.setQuality(mQuality);
    }

    public void setListener(CameraEventListener listener) {
        cameraBase.setListener(listener);
    }

    public void setCameraPosition(int position) {
        cameraBase.setCameraPosition(CameraPosition.values()[position]);
    }

    public void requestPermission() {
        ActivityCompat.requestPermissions((Activity) getContext(), VIDEO_RECORDER_PERMISSIONS, VIDEO_RECORDER_PERMISSIONS_REQUEST);
    }

    public boolean hasPermission() {
        return Build.VERSION.SDK_INT > 23 || Build.VERSION.SDK_INT < 23 || ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) == (PackageManager.PERMISSION_GRANTED) && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) == (PackageManager.PERMISSION_GRANTED);
    }

    public void start() {
        cameraBase.start();
    }

    public void stopRecording() {
        cameraBase.stopRecording();
    }

    public void startRecording() {
        cameraBase.startRecording();
    }

    public void stop() {
        cameraBase.stop();
    }

    public void release(){
        cameraBase.release();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (cameraBase.getTextViewListener() != null) {
            cameraBase.getTextViewListener().onSurfaceTextureAvailable(surface, width, height);
        }
        if (!hasPermission()) {
            requestPermission();
            return;
        }
        cameraBase.openCamera(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        if (cameraBase.getTextViewListener() != null) {
            cameraBase.getTextViewListener().onSurfaceTextureSizeChanged(surface, width, height);
        }
        cameraBase.updatePreview();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (cameraBase.getTextViewListener() != null) {
            cameraBase.getTextViewListener().onSurfaceTextureDestroyed(surface);
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        if (cameraBase.getTextViewListener() != null) {
            cameraBase.getTextViewListener().onSurfaceTextureUpdated(surface);
        }
    }

    public enum Quality {
        MAX_480P(0),
        MAX_720P(1),
        MAX_1080P(2),
        MAX_2160P(3),
        HIGHEST(4),
        LOWEST(5),
        QVGA(6);
        private int value;

        private Quality(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum CameraPosition {
        BACK(0),
        FRONT(1);
        private int value;

        private CameraPosition(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public void toggleCamera() {
        cameraBase.toggleCamera();
    }
}
