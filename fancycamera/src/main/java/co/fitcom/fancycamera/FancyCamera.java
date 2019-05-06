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
import android.media.MediaRecorder;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.TextureView;

import java.io.File;
import java.io.IOException;


public class FancyCamera extends TextureView implements TextureView.SurfaceTextureListener {
    private boolean mFlashEnabled = false;
    private boolean mSaveToGallery = false;
    private boolean isStarted = false;
    private int mCameraPosition = 0;
    private int mCameraOrientation = 0;
    private int mQuality = Quality.MAX_480P.getValue();
    private final Object mLock = new Object();
    private CameraEventListener listener;
    private final int VIDEO_RECORDER_PERMISSIONS_REQUEST = 868;
    private String[] VIDEO_RECORDER_PERMISSIONS = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};
    private boolean isReady = false;
    private CameraBase cameraBase;
    private MediaRecorder recorder;
    private boolean isGettingAudioLvls = false;
    static final private double EMA_FILTER = 0.6;
    private double mEMA = 0.0;
    private CameraEventListener internalListener;

    public FancyCamera(Context context) {
        super(context);
        init(context, null);
    }

    public FancyCamera(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void initListener() {
        if (!hasPermission()) {
            return;
        }
        if (recorder != null) deInitListener();
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setOutputFile("/dev/null");
        try {
            recorder.prepare();
            recorder.start();
            isGettingAudioLvls = true;
            mEMA = 0.0;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deInitListener() {
        if (isGettingAudioLvls) {
            try {
                recorder.stop();
                recorder.release();
                recorder = null;
                isGettingAudioLvls = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        if (Build.VERSION.SDK_INT >= 21) {
            cameraBase = new Camera2(getContext(), this, CameraPosition.values()[getCameraPosition()], CameraOrientation.values()[getCameraOrientation()]);
        } else {
            cameraBase = new Camera1(getContext(), this, CameraPosition.values()[getCameraPosition()]);
        }

        internalListener = new CameraEventListener() {
            @Override
            public void onCameraOpen() {
                initListener();
                if (listener != null) {
                    listener.onCameraOpen();
                }
            }

            @Override
            public void onCameraClose() {
                deInitListener();
                if (listener != null) {
                    listener.onCameraClose();
                }
            }

            @Override
            public void onPhotoEvent(PhotoEvent event) {
                if (listener != null) {
                    listener.onPhotoEvent(event);
                }
            }

            @Override
            public void onVideoEvent(VideoEvent event) {
                if (listener != null) {
                    listener.onVideoEvent(event);
                }
            }
        };

        cameraBase.setListener(internalListener);

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
                setCameraPosition(mCameraPosition);
                mCameraOrientation = a.getInteger(R.styleable.FancyCamera_cameraOrientation, 0);
                setCameraOrientation(mCameraOrientation);
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

    public void toggleFlash() {
        cameraBase.toggleFlash();
    }

    public void enableFlash() {
        cameraBase.enableFlash();
    }

    public void disableFlash() {
        cameraBase.disableFlash();
    }

    public boolean flashEnabled() {
        return cameraBase.flashEnabled();
    }

    public boolean cameraStarted() {
        return cameraBase.cameraStarted();
    }

    public boolean cameraRecording() {
        return cameraBase.cameraRecording();
    }

    public void takePhoto() {
        cameraBase.takePhoto();
    }

    public int getCameraPosition() {
        return mCameraPosition;
    }

    public int getCameraOrientation() {
        return mCameraOrientation;
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
        this.listener = listener;
    }

    public void setCameraPosition(int position) {
        cameraBase.setCameraPosition(CameraPosition.values()[position]);
    }

    public void setCameraPosition(FancyCamera.CameraPosition position) {
        cameraBase.setCameraPosition(position);
    }

    public void setCameraOrientation(int orientation) {
        cameraBase.setCameraOrientation(CameraOrientation.values()[orientation]);
    }

    public void setCameraOrientation(FancyCamera.CameraOrientation orientation) {
        cameraBase.setCameraOrientation(orientation);
    }

    public void requestPermission() {
        ActivityCompat.requestPermissions((Activity) getContext(), VIDEO_RECORDER_PERMISSIONS, VIDEO_RECORDER_PERMISSIONS_REQUEST);
    }

    public boolean hasPermission() {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) == (PackageManager.PERMISSION_GRANTED) && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) == (PackageManager.PERMISSION_GRANTED);
    }

    public void start() {
        cameraBase.start();
    }

    public void stopRecording() {
        cameraBase.stopRecording();
    }

    public void startRecording() {
        deInitListener();
        cameraBase.startRecording();
    }

    public void stop() {
        cameraBase.stop();
    }

    public void release() {
        cameraBase.release();
        deInitListener();
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

    public enum CameraOrientation {
        UNKNOWN(0),
        PORTRAIT(1),
        PORTRAIT_UPSIDE_DOWN(2),
        LANDSCAPE_LEFT(3),
        LANDSCAPE_RIGHT(4);
        private int value;

        CameraOrientation(int value) {
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

        CameraPosition(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public void toggleCamera() {
        cameraBase.toggleCamera();
    }

    public double getAmplitude() {
        double amp;
        if (cameraRecording()) {
            amp = cameraBase.getRecorder() != null ? cameraBase.getRecorder().getMaxAmplitude() : 0;
            return amp;
        }
        try {
            amp = recorder != null ? recorder.getMaxAmplitude() : 0;
        } catch (Exception ignored) {
            amp = 0;
        }
        return amp;
    }

    public double getDB() {
        return 20 * Math.log10(getAmplitude() / 32767.0);
    }

    public double getAmplitudeEMA() {
        double amp = getAmplitude();
        mEMA = EMA_FILTER * amp + (1.0 - EMA_FILTER) * mEMA;
        return mEMA;
    }
}
