/*
 * Created By Osei Fortune on 2/16/18 8:43 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 7:39 PM
 *
 */

package co.fitcom.fancycamera;

import android.support.annotation.Nullable;

import java.io.File;


public class PhotoEvent {
    private EventType mType;
    private File mFile;
    private String mMessage;

    PhotoEvent(EventType type, @Nullable File file, @Nullable String message) {
        mType = type;
        mFile = file;
        mMessage = message;
    }

    public EventType getType() {
        return mType;
    }

    public File getFile() {
        return mFile;
    }

    public String getMessage() {
        return mMessage;
    }

    public enum EventError {
        UNKNOWN {
            @Override
            public String toString() {
                return "Unknown";
            }
        }
    }

    public enum EventInfo {
        PHOTO_TAKEN {
            @Override
            public String toString() {
                return "Photo taken";
            }
        },
        UNKNOWN {
            @Override
            public String toString() {
                return "Unknown";
            }
        }
    }

}
