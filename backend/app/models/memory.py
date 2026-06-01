from pydantic import BaseModel

class MemoryCreate(BaseModel):
    title: str
    content: str

class MemoryOut(BaseModel):
    id: str
    title: str
    content: str
    created_at: str
