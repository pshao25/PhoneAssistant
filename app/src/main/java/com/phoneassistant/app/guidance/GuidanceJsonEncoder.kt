package com.phoneassistant.app.guidance

import org.json.JSONArray
import org.json.JSONObject

object GuidanceJsonEncoder {
    fun encodeRequest(request: GuidanceRequest): String = JSONObject()
        .put("userGoal", request.userGoal)
        .put("taskId", request.taskId)
        .put("stepNumber", request.stepNumber)
        .put("completedSteps", JSONArray(request.completedSteps))
        .put(
            "currentState",
            JSONObject()
                .put("packageName", request.screen.packageName)
                .put("screenTitle", request.screen.screenTitle),
        )
        .put(
            "elements",
            JSONArray().apply {
                request.screen.elements.forEach { element ->
                    put(
                        JSONObject()
                            .put("id", element.id)
                            .put("label", element.label)
                            .put("description", element.description)
                            .put("role", element.role.name.lowercase())
                            .put("clickable", element.clickable)
                            .put("enabled", element.enabled)
                            .put("checked", element.checked),
                    )
                }
            },
        )
        .put(
            "previousStep",
            request.previousStep?.let(::decisionToJson),
        )
        .toString()

    fun encodeDecision(decision: GuidanceDecision): String = decisionToJson(decision).toString()

    private fun decisionToJson(decision: GuidanceDecision): JSONObject = JSONObject()
        .put("status", decision.status.name.lowercase())
        .put("targetElementId", decision.targetElementId)
        .put("instruction", decision.instruction)
        .put(
            "expectedResult",
            decision.expectedResult?.let { expected ->
                JSONObject()
                    .put("elementId", expected.elementId)
                    .put("property", expected.property)
                    .put("value", expected.booleanValue)
                    .put("successMessage", expected.successMessage)
            },
        )
        .put("confidence", decision.confidence)
        .put("risk", decision.risk.name.lowercase())
}