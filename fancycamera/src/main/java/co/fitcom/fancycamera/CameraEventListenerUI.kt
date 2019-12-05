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
                            WHAT_EVENT -> {
                                if (eventData.getString(FILE) != null) {
                                    file = File(eventData.getString(FILE)!!)
                                }
                                onEventUI(Event(type, file, message))
                            }
                            WHAT_CAMERA_CLOSE_EVENT -> onCameraCloseUI()
                            WHAT_CAMERA_OPEN_EVENT -> onCameraOpenUI()
                        }
                    }
                }
            }
        }
    }


    override fun onEvent(event: Event) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onEventUI(event)
            return
        }
        ensureHandler()
        val message = handler!!.obtainMessage()
        message.what = WHAT_EVENT
        val bundle = Bundle()
        bundle.putString(MESSAGE, event.message)
        bundle.putSerializable(TYPE, event.type)
        if (event.file != null) {
            bundle.putString(FILE, event.file.path)
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

    abstract fun onEventUI(event: Event)


    companion object {
        private val WHAT_EVENT = 0x01
        private val WHAT_CAMERA_CLOSE_EVENT = 0x02
        private val WHAT_CAMERA_OPEN_EVENT = 0x03
        private val MESSAGE = "message"
        private val TYPE = "type"
        private val FILE = "file"
    }
}
