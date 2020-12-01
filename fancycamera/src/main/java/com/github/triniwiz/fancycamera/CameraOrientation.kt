package com.github.triniwiz.fancycamera

enum class CameraOrientation constructor(val value: Int) {
    UNKNOWN(0),
    PORTRAIT(1),
    PORTRAIT_UPSIDE_DOWN(2),
    LANDSCAPE_LEFT(3),
    LANDSCAPE_RIGHT(4);

    companion object {
        fun from(value: Int): CameraOrientation = values().first { it.value == value }
    }
}