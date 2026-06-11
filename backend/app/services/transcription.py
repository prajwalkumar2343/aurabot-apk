from typing import Optional
import base64
import logging
import requests
from fastapi import HTTPException
from app.core.config import settings
from app.services.prompt_harness import normalize_model_id

logger = logging.getLogger(__name__)

MAX_AUDIO_BYTES = 10 * 1024 * 1024
MIN_AUDIO_BYTES = 128
MIN_WAV_DATA_BYTES = 9_600
SUPPORTED_AUDIO_MIME_TYPES = {
    "audio/wav",
    "audio/wave",
    "audio/x-wav",
    "audio/m4a",
    "audio/mp4",
    "audio/mpeg",
    "audio/mp3",
    "audio/webm",
    "audio/ogg",
}


def _decode_audio_base64(audio_base64: str) -> tuple[str, bytes]:
    base64_data = audio_base64.strip()
    if "," in base64_data:
        base64_data = base64_data.split(",", 1)[1]
    if not base64_data:
        raise ValueError("empty audio payload")
    decoded = base64.b64decode(base64_data, validate=True)
    return base64_data, decoded


def _little_endian_u32(data: bytes, offset: int) -> int:
    return int.from_bytes(data[offset:offset + 4], byteorder="little", signed=False)


def _wav_data_size(data: bytes) -> Optional[int]:
    if len(data) < 44 or data[0:4] != b"RIFF" or data[8:12] != b"WAVE":
        return None
    offset = 12
    while offset + 8 <= len(data):
        chunk_id = data[offset:offset + 4]
        chunk_size = _little_endian_u32(data, offset + 4)
        chunk_data_start = offset + 8
        if chunk_id == b"data":
            return min(chunk_size, max(0, len(data) - chunk_data_start))
        offset = chunk_data_start + chunk_size + (chunk_size % 2)
    return 0


def _validate_audio_payload(mime_type: str, decoded: bytes) -> None:
    normalized_mime_type = mime_type.split(";", 1)[0].strip().lower()
    if normalized_mime_type not in SUPPORTED_AUDIO_MIME_TYPES:
        raise HTTPException(status_code=400, detail="Unsupported audio mime type")
    if len(decoded) > MAX_AUDIO_BYTES:
        raise HTTPException(status_code=413, detail="Audio payload is too large")
    if len(decoded) < MIN_AUDIO_BYTES:
        raise HTTPException(status_code=400, detail="Audio payload is too short")

    wav_data_size = _wav_data_size(decoded)
    if wav_data_size is not None and wav_data_size < MIN_WAV_DATA_BYTES:
        raise HTTPException(status_code=400, detail="Audio payload is too short")


def transcribe_audio(
    audio_base64: str,
    mime_type: str = "audio/m4a",
    api_key: Optional[str] = None,
    provider: Optional[str] = None
) -> str:
    try:
        base64_data, decoded = _decode_audio_base64(audio_base64)
        _validate_audio_payload(mime_type, decoded)
    except Exception as e:
        if isinstance(e, HTTPException):
            raise
        logger.warning(f"Invalid base64 provided: {e}")
        raise HTTPException(status_code=400, detail="Invalid base64 audio")

    normalized_provider = (provider or "gemini").strip().lower()
    if normalized_provider == "gemini":
        return _transcribe_with_gemini(base64_data, mime_type, api_key)
    if normalized_provider == "openai":
        return _transcribe_with_openai(decoded, mime_type, api_key)
    raise HTTPException(status_code=400, detail="Transcription currently supports gemini or openai providers")


def _transcribe_with_gemini(base64_data: str, mime_type: str, api_key: Optional[str]) -> str:
    key = api_key or settings.GEMINI_API_KEY
    if not key:
        raise HTTPException(status_code=500, detail="Gemini API Key is not configured for transcription")

    model = normalize_model_id("gemini", settings.GEMINI_MODEL)
    try:
        response = requests.post(
            f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
            headers={
                "x-goog-api-key": key,
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


def _transcribe_with_openai(decoded: bytes, mime_type: str, api_key: Optional[str]) -> str:
    if not api_key:
        raise HTTPException(status_code=400, detail="OpenAI API Key is required for transcription")
    extension = {
        "audio/wav": "wav",
        "audio/wave": "wav",
        "audio/x-wav": "wav",
        "audio/m4a": "m4a",
        "audio/mp4": "m4a",
        "audio/mpeg": "mp3",
        "audio/mp3": "mp3",
        "audio/webm": "webm",
        "audio/ogg": "ogg",
    }.get(mime_type.split(";", 1)[0].strip().lower(), "audio")
    try:
        response = requests.post(
            "https://api.openai.com/v1/audio/transcriptions",
            headers={"Authorization": f"Bearer {api_key}"},
            data={
                "model": settings.OPENAI_TRANSCRIPTION_MODEL,
                "response_format": "json",
            },
            files={
                "file": (f"aura-audio.{extension}", decoded, mime_type),
            },
            timeout=60,
        )
    except requests.exceptions.RequestException as e:
        logger.exception("OpenAI transcription API call failed")
        raise HTTPException(status_code=502, detail=f"Failed to connect to OpenAI transcription API: {str(e)}")

    if response.status_code >= 400:
        raise HTTPException(
            status_code=response.status_code,
            detail=f"OpenAI transcription API error: {response.text[:300]}",
        )

    payload = response.json()
    text = str(payload.get("text") or "").strip()
    if not text:
        raise HTTPException(status_code=502, detail="OpenAI transcription response did not include text")
    return text
