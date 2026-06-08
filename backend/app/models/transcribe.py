from typing import Optional
from pydantic import BaseModel

class TranscribeIn(BaseModel):
    audio_base64: str
    mime_type: str = "audio/m4a"
    api_key: Optional[str] = None
    provider: Optional[str] = None

class TranscribeOut(BaseModel):
    text: str
