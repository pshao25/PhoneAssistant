from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field

MAX_INSTRUCTION_LENGTH = 60


class ApiModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True)


class ElementRole(StrEnum):
    BUTTON = "button"
    CHECKBOX = "checkbox"
    INPUT = "input"
    SWITCH = "switch"
    TEXT = "text"
    UNKNOWN = "unknown"


class ScreenElement(ApiModel):
    id: str = Field(min_length=1)
    label: str
    description: str
    role: ElementRole
    clickable: bool
    enabled: bool
    checked: bool | None = None


class CurrentState(ApiModel):
    package_name: str = Field(alias="packageName")
    screen_title: str | None = Field(default=None, alias="screenTitle")


class GuidanceStatus(StrEnum):
    CONTINUE = "continue"
    CLARIFY = "clarify"
    COMPLETE = "complete"
    STOP = "stop"


class RiskLevel(StrEnum):
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"


class ExpectedResult(ApiModel):
    element_id: str = Field(alias="elementId", min_length=1)
    property: str
    value: bool
    success_message: str = Field(alias="successMessage", min_length=1)


class GuidanceDecision(ApiModel):
    status: GuidanceStatus
    target_element_id: str | None = Field(default=None, alias="targetElementId")
    instruction: str = Field(min_length=1, max_length=MAX_INSTRUCTION_LENGTH)
    expected_result: ExpectedResult | None = Field(default=None, alias="expectedResult")
    confidence: float = Field(ge=0, le=1)
    risk: RiskLevel


class GuidanceRequest(ApiModel):
    user_goal: str = Field(alias="userGoal", min_length=1, max_length=500)
    current_state: CurrentState = Field(alias="currentState")
    elements: list[ScreenElement] = Field(max_length=300)
    previous_step: GuidanceDecision | None = Field(default=None, alias="previousStep")
    task_id: str = Field(default="", alias="taskId", max_length=100)
    step_number: int = Field(default=1, alias="stepNumber", ge=1, le=8)
    completed_steps: list[str] = Field(default_factory=list, alias="completedSteps", max_length=8)