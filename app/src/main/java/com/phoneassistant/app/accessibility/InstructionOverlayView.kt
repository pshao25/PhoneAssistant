package com.phoneassistant.app.accessibility

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

class InstructionOverlayView(
    context: Context,
    showConfirm: Boolean,
    confirmLabel: String = "Confirm",
    onConfirm: () -> Unit,
    onExit: () -> Unit,
) : LinearLayout(context) {
    private val instructionText = TextView(context)

    var instruction: CharSequence
        get() = instructionText.text
        set(value) {
            instructionText.text = value
        }

    init {
        val density = resources.displayMetrics.density
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(
            (12 * density).toInt(),
            (8 * density).toInt(),
            (8 * density).toInt(),
            (8 * density).toInt(),
        )
        background = GradientDrawable().apply {
            setColor(Color.argb(242, 35, 35, 35))
            setStroke((1 * density).toInt(), Color.rgb(255, 193, 7))
            cornerRadius = 8 * density
        }
        elevation = 4 * density

        instructionText.apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        addView(
            instructionText,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        val actions = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END
        }
        actions.addView(createButton("Exit", isPrimary = false, onExit))
        if (showConfirm) {
            actions.addView(createButton(confirmLabel, isPrimary = true, onConfirm))
        }
        addView(
            actions,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (30 * density).toInt()).apply {
                topMargin = (6 * density).toInt()
            },
        )
    }

    private fun createButton(
        label: String,
        isPrimary: Boolean,
        onClick: () -> Unit,
    ): TextView {
        val density = resources.displayMetrics.density
        return TextView(context).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(if (isPrimary) Color.BLACK else Color.WHITE)
            setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
            background = GradientDrawable().apply {
                setColor(if (isPrimary) Color.rgb(255, 193, 7) else Color.TRANSPARENT)
                setStroke((1 * density).toInt(), Color.WHITE)
                cornerRadius = 15 * density
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply {
                marginStart = (8 * density).toInt()
            }
        }
    }
}