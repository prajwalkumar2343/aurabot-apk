import hashlib
from typing import Annotated, List, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator


WidgetActionId = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1, max_length=64, pattern=r"^[a-z0-9_-]+$"),
]
WidgetActionLabel = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=40)]
WidgetDetail = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=120)]
WidgetPayloadKey = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1, max_length=64, pattern=r"^[a-zA-Z0-9_.-]+$"),
]
WidgetPayloadValue = Annotated[str, StringConstraints(strip_whitespace=True, max_length=500)]

# This is a protocol value shared by the provider harness and the Android face.
# Keep the list finite so malformed model output can fail closed to neutral.
AuraEmotionName = Literal[
    "neutral",
    "happy",
    "joyful",
    "excited",
    "playful",
    "cute",
    "adoring",
    "affectionate",
    "grateful",
    "proud",
    "relieved",
    "hopeful",
    "encouraging",
    "curious",
    "interested",
    "focused",
    "thinking",
    "confused",
    "surprised",
    "amazed",
    "sleepy",
    "calm",
    "empathetic",
    "sad",
    "lonely",
    "worried",
    "concerned",
    "afraid",
    "embarrassed",
    "shy",
    "bashful",
    "doubtful",
    "skeptical",
    "annoyed",
    "frustrated",
    "determined",
    "mischievous",
    "smug",
    "angry",
    "furious",
    "enraged",
]
AURA_EMOTION_NAMES: tuple[str, ...] = AuraEmotionName.__args__
DEFAULT_AURA_EMOTION = "neutral"

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

class ChatWidgetActionOut(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: WidgetActionId
    label: WidgetActionLabel
    type: str = Field(pattern="^(assistant_message|open_app|dismiss)$")
    payload: dict[WidgetPayloadKey, WidgetPayloadValue] = Field(default_factory=dict, max_length=8)
    requires_confirmation: bool = False

    @model_validator(mode="after")
    def validate_action_payload(self):
        if self.type == "assistant_message" and not self.payload.get("message", "").strip():
            raise ValueError("assistant_message requires a non-empty message payload")
        if self.type == "open_app" and not (
            self.payload.get("package_name", "").strip() or self.payload.get("app_query", "").strip()
        ):
            raise ValueError("open_app requires package_name or app_query")
        if self.type == "dismiss" and self.payload:
            raise ValueError("dismiss does not accept a payload")
        return self


class ChatWidgetProposalOut(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    kind: str = Field(pattern="^(message|confirmation|itinerary|food_order|reminder|progress|report|meeting_notes)$")
    title: str = Field(min_length=1, max_length=80)
    message: str = Field(min_length=1, max_length=280)
    details: List[WidgetDetail] = Field(default_factory=list, max_length=6)
    actions: List[ChatWidgetActionOut] = Field(default_factory=list, max_length=2)
    presentation: str = Field(default="compact", pattern="^(compact|expanded|fullscreen)$")
    content_format: str = Field(default="plain_text", pattern="^(plain_text|html)$")
    content: Optional[str] = Field(default=None, max_length=60_000)
    risk: str = Field(default="low", pattern="^(low|medium|high)$")
    priority: int = Field(default=0, ge=0, le=100)
    expires_in_minutes: int = Field(default=60, ge=1, le=10_080)
    dedupe_key: str = Field(default="", max_length=120)

    @model_validator(mode="after")
    def enforce_confirmation_policy(self):
        action_ids = [action.id for action in self.actions]
        if len(action_ids) != len(set(action_ids)):
            raise ValueError("widget action ids must be unique")
        if self.kind == "confirmation" and not self.actions:
            raise ValueError("confirmation widgets require at least one action")
        normalized_content = self.content.strip() if self.content else None
        if self.presentation == "fullscreen" and not normalized_content:
            raise ValueError("fullscreen widgets require content")
        if self.content_format == "html" and not (
            self.kind == "report" and self.presentation == "fullscreen"
        ):
            raise ValueError("html is only supported by fullscreen report widgets")
        if self.kind == "meeting_notes" and self.presentation == "fullscreen":
            raise ValueError("meeting_notes widgets must be compact or expanded")
        self.content = normalized_content
        if not self.dedupe_key:
            identity = f"{self.kind}\0{self.title}\0{self.message}".encode("utf-8")
            self.dedupe_key = f"auto:{hashlib.sha256(identity).hexdigest()[:24]}"
        self.actions = [
            action.model_copy(
                update={
                    "requires_confirmation": (
                        action.requires_confirmation
                        or action.type == "assistant_message"
                        or self.risk in {"medium", "high"}
                    )
                }
            )
            for action in self.actions
        ]
        return self


class ChatSubagentCall(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    agent: str = Field(pattern="^(researcher|planner|reviewer)$")
    task: str = Field(min_length=1, max_length=4_000)
    context: str = Field(default="fresh", pattern="^(fresh|fork)$")
    session: Optional[str] = Field(default=None, max_length=120, pattern=r"^[a-zA-Z0-9_.:-]+$")

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
    widget: Optional[ChatWidgetProposalOut] = None
    calls: Optional[List[ChatSubagentCall]] = Field(default=None, max_length=3)

    @model_validator(mode="after")
    def validate_delegation(self):
        if self.type == "delegate_tasks" and not self.calls:
            raise ValueError("delegate_tasks requires at least one call")
        if self.type != "delegate_tasks" and self.calls is not None:
            raise ValueError("calls are only valid for delegate_tasks")
        return self

class ChatIn(BaseModel):
    message: str = Field(max_length=12_000)
    session_id: Optional[str] = Field(default=None, max_length=200)
    provider: str = Field(default="gemini", max_length=40)
    api_key: str = Field(default="", max_length=1_024)
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
    emotion: AuraEmotionName = DEFAULT_AURA_EMOTION
    # Optional bounded directive for a one-off emotion profile generated by the client.
    created_emotion: Optional[str] = Field(default=None, max_length=80)
    actions: List[ChatActionOut] = Field(default_factory=list, max_length=16)
