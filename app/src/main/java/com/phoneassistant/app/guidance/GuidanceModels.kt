package com.phoneassistant.app.guidance

import com.phoneassistant.app.screen.ScreenSnapshot

data class GuidanceRequest(
    val userGoal: String,
    val screen: ScreenSnapshot,
    val previousStep: GuidanceDecision? = null,
    val taskId: String = "",
    val stepNumber: Int = 1,
    val completedSteps: List<String> = emptyList(),
)

enum class GuidanceStatus {
    CONTINUE,
    CLARIFY,
    COMPLETE,
    STOP,
}

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
}

data class GuidanceDecision(
    val status: GuidanceStatus,
    val targetElementId: String?,
    val instruction: String,
    val expectedResult: ExpectedResult? = null,
    val confidence: Double,
    val risk: RiskLevel,
)

data class ExpectedResult(
    val elementId: String,
    val property: String,
    val booleanValue: Boolean,
    val successMessage: String,
)

sealed interface DecisionValidation {
    data class Valid(val decision: GuidanceDecision) : DecisionValidation
    data class Invalid(val reason: String) : DecisionValidation
}