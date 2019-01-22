/*
 * Created By Osei Fortune on 2/16/18 8:42 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 7:39 PM
 *
 */

package co.fitcom.fancycamera;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


public class Camera1 extends CameraBase {
    private Semaphore semaphore = new Semaphore(1);
    private final Object lock = new Object();
    private Camera mCamera;
    private Context mContext;
    private FancyCamera.CameraPosition mPosition;
    private Handler backgroundHandler;
    private HandlerThread backgroundHandlerThread;
    private boolean isRecording;
    private MediaRecorder mMediaRecorder;
    private boolean isStarted;
    private boolean autoStart;
    private boolean mPermit;
    private CamcorderProfile mProfile;
    private Timer mTimer;
    private TimerTask mTimerTask;
    private int mDuration = 0;
    private boolean isFlashEnabled = false;

    Camera1(Context context, TextureView textureView, @Nullable FancyCamera.CameraPosition position) {
        super(textureView);
        mContext = context;
        if (position == null) {
            mPosition = FancyCamera.CameraPosition.BACK;
        } else {
            mPosition = position;
        }
        startBackgroundThread();
        setTextViewListener(new TextViewListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public void onSurfaceTextureDestroyed(SurfaceTexture surface) {

            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    @Override
    MediaRecorder getRecorder() {
        return mMediaRecorder;
    }

    @Override
    boolean hasCamera() {
        return Camera.getNumberOfCameras() > 0;
    }

    @Override
    boolean cameraStarted() {
        return isStarted;
    }

    @Override
    boolean cameraRecording() {
        return isRecording;
    }

    private boolean getPermit() {
        return mPermit;
    }

    private void setPermit(boolean permit) {
        mPermit = permit;
    }

    private void startBackgroundThread() {
        backgroundHandlerThread = new HandlerThread(CameraThread);
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(backgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundHandlerThread.interrupt();
        try {
            backgroundHandlerThread.join();
            backgroundHandlerThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    void openCamera(int width, int height) {
        try {
            setPermit(semaphore.tryAcquire(1500, TimeUnit.MILLISECONDS));
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (getPermit()) {
                            mCamera = Camera.open(mPosition.getValue());
                            if(listener != null){
                                listener.onCameraOpen();
                            }
                            updatePreview();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (getPermit()) {
                            semaphore.release();
                        }
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void setupPreview() {
        if (mCamera == null) return;
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    setPermit(semaphore.tryAcquire(1500, TimeUnit.MILLISECONDS));
                    if (getPermit()) {
                        mCamera.reconnect();
                        mCamera.setPreviewTexture(getHolder().getSurfaceTexture());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    if (getPermit()) {
                        semaphore.release();
                    }
                }
            }
        });
    }

    @Override
    void start() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (getHolder().isAvailable()) {
                    try {
                        setPermit(semaphore.tryAcquire(1500, TimeUnit.MILLISECONDS));
                        if (getPermit()) {
                            updatePreview();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        if (getPermit()) {
                            semaphore.release();
                        }
                    }
                }
            }
        });
    }

    @Override
    void stop() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    setPermit(semaphore.tryAcquire(1500, TimeUnit.MILLISECONDS));
                    if (getPermit()) {
                        synchronized (lock) {
                            if (mCamera == null) return;
                            mCamera.stopPreview();
                            try {
                                mCamera.setPreviewTexture(null);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            mCamera.release();
                            mCamera = null;
                            isStarted = false;
                            if(listener != null){
                                listener.onCameraClose();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    semaphore.release();
                }
            }
        });
    }

    @Override
    void startRecording() {
        if (isRecording) {
            return;
        }
        Camera.Parameters params = mCamera.getParameters();
        CamcorderProfile profile = getCamcorderProfile(FancyCamera.Quality.values()[getQuality()]);
        List<Camera.Size> mSupportedPreviewSizes = params.getSupportedPreviewSizes();
        List<Camera.Size> mSupportedVideoSizes = params.getSupportedVideoSizes();
        Camera.Size optimalSize = getOptimalVideoSize(mSupportedVideoSizes,
                mSupportedPreviewSizes, getHolder().getWidth(), getHolder().getHeight());

        profile.videoFrameWidth = optimalSize.width;
        profile.videoFrameHeight = optimalSize.height;
        params.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
        setProfile(profile);
        mCamera.setParameters(params);
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }
        mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                if (listener != null) {
                    switch (what) {
                        case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
                            listener.onVideoEvent(new VideoEvent(EventType.INFO, null, VideoEvent.EventInfo.MAX_DURATION_REACHED.toString()));
                            break;
                        case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_APPROACHING:
                            listener.onVideoEvent(new VideoEvent(EventType.INFO, null, VideoEvent.EventInfo.MAX_FILESIZE_APPROACHING.toString()));
                            break;
                        case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
                            listener.onVideoEvent(new VideoEvent(EventType.INFO, null, VideoEvent.EventInfo.MAX_FILESIZE_REACHED.toString()));
                            break;
                        case MediaRecorder.MEDIA_RECORDER_INFO_NEXT_OUTPUT_FILE_STARTED:
                            listener.onVideoEvent(new VideoEvent(EventType.INFO, null, VideoEvent.EventInfo.NEXT_OUTPUT_FILE_STARTED.toString()));
                            break;
                        case MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN:
                            listener.onVideoEvent(new VideoEvent(EventType.INFO, null, VideoEvent.EventInfo.UNKNOWN.toString()));
                            break;
                    }
                }
            }
        });

        mMediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mr, int what, int extra) {
                if (listener != null) {
                    switch (what) {
                        case MediaRecorder.MEDIA_ERROR_SERVER_DIED:
                            listener.onVideoEvent(new VideoEvent(EventType.ERROR, null, VideoEvent.EventError.SERVER_DIED.toString()));
                            break;
                        case MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN:
                            listener.onVideoEvent(new VideoEvent(EventType.ERROR, null, VideoEvent.EventError.UNKNOWN.toString()));
                            break;
                    }
                }
            }

        });

        DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        Date today = Calendar.getInstance().getTime();
        setFile(new File(mContext.getCacheDir(), "VID_" + df.format(today) + ".mp4"));
        try {
            mPermit = semaphore.tryAcquire(1500, TimeUnit.MILLISECONDS);
            if (mPermit) {
                mCamera.unlock();
                try {
                    mMediaRecorder.setCamera(mCamera);
                    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                    mMediaRecorder.setProfile(getProfile());
                    mMediaRecorder.setOutputFile(getFile().getPath());
                    mMediaRecorder.prepare();
                    mMediaRecorder.start();
                    isRecording = true;
                    startDurationTimer();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (listener != null) {
                    listener.onVideoEvent(new VideoEvent(EventType.INFO, null, VideoEvent.EventInfo.RECORDING_STARTED.toString()));
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            semaphore.release();
        }
    }

    @Override
    void takePhoto() {
        if (isRecording) {
            return;
        }
        Camera.Parameters params = mCamera.getParameters();
        CamcorderProfile profile = getCamcorderProfile(FancyCamera.Quality.values()[getQuality()]);
        List<Camera.Size> mSupportedPreviewSizes = params.getSupportedPreviewSizes();
        List<Camera.Size> mSupportedVideoSizes = params.getSupportedVideoSizes();
        Camera.Size optimalSize = getOptimalVideoSize(mSupportedVideoSizes,
                mSupportedPreviewSizes, getHolder().getWidth(), getHolder().getHeight());

        profile.videoFrameWidth = optimalSize.width;
        profile.videoFrameHeight = optimalSize.height;
        params.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
        setProfile(profile);
        mCamera.setParameters(params);
        DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        Date today = Calendar.getInstance().getTime();
        setFile(new File(mContext.getCacheDir(), "PIC_" + df.format(today) + ".jpg"));
        mCamera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(getFile());
                    fos.write(data);
                    if (getListener() != null) {
                        PhotoEvent event = new PhotoEvent(EventType.INFO, getFile(), PhotoEvent.EventInfo.PHOTO_TAKEN.toString());
                        getListener().onPhotoEvent(event);
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    @Override
    void stopRecording() {
        if (!isRecording) {
            return;
        }
        try {
            mPermit = semaphore.tryAcquire(1500, TimeUnit.MILLISECONDS);
            if (mPermit) {
                if (isRecording) {
                    try {
                        mMediaRecorder.stop();
                        stopDurationTimer();
                        mMediaRecorder.reset();
                        mMediaRecorder.release();
                        mMediaRecorder = null;
                        if (listener != null) {
                            listener.onVideoEvent(new VideoEvent(EventType.INFO, getFile(), VideoEvent.EventInfo.RECORDING_FINISHED.toString()));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        getFile().delete();
                    } finally {
                        isRecording = false;
                        mCamera.lock();
                    }
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            semaphore.release();
            setPermit(false);
        }
    }

    @Override
    void toggleCamera() {
        stop();
        if (mPosition == FancyCamera.CameraPosition.BACK) {
            setCameraPosition(FancyCamera.CameraPosition.FRONT);
        } else {
            setCameraPosition(FancyCamera.CameraPosition.BACK);
        }
        openCamera(getHolder().getWidth(), getHolder().getHeight());
        // updatePreview();
    }

    @Override
    void updatePreview() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    setPermit(semaphore.tryAcquire(1500, TimeUnit.MILLISECONDS));
                    if (getPermit()) {
                        setupPreview();
                        if (mCamera != null) {
                            if (isStarted) {
                                mCamera.stopPreview();
                                isStarted = false;
                            }
                            updatePreviewSize();
                            updateCameraDisplayOrientation((Activity) mContext, mPosition.getValue(), mCamera);
                            setupPreview();
                            if (!isStarted) {
                                mCamera.startPreview();
                                isStarted = true;
                            }
                            isStarted = true;
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    if (getPermit()) {
                        semaphore.release();
                    }
                }
            }
        });
    }

    @Override
    void release() {
        if (isRecording) {
            stopRecording();
        }
        stop();
    }

    @Override
    void setCameraPosition(FancyCamera.CameraPosition position) {
        stop();

        if (Camera.getNumberOfCameras() < 2) {
            mPosition = FancyCamera.CameraPosition.BACK;
        } else {
            mPosition = position;
        }
        if (isStarted) {
            start();
        }
    }


    @Override
    boolean hasFlash() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }


    @Override
    void toggleFlash() {
        if (!hasFlash()) {
            return;
        }
        isFlashEnabled = !isFlashEnabled;
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setFlashMode(isFlashEnabled ? Camera.Parameters.FLASH_MODE_ON : Camera.Parameters.FLASH_MODE_OFF);
            mCamera.setParameters(parameters);
        }
    }

    @Override
    void enableFlash() {
        if (!hasFlash()) {
            return;
        }
        isFlashEnabled = true;
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
            mCamera.setParameters(parameters);
        }
    }

    @Override
    void disableFlash() {
        if (!hasFlash()) {
            return;
        }
        isFlashEnabled = false;
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            mCamera.setParameters(parameters);
        }
    }

    @Override
    boolean flashEnabled() {
        return isFlashEnabled;
    }

    private void setProfile(CamcorderProfile profile) {
        mProfile = profile;
    }

    private CamcorderProfile getProfile() {
        return mProfile;
    }

    private void updatePreviewSize() {
        Camera.Parameters params = mCamera.getParameters();
        List<Camera.Size> mSupportedPreviewSizes = params.getSupportedPreviewSizes();
        List<Camera.Size> mSupportedVideoSizes = params.getSupportedVideoSizes();
        Camera.Size optimalSize = getOptimalVideoSize(mSupportedVideoSizes,
                mSupportedPreviewSizes, getHolder().getWidth(), getHolder().getHeight());
        params.setPreviewSize(optimalSize.width, optimalSize.height);
        mCamera.setParameters(params);
    }

    private void updateCameraDisplayOrientation(Activity activity,
                                                int cameraId, Camera camera) {
        boolean permit = false;
        try {
            permit = semaphore.tryAcquire(1500, TimeUnit.MILLISECONDS);
            if (permit) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(cameraId, info);
                int rotation = activity.getWindowManager().getDefaultDisplay()
                        .getRotation();
                int degrees = 0;
                switch (rotation) {
                    case Surface.ROTATION_0:
                        degrees = 0;
                        break;
                    case Surface.ROTATION_90:
                        degrees = 90;
                        break;
                    case Surface.ROTATION_180:
                        degrees = 180;
                        break;
                    case Surface.ROTATION_270:
                        degrees = 270;
                        break;
                }

                int result;
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    result = (info.orientation + degrees) % 360;
                    result = (360 - result) % 360;
                } else {
                    result = (info.orientation - degrees + 360) % 360;
                }
                camera.setDisplayOrientation(result);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if (permit) {
                semaphore.release();
            }
        }
    }

    private CamcorderProfile getCamcorderProfile(FancyCamera.Quality quality) {
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
        switch (quality) {
            case MAX_480P:
                if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_480P)) {
                    profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
                } else {
                    profile = getCamcorderProfile(FancyCamera.Quality.QVGA);
                }
                break;
            case MAX_720P:
                if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P)) {
                    profile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
                } else {
                    profile = getCamcorderProfile(FancyCamera.Quality.MAX_480P);
                }
                break;
            case MAX_1080P:
                if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_1080P)) {
                    profile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
                } else {
                    profile = getCamcorderProfile(FancyCamera.Quality.MAX_720P);
                }

                break;
            case MAX_2160P:
                try {
                    profile = CamcorderProfile.get(CamcorderProfile.QUALITY_2160P);
                } catch (Exception e) {
                    profile = getCamcorderProfile(FancyCamera.Quality.HIGHEST);
                }
                break;
            case HIGHEST:
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
                break;
            case LOWEST:
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
                break;
            case QVGA:
                if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_QVGA)) {
                    profile = CamcorderProfile.get(CamcorderProfile.QUALITY_QVGA);
                } else {
                    profile = getCamcorderProfile(FancyCamera.Quality.LOWEST);
                }
                break;

        }
        return profile;
    }

    private Camera.Size getOptimalVideoSize(List<Camera.Size> supportedVideoSizes,
                                            List<Camera.Size> previewSizes, int w, int h) {
        // Use a very small tolerance because we want an exact match.
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;

        // Supported video sizes list might be null, it means that we are allowed to use the preview
        // sizes
        List<Camera.Size> videoSizes;
        if (supportedVideoSizes != null) {
            videoSizes = supportedVideoSizes;
        } else {
            videoSizes = previewSizes;
        }
        Camera.Size optimalSize = null;

        // Start with max value and refine as we iterate over available video sizes. This is the
        // minimum difference between view and camera height.
        double minDiff = Double.MAX_VALUE;

        // Target view height
        int targetHeight = h;

        // Try to find a video size that matches aspect ratio and the target view size.
        // Iterate over all available sizes and pick the largest size that can fit in the view and
        // still maintain the aspect ratio.
        for (Camera.Size size : videoSizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;
            if (Math.abs(size.height - targetHeight) < minDiff && previewSizes.contains(size)) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find video size that matches the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : videoSizes) {
                if (Math.abs(size.height - targetHeight) < minDiff && previewSizes.contains(size)) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }
}
