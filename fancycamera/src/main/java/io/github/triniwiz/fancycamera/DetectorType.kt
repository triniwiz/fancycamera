package io.github.triniwiz.fancycamera

enum class DetectorType(private val type: String) {
    Barcode("barcode"), DigitalInk("digitalInk"), Face("face"), Image("image"), Object("object"), Pose("pose"), Text("text"), All("all"), None("none"), Selfie("selfie");


    override fun toString(): String {
        return type
    }
}