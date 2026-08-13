from typing import Optional
from pydantic import BaseModel, Field

class TranscribeIn(BaseModel):
    audio_base64: str = Field(max_length=22_500_000)
    mime_type: str = Field(default="audio/m4a", max_length=100)
    api_key: Optional[str] = Field(default=None, max_length=1_024)
    provider: Optional[str] = Field(default=None, max_length=40)

class TranscribeOut(BaseModel):
    text: str
