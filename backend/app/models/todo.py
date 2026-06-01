from pydantic import BaseModel
from typing import Optional

class TodoCreate(BaseModel):
    title: str

class TodoUpdate(BaseModel):
    title: Optional[str] = None
    done: Optional[bool] = None

class TodoOut(BaseModel):
    id: str
    title: str
    done: bool
    created_at: str
