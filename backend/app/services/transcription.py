import base64
import logging
import requests
from fastapi import HTTPException
from app.core.config import settings

logger = logging.getLogger(__name__)

def transcribe_audio(audio_base64: str, mime_type: str = "audio/m4a") -> str:
    try:
        base64_data = audio_base64
        if "," in base64_data:
            base64_data = base64_data.split(",", 1)[1]
        
        # Simple validation check for base64 correctness
        base64.b64decode(base64_data)
    except Exception as e:
        logger.warning(f"Invalid base64 provided: {e}")
        raise HTTPException(status_code=400, detail="Invalid base64 audio")

    if not settings.GEMINI_API_KEY:
        raise HTTPException(status_code=500, detail="Gemini API Key is not configured for transcription")

    try:
        response = requests.post(
            f"https://generativelanguage.googleapis.com/v1beta/models/{settings.GEMINI_MODEL}:generateContent",
            headers={
                "x-goog-api-key": settings.GEMINI_API_KEY,
                "Content-Type": "application/json",
            },
            json={
                "contents": [
                    {
                        "parts": [
                            {
                                "text": "Transcribe this audio. Return ONLY the transcription text, nothing else."
                            },
                            {
                                "inlineData": {
                                    "mimeType": mime_type,
                                    "data": base64_data,
                                }
                            }
                        ]
                    }
                ],
                "systemInstruction": {
                    "parts": [
                        {
                            "text": "You are a strict audio transcriber. Return only the spoken words in plain text, no punctuation explanations, no preamble."
                        }
                    ]
                }
            },
            timeout=60,
        )
    except requests.exceptions.RequestException as e:
        logger.exception("Gemini transcription API call failed")
        raise HTTPException(status_code=502, detail=f"Failed to connect to Gemini transcription API: {str(e)}")

    if response.status_code >= 400:
        raise HTTPException(
            status_code=response.status_code,
            detail=f"Gemini transcription API error: {response.text[:300]}",
        )
    
    payload = response.json()
    candidates = payload.get("candidates") or []
    parts = candidates[0].get("content", {}).get("parts", []) if candidates else []
    text = "".join(part.get("text", "") for part in parts).strip()
    if not text:
        raise HTTPException(status_code=502, detail="Gemini transcription response did not include text")
    
    return text
