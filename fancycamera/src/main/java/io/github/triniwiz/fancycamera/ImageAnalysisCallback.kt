package io.github.triniwiz.fancycamera

import java.lang.Exception

interface ImageAnalysisCallback {
    fun onSuccess(result: Any)
    fun onError(message: String, exception: Exception)
}