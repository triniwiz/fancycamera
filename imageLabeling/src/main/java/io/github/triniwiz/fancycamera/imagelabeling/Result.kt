package io.github.triniwiz.fancycamera.imagelabeling

import com.google.mlkit.vision.label.ImageLabel

class Result(label: ImageLabel) {
    val text = label.text
    val confidence = label.confidence
    val index = label.index
}