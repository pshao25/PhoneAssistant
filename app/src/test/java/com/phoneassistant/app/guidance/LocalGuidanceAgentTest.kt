package com.phoneassistant.app.guidance

import com.phoneassistant.app.screen.ElementBounds
import com.phoneassistant.app.screen.ElementRole
import com.phoneassistant.app.screen.ScreenElement
import com.phoneassistant.app.screen.ScreenSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalGuidanceAgentTest {
    private val darkThemeElement = ScreenElement(
        id = "e_dark_theme",
        label = "Dark theme",
        description = "",
        role = ElementRole.BUTTON,
        clickable = true,
        enabled = true,
        checked = null,
        bounds = ElementBounds(0, 100, 720, 220),
    )
    private val darkThemeSwitch = ScreenElement(
        id = "e_dark_theme_switch",
        label = "Dark theme",
        description = "",
        role = ElementRole.SWITCH,
        clickable = true,
        enabled = true,
        checked = false,
        bounds = ElementBounds(620, 120, 700, 200),
    )
    private val request = GuidanceRequest(
        userGoal = "Turn on dark theme",
        screen = ScreenSnapshot(
            packageName = "com.android.settings",
            screenTitle = "Display & touch",
            capturedAt = 1L,
            elements = listOf(darkThemeElement, darkThemeSwitch),
        ),
    )

    @Test
    fun selectsElementIdThatMatchesGoal() {
        val decision = LocalGuidanceAgent().decide(request)

        assertEquals(GuidanceStatus.CONTINUE, decision.status)
        assertEquals("e_dark_theme", decision.targetElementId)
        assertEquals(1.0, decision.confidence, 0.0)
        assertEquals("e_dark_theme_switch", decision.expectedResult?.elementId)
        assertEquals(true, decision.expectedResult?.booleanValue)
    }

    @Test
    fun returnsCompleteWhenDesiredSwitchStateIsAlreadyReached() {
        val completedRequest = request.copy(
            screen = request.screen.copy(
                elements = listOf(darkThemeElement, darkThemeSwitch.copy(checked = true)),
            ),
        )

        val decision = LocalGuidanceAgent().decide(completedRequest)

        assertEquals(GuidanceStatus.COMPLETE, decision.status)
        assertEquals(null, decision.targetElementId)
        assertEquals(null, decision.expectedResult)
    }

    @Test
    fun asksForClarificationWhenNoElementMatches() {
        val decision = LocalGuidanceAgent().decide(
            request.copy(userGoal = "Connect to a printer"),
        )

        assertEquals(GuidanceStatus.CLARIFY, decision.status)
        assertEquals(null, decision.targetElementId)
    }

    @Test
    fun selectsFirstMakeLargerControlForTextSizeGoal() {
        val makeTextLarger = darkThemeElement.copy(
            id = "e_font_larger",
            label = "Make larger",
            bounds = ElementBounds(608, 858, 692, 942),
        )
        val makeDisplayLarger = makeTextLarger.copy(
            id = "e_display_larger",
            bounds = ElementBounds(608, 1065, 692, 1149),
        )
        val displayRequest = request.copy(
            userGoal = "make text bigger",
            screen = request.screen.copy(
                screenTitle = "Display size and text",
                elements = listOf(makeTextLarger, makeDisplayLarger),
            ),
        )

        val decision = LocalGuidanceAgent().decide(displayRequest)

        assertEquals(GuidanceStatus.CONTINUE, decision.status)
        assertEquals("e_font_larger", decision.targetElementId)
        assertTrue(decision.confidence >= 0.7)
        assertTrue(GuidanceDecisionValidator().validate(displayRequest, decision) is DecisionValidation.Valid)
    }

    @Test
    fun validatorAcceptsKnownActionableTarget() {
        val decision = LocalGuidanceAgent().decide(request)

        val validation = GuidanceDecisionValidator().validate(request, decision)

        assertTrue(validation is DecisionValidation.Valid)
    }

    @Test
    fun validatorRejectsUnknownTarget() {
        val decision = GuidanceDecision(
            status = GuidanceStatus.CONTINUE,
            targetElementId = "e_invented",
            instruction = "Tap it.",
            confidence = 0.99,
            risk = RiskLevel.LOW,
        )

        val validation = GuidanceDecisionValidator().validate(request, decision)

        assertTrue(validation is DecisionValidation.Invalid)
    }

    @Test
    fun validatorRejectsLowConfidenceDecision() {
        val decision = GuidanceDecision(
            status = GuidanceStatus.CONTINUE,
            targetElementId = darkThemeElement.id,
            instruction = "Tap Dark theme.",
            confidence = 0.4,
            risk = RiskLevel.LOW,
        )

        val validation = GuidanceDecisionValidator().validate(request, decision)

        assertTrue(validation is DecisionValidation.Invalid)
    }

    @Test
    fun validatorRejectsInstructionOver60Characters() {
        val decision = GuidanceDecision(
            status = GuidanceStatus.CONTINUE,
            targetElementId = darkThemeElement.id,
            instruction = "x".repeat(61),
            confidence = 0.99,
            risk = RiskLevel.LOW,
        )

        val validation = GuidanceDecisionValidator().validate(request, decision)

        assertTrue(validation is DecisionValidation.Invalid)
    }
}