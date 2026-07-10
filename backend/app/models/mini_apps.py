from pydantic import BaseModel, ConfigDict, Field
from typing import Optional


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class MiniAppMetadata(StrictModel):
    name: str = Field(max_length=80)
    description: str = Field(default="", max_length=500)
    category: str = Field(default="Tool", max_length=80)
    builtIn: bool = False


class MiniAppTheme(StrictModel):
    primary: str = Field(default="#4F46E5", max_length=32)
    secondary: str = Field(default="#14B8A6", max_length=32)
    surface: str = Field(default="#111827", max_length=32)


class MiniAppIcon(StrictModel):
    type: str = Field(default="initial", max_length=32)
    value: str = Field(default="A", max_length=16)
    background: str = Field(default="#4F46E5", max_length=32)


class MiniAppField(StrictModel):
    name: str = Field(max_length=80)
    type: str = Field(max_length=32)
    required: bool = False
    defaultValue: Optional[str] = Field(default=None, max_length=4000)


class MiniAppDataSchema(StrictModel):
    recordType: str = Field(default="record", max_length=80)
    fields: list[MiniAppField] = Field(default_factory=list, max_length=60)


class MiniAppComponentItem(StrictModel):
    label: str = Field(max_length=160)
    actionId: Optional[str] = Field(default=None, max_length=100)
    value: Optional[str] = Field(default=None, max_length=500)


class MiniAppComponent(StrictModel):
    type: str = Field(max_length=60)
    title: str = Field(default="", max_length=160)
    actionId: Optional[str] = Field(default=None, max_length=100)
    source: Optional[str] = Field(default=None, max_length=100)
    metric: Optional[str] = Field(default=None, max_length=100)
    items: list[MiniAppComponentItem] = Field(default_factory=list, max_length=20)


class MiniAppScreen(StrictModel):
    id: str = Field(max_length=100)
    title: str = Field(max_length=160)
    components: list[MiniAppComponent] = Field(default_factory=list, max_length=40)


class MiniAppAction(StrictModel):
    id: str = Field(max_length=100)
    type: str = Field(max_length=60)
    recordType: str = Field(default="record", max_length=80)
    values: dict[str, str] = Field(default_factory=dict, max_length=60)


class MiniAppAssistantIntent(StrictModel):
    name: str = Field(max_length=100)
    utterances: list[str] = Field(default_factory=list, max_length=20)
    actionId: Optional[str] = Field(default=None, max_length=100)
    screenId: Optional[str] = Field(default=None, max_length=100)


class MiniAppCodeBundle(StrictModel):
    entry: str = "App.jsx"
    appJsx: str = Field(max_length=30_000)
    css: str = Field(default="", max_length=16_000)
    compiledJs: str = Field(default="", max_length=1_500_000)
    allowedApis: list[str] = Field(default_factory=lambda: ["records"], max_length=10)


class MiniAppWidget(StrictModel):
    type: str = Field(default="summary", max_length=32)
    title: str = Field(default="", max_length=60)
    description: str = Field(default="", max_length=160)
    metric: str = Field(default="today_count", max_length=32)
    goal: Optional[int] = Field(default=None, ge=1, le=1_000_000)
    actionIds: list[str] = Field(default_factory=list, max_length=3)


class MiniAppBundle(StrictModel):
    id: str = Field(max_length=120)
    version: int = Field(default=1, ge=1, le=100_000)
    runtime: str = Field(default="native", max_length=32)
    metadata: MiniAppMetadata
    theme: MiniAppTheme = Field(default_factory=MiniAppTheme)
    icon: MiniAppIcon = Field(default_factory=MiniAppIcon)
    dataSchema: MiniAppDataSchema = Field(default_factory=MiniAppDataSchema)
    screens: list[MiniAppScreen] = Field(default_factory=list, max_length=12)
    actions: list[MiniAppAction] = Field(default_factory=list, max_length=60)
    assistantIntents: list[MiniAppAssistantIntent] = Field(default_factory=list, max_length=60)
    capabilities: list[str] = Field(default_factory=lambda: ["local_storage"], max_length=10)
    codeBundle: Optional[MiniAppCodeBundle] = None
    widget: Optional[MiniAppWidget] = None


class MiniAppRecordCreate(StrictModel):
    recordType: str = "record"
    values: dict[str, object] = Field(default_factory=dict)


class MiniAppRecordUpdate(StrictModel):
    values: dict[str, object] = Field(default_factory=dict)


class MiniAppRecordOut(StrictModel):
    id: str
    miniAppId: str
    recordType: str
    values: dict[str, object] = Field(default_factory=dict)
    createdAt: str
    updatedAt: str


class MiniAppBuildIn(StrictModel):
    prompt: str = Field(max_length=8000)
    provider: str = "gemini"
    api_key: str
    model: str
    runtime: Optional[str] = None


class MiniAppBuildOut(StrictModel):
    bundle: MiniAppBundle


class MiniAppRevisionIn(StrictModel):
    instruction: str = Field(max_length=4000)
    currentBundle: MiniAppBundle
    recordSample: list[dict[str, object]] = Field(default_factory=list, max_length=8)
    provider: str = "gemini"
    api_key: str
    model: str
    runtime: Optional[str] = None


class MiniAppRevisionOut(StrictModel):
    bundle: MiniAppBundle
    summary: str
    migrationPlan: list[str] = Field(default_factory=list)


class MiniAppWidgetBuildIn(StrictModel):
    miniApp: MiniAppBundle
    instruction: str = Field(default="", max_length=1000)
    provider: str = "gemini"
    api_key: str
    model: str


class MiniAppWidgetBuildOut(StrictModel):
    widget: MiniAppWidget
