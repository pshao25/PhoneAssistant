package com.phoneassistant.app.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.phoneassistant.app.screen.ElementBounds
import com.phoneassistant.app.screen.ElementRole
import com.phoneassistant.app.screen.ScreenElement
import com.phoneassistant.app.screen.ScreenSnapshot
import java.security.MessageDigest

class ScreenSnapshotBuilder {
    fun build(
        root: AccessibilityNodeInfo,
        packageName: String,
    ): ScreenSnapshot {
        val candidates = mutableListOf<ElementCandidate>()
        collectCandidates(root, depth = 0, output = candidates)
        val consolidatedCandidates = consolidate(candidates)
        val elements = consolidatedCandidates
            .take(MAX_ELEMENTS)
            .map(::toScreenElement)

        return ScreenSnapshot(
            packageName = packageName,
            screenTitle = candidates.firstOrNull { it.depth <= 2 }?.label,
            capturedAt = System.currentTimeMillis(),
            elements = elements,
        )
    }

    private fun collectCandidates(
        node: AccessibilityNodeInfo,
        depth: Int,
        output: MutableList<ElementCandidate>,
    ) {
        if (depth > MAX_DEPTH || output.size >= MAX_CANDIDATES || !node.isVisibleToUser) return

        val label = node.text?.toString()?.take(MAX_TEXT_LENGTH).orEmpty()
        val description = node.contentDescription?.toString()?.take(MAX_TEXT_LENGTH).orEmpty()
        if (label.isNotBlank() || description.isNotBlank()) {
            val actionableNode = findActionableAncestor(node)
            val bounds = Rect().also(actionableNode::getBoundsInScreen)
            if (!bounds.isEmpty) {
                output += ElementCandidate(
                    depth = depth,
                    label = label.ifBlank { description },
                    description = description,
                    className = actionableNode.className?.toString().orEmpty(),
                    viewId = actionableNode.viewIdResourceName.orEmpty(),
                    clickable = actionableNode.isClickable,
                    enabled = actionableNode.isEnabled,
                    checked = if (actionableNode.isCheckable) actionableNode.isChecked else null,
                    bounds = ElementBounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
                )
            }
        }

        repeat(node.childCount) { index ->
            node.getChild(index)?.let { child -> collectCandidates(child, depth + 1, output) }
        }
    }

    private fun consolidate(candidates: List<ElementCandidate>): List<ElementCandidate> = candidates
        .groupBy { candidate -> "${candidate.bounds}|${candidate.clickable}" }
        .values
        .map { group ->
            val primary = group.first()
            val secondaryText = group
                .flatMap { candidate -> listOf(candidate.label, candidate.description) }
                .filter { value -> value.isNotBlank() && value != primary.label }
                .distinct()
                .joinToString(" / ")
                .take(MAX_TEXT_LENGTH)

            primary.copy(
                depth = group.minOf { it.depth },
                description = secondaryText,
                checked = group.firstNotNullOfOrNull { it.checked },
            )
        }

    private fun findActionableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current = node
        repeat(MAX_PARENT_SEARCH_DEPTH) {
            if (current.isClickable) return current
            current = current.parent ?: return current
        }
        return current
    }

    private fun toScreenElement(candidate: ElementCandidate): ScreenElement {
        val role = when {
            candidate.className.contains("Switch", ignoreCase = true) -> ElementRole.SWITCH
            candidate.className.contains("CheckBox", ignoreCase = true) -> ElementRole.CHECKBOX
            candidate.className.contains("EditText", ignoreCase = true) -> ElementRole.INPUT
            candidate.clickable -> ElementRole.BUTTON
            candidate.className.contains("Text", ignoreCase = true) -> ElementRole.TEXT
            else -> ElementRole.UNKNOWN
        }
        val identity = listOf(
            candidate.label.lowercase(),
            candidate.description.lowercase(),
            candidate.viewId,
            role.name,
            candidate.bounds.toString(),
        ).joinToString("|")

        return ScreenElement(
            id = "e_${identity.sha256().take(10)}",
            label = candidate.label,
            description = candidate.description,
            role = role,
            clickable = candidate.clickable,
            enabled = candidate.enabled,
            checked = candidate.checked,
            bounds = candidate.bounds,
        )
    }

    private fun String.sha256(): String = MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private data class ElementCandidate(
        val depth: Int,
        val label: String,
        val description: String,
        val className: String,
        val viewId: String,
        val clickable: Boolean,
        val enabled: Boolean,
        val checked: Boolean?,
        val bounds: ElementBounds,
    )

    private companion object {
        const val MAX_DEPTH = 12
        const val MAX_CANDIDATES = 300
        const val MAX_ELEMENTS = 200
        const val MAX_TEXT_LENGTH = 120
        const val MAX_PARENT_SEARCH_DEPTH = 8
    }
}