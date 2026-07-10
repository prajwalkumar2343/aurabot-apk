from pydantic import BaseModel, Field
from typing import List, Optional

class ChatMemoryIn(BaseModel):
    title: str = Field(max_length=240)
    content: str = Field(max_length=8_000)

class ChatTodoIn(BaseModel):
    title: str = Field(max_length=500)
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
    message: str = Field(max_length=12_000)
    session_id: Optional[str] = Field(default=None, max_length=200)
    provider: str = Field(default="gemini", max_length=40)
    api_key: str = Field(max_length=1_024)
    model: str = Field(max_length=200)
    memories: List[ChatMemoryIn] = Field(default_factory=list, max_length=40)
    todos: List[ChatTodoIn] = Field(default_factory=list, max_length=100)
    apps: List[ChatAppIn] = Field(default_factory=list, max_length=500)
    mini_apps: List[ChatMiniAppIn] = Field(default_factory=list, max_length=100)
    automations: List[ChatAutomationIn] = Field(default_factory=list, max_length=100)
    context_files: List[str] = Field(default_factory=list)
    planning_mode: str = "auto"
    model_route: str = "off"
    max_repair_attempts: int = 1
    image_base64: Optional[str] = Field(default=None, max_length=8_000_000)
    image_mime_type: Optional[str] = Field(default=None, max_length=100)

class ChatOut(BaseModel):
    reply: str
    session_id: str
    actions: List[ChatActionOut] = Field(default_factory=list)
