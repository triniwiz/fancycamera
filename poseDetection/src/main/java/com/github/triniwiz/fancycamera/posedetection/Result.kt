package com.github.triniwiz.fancycamera.posedetection

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

class Result(pose: Pose) {
    val landMarks: Array<LandMark>

    init {
        val landMarks = mutableListOf<LandMark>()
        for (landMark in pose.allPoseLandmarks) {
            landMarks.add(LandMark(landMark))
        }
        this.landMarks = landMarks.toTypedArray()
    }

    class LandMark(landmark: PoseLandmark) {
        val inFrameLikelihood = landmark.inFrameLikelihood
        val position = PointF(landmark.position)
        val type: Type

        init {
            type = when (landmark.landmarkType) {
                PoseLandmark.NOSE -> Type.Nose
                PoseLandmark.LEFT_EYE_INNER -> Type.LeftEyeInner
                PoseLandmark.LEFT_EYE -> Type.LeftEye
                PoseLandmark.LEFT_EYE_OUTER -> Type.LeftEyeOuter
                PoseLandmark.RIGHT_EYE_INNER -> Type.RightEyeInner
                PoseLandmark.RIGHT_EYE -> Type.RightEye
                PoseLandmark.RIGHT_EYE_OUTER -> Type.RightEyeOuter
                PoseLandmark.LEFT_EAR -> Type.LeftEar
                PoseLandmark.RIGHT_EAR -> Type.RightEar
                PoseLandmark.LEFT_MOUTH -> Type.LeftMouth
                PoseLandmark.RIGHT_MOUTH -> Type.RightMouth
                PoseLandmark.LEFT_SHOULDER -> Type.LeftShoulder
                PoseLandmark.RIGHT_SHOULDER -> Type.RightShoulder
                PoseLandmark.LEFT_ELBOW -> Type.LeftElbow
                PoseLandmark.RIGHT_ELBOW -> Type.RightElbow
                PoseLandmark.LEFT_WRIST -> Type.LeftWrist
                PoseLandmark.RIGHT_WRIST -> Type.RightWrist
                PoseLandmark.LEFT_PINKY -> Type.LeftPinky
                PoseLandmark.RIGHT_PINKY -> Type.RightPinky
                PoseLandmark.LEFT_INDEX -> Type.LeftIndex
                PoseLandmark.RIGHT_INDEX -> Type.RightIndex
                PoseLandmark.LEFT_THUMB -> Type.LeftThumb
                PoseLandmark.RIGHT_THUMB -> Type.RightThumb
                PoseLandmark.LEFT_HIP -> Type.LeftHip
                PoseLandmark.RIGHT_HIP -> Type.RightHip
                PoseLandmark.LEFT_KNEE -> Type.LeftKnee
                PoseLandmark.RIGHT_KNEE -> Type.RightKnee
                PoseLandmark.LEFT_ANKLE -> Type.LeftAnkle
                PoseLandmark.RIGHT_ANKLE -> Type.RightAnkle
                PoseLandmark.LEFT_HEEL -> Type.LeftHeel
                PoseLandmark.RIGHT_HEEL -> Type.RightHeel
                PoseLandmark.LEFT_FOOT_INDEX -> Type.LeftFootIndex
                PoseLandmark.RIGHT_FOOT_INDEX -> Type.RightFootIndex
                else -> Type.Unknown
            }
        }

        class PointF(point: android.graphics.PointF) {
            val x = point.x
            val y = point.y
        }

        enum class Type(val type: String) {
            Nose("nose"),
            LeftEyeInner("leftEyeInner"),
            LeftEye("leftEye"),
            LeftEyeOuter("leftEyeOuter"),
            RightEyeInner("RightEyeInner"),
            RightEye("leftEye"),
            RightEyeOuter("leftEyeOuter"),
            LeftEar("leftEar"),
            RightEar("rightEar"),
            LeftMouth("leftMouth"),
            RightMouth("rightMouth"),
            LeftShoulder("leftShoulder"),
            RightShoulder("rightShoulder"),
            LeftElbow("leftElbow"),
            RightElbow("rightElbow"),
            LeftWrist("leftWrist"),
            RightWrist("rightWrist"),
            LeftPinky("leftPinky"),
            RightPinky("rightPinky"),
            LeftIndex("leftIndex"),
            RightIndex("rightIndex"),
            LeftThumb("leftThumb"),
            RightThumb("rightThumb"),
            LeftHip("leftHip"),
            RightHip("rightHip"),
            LeftKnee("leftKnee"),
            RightKnee("rightKnee"),
            LeftAnkle("leftAnkle"),
            RightAnkle("rightAnkle"),
            LeftHeel("leftHeel"),
            RightHeel("rightHeel"),
            LeftFootIndex("leftFootIndex"),
            RightFootIndex("rightFootIndex"),
            Unknown("unknown")
        }
    }
}