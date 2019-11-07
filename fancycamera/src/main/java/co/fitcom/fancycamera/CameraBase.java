
/*
 * Created By Osei Fortune on 2/16/18 8:42 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 8:42 PM
 *
 */

package co.fitcom.fancycamera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.view.TextureView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

public abstract class CameraBase {
    private Timer mTimer;
    private TimerTask mTimerTask;
    private int mDuration = 0;
    private TextureView holder;
    private File mFile;
    CameraEventListener listener;
    int quality;

    CameraBase(TextureView holder) {
        this.holder = holder;
    }

    public final static String CameraThread = "CameraThread";

    private TextViewListener textViewListener;

    public TextureView getHolder() {
        return holder;
    }

    void setFile(File file) {
        mFile = file;
    }

    File getFile() {
        return mFile;
    }

    void setQuality(int quality) {
        this.quality = quality;
    }

    int getQuality() {
        return quality;
    }

    abstract public void setEnableAudioLevels(boolean enable);

    abstract public boolean isAudioLevelsEnabled();

    abstract boolean getAutoSquareCrop();

    abstract void setAutoSquareCrop(boolean crop);

    abstract MediaRecorder getRecorder();

    abstract boolean getSaveToGallery();

    abstract public void setSaveToGallery(boolean saveToGallery);

    abstract public boolean getAutoFocus();

    abstract public void setAutoFocus(boolean focus);

    abstract int getMaxAudioBitRate();

    abstract int getMaxVideoBitrate();

    abstract int getMaxVideoFrameRate();

    abstract public boolean getDisableHEVC();

    abstract public void setDisableHEVC(boolean disableHEVC);

    abstract public void setMaxAudioBitRate(int maxAudioBitRate);

    abstract public void setMaxVideoBitrate(int maxVideoBitrate);

    abstract public void setMaxVideoFrameRate(int maxVideoFrameRate);

    abstract public int getNumberOfCameras();

    abstract boolean hasCamera();

    abstract boolean hasFlash();

    abstract boolean cameraStarted();

    abstract boolean cameraRecording();

    abstract void openCamera(int width, int height);

    abstract void start();

    abstract void stop();

    abstract void startRecording();

    abstract void takePhoto();

    abstract void stopRecording();

    abstract void toggleCamera();

    abstract void updatePreview();

    abstract void release();

    abstract void setCameraPosition(FancyCamera.CameraPosition position);

    abstract void setCameraOrientation(FancyCamera.CameraOrientation orientation);

    abstract void toggleFlash();

    abstract void enableFlash();

    abstract void disableFlash();

    abstract boolean flashEnabled();

    public void setTextViewListener(TextViewListener listener) {
        textViewListener = listener;
    }

    public TextViewListener getTextViewListener() {
        return textViewListener;
    }

    public CameraEventListener getListener() {
        return listener;
    }

    public void setListener(CameraEventListener listener) {
        this.listener = listener;
    }

    void startDurationTimer() {
        mTimer = new Timer();
        mTimerTask = new TimerTask() {
            @Override
            public void run() {
                mDuration += 1;
            }
        };
        mTimer.schedule(mTimerTask, 0, 1000);
    }

    void stopDurationTimer() {
        mTimerTask.cancel();
        mTimer.cancel();
        mDuration = 0;
    }

    int getDuration() {
        return mDuration;
    }

    public boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        return ContextCompat.checkSelfPermission(holder.getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);
    }

    public void requestStoragePermission() {
        ActivityCompat.requestPermissions((Activity) holder.getContext(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 868);
    }

}