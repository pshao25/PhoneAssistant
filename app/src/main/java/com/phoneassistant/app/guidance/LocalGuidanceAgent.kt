package com.phoneassistant.app.guidance

import com.phoneassistant.app.screen.ScreenElement

fun interface GuidanceAgent {
    fun decide(request: GuidanceRequest): GuidanceDecision
}

class LocalGuidanceAgent : GuidanceAgent {
    override fun decide(request: GuidanceRequest): GuidanceDecision {
        val goal = normalize(request.userGoal)
        val desiredCheckedState = desiredCheckedState(goal)
        val rankedElements = request.screen.elements
            .asSequence()
            .filter { it.enabled && it.clickable }
            .map { element -> element to score(element, goal) }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .toList()

        val bestMatch = rankedElements.firstOrNull()
            ?: return GuidanceDecision(
                status = GuidanceStatus.CLARIFY,
                targetElementId = null,
                instruction = "I cannot find a visible control for that goal. What would you like to tap?",
                confidence = 0.0,
                risk = RiskLevel.LOW,
            )

        val (element, score) = bestMatch
        val stateElement = request.screen.elements.firstOrNull { candidate ->
            candidate.checked != null &&
                (normalize(candidate.label) == normalize(element.label) ||
                    normalize(candidate.description) == normalize(element.label))
        }
        if (desiredCheckedState != null && stateElement?.checked == desiredCheckedState) {
            return GuidanceDecision(
                status = GuidanceStatus.COMPLETE,
                targetElementId = null,
                instruction = "Done. ${element.label} is ${if (desiredCheckedState) "on" else "off"}.",
                confidence = score.coerceAtMost(1.0),
                risk = RiskLevel.LOW,
            )
        }

        return GuidanceDecision(
            status = GuidanceStatus.CONTINUE,
            targetElementId = element.id,
            instruction = "Tap ${element.label.ifBlank { element.description }}.",
            expectedResult = if (desiredCheckedState != null && stateElement != null) {
                ExpectedResult(
                    elementId = stateElement.id,
                    property = "checked",
                    booleanValue = desiredCheckedState,
                    successMessage = "Done. ${element.label} is " +
                        if (desiredCheckedState) "on." else "off.",
                )
            } else {
                null
            },
            confidence = score.coerceAtMost(1.0),
            risk = RiskLevel.LOW,
        )
    }

    private fun desiredCheckedState(normalizedGoal: String): Boolean? = when {
        OFF_WORDS.any(normalizedGoal::contains) -> false
        ON_WORDS.any(normalizedGoal::contains) -> true
        else -> null
    }

    private fun score(element: ScreenElement, normalizedGoal: String): Double {
        val label = normalize(element.label)
        val description = normalize(element.description)
        if (label.isNotBlank() && normalizedGoal.contains(label)) return 1.0
        if (description.isNotBlank() && normalizedGoal.contains(description)) return 0.95
        if (SIZE_DIRECTIONS.any { direction ->
                direction in normalizedGoal && direction in "$label $description"
            }
        ) return 0.9

        val goalTokens = normalizedGoal.tokens()
        val elementTokens = "$label $description".tokens()
        if (goalTokens.isEmpty() || elementTokens.isEmpty()) return 0.0
        return goalTokens.intersect(elementTokens).size.toDouble() / goalTokens.size
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\b(bigger|increase|enlarge)\\b"), "larger")
        .trim()

    private fun String.tokens(): Set<String> = split(' ')
        .filter { it.length > 1 && it !in STOP_WORDS }
        .toSet()

    private companion object {
        val STOP_WORDS = setOf("a", "an", "the", "to", "on", "off", "please", "turn", "open")
        val ON_WORDS = setOf("turn on", "enable", "activate", "open", "开启", "打开")
        val OFF_WORDS = setOf("turn off", "disable", "deactivate", "关闭")
        val SIZE_DIRECTIONS = setOf("larger", "smaller")
    }
}