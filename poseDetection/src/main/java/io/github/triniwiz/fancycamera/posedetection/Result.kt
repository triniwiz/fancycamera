package io.github.triniwiz.fancycamera.posedetection

import com.google.gson.annotations.SerializedName
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
            @SerializedName("node")
            Nose("nose"),

            @SerializedName("leftEyeInner")
            LeftEyeInner("leftEyeInner"),

            @SerializedName("leftEye")
            LeftEye("leftEye"),

            @SerializedName("leftEyeOuter")
            LeftEyeOuter("leftEyeOuter"),

            @SerializedName("rightEyeInner")
            RightEyeInner("rightEyeInner"),

            @SerializedName("rightEye")
            RightEye("rightEye"),

            @SerializedName("rightEyeOuter")
            RightEyeOuter("rightEyeOuter"),

            @SerializedName("leftEar")
            LeftEar("leftEar"),

            @SerializedName("rightEar")
            RightEar("rightEar"),

            @SerializedName("leftMouth")
            LeftMouth("leftMouth"),

            @SerializedName("rightMouth")
            RightMouth("rightMouth"),

            @SerializedName("leftShoulder")
            LeftShoulder("leftShoulder"),

            @SerializedName("rightShoulder")
            RightShoulder("rightShoulder"),

            @SerializedName("leftElbow")
            LeftElbow("leftElbow"),

            @SerializedName("rightElbow")
            RightElbow("rightElbow"),

            @SerializedName("leftWrist")
            LeftWrist("leftWrist"),

            @SerializedName("rightWrist")
            RightWrist("rightWrist"),

            @SerializedName("leftPinky")
            LeftPinky("leftPinky"),

            @SerializedName("rightPinky")
            RightPinky("rightPinky"),

            @SerializedName("leftIndex")
            LeftIndex("leftIndex"),

            @SerializedName("rightIndex")
            RightIndex("rightIndex"),

            @SerializedName("leftThumb")
            LeftThumb("leftThumb"),

            @SerializedName("rightThumb")
            RightThumb("rightThumb"),

            @SerializedName("leftHip")
            LeftHip("leftHip"),

            @SerializedName("rightHip")
            RightHip("rightHip"),

            @SerializedName("leftKnee")
            LeftKnee("leftKnee"),

            @SerializedName("rightKnee")
            RightKnee("rightKnee"),

            @SerializedName("leftAnkle")
            LeftAnkle("leftAnkle"),

            @SerializedName("rightAnkle")
            RightAnkle("rightAnkle"),

            @SerializedName("leftHeel")
            LeftHeel("leftHeel"),

            @SerializedName("rightHeel")
            RightHeel("rightHeel"),

            @SerializedName("leftFootIndex")
            LeftFootIndex("leftFootIndex"),

            @SerializedName("rightFootIndex")
            RightFootIndex("rightFootIndex"),

            @SerializedName("unknown")
            Unknown("unknown")
        }
    }
}