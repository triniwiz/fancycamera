package io.github.triniwiz.fancycamera.facedetection

import android.graphics.Rect
import com.google.mlkit.vision.face.Face

class Result(instance: Face) {
    val smilingProbability = instance.smilingProbability
    val leftEyeOpenProbability = instance.leftEyeOpenProbability
    val rightEyeOpenProbability = instance.rightEyeOpenProbability
    val trackingId = instance.smilingProbability
    val bounds = Bounds(instance.boundingBox)
    val headEulerAngleZ = instance.headEulerAngleZ
    val headEulerAngleY = instance.headEulerAngleY
    val headEulerAngleX = instance.headEulerAngleX


    class Bounds(rect: Rect) {
        val x: Int = rect.left

        val y: Int = rect.top

        val width: Int = rect.width()

        val height: Int = rect.height()
    }
}