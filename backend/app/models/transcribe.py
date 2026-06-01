from pydantic import BaseModel

class TranscribeIn(BaseModel):
    audio_base64: str
    mime_type: str = "audio/m4a"

class TranscribeOut(BaseModel):
    text: str
