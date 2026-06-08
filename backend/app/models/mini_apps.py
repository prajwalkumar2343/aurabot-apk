from pydantic import BaseModel, Field
from typing import Optional


class MiniAppMetadata(BaseModel):
    name: str
    description: str = ""
    category: str = "Tool"
    builtIn: bool = False


class MiniAppTheme(BaseModel):
    primary: str = "#4F46E5"
    secondary: str = "#14B8A6"
    surface: str = "#111827"


class MiniAppIcon(BaseModel):
    type: str = "initial"
    value: str = "A"
    background: str = "#4F46E5"


class MiniAppField(BaseModel):
    name: str
    type: str
    required: bool = False
    defaultValue: Optional[str] = None


class MiniAppDataSchema(BaseModel):
    recordType: str = "record"
    fields: list[MiniAppField] = Field(default_factory=list)


class MiniAppComponentItem(BaseModel):
    label: str
    actionId: Optional[str] = None
    value: Optional[str] = None


class MiniAppComponent(BaseModel):
    type: str
    title: str = ""
    actionId: Optional[str] = None
    source: Optional[str] = None
    metric: Optional[str] = None
    items: list[MiniAppComponentItem] = Field(default_factory=list)


class MiniAppScreen(BaseModel):
    id: str
    title: str
    components: list[MiniAppComponent] = Field(default_factory=list)


class MiniAppAction(BaseModel):
    id: str
    type: str
    recordType: str = "record"
    values: dict[str, str] = Field(default_factory=dict)


class MiniAppAssistantIntent(BaseModel):
    name: str
    utterances: list[str] = Field(default_factory=list)
    actionId: Optional[str] = None
    screenId: Optional[str] = None


class MiniAppCodeBundle(BaseModel):
    entry: str = "App.jsx"
    appJsx: str
    css: str = ""
    compiledJs: str = ""
    allowedApis: list[str] = Field(default_factory=lambda: ["records"])


class MiniAppBundle(BaseModel):
    id: str
    version: int = 1
    runtime: str = "native"
    metadata: MiniAppMetadata
    theme: MiniAppTheme = Field(default_factory=MiniAppTheme)
    icon: MiniAppIcon = Field(default_factory=MiniAppIcon)
    dataSchema: MiniAppDataSchema = Field(default_factory=MiniAppDataSchema)
    screens: list[MiniAppScreen] = Field(default_factory=list)
    actions: list[MiniAppAction] = Field(default_factory=list)
    assistantIntents: list[MiniAppAssistantIntent] = Field(default_factory=list)
    capabilities: list[str] = Field(default_factory=lambda: ["local_storage"])
    codeBundle: Optional[MiniAppCodeBundle] = None


class MiniAppRecordCreate(BaseModel):
    recordType: str = "record"
    values: dict[str, object] = Field(default_factory=dict)


class MiniAppRecordUpdate(BaseModel):
    values: dict[str, object] = Field(default_factory=dict)


class MiniAppRecordOut(BaseModel):
    id: str
    miniAppId: str
    recordType: str
    values: dict[str, object] = Field(default_factory=dict)
    createdAt: str
    updatedAt: str


class MiniAppBuildIn(BaseModel):
    prompt: str
    provider: str = "gemini"
    api_key: str
    model: str
    runtime: Optional[str] = None


class MiniAppBuildOut(BaseModel):
    bundle: MiniAppBundle


class MiniAppRevisionIn(BaseModel):
    instruction: str
    currentBundle: MiniAppBundle
    recordSample: list[dict[str, object]] = Field(default_factory=list)
    provider: str = "gemini"
    api_key: str
    model: str
    runtime: Optional[str] = None


class MiniAppRevisionOut(BaseModel):
    bundle: MiniAppBundle
    summary: str
    migrationPlan: list[str] = Field(default_factory=list)
