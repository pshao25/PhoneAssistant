from fastapi import Depends, FastAPI

from app.guidance import GuidanceProvider, get_provider, validate_decision
from app.models import GuidanceDecision, GuidanceRequest

app = FastAPI(title="PhoneAssist Guidance API", version="0.1.0")


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/guidance/decide", response_model=GuidanceDecision)
async def decide(
    request: GuidanceRequest,
    provider: GuidanceProvider = Depends(get_provider),
) -> GuidanceDecision:
    decision = await provider.decide(request)
    return validate_decision(request, decision)