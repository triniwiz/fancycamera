/*
 * Created By Osei Fortune on 2/16/18 8:43 PM
 * Copyright (c) 2018
 * Last modified 2/16/18 7:16 PM
 *
 */

package co.fitcom.fancycamera

interface CameraEventListener {
    fun onCameraOpen()
    fun onCameraClose()
    fun onPhotoEvent(event: PhotoEvent)
    fun onVideoEvent(event: VideoEvent)
}
