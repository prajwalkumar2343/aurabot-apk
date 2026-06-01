from pydantic import BaseModel, Field
from typing import List, Optional

class ChatMemoryIn(BaseModel):
    title: str
    content: str

class ChatTodoIn(BaseModel):
    title: str
    done: bool

class ChatAppIn(BaseModel):
    label: str
    package_name: str

class ChatActionOut(BaseModel):
    type: str
    package_name: Optional[str] = None
    app_query: Optional[str] = None
    duration_minutes: Optional[int] = None

class ChatIn(BaseModel):
    message: str
    session_id: Optional[str] = None
    provider: str = "gemini"
    api_key: str
    model: str
    memories: List[ChatMemoryIn] = Field(default_factory=list)
    todos: List[ChatTodoIn] = Field(default_factory=list)
    apps: List[ChatAppIn] = Field(default_factory=list)

class ChatOut(BaseModel):
    reply: str
    session_id: str
    actions: List[ChatActionOut] = Field(default_factory=list)
