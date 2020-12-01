package com.github.triniwiz.fancycamera.facedetection

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
        class Origin(val x: Int, val y: Int)
        class Size(val width: Int,
                   val height: Int)

        val origin = Origin(rect.left, rect.right)
        val size = Size(rect.width(), rect.height())
    }
}