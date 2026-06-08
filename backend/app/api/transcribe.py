import asyncio
import logging
from fastapi import APIRouter, Depends, HTTPException
from app.core.security import get_optional_current_user
from app.models.transcribe import TranscribeIn, TranscribeOut
from app.services.transcription import transcribe_audio

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/transcribe", tags=["Transcription"])

@router.post("", response_model=TranscribeOut)
async def transcribe(data: TranscribeIn, user=Depends(get_optional_current_user)):
    try:
        text = await asyncio.to_thread(
            transcribe_audio,
            data.audio_base64,
            data.mime_type,
            data.api_key,
            data.provider
        )
        return TranscribeOut(text=text)
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("transcribe failed")
        raise HTTPException(status_code=500, detail=f"Transcription error: {str(e)[:200]}")
