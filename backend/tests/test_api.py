from fastapi.testclient import TestClient
from fastapi import HTTPException
from pydantic import ValidationError
import pytest

from app.guidance import validate_decision
from app.main import app
from app.models import GuidanceDecision, GuidanceRequest

client = TestClient(app)

REQUEST = {
    "userGoal": "Turn on dark theme",
    "currentState": {
        "packageName": "com.android.settings",
        "screenTitle": "Display & touch",
    },
    "elements": [
        {
            "id": "e_dark_theme",
            "label": "Dark theme",
            "description": "",
            "role": "button",
            "clickable": True,
            "enabled": True,
            "checked": None,
        },
        {
            "id": "e_dark_theme_switch",
            "label": "Dark theme",
            "description": "",
            "role": "switch",
            "clickable": True,
            "enabled": True,
            "checked": False,
        },
    ],
}


@pytest.fixture(autouse=True)
def use_local_provider(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("GUIDANCE_PROVIDER", "local")


def test_health() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_local_provider_returns_valid_checked_result() -> None:
    response = client.post("/api/guidance/decide", json=REQUEST)

    assert response.status_code == 200
    decision = response.json()
    assert decision["status"] == "continue"
    assert decision["targetElementId"] == "e_dark_theme"
    assert decision["expectedResult"] == {
        "elementId": "e_dark_theme_switch",
        "property": "checked",
        "value": True,
        "successMessage": "Done. Dark theme is on.",
    }


def test_request_accepts_previous_step_context() -> None:
    request = REQUEST | {
        "previousStep": {
            "status": "continue",
            "targetElementId": "e_previous",
            "instruction": "Open Display size and text.",
            "confidence": 0.95,
            "risk": "low",
        }
    }

    parsed = GuidanceRequest.model_validate(request)

    assert parsed.previous_step is not None
    assert parsed.previous_step.target_element_id == "e_previous"


def test_local_provider_reports_already_complete() -> None:
    request = REQUEST | {
        "elements": [REQUEST["elements"][0], REQUEST["elements"][1] | {"checked": True}],
    }

    response = client.post("/api/guidance/decide", json=request)

    assert response.status_code == 200
    assert response.json()["status"] == "complete"
    assert response.json()["targetElementId"] is None


def test_validator_rejects_invented_target_id() -> None:
    request = GuidanceRequest.model_validate(REQUEST)
    decision = GuidanceDecision.model_validate(
        {
            "status": "continue",
            "targetElementId": "e_invented",
            "instruction": "Tap it.",
            "confidence": 0.99,
            "risk": "low",
        }
    )

    with pytest.raises(HTTPException) as error:
        validate_decision(request, decision)

    assert error.value.status_code == 422


def test_validator_accepts_clicked_completion_for_actionable_target() -> None:
    request = GuidanceRequest.model_validate(REQUEST)
    decision = GuidanceDecision.model_validate(
        {
            "status": "continue",
            "targetElementId": "e_dark_theme",
            "instruction": "Tap Dark theme.",
            "expectedResult": {
                "elementId": "e_dark_theme",
                "property": "clicked",
                "value": True,
                "successMessage": "Done.",
            },
            "confidence": 0.99,
            "risk": "low",
        }
    )

    assert validate_decision(request, decision) == decision


def test_validator_converts_low_confidence_decision_to_clarify() -> None:
    request = GuidanceRequest.model_validate(REQUEST)
    decision = GuidanceDecision.model_validate(
        {
            "status": "continue",
            "targetElementId": "e_dark_theme",
            "instruction": "Tap Dark theme.",
            "confidence": 0.5,
            "risk": "low",
        }
    )

    validated = validate_decision(request, decision)

    assert validated.status == "clarify"
    assert validated.target_element_id is None
    assert validated.confidence == 0.5


def test_validator_converts_targeted_complete_decision_to_continue() -> None:
    request = GuidanceRequest.model_validate(REQUEST)
    decision = GuidanceDecision.model_validate(
        {
            "status": "complete",
            "targetElementId": "e_dark_theme",
            "instruction": "Tap Dark theme.",
            "confidence": 0.95,
            "risk": "low",
        }
    )

    validated = validate_decision(request, decision)

    assert validated.status == "continue"
    assert validated.target_element_id == "e_dark_theme"


def test_decision_rejects_instruction_over_60_characters() -> None:
    with pytest.raises(ValidationError):
        GuidanceDecision.model_validate(
            {
                "status": "continue",
                "targetElementId": "e_dark_theme",
                "instruction": "x" * 61,
                "confidence": 0.99,
                "risk": "low",
            }
        )