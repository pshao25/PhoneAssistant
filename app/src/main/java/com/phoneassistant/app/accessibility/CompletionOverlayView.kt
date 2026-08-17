package com.phoneassistant.app.accessibility

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView

class CompletionOverlayView(context: Context) : TextView(context) {
    init {
        text = "Done"
        setTextColor(Color.WHITE)
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(32, 18, 32, 18)
        background = GradientDrawable().apply {
            setColor(Color.rgb(30, 126, 76))
            cornerRadius = 24f
        }
        elevation = 12f
    }
}