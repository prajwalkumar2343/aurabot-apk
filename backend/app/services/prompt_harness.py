from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Optional

from app.models.chat import ChatActionOut, ChatIn


WORKSPACE_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_CONTEXT_FILES = ("README.md", "memory/PRD.md")
ALLOWED_CONTEXT_FILES = {
    "README.md",
    "memory/PRD.md",
    "memory/mini_app_builder_skill.md",
    "design_guidelines.json",
}
MAX_CONTEXT_CHARS = 1800
DEFAULT_GEMINI_FAST_MODEL = "gemini-2.5-flash"
DEFAULT_GEMINI_DEEP_MODEL = "gemini-2.5-pro"


@dataclass(frozen=True)
class ContextSnippet:
    path: str
    title: str
    content: str


@dataclass(frozen=True)
class SkillCard:
    name: str
    summary: str
    triggers: tuple[str, ...]
    detail: str


@dataclass(frozen=True)
class PromptHarness:
    context_snippets: list[ContextSnippet] = field(default_factory=list)
    skill_summaries: list[str] = field(default_factory=list)
    activated_skills: list[SkillCard] = field(default_factory=list)
    planning_mode: str = "off"
    routed_model: Optional[str] = None
    route_reason: str = "model routing disabled"
    max_repair_attempts: int = 1


SKILL_CARDS = (
    SkillCard(
        name="launcher_actions",
        summary="Use local Android launcher actions for opening apps and blocking distractions.",
        triggers=("block", "restrict", "pause", "limit", "open app", "launcher"),
        detail=(
            "When the user asks to block, restrict, pause, or limit an app, call block_app with "
            "an exact package name when available and a positive duration. Ask a short clarification "
            "only if the target app cannot be inferred from installed apps or the user request."
        ),
    ),
    SkillCard(
        name="mini_app_actions",
        summary="Use installed Aura mini app intents for opening mini apps and local records.",
        triggers=("mini app", "habit", "streak", "log", "check in", "record", "tracker"),
        detail=(
            "For installed mini apps, prefer exact mini_app_id and declared action_id when available. "
            "Use create_mini_app_record for check-ins/logging and query_mini_app_records for counts, "
            "history, or streak-like requests."
        ),
    ),
    SkillCard(
        name="memory_tasks",
        summary="Use local memories and tasks as grounding context for personal assistant replies.",
        triggers=("memory", "remember", "todo", "task", "remind", "what do i"),
        detail=(
            "Ground answers in the Local memories and Local tasks sections. Do not invent personal "
            "facts beyond those sections. If a requested memory/task mutation is not available as a "
            "tool, state the limitation briefly."
        ),
    ),
    SkillCard(
        name="mini_app_builder",
        summary="Create safe Aura mini apps, usually as React runtime apps for assistant-built custom tools.",
        triggers=("build", "create", "make", "generate", "mini app"),
        detail=(
            "When the user asks to create, build, make, or generate a mini app, call create_mini_app "
            "with a specific professional mini_app_prompt that asks for runtime react unless the user explicitly "
            "requested a native/declarative mini app. The prompt should describe the workflow, data model, polished "
            "React UI, local records, assistant intents, and any helpful screens/actions. Do not ask for APKs, "
            "webviews, plugins, remote URLs, network calls, browser storage APIs, or unsupported capabilities. "
            "When the user asks to revise, upgrade, patch, or add capabilities to an installed mini app, call "
            "revise_mini_app with the exact target mini app and a specific revision_instruction."
        ),
    ),
)


def _safe_context_path(path: str) -> Optional[Path]:
    normalized = path.strip().replace("\\", "/")
    if normalized not in ALLOWED_CONTEXT_FILES:
        return None
    candidate = (WORKSPACE_ROOT / normalized).resolve()
    if not str(candidate).startswith(str(WORKSPACE_ROOT)):
        return None
    return candidate


def load_context_files(requested: Iterable[str], message: str = "") -> list[ContextSnippet]:
    selected = list(dict.fromkeys([item.strip() for item in requested if item.strip()]))
    if not selected:
        selected = list(DEFAULT_CONTEXT_FILES)
        lower = message.lower()
        if any(word in lower for word in ("design", "theme", "visual", "ui")):
            selected.append("design_guidelines.json")

    snippets: list[ContextSnippet] = []
    for item in selected[:4]:
        path = _safe_context_path(item)
        if not path or not path.exists() or not path.is_file():
            continue
        content = path.read_text(encoding="utf-8", errors="replace").strip()
        if len(content) > MAX_CONTEXT_CHARS:
            content = content[:MAX_CONTEXT_CHARS].rstrip() + "\n[truncated]"
        snippets.append(ContextSnippet(path=item, title=item.split("/")[-1], content=content))
    return snippets


def discover_skills(data: ChatIn) -> tuple[list[str], list[SkillCard]]:
    message = data.message.lower()
    summaries = [f"- {card.name}: {card.summary}" for card in SKILL_CARDS]
    activated: list[SkillCard] = []
    for card in SKILL_CARDS:
        if any(trigger in message for trigger in card.triggers):
            activated.append(card)
    if data.mini_apps and not any(card.name == "mini_app_actions" for card in activated):
        activated.append(next(card for card in SKILL_CARDS if card.name == "mini_app_actions"))
    return summaries, activated[:3]


def resolve_planning_mode(data: ChatIn) -> str:
    requested = (data.planning_mode or "auto").lower().strip()
    if requested in {"off", "plan"}:
        return requested
    message = data.message.lower()
    complex_markers = ("build", "create", "implement", "plan", "multi", "workflow", "debug", "fix")
    return "plan" if any(marker in message for marker in complex_markers) else "off"


def route_model(data: ChatIn, planning_mode: str) -> tuple[str, str]:
    route = (data.model_route or "off").lower().strip()
    if route == "off" and data.model.lower().strip() != "auto":
        return normalize_model_id(data.provider, data.model), "model routing disabled"

    provider = data.provider.lower().strip()
    complex_request = planning_mode == "plan" or any(
        word in data.message.lower() for word in ("build", "implement", "debug", "repair", "generate")
    )
    routes = {
        "gemini": (DEFAULT_GEMINI_FAST_MODEL, DEFAULT_GEMINI_DEEP_MODEL),
        "openai": ("gpt-4.1-mini", "gpt-4.1"),
        "openrouter": ("openai/gpt-4.1-mini", "openai/gpt-4.1"),
    }
    fast_model, deep_model = routes.get(provider, (data.model, data.model))
    routed = deep_model if complex_request else fast_model
    return routed, f"routed to {'deep' if complex_request else 'fast'} {provider} model"


def normalize_model_id(provider: str, model: str) -> str:
    model_id = (model or "").strip()
    provider_id = (provider or "").lower().strip()
    if provider_id == "gemini":
        if model_id.startswith("models/"):
            model_id = model_id.removeprefix("models/")
        if model_id.startswith("gemini/"):
            model_id = model_id.removeprefix("gemini/")
    return model_id


def build_prompt_harness(data: ChatIn) -> PromptHarness:
    planning_mode = resolve_planning_mode(data)
    routed_model, route_reason = route_model(data, planning_mode)
    skill_summaries, activated_skills = discover_skills(data)
    return PromptHarness(
        context_snippets=load_context_files(data.context_files, data.message),
        skill_summaries=skill_summaries,
        activated_skills=activated_skills,
        planning_mode=planning_mode,
        routed_model=routed_model,
        route_reason=route_reason,
        max_repair_attempts=max(0, min(data.max_repair_attempts, 2)),
    )


def format_context_snippets(snippets: list[ContextSnippet]) -> str:
    if not snippets:
        return "- none"
    return "\n\n".join(f"### {snippet.path}\n{snippet.content}" for snippet in snippets)


def format_activated_skills(skills: list[SkillCard]) -> str:
    if not skills:
        return "- none"
    return "\n".join(f"- {skill.name}: {skill.detail}" for skill in skills)


def format_skill_summaries(summaries: list[str]) -> str:
    return "\n".join(summaries) or "- none"


def repair_needed(raw: str, reply: str, actions: list[ChatActionOut], data: ChatIn) -> Optional[str]:
    text = raw.strip()
    lower_message = data.message.lower()
    wants_action = any(
        word in lower_message
        for word in ("block", "restrict", "pause", "limit", "open", "log", "check in", "record", "streak", "create", "build", "make", "generate", "revise", "upgrade", "patch", "automate", "automation", "daily", "schedule", "remind", "when i", "when leaving", "when entering")
    )
    if wants_action and not actions and any(word in reply.lower() for word in ("done", "blocked", "opened", "saved")):
        return "reply claimed a local action without calling the matching tool"
    if "actions" in text and not actions and not text.startswith("{"):
        return "response mentioned actions but did not return a valid action structure"
    for action in actions:
        if action.type == "block_app" and (action.duration_minutes is None or action.duration_minutes <= 0):
            return "block_app requires a positive duration_minutes value"
        if action.type == "create_mini_app" and not (action.mini_app_prompt or "").strip():
            return "create_mini_app requires a specific mini_app_prompt value"
        if action.type == "revise_mini_app" and not (action.revision_instruction or "").strip():
            return "revise_mini_app requires a specific revision_instruction value"
        if action.type == "create_automation":
            spec = action.automation_spec or {}
            if not spec.get("name") or not isinstance(spec.get("trigger"), dict) or not spec.get("actions"):
                return "create_automation requires automation_spec with name, trigger, and at least one action"
    return None


def build_repair_system_message(system_message: str, reason: str, raw_reply: str) -> str:
    excerpt = raw_reply.strip()[:800]
    return (
        f"{system_message}\n\n"
        "Repair pass:\n"
        f"- Previous response problem: {reason}.\n"
        "- Return a corrected short reply and call the matching tool when a local action is needed.\n"
        "- Preserve the allowed expression tag requirement.\n"
        f"Previous response excerpt:\n{excerpt}"
    )
