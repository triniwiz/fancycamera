/*
 * Created By Osei Fortune on 2/16/18 8:44 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 7:39 PM
 *
 */

package co.fitcom.fancycamera

import java.io.File

class VideoEvent internal constructor(val type: EventType, val file: File?, val message: String?) {

    enum class EventError {
        SERVER_DIED {
            override fun toString(): String {
                return "Server died"
            }
        },
        UNKNOWN {
            override fun toString(): String {
                return "Unknown"
            }
        }
    }

    enum class EventInfo {
        RECORDING_STARTED {
            override fun toString(): String {
                return "Recording started"
            }
        },
        RECORDING_FINISHED {
            override fun toString(): String {
                return "Recording finished"
            }
        },
        MAX_DURATION_REACHED {
            override fun toString(): String {
                return "Max duration reached"
            }
        },
        MAX_FILESIZE_APPROACHING {
            override fun toString(): String {
                return "Max filesize approaching"
            }
        },
        MAX_FILESIZE_REACHED {
            override fun toString(): String {
                return "Max filesize reached"
            }
        },
        NEXT_OUTPUT_FILE_STARTED {
            override fun toString(): String {
                return "Next output file started"
            }
        },
        UNKNOWN {
            override fun toString(): String {
                return "Unknown"
            }
        }
    }
}
