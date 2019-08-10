/*
 * Created By Osei Fortune on 2/16/18 8:44 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 7:39 PM
 *
 */

package co.fitcom.fancycamera;

import androidx.annotation.Nullable;

import java.io.File;

public class VideoEvent {
    private EventType mType;
    private File mFile;
    private String mMessage;
    VideoEvent(EventType type, @Nullable File file, @Nullable String message){
        mType = type;
        mFile = file;
        mMessage = message;
    }
    public EventType getType(){
        return mType;
    }
    public File getFile(){
        return  mFile;
    }

    public String getMessage(){
        return  mMessage;
    }

    public enum EventError{
        SERVER_DIED{
            @Override
            public String toString() {
                return "Server died";
            }
        },
        UNKNOWN{
            @Override
            public String toString() {
                return "Unknown";
            }
        }
    }

    public enum EventInfo{
        RECORDING_STARTED{
            @Override
            public String toString() {
                return "Recording started";
            }
        },
        RECORDING_FINISHED{
            @Override
            public String toString() {
                return "Recording finished";
            }
        },
        MAX_DURATION_REACHED {
            @Override
            public String toString() {
                return "Max duration reached";
            }
        },
        MAX_FILESIZE_APPROACHING{
            @Override
            public String toString() {
                return "Max filesize approaching";
            }
        },
        MAX_FILESIZE_REACHED{
            @Override
            public String toString() {
                return "Max filesize reached";
            }
        },
        NEXT_OUTPUT_FILE_STARTED{
            @Override
            public String toString() {
                return "Next output file started";
            }
        },
        UNKNOWN{
            @Override
            public String toString() {
                return "Unknown";
            }
        }
    }
}
