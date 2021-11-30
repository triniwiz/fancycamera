package io.github.triniwiz.fancycamera.digitalInkRecognition

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.LinearLayout

class DigitalInkRecognition {
    class View(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) :
        LinearLayout(context, attrs, defStyleAttr) {
        override fun onTouchEvent(event: MotionEvent?): Boolean {
            return super.onTouchEvent(event)
        }
    }
}