import json
import os
import re
from typing import Protocol

import httpx
from fastapi import HTTPException, status
from dotenv import load_dotenv

from app.models import (
    ExpectedResult,
    GuidanceDecision,
    GuidanceRequest,
    GuidanceStatus,
    MAX_INSTRUCTION_LENGTH,
    RiskLevel,
    ScreenElement,
)

load_dotenv()


class GuidanceProvider(Protocol):
    async def decide(self, request: GuidanceRequest) -> GuidanceDecision: ...


class LocalGuidanceProvider:
    async def decide(self, request: GuidanceRequest) -> GuidanceDecision:
        goal = _normalize(request.user_goal)
        desired_state = _desired_checked_state(goal)
        ranked = sorted(
            (
                (_score(element, goal), element)
                for element in request.elements
                if element.enabled and element.clickable
            ),
            key=lambda item: item[0],
            reverse=True,
        )
        if not ranked or ranked[0][0] <= 0:
            return GuidanceDecision(
                status=GuidanceStatus.CLARIFY,
                instruction="I cannot find a visible control for that goal.",
                confidence=0,
                risk=RiskLevel.LOW,
            )

        score, target = ranked[0]
        state_element = next(
            (
                element
                for element in request.elements
                if element.checked is not None
                and _normalize(element.label) == _normalize(target.label)
            ),
            None,
        )
        state_word = "on" if desired_state else "off"
        if desired_state is not None and state_element and state_element.checked == desired_state:
            return GuidanceDecision(
                status=GuidanceStatus.COMPLETE,
                instruction=f"Done. {target.label} is {state_word}.",
                confidence=min(score, 1),
                risk=RiskLevel.LOW,
            )

        expected_result = None
        if desired_state is not None and state_element:
            expected_result = ExpectedResult(
                elementId=state_element.id,
                property="checked",
                value=desired_state,
                successMessage=f"Done. {target.label} is {state_word}.",
            )
        return GuidanceDecision(
            status=GuidanceStatus.CONTINUE,
            targetElementId=target.id,
            instruction=f"Tap {target.label or target.description}.",
            expectedResult=expected_result,
            confidence=min(score, 1),
            risk=RiskLevel.LOW,
        )


class GeminiGuidanceProvider:
    def __init__(self, api_key: str, model: str) -> None:
        self._api_key = api_key
        self._model = model

    async def decide(self, request: GuidanceRequest) -> GuidanceDecision:
        url = (
            "https://generativelanguage.googleapis.com/v1beta/models/"
            f"{self._model}:generateContent"
        )
        payload = {
            "contents": [{"parts": [{"text": _build_prompt(request)}]}],
            "generationConfig": {
                "responseMimeType": "application/json",
                "temperature": 0.1,
            },
        }
        try:
            async with httpx.AsyncClient(timeout=15) as client:
                response = await client.post(url, params={"key": self._api_key}, json=payload)
                response.raise_for_status()
            text = response.json()["candidates"][0]["content"]["parts"][0]["text"]
            return GuidanceDecision.model_validate_json(text)
        except (httpx.HTTPError, KeyError, IndexError, ValueError) as error:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail="The AI provider returned an invalid response.",
            ) from error


def get_provider() -> GuidanceProvider:
    provider_name = os.getenv("GUIDANCE_PROVIDER", "local").lower()
    if provider_name == "local":
        return LocalGuidanceProvider()
    if provider_name == "gemini":
        api_key = os.getenv("GEMINI_API_KEY")
        if not api_key:
            raise RuntimeError("GEMINI_API_KEY is required when GUIDANCE_PROVIDER=gemini")
        return GeminiGuidanceProvider(
            api_key=api_key,
            model=os.getenv("GEMINI_MODEL", "gemini-3.1-flash-lite"),
        )
    raise RuntimeError(f"Unsupported GUIDANCE_PROVIDER: {provider_name}")


def validate_decision(
    request: GuidanceRequest,
    decision: GuidanceDecision,
) -> GuidanceDecision:
    elements_by_id = {element.id: element for element in request.elements}
    if decision.status != GuidanceStatus.CONTINUE:
        if decision.target_element_id is not None:
            target = elements_by_id.get(decision.target_element_id)
            if target is None or not target.enabled or not target.clickable:
                _reject("Target element is missing or not actionable")
            decision = decision.model_copy(update={"status": GuidanceStatus.CONTINUE})
        else:
            return decision
    if decision.risk == RiskLevel.HIGH:
        _reject("High-risk decisions cannot continue")
    target = elements_by_id.get(decision.target_element_id or "")
    if target is None or not target.enabled or not target.clickable:
        _reject("Target element is missing or not actionable")
    if decision.confidence < 0.7:
        return GuidanceDecision(
            status=GuidanceStatus.CLARIFY,
            instruction="I cannot identify the next control safely. Tap Retry.",
            confidence=decision.confidence,
            risk=RiskLevel.LOW,
        )
    if decision.expected_result:
        expected_element = elements_by_id.get(decision.expected_result.element_id)
        if expected_element is None:
            _reject("Expected result must reference a visible element")
        if decision.expected_result.property == "checked" and expected_element.checked is None:
            _reject("Checked result must reference a checkable element")
        if decision.expected_result.property == "clicked" and (
            not expected_element.clickable or not expected_element.enabled
        ):
            _reject("Clicked result must reference an actionable element")
        if decision.expected_result.property not in {"checked", "clicked"}:
            _reject("Unsupported expected-result property")
    return decision


def _reject(detail: str) -> None:
    raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_CONTENT, detail=detail)


def _build_prompt(request: GuidanceRequest) -> str:
    request_json = request.model_dump_json(by_alias=True, exclude_none=True)
    return f"""You guide an older adult through one visible Android screen.
Keep instruction to one explicit action sentence of at most {MAX_INSTRUCTION_LENGTH} characters,
including spaces. State the exact gesture (tap, swipe, or type) and the visible control label so the
user can act without guessing. Do not explain why, add background, or combine later steps.
Return exactly one JSON object and no markdown. Never invent element IDs. Never return coordinates.
Choose only an enabled, clickable element from the supplied list. For risky or irreversible actions,
return status \"stop\". If the goal is already satisfied, return status \"complete\".
For a checkable control, expectedResult must use property \"checked\" and the desired boolean value.
For a final one-shot button that fully satisfies the original goal, expectedResult may use property
\"clicked\" and value true. Omit expectedResult for navigation actions because reaching an intermediate
screen does not complete the user's goal. Use previousStep, stepNumber, and completedSteps to continue
the task without repeating earlier actions. Entries in completedSteps are actions the user has actually
clicked. If those actions satisfy the original goal, return status \"complete\" instead of repeating
the last action. Never exceed eight steps.
Schema:
{json.dumps(GuidanceDecision.model_json_schema(by_alias=True))}
Request:
{request_json}
"""


def _normalize(value: str) -> str:
    return " ".join(re.sub(r"[^\w]+", " ", value.lower()).split())


def _desired_checked_state(goal: str) -> bool | None:
    if any(word in goal for word in ("turn off", "disable", "关闭")):
        return False
    if any(word in goal for word in ("turn on", "enable", "开启", "打开")):
        return True
    return None


def _score(element: ScreenElement, goal: str) -> float:
    label = _normalize(element.label)
    description = _normalize(element.description)
    if label and label in goal:
        return 1
    if description and description in goal:
        return 0.95
    goal_tokens = set(goal.split()) - {"a", "an", "the", "to", "on", "off", "turn"}
    element_tokens = set(f"{label} {description}".split())
    return len(goal_tokens & element_tokens) / len(element_tokens) if element_tokens else 0