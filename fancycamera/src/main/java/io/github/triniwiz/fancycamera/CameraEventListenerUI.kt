/*
 * Created By Osei Fortune on 2/16/18 8:43 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 7:51 PM
 *
 */

package io.github.triniwiz.fancycamera

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message

import java.io.File
import java.lang.Exception


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
                        if (eventData == null && (msg.what != WHAT_CAMERA_CLOSE_EVENT || msg.what != WHAT_CAMERA_OPEN_EVENT || msg.what != WHAT_CAMERA_VIDEO_START_EVENT)) {
                            return
                        }
                        val message = eventData.getString(MESSAGE)
                        var file: File? = null
                        when (msg.what) {
                            WHAT_CAMERA_PHOTO_EVENT -> {
                                if (eventData.getString(FILE) != null) {
                                    file = File(eventData.getString(FILE)!!)
                                }
                                onCameraPhotoUI(file)
                            }
                            WHAT_CAMERA_VIDEO_EVENT -> {
                                if (eventData.getString(FILE) != null) {
                                    file = File(eventData.getString(FILE)!!)
                                }
                                onCameraVideoUI(file)
                            }
                            WHAT_CAMERA_ANALYSIS_EVENT -> {
                                onCameraAnalysisUI(msg.obj as ImageAnalysis)
                            }
                            WHAT_CAMERA_CLOSE_EVENT -> onCameraCloseUI()
                            WHAT_CAMERA_OPEN_EVENT -> onCameraOpenUI()
                            WHAT_READY_EVENT -> onReadyUI()
                            WHAT_CAMERA_VIDEO_START_EVENT -> onCameraVideoStartUI()
                            WHAT_CAMERA_ERROR_EVENT -> {
                                onCameraErrorUI(message!!, msg.obj as Exception)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onReady() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onReadyUI()
            return
        }
        ensureHandler()
        val message = handler!!.obtainMessage()
        message.what = WHAT_READY_EVENT
        val bundle = Bundle()
        message.data = bundle
        handler!!.sendMessage(message)
    }

    override fun onCameraPhoto(file: File?) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onCameraPhotoUI(file)
            return
        }
        ensureHandler()
        val message = handler!!.obtainMessage()
        message.what = WHAT_CAMERA_PHOTO_EVENT
        val bundle = Bundle()
        if (file != null) {
            bundle.putString(FILE, file.path)
        }
        message.data = bundle
        handler!!.sendMessage(message)
    }

    override fun onCameraVideo(file: File?) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onCameraVideoUI(file)
            return
        }
        ensureHandler()
        val message = handler!!.obtainMessage()
        message.what = WHAT_CAMERA_VIDEO_EVENT
        val bundle = Bundle()
        if (file != null) {
            bundle.putString(FILE, file.path)
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

    override fun onCameraVideoStart() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onCameraVideoStartUI()
            return
        }
        ensureHandler()
        val message = handler!!.obtainMessage()
        message.what = WHAT_CAMERA_VIDEO_START_EVENT
        val bundle = Bundle()
        message.data = bundle
        handler!!.sendMessage(message)
    }

    override fun onCameraAnalysis(analysis: ImageAnalysis) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onCameraAnalysisUI(analysis)
            return
        }
        ensureHandler()
        val message = handler!!.obtainMessage()
        message.what = WHAT_CAMERA_ANALYSIS_EVENT
        val bundle = Bundle()
        message.data = bundle
        message.obj = analysis
        handler!!.sendMessage(message)
    }

    override fun onCameraError(message: String, ex: Exception) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onCameraErrorUI(message, ex)
            return
        }
        ensureHandler()
        val msg = handler!!.obtainMessage()
        msg.what = WHAT_CAMERA_ERROR_EVENT
        val bundle = Bundle()
        bundle.putString(MESSAGE, message)
        msg.data = bundle
        msg.obj = ex
        handler!!.sendMessage(msg)
    }

    abstract fun onReadyUI()

    abstract fun onCameraOpenUI()

    abstract fun onCameraCloseUI()

    abstract fun onCameraPhotoUI(file: File?)

    abstract fun onCameraVideoUI(file: File?)

    abstract fun onCameraVideoStartUI()

    abstract fun onCameraAnalysisUI(analysis: ImageAnalysis)

    abstract fun onCameraErrorUI(message: String, ex: Exception)


    companion object {
        private val WHAT_CAMERA_CLOSE_EVENT = 0x01
        private val WHAT_CAMERA_OPEN_EVENT = 0x02
        private val WHAT_CAMERA_PHOTO_EVENT = 0x03
        private val WHAT_CAMERA_VIDEO_EVENT = 0x04
        private val WHAT_CAMERA_ANALYSIS_EVENT = 0x05
        private val WHAT_CAMERA_ERROR_EVENT = 0x06
        private val WHAT_CAMERA_VIDEO_START_EVENT = 0x07
        private val WHAT_READY_EVENT = 0x08
        private val MESSAGE = "message"
        private val TYPE = "type"
        private val FILE = "file"
    }
}
