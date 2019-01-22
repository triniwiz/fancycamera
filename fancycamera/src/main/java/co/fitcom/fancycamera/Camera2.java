/*
 * Created By Osei Fortune on 2/16/18 8:42 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 8:42 PM
 *
 */

package co.fitcom.fancycamera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
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

@TargetApi(21)
class Camera2 extends CameraBase {
    private static final Object lock = new Object();
    private CameraManager mManager;
    MediaRecorder mMediaRecorder;
    private FancyCamera.CameraPosition mPosition;
    private Context mContext;
    private Handler backgroundHandler;
    private HandlerThread backgroundHandlerThread;
    private CameraCharacteristics characteristics;
    private Size previewSize;
    private Size videoSize;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mPreviewSession;
    private Semaphore semaphore = new Semaphore(1);
    private boolean isRecording = false;
    private boolean isStarted = false;
    private boolean isFlashEnabled = false;
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    private Integer mSensorOrientation;
    private String cameraIdToOpen = "0";
    boolean mAutoFocus;


    Camera2(Context context, TextureView textureView, @Nullable FancyCamera.CameraPosition position) {
        super(textureView);
        if (position == null) {
            mPosition = FancyCamera.CameraPosition.BACK;
        } else {
            mPosition = position;
        }
        mContext = context;
        startBackgroundThread();

        setTextViewListener(new TextViewListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                if (getHolder() != null) {
                    Handler mainHandler = new Handler(mContext.getMainLooper());
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            configureTransform(getHolder().getWidth(), getHolder().getHeight());
                        }
                    });
                }
            }

            @Override
            public void onSurfaceTextureDestroyed(SurfaceTexture surface) {
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });

    }


    private void startBackgroundThread() {
        synchronized (lock) {
            backgroundHandlerThread = new HandlerThread(CameraThread);
            backgroundHandlerThread.start();
            backgroundHandler = new Handler(backgroundHandlerThread.getLooper());
        }
    }


    private void stopBackgroundThread() {
        if (backgroundHandlerThread == null)
            return;
        if (backgroundHandlerThread != null) {
            backgroundHandlerThread.quitSafely();
        }
        try {
            backgroundHandlerThread.join();
            backgroundHandlerThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    @Override
    boolean hasCamera() {
        try {
            return mManager.getCameraIdList().length > 0;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    boolean cameraStarted() {
        return isStarted;
    }

    @Override
    boolean cameraRecording() {
        return isRecording;
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
                    if (mPosition == FancyCamera.CameraPosition.FRONT) {
                        CameraCharacteristics characteristics = mManager.getCameraCharacteristics(cameraId);
                        cameraOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                        if (cameraOrientation == CameraCharacteristics.LENS_FACING_FRONT) {
                            cameraIdToOpen = cameraId;
                            map = characteristics
                                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                            break;
                        }

                    } else {
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
                        isStarted = true;
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
                        if(listener != null){
                            listener.onCameraOpen();
                        }
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        semaphore.release();
                        camera.close();
                        isStarted = false;
                        mCameraDevice = null;
                        if(listener != null){
                            listener.onCameraClose();
                        }
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        semaphore.release();
                        camera.close();
                        mCameraDevice = null;
                        isStarted = false;
                        if(listener != null){
                            listener.onCameraClose();
                        }
                    }
                }, backgroundHandler);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }


    }


    @Override
    MediaRecorder getRecorder() {
        return mMediaRecorder;
    }

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
            permit = semaphore.tryAcquire(1500, TimeUnit.MILLISECONDS);
            if (permit) {
                mMediaRecorder = new MediaRecorder();
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
                Date today = Calendar.getInstance().getTime();
                setFile(new File(mContext.getCacheDir(), "VID_" + df.format(today) + ".mp4"));
                CamcorderProfile profile = getCamcorderProfile(FancyCamera.Quality.values()[quality]);
                mMediaRecorder.setProfile(profile);
                mMediaRecorder.setOutputFile(getFile().getPath());
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

                int orientation = activity.getResources().getConfiguration().orientation;

                if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                    if (mSensorOrientation != null) {
                        switch (mSensorOrientation) {
                            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                                break;
                            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                                break;
                        }
                    }
                } else if (orientation == Configuration.ORIENTATION_LANDSCAPE && Surface.ROTATION_90 == rotation) {
                    mMediaRecorder.setOrientationHint(0);
                } else if (orientation == Configuration.ORIENTATION_LANDSCAPE && Surface.ROTATION_270 == rotation) {
                    mMediaRecorder.setOrientationHint(0);
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


    void updateAutoFocus() {
        if (mAutoFocus) {
            int[] modes = characteristics.get(
                    CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            // Auto focus is not supported
            if (modes == null || modes.length == 0 ||
                    (modes.length == 1 && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF)) {
                mAutoFocus = false;
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_OFF);
            } else {
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            }
        } else {
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF);
        }
    }


    private Size getPreviewSize(Size[] sizes) {
        return getSupportedSize(sizes, quality);
    }


    private Size getSupportedSize(Size[] sizes, int quality) {
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point deviceSize = new Point();
        display.getSize(deviceSize);
        int width = deviceSize.x;
        int height = deviceSize.y;

        Size optimalSize = null;
        int count = 0;
        for (Size size : sizes) {
            count++;
            if (quality == FancyCamera.Quality.LOWEST.getValue()) {
                return sizes[sizes.length - 1];
            } else if (quality == FancyCamera.Quality.HIGHEST.getValue()) {
                if (size.getHeight() <= height && size.getWidth() <= width) {
                    optimalSize = size;
                    break;
                } else {
                    if (count == sizes.length - 1) {
                        optimalSize = sizes[sizes.length - 1];
                        break;
                    }
                }
            } else if (quality == FancyCamera.Quality.MAX_480P.getValue()) {
                if (size.getHeight() == 480 && size.getWidth() <= width) {
                    optimalSize = size;
                    break;
                } else {
                    if (count == sizes.length - 1) {
                        optimalSize = getSupportedSize(sizes, FancyCamera.Quality.QVGA.getValue());
                        break;
                    }
                }
            } else if (quality == FancyCamera.Quality.MAX_720P.getValue()) {
                if (size.getHeight() == 720 && size.getWidth() <= width) {
                    optimalSize = size;
                    break;
                } else {
                    if (count == sizes.length - 1) {
                        optimalSize = getSupportedSize(sizes, FancyCamera.Quality.MAX_480P.getValue());
                        break;
                    }
                }
            } else if (quality == FancyCamera.Quality.MAX_1080P.getValue()) {
                if (size.getHeight() == 1080 && size.getWidth() <= width) {
                    optimalSize = size;
                    break;
                } else {
                    if (count == sizes.length - 1) {
                        optimalSize = getSupportedSize(sizes, FancyCamera.Quality.MAX_720P.getValue());
                        break;
                    }
                }
            } else if (quality == FancyCamera.Quality.MAX_2160P.getValue()) {
                if (size.getHeight() == 2160 && size.getWidth() <= width) {
                    optimalSize = size;
                    break;
                } else {
                    if (count == sizes.length - 1) {
                        optimalSize = getSupportedSize(sizes, FancyCamera.Quality.MAX_1080P.getValue());
                        break;
                    }
                }
            } else if (quality == FancyCamera.Quality.QVGA.getValue()) {
                if (size.getHeight() == 240 && size.getWidth() <= width) {
                    optimalSize = size;
                    break;
                } else {
                    if (count == sizes.length - 1) {
                        optimalSize = sizes[sizes.length - 1];
                        break;
                    }
                }
            }
        }
        return optimalSize;
    }


    private void startPreview() {
        if (mCameraDevice == null || !getHolder().isAvailable()) {
            return;
        }
        synchronized (lock) {
            SurfaceTexture texture = getHolder().getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            try {
                if (mCameraDevice == null) return;
                mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                Surface previewSurface = new Surface(texture);
                mPreviewBuilder.addTarget(previewSurface);
                // tempOffFlash(mPreviewBuilder);
                mCameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                        new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                synchronized (lock) {
                                    mPreviewSession = session;
                                    updatePreview();
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                            }
                        }, backgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }


    private void closePreviewSession() {
        synchronized (lock) {
            if (mPreviewSession != null) {
                mPreviewSession.close();
                mPreviewSession = null;
            }

            if (null != mMediaRecorder) {
                mMediaRecorder.reset();
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        }
    }

    @Override
    void start() {
        if (isStarted && mCameraDevice != null) {
            return;
        }
        stop();
        openCamera(getHolder().getWidth(), getHolder().getHeight());
    }


    @Override
    void stop() {
        if (!isStarted && mCameraDevice == null) {
            return;
        }
        try {
            boolean permit = semaphore.tryAcquire(1500, TimeUnit.MILLISECONDS);
            if (permit) {
                synchronized (lock) {
                    if (mCameraDevice != null) {
                        closePreviewSession();
                        if (mCameraDevice == null) {
                            return;
                        }
                        mCameraDevice.close();
                        mCameraDevice = null;
                    }
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            semaphore.release();
        }
    }


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
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            setupFlash(mPreviewBuilder);
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
                    synchronized (lock) {
                        mPreviewSession = cameraCaptureSession;
                        updatePreview();
                        Handler mainHandler = new Handler(mContext.getMainLooper());
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    boolean permit = semaphore.tryAcquire(1500, TimeUnit.MILLISECONDS);
                                    if (permit) {
                                        mMediaRecorder.start();
                                        isRecording = true;
                                        startDurationTimer();
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
                        });
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            }, backgroundHandler);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    void takePhoto() {
        if (null == mCameraDevice || !getHolder().isAvailable() || null == previewSize || isRecording) {
            return;
        }

        try {
            Size[] jpegSizes;

            int width = 640;
            int height = 480;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }

            final ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);


            SurfaceTexture texture = getHolder().getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(reader.getSurface());
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);

            final CaptureRequest.Builder photoPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            setupFlash(photoPreviewBuilder);
            photoPreviewBuilder.addTarget(reader.getSurface());
            photoPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);


            DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
            Date today = Calendar.getInstance().getTime();
            setFile(new File(mContext.getCacheDir(), "PIC_" + df.format(today) + ".jpg"));
            ImageReader.OnImageAvailableListener readOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);
                    try {
                        save(bytes, image);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (getListener() != null) {
                            PhotoEvent event = new PhotoEvent(EventType.INFO, getFile(), PhotoEvent.EventInfo.PHOTO_TAKEN.toString());
                            getListener().onPhotoEvent(event);
                        }
                    }
                }

                private void save(byte[] bytes, Image image) throws IOException {
                    Bitmap bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    Matrix matrix = new Matrix();
                    matrix.postRotate(mSensorOrientation);
                    Bitmap rotated = Bitmap.createBitmap(bm, 0, 0, image.getWidth(), image.getHeight(), matrix, true);

                    try (OutputStream outputStream = new FileOutputStream(getFile())) {
                        //outputStream.write(bytes);
                        rotated.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                    }
                    bm.recycle();
                    rotated.recycle();
                }
            };
            reader.setOnImageAvailableListener(readOnImageAvailableListener, backgroundHandler);
            final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    stop();
                    start(); //TODO allow user to choose
                }
            };

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.capture(photoPreviewBuilder.build(), captureCallback, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void stopRecord() {
        synchronized (lock) {
            if (!isRecording) {
                return;
            }

            try {
                mPreviewSession.stopRepeating();
                mPreviewSession.abortCaptures();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }


            try {
                mMediaRecorder.stop();
            } catch (RuntimeException e) {
                //handle the exception
            }

            isStarted = false;
            isRecording = false;
            stopDurationTimer();
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
            if (listener != null) {
                listener.onVideoEvent(new VideoEvent(EventType.INFO, getFile(), VideoEvent.EventInfo.RECORDING_FINISHED.toString()));
            }
            setFile(null);
        }
    }

    @Override
    void stopRecording() {
        try {
            boolean permit = semaphore.tryAcquire(1500, TimeUnit.MILLISECONDS);
            if (permit) {
                synchronized (lock) {
                    stopRecord();
                    start();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            semaphore.release();
        }
    }


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
        try {
            boolean permit = semaphore.tryAcquire(1500, TimeUnit.MILLISECONDS);
            if (permit) {
                synchronized (lock) {
                    if (isRecording) {
                        stopRecord();
                    }
                    stop();
                    if (mPosition == FancyCamera.CameraPosition.BACK) {
                        mPosition = FancyCamera.CameraPosition.FRONT;
                    } else {
                        mPosition = FancyCamera.CameraPosition.BACK;
                    }
                    start();

                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            semaphore.release();
        }
    }


    private void tempOffFlash(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
    }

    private void setupFlash(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.FLASH_MODE, isFlashEnabled ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
    }

    @Override
    void updatePreview() {
        if (null == mCameraDevice || null == mPreviewSession) {
            return;
        }
        setUpCaptureRequestBuilder(mPreviewBuilder);
        try {
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }catch (IllegalStateException e){
            e.printStackTrace();
        }
    }

    @Override
    void release() {
        if (isRecording) {
            stopRecord();
        }
        stop();
    }


    @Override
    void setCameraPosition(FancyCamera.CameraPosition position) {
        synchronized (lock) {
            if (position != mPosition) {
                mPosition = position;
                if (null == mCameraDevice) {
                    return;
                }
                stop();
                start();
            }
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
        if (mCameraDevice != null) {
            start();
        }
    }

    @Override
    void enableFlash() {
        if (!hasFlash()) {
            return;
        }
        isFlashEnabled = true;
        if (mCameraDevice != null) {
            start();
        }
    }

    @Override
    void disableFlash() {
        if (!hasFlash()) {
            return;
        }
        isFlashEnabled = false;
        if (mCameraDevice != null) {
            start();
        }

    }

    @Override
    boolean flashEnabled() {
        return isFlashEnabled;
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
