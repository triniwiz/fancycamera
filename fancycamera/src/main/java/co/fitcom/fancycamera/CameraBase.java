
/*
 * Created By Osei Fortune on 2/16/18 8:42 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 8:42 PM
 *
 */

package co.fitcom.fancycamera;

import android.view.TextureView;

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

    public static String CameraThread = "CameraThread";
    public static String CameraRecorderThread = "CameraRecorderThread";
    public static String PreviewThread = "PreviewThread";
    public static String sessionThread = "SessionThread";
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

    abstract boolean hasCamera();

    abstract void openCamera(int width, int height);

    abstract void start();

    abstract void stop();

    abstract void startRecording();

    abstract void stopRecording();

    abstract void toggleCamera();

    abstract void updatePreview();

    abstract void setCameraPosition(FancyCamera.CameraPosition position);

    public void setTextViewListener(TextViewListener listener){
        textViewListener = listener;
    }

    public TextViewListener getTextViewListener(){
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

}