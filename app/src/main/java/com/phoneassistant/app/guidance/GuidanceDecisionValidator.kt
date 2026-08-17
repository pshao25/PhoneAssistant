package com.phoneassistant.app.guidance

class GuidanceDecisionValidator(
    private val minimumConfidence: Double = 0.7,
) {
    fun validate(
        request: GuidanceRequest,
        decision: GuidanceDecision,
    ): DecisionValidation {
        if (decision.instruction.length > MAX_INSTRUCTION_LENGTH) {
            return DecisionValidation.Invalid("Instruction exceeds $MAX_INSTRUCTION_LENGTH characters")
        }
        if (decision.confidence !in 0.0..1.0) {
            return DecisionValidation.Invalid("Confidence must be between 0 and 1")
        }
        if (decision.risk == RiskLevel.HIGH && decision.status == GuidanceStatus.CONTINUE) {
            return DecisionValidation.Invalid("High-risk decisions cannot continue")
        }
        if (decision.status != GuidanceStatus.CONTINUE) {
            return if (decision.targetElementId == null) {
                DecisionValidation.Valid(decision)
            } else {
                DecisionValidation.Invalid("Only continue decisions may target an element")
            }
        }

        val targetId = decision.targetElementId
            ?: return DecisionValidation.Invalid("Continue decision requires a target element")
        val element = request.screen.elements.find { it.id == targetId }
            ?: return DecisionValidation.Invalid("Target element does not exist in this snapshot")
        if (!element.enabled || !element.clickable) {
            return DecisionValidation.Invalid("Target element is not actionable")
        }
        if (decision.confidence < minimumConfidence) {
            return DecisionValidation.Invalid("Decision confidence is below threshold")
        }
        val expectedResult = decision.expectedResult
        if (expectedResult != null) {
            val expectedElement = request.screen.elements.find { it.id == expectedResult.elementId }
                ?: return DecisionValidation.Invalid("Expected-result element does not exist")
            when (expectedResult.property) {
                "checked" -> if (expectedElement.checked == null) {
                    return DecisionValidation.Invalid("Expected result does not target a checkable element")
                }
                "clicked" -> if (!expectedElement.clickable || !expectedElement.enabled) {
                    return DecisionValidation.Invalid("Expected result does not target an actionable element")
                }
                else -> return DecisionValidation.Invalid("Unsupported expected-result property")
            }
        }
        return DecisionValidation.Valid(decision)
    }

    private companion object {
        const val MAX_INSTRUCTION_LENGTH = 60
    }
}