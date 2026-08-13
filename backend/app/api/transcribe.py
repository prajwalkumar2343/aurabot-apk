import asyncio
import logging
from fastapi import APIRouter, Depends, HTTPException
from app.core.security import get_current_user
from app.models.transcribe import TranscribeIn, TranscribeOut
from app.services.transcription import transcribe_audio
from app.services.provider_credentials import resolve_provider_credentials

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/transcribe", tags=["Transcription"])


@router.post("", response_model=TranscribeOut)
async def transcribe(data: TranscribeIn, user=Depends(get_current_user)):
    # Preserve the legacy server-configured transcription fallback for existing
    # password accounts. Managed accounts are the only callers that receive the
    # new Aura-owned credential injection policy.
    if user.get("service_mode") == "managed" or data.api_key:
        data = resolve_provider_credentials(data, user)
    try:
        text = await asyncio.to_thread(
            transcribe_audio,
            data.audio_base64,
            data.mime_type,
            data.api_key,
            data.provider,
        )
        return TranscribeOut(text=text)
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("transcribe failed")
        raise HTTPException(
            status_code=500, detail=f"Transcription error: {str(e)[:200]}"
        )
