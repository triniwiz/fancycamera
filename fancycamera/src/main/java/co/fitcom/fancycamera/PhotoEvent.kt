/*
 * Created By Osei Fortune on 2/16/18 8:43 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 7:39 PM
 *
 */

package co.fitcom.fancycamera

import java.io.File


class PhotoEvent internal constructor(val type: EventType, val file: File?, val message: String?) {

    enum class EventError {
        UNKNOWN {
            override fun toString(): String {
                return "Unknown"
            }
        }
    }

    enum class EventInfo {
        PHOTO_TAKEN {
            override fun toString(): String {
                return "Photo taken"
            }
        },
        UNKNOWN {
            override fun toString(): String {
                return "Unknown"
            }
        }
    }

}
