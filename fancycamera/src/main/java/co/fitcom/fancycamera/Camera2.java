/*
 * Created By Osei Fortune on 2/16/18 8:42 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 8:42 PM
 *
 */

package co.fitcom.fancycamera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

class Camera2 extends CameraBase {
    private static final Object lock = new Object();
    private CameraManager mManager;
    private MediaRecorder mMediaRecorder;
    private FancyCamera.CameraPosition mPosition;
    private Context mContext;
    private Handler previewHandler;
    private HandlerThread previewHandlerThread;
    private Handler mHandler;
    private HandlerThread handlerThread;
    private HandlerThread recordingHandlerThread;
    private Handler recordingHandler;
    private Handler sessionHandler;
    private HandlerThread sessionHandlerThread;
    private CameraCharacteristics characteristics;
    private Size previewSize;
    private Size videoSize;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mPreviewSession;
    private Semaphore semaphore = new Semaphore(1);
    private boolean isRecording = false;

    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    private Integer mSensorOrientation;
    private String cameraIdToOpen = "0";
    @SuppressLint("NewApi")
    Camera2(Context context, TextureView textureView, @Nullable FancyCamera.CameraPosition position) {
        super(textureView);
        if (position == null) {
            mPosition = FancyCamera.CameraPosition.BACK;
        } else {
            mPosition = position;
        }
        mContext = context;
        startBackgroundThread();
    }


    private void startBackgroundThread() {
        synchronized (lock){
            handlerThread = new HandlerThread(CameraThread);
            handlerThread.start();
            mHandler = new Handler(handlerThread.getLooper());
            recordingHandlerThread = new HandlerThread(CameraRecorderThread);
            recordingHandlerThread.start();
            recordingHandler = new Handler(recordingHandlerThread.getLooper());
            previewHandlerThread = new HandlerThread(PreviewThread);
            previewHandlerThread.start();
            previewHandler = new Handler(previewHandlerThread.getLooper());
            sessionHandlerThread = new HandlerThread(sessionThread);
            sessionHandlerThread.start();
            sessionHandler = new Handler(sessionHandlerThread.getLooper());
        }
    }

    @SuppressLint("NewApi")
    private void stopBackgroundThread() {
        if(handlerThread == null && recordingHandlerThread == null && previewHandlerThread == null) return;
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
        if (recordingHandlerThread != null) {
            recordingHandlerThread.quitSafely();
        }
        previewHandlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            mHandler = null;
            recordingHandlerThread.join();
            recordingHandlerThread = null;
            recordingHandler = null;
            previewHandlerThread.join();
            previewHandlerThread = null;
            previewHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("NewApi")
    @Override
    boolean hasCamera() {
        try {
            return mManager.getCameraIdList().length > 0;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return false;
    }


    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }


    @SuppressLint("NewApi")
    @Override
    void openCamera(int width, int height) {
        mManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        assert mManager != null;
        try {
            synchronized (lock) {
                String[] cameraList = mManager.getCameraIdList();
                int cameraOrientation;
                StreamConfigurationMap map = null;
                for (String cameraId : cameraList) {
                    if (mPosition == FancyCamera.CameraPosition.BACK) {
                        characteristics = mManager.getCameraCharacteristics(cameraId);
                        cameraOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                        if (cameraOrientation == CameraCharacteristics.LENS_FACING_BACK) {
                            cameraIdToOpen = cameraId;
                            map = characteristics
                                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                            break;
                        }
                    } else if(mPosition == FancyCamera.CameraPosition.FRONT) {
                        CameraCharacteristics characteristics = mManager.getCameraCharacteristics(cameraId);
                        cameraOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                        if (cameraOrientation == CameraCharacteristics.LENS_FACING_FRONT) {
                            cameraIdToOpen = cameraId;
                            map = characteristics
                                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                            break;
                        }

                    }else{
                        CameraCharacteristics characteristics = mManager.getCameraCharacteristics(cameraId);
                        cameraOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                        if (cameraOrientation == CameraCharacteristics.LENS_FACING_BACK) {
                            cameraIdToOpen = cameraId;
                            map = characteristics
                                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                            break;
                        }
                    }
                }

                if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }


                if (map == null) {
                    throw new RuntimeException("Cannot get available preview/video sizes");
                }

                previewSize = getPreviewSize(map.getOutputSizes(SurfaceTexture.class));
                videoSize = getPreviewSize(map.getOutputSizes(MediaRecorder.class));
                mMediaRecorder = new MediaRecorder();
                mManager.openCamera(cameraIdToOpen, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        mCameraDevice = camera;
                        if (getHolder() != null) {
                            Handler mainHandler = new Handler(mContext.getMainLooper());
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    configureTransform(getHolder().getWidth(), getHolder().getHeight());
                                }
                            });
                        }
                        startPreview();
                        semaphore.release();
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        semaphore.release();
                        camera.close();

                        mCameraDevice = null;
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        semaphore.release();
                        camera.close();
                        mCameraDevice = null;
                    }
                }, mHandler);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    @SuppressLint("NewApi")
    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }


    @SuppressLint("InlinedApi")
    private void setUpMediaRecorder() throws IOException {
        final Activity activity = (Activity) mContext;
        if (null == activity) {
            return;
        }
        boolean permit = false;
        try {
            permit = semaphore.tryAcquire(1, TimeUnit.SECONDS);
            if (permit) {
                if(mMediaRecorder == null){
                    mMediaRecorder = new MediaRecorder();
                }
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
                Date today = Calendar.getInstance().getTime();
                setFile(new File(mContext.getCacheDir(), "VID_" + df.format(today) + ".mp4"));
                mMediaRecorder.setProfile(getCamcorderProfile(FancyCamera.Quality.values()[quality]));
                mMediaRecorder.setOutputFile(getFile().getPath());
                System.out.println(videoSize.getHeight());
                System.out.println(videoSize.getWidth());
                mMediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
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
                int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                if(mSensorOrientation != null){
                    switch (mSensorOrientation) {
                        case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                            mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                            break;
                        case SENSOR_ORIENTATION_INVERSE_DEGREES:
                            mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                            break;
                    }
                }
                mMediaRecorder.prepare();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if (permit) {
                semaphore.release();
            }
        }
    }

    @SuppressLint("NewApi")
    private Size getPreviewSize(Size[] sizes) {
        if (quality == FancyCamera.Quality.HIGHEST.getValue()) {
            return sizes[0];
        }

        if (quality == FancyCamera.Quality.LOWEST.getValue()) {
            return sizes[sizes.length - 1];
        }

        for (Size size : sizes) {
            if (quality == FancyCamera.Quality.MAX_480P.getValue() && size.getWidth() == 480) {
                return size;
            } else if (quality == FancyCamera.Quality.MAX_720P.getValue() && size.getWidth() == 720) {
                return size;
            } else if (quality == FancyCamera.Quality.MAX_1080P.getValue() && size.getWidth() == 1080) {
                return size;
            } else if (quality == FancyCamera.Quality.MAX_2160P.getValue() && size.getWidth() == 2160) {
                return size;
            } else if (quality == FancyCamera.Quality.QVGA.getValue() && size.getWidth() == 240) {
                return size;
            }
        }
        return sizes[sizes.length - 1];
    }

    @SuppressLint("NewApi")
    private void startPreview() {
        if (mCameraDevice == null || !getHolder().isAvailable()) {
            return;
        }
        synchronized (lock){
            SurfaceTexture texture = getHolder().getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            try {
                if (mCameraDevice == null) return;
                mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                Surface previewSurface = new Surface(texture);
                mPreviewBuilder.addTarget(previewSurface);

                mCameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                        new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                synchronized (lock){
                                    mPreviewSession = session;
                                    updatePreview();
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                            }
                        }, sessionHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressLint("NewApi")
    private void closePreviewSession() {
        synchronized (lock){
            if (mPreviewSession != null) {
                mPreviewSession.close();
                mPreviewSession = null;
            }

            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        }
    }

    @Override
    void start() {
        //startBackgroundThread();
        stop();
        openCamera(getHolder().getWidth(),getHolder().getHeight());
        //startPreview();
    }

    @SuppressLint("NewApi")
    @Override
    void stop() {
        synchronized (lock){
            if (mCameraDevice != null) {
                closePreviewSession();
                mCameraDevice.close();
                mCameraDevice = null;
                //stopBackgroundThread();
            }
        }
    }

    @SuppressLint("NewApi")
    @Override
    void startRecording() {
        if (null == mCameraDevice || !getHolder().isAvailable() || null == previewSize || isRecording) {
            return;
        }

        try {
            closePreviewSession();
            setUpMediaRecorder();
            SurfaceTexture texture = getHolder().getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    synchronized (lock){
                        mPreviewSession = cameraCaptureSession;
                        updatePreview();
                        recordingHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mMediaRecorder.start();
                                isRecording = true;
                                startDurationTimer();
                                if (listener != null) {
                                    listener.onVideoEvent(new VideoEvent(EventType.INFO, null, VideoEvent.EventInfo.RECORDING_STARTED.toString()));
                                }
                            }
                        });
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            }, sessionHandler);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    void stopRecording() {
        sessionHandler.post(new Runnable() {
            @Override
            public void run() {
                recordingHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (lock) {
                            if(!isRecording){
                                return;
                            }
                            mMediaRecorder.stop();
                            isRecording = false;
                            stopDurationTimer();
                            mMediaRecorder.reset();
                            if (listener != null) {
                                listener.onVideoEvent(new VideoEvent(EventType.INFO, getFile(), VideoEvent.EventInfo.RECORDING_FINISHED.toString()));
                            }
                            setFile(null);
                        }
                    }
                });
                startPreview();
            }
        });
    }

    @SuppressLint("NewApi")
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = (Activity) mContext;
        if (null == getHolder() || null == previewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        getHolder().setTransform(matrix);
    }


    @Override
    void toggleCamera() {
        stop();
        synchronized (lock){
            if (mPosition == FancyCamera.CameraPosition.BACK) {
                mPosition = FancyCamera.CameraPosition.FRONT;
            } else {
                mPosition = FancyCamera.CameraPosition.BACK;
            }
            start();
        }
    }

    @SuppressLint("NewApi")
    @Override
    void updatePreview() {
        if (null == mCameraDevice || null == mPreviewSession) {
            return;
        }
        setUpCaptureRequestBuilder(mPreviewBuilder);
        try {
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, previewHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("NewApi")
    @Override
    void setCameraPosition(FancyCamera.CameraPosition position) {
        if(position != mPosition){
            synchronized (lock){
                mPosition = position;

            }
            if (null == mCameraDevice) {
                return;
            }
            stop();
            start();
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
}
