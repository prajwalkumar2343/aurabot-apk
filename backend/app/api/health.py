from fastapi import APIRouter
from app.core.config import settings

router = APIRouter(tags=["Health & System"])

@router.get("/")
async def root():
    return {"status": "ok", "service": "aura-assistant"}

@router.get("/health")
async def health():
    return {"status": "healthy", "model": settings.GEMINI_MODEL}
