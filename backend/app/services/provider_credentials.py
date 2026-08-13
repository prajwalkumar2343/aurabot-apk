from fastapi import HTTPException
from pydantic import BaseModel

from app.core.config import settings


def resolve_provider_credentials(data: BaseModel, user: dict) -> BaseModel:
    """Add an Aura-owned key only for accounts explicitly provisioned as managed."""
    api_key = str(getattr(data, "api_key", "") or "").strip()
    if user.get("service_mode") != "managed":
        if api_key:
            return data
        raise HTTPException(status_code=400, detail="Provider API key is required")

    if not settings.MANAGED_GEMINI_API_KEY:
        raise HTTPException(
            status_code=503, detail="Managed assistant is not configured"
        )

    updates = {"provider": "gemini", "api_key": settings.MANAGED_GEMINI_API_KEY}
    if hasattr(data, "model"):
        updates["model"] = settings.GEMINI_MODEL
    return data.model_copy(update=updates)
