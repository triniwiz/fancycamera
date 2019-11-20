/*
 * Created By Osei Fortune on 2/16/18 8:43 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 7:51 PM
 *
 */

package co.fitcom.fancycamera

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log

import java.io.File


abstract class CameraEventListenerUI : CameraEventListener {
    private var handler: Handler? = null

    private fun ensureHandler() {
        if (handler != null) {
            return
        }
        synchronized(CameraEventListenerUI::class.java) {
            if (handler == null) {
                handler = object : Handler(Looper.getMainLooper()) {
                    override fun handleMessage(msg: Message) {
                        val eventData = msg.data
                        if (eventData == null && (msg.what != WHAT_CAMERA_CLOSE_EVENT || msg.what != WHAT_CAMERA_OPEN_EVENT)) {
                            return
                        }
                        val type = eventData!!.getSerializable(TYPE) as EventType
                        val message = eventData.getString(MESSAGE)
                        var file: File? = null
                        when (msg.what) {
                            WHAT_PHOTO_EVENT -> {
                                if (eventData.getString(FILE) != null) {
                                    file = File(eventData.getString(FILE)!!)
                                }
                                onPhotoEventUI(PhotoEvent(type, file, message))
                            }
                            WHAT_VIDEO_EVENT -> {
                                if (eventData.getString(FILE) != null) {
                                    file = File(eventData.getString(FILE)!!)
                                }
                                onVideoEventUI(VideoEvent(type, file, message))
                            }
                            WHAT_CAMERA_CLOSE_EVENT -> onCameraCloseUI()
                            WHAT_CAMERA_OPEN_EVENT -> onCameraOpenUI()
                        }
                    }
                }
            }
        }
    }


    override fun onPhotoEvent(event: PhotoEvent) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onPhotoEventUI(event)
            return
        }
        ensureHandler()
        val message = handler!!.obtainMessage()
        message.what = WHAT_PHOTO_EVENT
        val bundle = Bundle()
        bundle.putString(MESSAGE, event.message)
        bundle.putSerializable(TYPE, event.type)
        if (event.file != null) {
            bundle.putString(FILE, event.file!!.path)
        }
        message.data = bundle
        handler!!.sendMessage(message)
    }

    override fun onVideoEvent(event: VideoEvent) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onVideoEventUI(event)
            return
        }
        ensureHandler()
        val message = handler!!.obtainMessage()
        message.what = WHAT_VIDEO_EVENT
        val bundle = Bundle()
        bundle.putString(MESSAGE, event.message)
        bundle.putSerializable(TYPE, event.type)
        if (event.file != null) {
            bundle.putString(FILE, event.file!!.path)
        }
        message.data = bundle
        handler!!.sendMessage(message)
    }

    override fun onCameraClose() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onCameraCloseUI()
            return
        }
        ensureHandler()
        val message = handler!!.obtainMessage()
        message.what = WHAT_CAMERA_CLOSE_EVENT
        val bundle = Bundle()
        message.data = bundle
        handler!!.sendMessage(message)
    }

    override fun onCameraOpen() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onCameraOpenUI()
            return
        }
        ensureHandler()
        val message = handler!!.obtainMessage()
        message.what = WHAT_CAMERA_OPEN_EVENT
        val bundle = Bundle()
        message.data = bundle
        handler!!.sendMessage(message)
    }

    abstract fun onCameraOpenUI()

    abstract fun onCameraCloseUI()

    abstract fun onPhotoEventUI(event: PhotoEvent)

    abstract fun onVideoEventUI(event: VideoEvent)

    companion object {
        private val WHAT_PHOTO_EVENT = 0x01
        private val WHAT_VIDEO_EVENT = 0x02
        private val WHAT_CAMERA_CLOSE_EVENT = 0x03
        private val WHAT_CAMERA_OPEN_EVENT = 0x04
        private val MESSAGE = "message"
        private val TYPE = "type"
        private val FILE = "file"
    }
}
