package com.github.triniwiz.fancycamera

import java.lang.Exception

interface ImageAnalysisCallback {
    fun onSuccess(result: String)
    fun onError(message: String, exception: Exception)
}