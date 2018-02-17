/*
 * Created By Osei Fortune on 2/16/18 8:43 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 7:51 PM
 *
 */

package co.fitcom.fancycamera;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.io.File;


public abstract class CameraEventListenerUI implements CameraEventListener {
    private Handler handler;
    private static final int WHAT_PHOTO_EVENT = 0x01;
    private static final int WHAT_VIDEO_EVENT = 0x02;
    private static final String MESSAGE = "message";
    private static final String TYPE = "type";
    private static final String FILE = "file";

    private void ensureHandler() {
        if (handler != null) {
            return;
        }
        synchronized (CameraEventListenerUI.class) {
            if (handler == null) {
                handler = new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
                        Bundle eventData = msg.getData();
                        if (eventData == null) {
                            return;
                        }
                        EventType type = (EventType) eventData.getSerializable(TYPE);
                        String message = eventData.getString(MESSAGE);
                        File file = null;
                        switch (msg.what) {
                            case WHAT_PHOTO_EVENT:
                                if (eventData.getString(FILE) != null) {
                                    file = new File(eventData.getString(FILE));
                                }
                                onPhotoEventUI(new PhotoEvent(type, file, message));
                                break;
                            case WHAT_VIDEO_EVENT:
                                if (eventData.getString(FILE) != null) {
                                    file = new File(eventData.getString(FILE));
                                }
                                onVideoEventUI(new VideoEvent(type, file, message));
                                break;
                        }
                    }
                };
            }
        }
    }


    @Override
    public void onPhotoEvent(PhotoEvent event) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onPhotoEventUI(event);
            return;
        }
        ensureHandler();
        Message message = handler.obtainMessage();
        message.what = WHAT_PHOTO_EVENT;
        Bundle bundle = new Bundle();
        bundle.putString(MESSAGE, event.getMessage());
        bundle.putSerializable(TYPE, event.getType());
        if (event.getFile() != null) {
            bundle.putString(FILE, event.getFile().getPath());
        }
        message.setData(bundle);
        handler.sendMessage(message);
    }

    @Override
    public void onVideoEvent(VideoEvent event) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onVideoEventUI(event);
            return;
        }
        ensureHandler();
        Message message = handler.obtainMessage();
        message.what = WHAT_VIDEO_EVENT;
        Bundle bundle = new Bundle();
        bundle.putString(MESSAGE, event.getMessage());
        bundle.putSerializable(TYPE, event.getType());
        if (event.getFile() != null) {
            bundle.putString(FILE, event.getFile().getPath());
        }
        message.setData(bundle);
        handler.sendMessage(message);
    }

    public abstract void onPhotoEventUI(PhotoEvent event);

    public abstract void onVideoEventUI(VideoEvent event);
}
