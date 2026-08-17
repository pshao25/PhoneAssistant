package com.phoneassistant.app.accessibility

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class HighlightOverlayView(context: Context) : View(context) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(36, 255, 193, 7)
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 193, 7)
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = strokePaint.strokeWidth / 2
        val radius = 18f
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, fillPaint)
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, strokePaint)
    }
}