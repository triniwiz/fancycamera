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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@androidx.annotation.RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class Camera2 extends CameraBase {
    private static final Object lock = new Object();
    private static final String TAG = "Camera2.Fancy";
    private CameraManager mManager;
    MediaRecorder mMediaRecorder;
    private FancyCamera.CameraPosition mPosition;
    private FancyCamera.CameraOrientation mOrientation;
    private Context mContext;
    private Handler backgroundHandler;
    private HandlerThread backgroundHandlerThread;
    private CameraCharacteristics characteristics;
    private Size previewSize;
    private Size videoSize;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mPreviewSession;
    private boolean isRecording = false;
    private boolean isStarted = false;
    private boolean isFlashEnabled = false;
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    private Integer mSensorOrientation;
    private String cameraIdToOpen = "0";
    private boolean mAutoFocus = true;
    private boolean disableHEVC = false;
    private int maxVideoBitrate = -1;
    private int maxAudioBitRate = -1;
    private int maxVideoFrameRate = -1;
    private boolean saveToGallery = false;
    private boolean autoSquareCrop = false;
    private boolean isAudioLevelsEnabled = false;
    private ImageReader reader;

    private ImageReader.OnImageAvailableListener readOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            Image image = imageReader.acquireLatestImage();
            Bitmap bitmap = imageToBitmap(image);
            try {
                save(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                reader = null;
                Uri contentUri = Uri.fromFile(getFile());
                Intent mediaScanIntent = new android.content.Intent(
                        "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                        contentUri
                );
                mContext.sendBroadcast(mediaScanIntent);
                if (getListener() != null) {
                    PhotoEvent event = new PhotoEvent(EventType.INFO, getFile(), PhotoEvent.EventInfo.PHOTO_TAKEN.toString());
                    getListener().onPhotoEvent(event);
                } else {
                    Log.w(TAG, "No listener found");
                }
            }
        }

        private Bitmap imageToBitmap(Image image) {
            // NV21 is a plane of 8 bit Y values followed by interleaved  Cb Cr
            ByteBuffer ib = ByteBuffer.allocate(image.getHeight() * image.getWidth() * 2);

            ByteBuffer y = image.getPlanes()[0].getBuffer();
            ByteBuffer cr = image.getPlanes()[1].getBuffer();
            ByteBuffer cb = image.getPlanes()[2].getBuffer();
            ib.put(y);
            ib.put(cb);
            ib.put(cr);

            YuvImage yuvImage = new YuvImage(ib.array(),
                    ImageFormat.NV21, image.getWidth(), image.getHeight(), null);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0,
                    image.getWidth(), image.getHeight()), 50, out);
            byte[] imageBytes = out.toByteArray();
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        }

        private void save(Bitmap bm) throws IOException {
            int originalWidth = bm.getWidth();
            int originalHeight = bm.getHeight();
            int offsetWidth = 0;
            int offsetHeight = 0;
            if (getAutoSquareCrop()) {
                if (originalWidth < originalHeight) {
                    offsetHeight = (originalHeight - originalWidth) / 2;
                    originalHeight = originalWidth;
                } else {
                    offsetWidth = (originalWidth - originalHeight) / 2;
                    originalWidth = originalHeight;
                }
            }

            // this flips the front camera image to not be 'mirrored effect' for selfies
            // does not flip if using the back camera
            Matrix matrix = new Matrix();
            if (mPosition == FancyCamera.CameraPosition.FRONT) {
                float[] mirrorY = {-1, 0, 0, 0, 1, 0, 0, 0, 1};
                Matrix matrixMirrorY = new Matrix();
                matrixMirrorY.setValues(mirrorY);
                matrix.postConcat(matrixMirrorY);
                matrix.postRotate(90);
            } else {
                matrix.postRotate(mSensorOrientation);
            }

            Bitmap rotated = Bitmap.createBitmap(bm, offsetWidth, offsetHeight, originalWidth, originalHeight, matrix, false);

            File cameraDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
            if (!cameraDir.exists()) {
                final boolean mkdirs = cameraDir.mkdirs();
            }
            try (OutputStream outputStream = new FileOutputStream(getFile())) {
                rotated.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            }
            bm.recycle();
            rotated.recycle();
        }
    };

    Camera2(Context context, TextureView textureView, @Nullable FancyCamera.CameraPosition position, @Nullable FancyCamera.CameraOrientation orientation) {
        super(textureView);

        if (position == null) {
            mPosition = FancyCamera.CameraPosition.BACK;
        } else {
            mPosition = position;
        }

        if (orientation == null) {
            mOrientation = FancyCamera.CameraOrientation.UNKNOWN;
        } else {
            mOrientation = orientation;
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

    @Override
    public void setEnableAudioLevels(boolean enable) {
        synchronized (lock) {
            isAudioLevelsEnabled = enable;
        }
    }

    @Override
    public boolean isAudioLevelsEnabled() {
        return isAudioLevelsEnabled;
    }

    @Override
    boolean getAutoSquareCrop() {
        return autoSquareCrop;
    }

    @Override
    public void setAutoSquareCrop(boolean autoSquareCrop) {
        synchronized (lock) {
            this.autoSquareCrop = autoSquareCrop;
        }
    }

    @Override
    public boolean getAutoFocus() {
        return mAutoFocus;
    }

    @Override
    public void setAutoFocus(boolean focus) {
        synchronized (lock) {
            mAutoFocus = focus;
        }
    }

    @Override
    public boolean getSaveToGallery() {
        return saveToGallery;
    }

    @Override
    public void setSaveToGallery(boolean saveToGallery) {
        synchronized (lock) {
            this.saveToGallery = saveToGallery;
        }
    }

    @Override
    public int getMaxAudioBitRate() {
        return maxAudioBitRate;
    }

    @Override
    public int getMaxVideoBitrate() {
        return maxVideoBitrate;
    }

    @Override
    public int getMaxVideoFrameRate() {
        return maxVideoFrameRate;
    }

    @Override
    public boolean getDisableHEVC() {
        return disableHEVC;
    }

    @Override
    public void setDisableHEVC(boolean disableHEVC) {
        synchronized (lock) {
            this.disableHEVC = disableHEVC;
        }
    }

    @Override
    public void setMaxAudioBitRate(int maxAudioBitRate) {
        synchronized (lock) {
            this.maxAudioBitRate = maxAudioBitRate;
        }
    }

    @Override
    public void setMaxVideoBitrate(int maxVideoBitrate) {
        synchronized (lock) {
            this.maxVideoBitrate = maxVideoBitrate;
        }
    }

    @Override
    public void setMaxVideoFrameRate(int maxVideoFrameRate) {
        synchronized (lock) {
            this.maxVideoFrameRate = maxVideoFrameRate;
        }
    }

    @Override
    public int getNumberOfCameras() {
        if (mManager != null) {
            try {
                return mManager.getCameraIdList().length;
            } catch (CameraAccessException e) {
                e.printStackTrace();
                return 0;
            }
        }
        return 0;
    }

    private void startBackgroundThread() {
        synchronized (lock) {
            backgroundHandlerThread = new HandlerThread(CameraThread);
            backgroundHandlerThread.start();
            backgroundHandler = new Handler(backgroundHandlerThread.getLooper());
        }
    }


    private void stopBackgroundThread() {
        synchronized (lock) {
            backgroundHandlerThread.interrupt();
            try {
                backgroundHandlerThread.join();
                backgroundHandlerThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    boolean hasCamera() {
        if (mManager == null) return false;
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
        synchronized (lock) {
            if (mManager == null) {
                mManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
            }
            if (mManager == null) {
                // emit error
                return;
            }
            try {
                String[] cameraList = mManager.getCameraIdList();
                int cameraOrientation;
                StreamConfigurationMap map = null;
                for (String cameraId : cameraList) {
                    if (mPosition == FancyCamera.CameraPosition.FRONT) {
                        characteristics = mManager.getCameraCharacteristics(cameraId);
                        cameraOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                        if (cameraOrientation == CameraCharacteristics.LENS_FACING_FRONT) {
                            cameraIdToOpen = cameraId;
                            map = characteristics
                                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                            break;
                        }

                    } else {
                        characteristics = mManager.getCameraCharacteristics(cameraId);
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
                        synchronized (lock) {
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
                            if (listener != null) {
                                listener.onCameraOpen();
                            }
                        }

                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        synchronized (lock) {
                            camera.close();
                            isStarted = false;
                            mCameraDevice = null;
                            if (listener != null) {
                                listener.onCameraClose();
                            }
                        }
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        synchronized (lock) {
                            camera.close();
                            mCameraDevice = null;
                            isStarted = false;
                            if (listener != null) {
                                listener.onCameraClose();
                            }
                        }
                    }
                }, backgroundHandler);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }


    }


    @Override
    MediaRecorder getRecorder() {
        return mMediaRecorder;
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    private static int getIntFieldIfExists(Class<?> klass, String fieldName,
                                           Class<?> obj, int defaultVal) {
        try {
            Field f = klass.getDeclaredField(fieldName);
            return f.getInt(obj);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private static boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT);
    }

    @SuppressLint("InlinedApi")
    private void setUpMediaRecorder() throws IOException {
        final Activity activity = (Activity) mContext;
        if (null == activity) {
            return;
        }

        synchronized (lock) {
            mMediaRecorder = new MediaRecorder();
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
            Date today = Calendar.getInstance().getTime();
            if (getSaveToGallery() && hasStoragePermission()) {
                File cameraDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
                if (!cameraDir.exists()) {
                    final boolean mkdirs = cameraDir.mkdirs();
                }
                setFile(new File(cameraDir, "VID_" + df.format(today) + ".mp4"));
            } else {
                setFile(new File(mContext.getExternalFilesDir(null), "VID_" + df.format(today) + ".mp4"));
            }
            CamcorderProfile profile = getCamcorderProfile(FancyCamera.Quality.values()[quality]);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
            mMediaRecorder.setAudioChannels(profile.audioChannels);

            if (mOrientation == null || mOrientation == FancyCamera.CameraOrientation.UNKNOWN) {
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
            } else {
                switch (mOrientation) {
                    case PORTRAIT_UPSIDE_DOWN:
                        mMediaRecorder.setOrientationHint(270);
                        break;
                    case LANDSCAPE_LEFT:
                        mMediaRecorder.setOrientationHint(0);
                        break;
                    case LANDSCAPE_RIGHT:
                        mMediaRecorder.setOrientationHint(180);
                        break;
                    default:
                        mMediaRecorder.setOrientationHint(90);
                        break;
                }
            }

            boolean isHEVCSupported = !disableHEVC && android.os.Build.VERSION.SDK_INT >= 24;

            // Use half bit rate for HEVC
            int videoBitRate = isHEVCSupported ? profile.videoBitRate / 2 : profile.videoBitRate;
            int maxVideoBitrate = profile.videoBitRate;
            if (this.maxVideoBitrate > -1) {
                maxVideoBitrate = this.maxVideoBitrate;
            }
            int maxVideoFrameRate = profile.videoFrameRate;
            if (this.maxVideoFrameRate > -1) {
                maxVideoFrameRate = this.maxVideoFrameRate;
            }
            int maxAudioBitRate = profile.audioBitRate;
            if (this.maxAudioBitRate > -1) {
                maxAudioBitRate = this.maxAudioBitRate;
            }

            mMediaRecorder.setVideoFrameRate(Math.min(profile.videoFrameRate, maxVideoFrameRate));
            mMediaRecorder.setVideoEncodingBitRate(Math.min(videoBitRate, maxVideoBitrate));
            mMediaRecorder.setAudioEncodingBitRate(Math.min(profile.audioBitRate, maxAudioBitRate));

            if (isHEVCSupported) {
                int h265 = Camera2.getIntFieldIfExists(MediaRecorder.VideoEncoder.class,
                        "HEVC", null, MediaRecorder.VideoEncoder.DEFAULT);
                if (h265 == MediaRecorder.VideoEncoder.DEFAULT) {
                    h265 = Camera2.getIntFieldIfExists(MediaRecorder.VideoEncoder.class,
                            "H265", null, MediaRecorder.VideoEncoder.DEFAULT);
                }
                // Emulator seems to dislike H264/HEVC
                if (isEmulator()) {
                    mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                } else {
                    mMediaRecorder.setVideoEncoder(h265);
                }
            } else {
                mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            }
            mMediaRecorder.setAudioEncoder(profile.audioCodec);
            mMediaRecorder.setOutputFile(getFile().getPath());
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

            mMediaRecorder.prepare();
        }
    }


    private void updateAutoFocus(boolean isVideo) {
        if (mAutoFocus) {
            int[] modes = characteristics.get(
                    CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            // Auto focus is not supported
            if (modes == null || modes.length == 0 ||
                    (modes.length == 1 && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF)) {
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_OFF);
            } else {
                boolean hasVideoSupport = false;
                boolean hasPhotoSupport = false;
                boolean hasAutoSupport = false;
                for (int mode : modes) {
                    if (mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) {
                        hasVideoSupport = true;
                    }
                    if (mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) {
                        hasPhotoSupport = true;
                    }
                    if (mode == CaptureRequest.CONTROL_AF_MODE_AUTO) {
                        hasAutoSupport = true;
                    }
                }
                if (isVideo) {
                    if (hasVideoSupport) {
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                    } else if (hasAutoSupport) {
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_AUTO);
                    }
                } else {
                    if (hasPhotoSupport) {
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    } else if (hasAutoSupport) {
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_AUTO);
                    }

                }
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
        synchronized (lock) {
            if (mCameraDevice == null || !getHolder().isAvailable()) {
                return;
            }
            SurfaceTexture texture = getHolder().getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            try {
                if (mCameraDevice == null) return;
                mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                Surface previewSurface = new Surface(texture);
                mPreviewBuilder.addTarget(previewSurface);
                updateAutoFocus(true);
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
                                Log.e(TAG, "configure fail " + session.toString());
                            }
                        }, backgroundHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "CameraAccessException " + e.toString());
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
        synchronized (lock) {
            if (isStarted && mCameraDevice != null) {
                return;
            }
            stop();
            openCamera(getHolder().getWidth(), getHolder().getHeight());
        }
    }


    @Override
    void stop() {
        synchronized (lock) {
            if (!isStarted && mCameraDevice == null) {
                return;
            }
            if (mCameraDevice != null) {
                closePreviewSession();
                isStarted = false;
                if (mCameraDevice == null) {
                    return;
                }
                mCameraDevice.close();
                mCameraDevice = null;
            }
        }
    }


    @Override
    void startRecording() {
        synchronized (lock) {
            if (null == mCameraDevice || !getHolder().isAvailable() || null == previewSize || isRecording) {
                return;
            }
            closePreviewSession();
            try {
                setUpMediaRecorder();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            SurfaceTexture texture = getHolder().getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            try {
                mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                return;
            }
            updateAutoFocus(true);
            setupFlash(mPreviewBuilder);
            List<Surface> surfaces = new ArrayList<>();
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);
            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);
            try {
                mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull final CameraCaptureSession cameraCaptureSession) {
                        synchronized (lock) {
                            mPreviewSession = cameraCaptureSession;
                            updatePreview();
                            Handler mainHandler = new Handler(mContext.getMainLooper());
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    synchronized (lock) {
                                        try {
                                            mMediaRecorder.start();
                                        } catch (IllegalStateException e) {
                                            try {
                                                setUpMediaRecorder();
                                            } catch (IOException ex) {
                                                ex.printStackTrace();
                                            }
                                        }
                                        isRecording = true;
                                        startDurationTimer();
                                        if (listener != null) {
                                            listener.onVideoEvent(new VideoEvent(EventType.INFO, null, VideoEvent.EventInfo.RECORDING_STARTED.toString()));
                                        }
                                    }
                                }
                            });
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                        Log.e(TAG, "onConfigureFailed");
                    }
                }, backgroundHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
            }
        }
    }

    private boolean isCapturingPhoto;

    @Override
    void takePhoto() {
        synchronized (lock) {
            if (null == mCameraDevice || !getHolder().isAvailable() || null == previewSize || isRecording || isCapturingPhoto) {
                return;
            }

            try {
                isCapturingPhoto = true;
                Size[] jpegSizes = null;

                int width = 640;
                int height = 480;
                if (characteristics != null) {
                    jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
                }

                if (jpegSizes != null && jpegSizes.length > 0) {
                    Size size = jpegSizes[0];
                    width = size.getHeight();
                    height = size.getHeight();
                }

                reader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2);

                SurfaceTexture texture = getHolder().getSurfaceTexture();
                assert texture != null;
                texture.setDefaultBufferSize(width, height);

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
                if (getSaveToGallery() && hasStoragePermission()) {
                    File cameraDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
                    if (!cameraDir.exists()) {
                        final boolean mkdirs = cameraDir.mkdirs();
                    }
                    setFile(new File(cameraDir, "PIC_" + df.format(today) + ".jpg"));
                } else {
                    setFile(new File(mContext.getExternalFilesDir(null), "PIC_" + df.format(today) + ".jpg"));
                }

                reader.setOnImageAvailableListener(readOnImageAvailableListener, backgroundHandler);

                final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                        synchronized (lock) {
                            stop();
                            start(); //TODO allow user to choose
                            isCapturingPhoto = false;
                        }
                    }

                    @Override
                    public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                        super.onCaptureFailed(session, request, failure);
                        Log.e(TAG, "onCaptureFailed " + session + " " + request);
                    }
                };

                mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        synchronized (lock) {
                            if (isStarted) {
                                try {
                                    session.capture(photoPreviewBuilder.build(), captureCallback, backgroundHandler);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                    isCapturingPhoto = false;
                                }
                            }
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        synchronized (lock) {
                            isCapturingPhoto = false;
                        }
                    }
                }, backgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                isCapturingPhoto = false;
            }
        }

    }

    private void stopRecord() {
        synchronized (lock) {
            if (!isRecording) {
                return;
            }

            try {
                if (mPreviewSession != null) {
                    mPreviewSession.stopRepeating();
                    mPreviewSession.abortCaptures();
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }


            try {
                if (mMediaRecorder != null) {
                    mMediaRecorder.stop();
                }
            } catch (RuntimeException e) {
                //handle the exception
            }

            isStarted = false;
            isRecording = false;
            stopDurationTimer();

            if (mMediaRecorder != null) {
                mMediaRecorder.reset();
                mMediaRecorder.release();
            }

            mMediaRecorder = null;

            if (getFile() != null) {
                Uri contentUri = Uri.fromFile(getFile());
                Intent mediaScanIntent = new android.content.Intent(
                        "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                        contentUri
                );
                mContext.sendBroadcast(mediaScanIntent);
            }

            if (listener != null) {
                listener.onVideoEvent(new VideoEvent(EventType.INFO, getFile(), VideoEvent.EventInfo.RECORDING_FINISHED.toString()));
            }
            setFile(null);
        }
    }

    @Override
    void stopRecording() {
        synchronized (lock) {
            stopRecord();
            start();
        }
    }


    private void configureTransform(int viewWidth, int viewHeight) {
        synchronized (lock) {
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

            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);

            if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
                matrix.postRotate(90 * (rotation - 2), centerX, centerY);
            } else if (Surface.ROTATION_180 == rotation) {
                matrix.postRotate(180, centerX, centerY);
            } else {
                matrix.postRotate(0, centerX, centerY);
            }
            getHolder().setTransform(matrix);
        }
    }


    @Override
    void toggleCamera() {
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


    private void tempOffFlash(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
    }

    private void setupFlash(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.FLASH_MODE, isFlashEnabled ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
    }

    @Override
    void updatePreview() {
        synchronized (lock) {
            if (null == mCameraDevice || null == mPreviewSession) {
                return;
            }
            setUpCaptureRequestBuilder(mPreviewBuilder);
            try {
                mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, backgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    void release() {
        synchronized (lock) {
            if (isRecording) {
                stopRecord();
            }
            stop();
        }
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
    void setCameraOrientation(FancyCamera.CameraOrientation orientation) {
        synchronized (lock) {
            mOrientation = orientation;
        }
    }

    @Override
    boolean hasFlash() {
        if (characteristics != null) {
            Boolean info = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if (info != null) {
                return info;
            }
        }
        return false;
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
