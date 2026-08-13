from datetime import datetime
from typing import Literal, Optional

from pydantic import BaseModel, Field

from app.models.chat import AuraEmotionName, ChatActionOut, ChatIn


RunState = Literal[
    "queued",
    "running",
    "completed",
    "failed",
    "interrupted",
    "cancelled",
]
RunPhase = Literal[
    "admitted",
    "planning",
    "delegating",
    "synthesizing",
    "completed",
    "failed",
    "interrupted",
    "cancelled",
]


class AgentRunCreateIn(ChatIn):
    pass


class AgentRunAcceptedOut(BaseModel):
    run_id: str
    session_id: str
    state: RunState


class AgentChildRunOut(BaseModel):
    id: str
    agent: Literal["researcher", "planner", "reviewer"]
    state: RunState
    phase: RunPhase
    output: Optional[str] = None
    error: Optional[str] = None


class AgentRunOut(BaseModel):
    id: str
    session_id: str
    state: RunState
    phase: RunPhase
    reply: Optional[str] = None
    emotion: AuraEmotionName = "neutral"
    created_emotion: Optional[str] = Field(default=None, max_length=80)
    actions: list[ChatActionOut] = Field(default_factory=list)
    children: list[AgentChildRunOut] = Field(default_factory=list, max_length=3)
    error: Optional[str] = None
    created_at: datetime
    updated_at: datetime
