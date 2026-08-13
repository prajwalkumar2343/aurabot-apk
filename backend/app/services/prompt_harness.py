import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Optional

from app.models.chat import ChatActionOut, ChatIn


WORKSPACE_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_CONTEXT_FILES = ("README.md", "memory/PRD.md")
SKILLS_ROOT = WORKSPACE_ROOT / "skills"
ALLOWED_CONTEXT_FILES = {
    "README.md",
    "memory/PRD.md",
    "memory/mini_app_builder_skill.md",
    "design_guidelines.json",
}
MAX_CONTEXT_CHARS = 1800
MAX_SKILL_DETAIL_CHARS = 5000
DEFAULT_GEMINI_FAST_MODEL = "gemini-2.5-flash"
DEFAULT_GEMINI_DEEP_MODEL = "gemini-2.5-pro"


@dataclass(frozen=True)
class ContextSnippet:
    path: str
    title: str
    content: str


@dataclass(frozen=True)
class SkillDefinition:
    name: str
    summary: str
    triggers: tuple[str, ...]
    detail: str
    path: str


@dataclass(frozen=True)
class PromptHarness:
    context_snippets: list[ContextSnippet] = field(default_factory=list)
    skill_summaries: list[str] = field(default_factory=list)
    activated_skills: list[SkillDefinition] = field(default_factory=list)
    planning_mode: str = "off"
    routed_model: Optional[str] = None
    route_reason: str = "model routing disabled"
    max_repair_attempts: int = 1


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


def _parse_frontmatter(raw: str) -> tuple[dict[str, object], str]:
    if not raw.startswith("---\n"):
        return {}, raw

    marker = "\n---\n"
    end = raw.find(marker, 4)
    if end == -1:
        return {}, raw

    frontmatter = raw[4:end]
    body = raw[end + len(marker):]
    metadata: dict[str, object] = {}
    current_list_key: Optional[str] = None

    for line in frontmatter.splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if line.startswith("  - ") and current_list_key:
            items = metadata.setdefault(current_list_key, [])
            if isinstance(items, list):
                items.append(line[4:].strip().strip("\"'"))
            continue
        current_list_key = None
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        key = key.strip()
        value = value.strip()
        if not key:
            continue
        if not value:
            metadata[key] = []
            current_list_key = key
            continue
        if value.startswith("[") and value.endswith("]"):
            metadata[key] = [
                item.strip().strip("\"'")
                for item in value[1:-1].split(",")
                if item.strip()
            ]
        else:
            metadata[key] = value.strip("\"'")

    return metadata, body


def _string_list(value: object) -> tuple[str, ...]:
    if isinstance(value, list):
        return tuple(str(item).strip().lower() for item in value if str(item).strip())
    if isinstance(value, str):
        return tuple(item.strip().lower() for item in value.split(",") if item.strip())
    return ()


def _relative_workspace_path(path: Path) -> str:
    try:
        return path.resolve().relative_to(WORKSPACE_ROOT).as_posix()
    except ValueError:
        return path.name


def load_skill_definitions(skills_root: Path = SKILLS_ROOT) -> list[SkillDefinition]:
    if not skills_root.exists() or not skills_root.is_dir():
        return []

    skill_files = sorted(path for path in skills_root.glob("*/SKILL.md") if path.is_file())
    definitions: list[SkillDefinition] = []
    seen_names: set[str] = set()

    for path in skill_files:
        raw = path.read_text(encoding="utf-8", errors="replace")
        metadata, body = _parse_frontmatter(raw)
        name = str(metadata.get("name") or path.parent.name).strip()
        if not name or name in seen_names:
            continue
        summary = str(metadata.get("description") or "").strip()
        triggers = _string_list(metadata.get("triggers"))
        detail = body.strip()
        if len(detail) > MAX_SKILL_DETAIL_CHARS:
            detail = detail[:MAX_SKILL_DETAIL_CHARS].rstrip() + "\n[truncated]"
        definitions.append(
            SkillDefinition(
                name=name,
                summary=summary,
                triggers=triggers,
                detail=detail,
                path=_relative_workspace_path(path),
            )
        )
        seen_names.add(name)

    return definitions


def discover_skills(data: ChatIn) -> tuple[list[str], list[SkillDefinition]]:
    message = data.message.lower()
    definitions = load_skill_definitions()
    summaries = [
        f"- {skill.name}: {skill.summary} ({skill.path})"
        for skill in definitions
        if skill.summary
    ]
    activated: list[SkillDefinition] = []
    for skill in definitions:
        if skill.triggers and any(trigger in message for trigger in skill.triggers):
            activated.append(skill)
    if data.mini_apps and not any(skill.name == "mini_app_actions" for skill in activated):
        mini_app_actions = next((skill for skill in definitions if skill.name == "mini_app_actions"), None)
        if mini_app_actions:
            activated.append(mini_app_actions)
    return summaries, activated[:4]


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


def format_activated_skills(skills: list[SkillDefinition]) -> str:
    if not skills:
        return "- none"
    return "\n\n".join(f"### {skill.name} ({skill.path})\n{skill.detail}" for skill in skills)


def format_skill_summaries(summaries: list[str]) -> str:
    return "\n".join(summaries) or "- none"


def repair_needed(raw: str, reply: str, actions: list[ChatActionOut], data: ChatIn) -> Optional[str]:
    text = raw.strip()
    lower_message = data.message.lower()
    if not actions:
        try:
            attempted_actions = json.loads(text).get("actions") if text.startswith("{") else None
        except (json.JSONDecodeError, AttributeError):
            attempted_actions = None
        if attempted_actions:
            return "response contained an unsupported or invalid action structure"
        if "actions" in text and not text.startswith("{"):
            return "response mentioned actions but did not return a valid action structure"
    wants_action = any(
        word in lower_message
        for word in ("block", "restrict", "pause", "limit", "open", "log", "check in", "record", "streak", "create", "build", "make", "generate", "revise", "upgrade", "patch", "automate", "automation", "daily", "schedule", "remind", "when i", "when leaving", "when entering")
    )
    if wants_action and not actions and any(word in reply.lower() for word in ("done", "blocked", "opened", "saved")):
        return "reply claimed a local action without calling the matching tool"
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
        if action.type == "present_widget" and action.widget is None:
            return "present_widget requires a valid widget payload"
    return None


def build_repair_system_message(system_message: str, reason: str, raw_reply: str) -> str:
    excerpt = raw_reply.strip()[:800]
    return (
        f"{system_message}\n\n"
        "Repair pass:\n"
        f"- Previous response problem: {reason}.\n"
        "- Return a corrected short reply and call the matching tool when a local action is needed.\n"
        "- Preserve the JSON response contract, choose one valid emotion, and use created_emotion only as a bounded `create <emotion>` directive when needed.\n"
        f"Previous response excerpt:\n{excerpt}"
    )
