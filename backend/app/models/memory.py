from pydantic import BaseModel

class MemoryCreate(BaseModel):
    title: str
    content: str

class MemorySearchIn(BaseModel):
    query: str
    limit: int = 8

class MemoryOut(BaseModel):
    id: str
    title: str
    content: str
    created_at: str

class MemorySearchOut(BaseModel):
    memory_id: str
    title: str
    chunk_text: str
    score: float
    source_type: str
