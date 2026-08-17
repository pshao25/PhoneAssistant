package com.phoneassistant.app.screen

data class ElementBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val isEmpty: Boolean
        get() = right <= left || bottom <= top
}

enum class ElementRole {
    BUTTON,
    CHECKBOX,
    INPUT,
    SWITCH,
    TEXT,
    UNKNOWN,
}

data class ScreenElement(
    val id: String,
    val label: String,
    val description: String,
    val role: ElementRole,
    val clickable: Boolean,
    val enabled: Boolean,
    val checked: Boolean?,
    val bounds: ElementBounds,
)

data class ScreenSnapshot(
    val packageName: String,
    val screenTitle: String?,
    val capturedAt: Long,
    val elements: List<ScreenElement>,
)