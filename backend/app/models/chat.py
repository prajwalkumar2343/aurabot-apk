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

class ChatMiniAppIn(BaseModel):
    id: str
    name: str
    intents: List[str] = Field(default_factory=list)
    actions: List[str] = Field(default_factory=list)

class ChatAutomationIn(BaseModel):
    id: str
    name: str
    enabled: bool = True
    trigger_type: str
    action_types: List[str] = Field(default_factory=list)

class ChatActionOut(BaseModel):
    type: str
    package_name: Optional[str] = None
    app_query: Optional[str] = None
    duration_minutes: Optional[int] = None
    mini_app_id: Optional[str] = None
    mini_app_query: Optional[str] = None
    mini_app_prompt: Optional[str] = None
    revision_instruction: Optional[str] = None
    open_after_create: Optional[bool] = None
    action_id: Optional[str] = None
    record_type: Optional[str] = None
    values: Optional[dict[str, str]] = None
    automation_spec: Optional[dict] = None

class ChatIn(BaseModel):
    message: str
    session_id: Optional[str] = None
    provider: str = "gemini"
    api_key: str
    model: str
    memories: List[ChatMemoryIn] = Field(default_factory=list)
    todos: List[ChatTodoIn] = Field(default_factory=list)
    apps: List[ChatAppIn] = Field(default_factory=list)
    mini_apps: List[ChatMiniAppIn] = Field(default_factory=list)
    automations: List[ChatAutomationIn] = Field(default_factory=list)
    context_files: List[str] = Field(default_factory=list)
    planning_mode: str = "auto"
    model_route: str = "off"
    max_repair_attempts: int = 1
    image_base64: Optional[str] = None
    image_mime_type: Optional[str] = None

class ChatOut(BaseModel):
    reply: str
    session_id: str
    actions: List[ChatActionOut] = Field(default_factory=list)
