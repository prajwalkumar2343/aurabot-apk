import json
import re
import uuid
from fastapi import HTTPException
from pathlib import Path
from typing import Optional

from app.models.chat import ChatIn
from app.models.mini_apps import MiniAppBuildIn, MiniAppBundle
from app.services.llm import call_gemini, call_openai, call_openrouter


MINI_APP_BUILDER_SKILL_PATH = Path(__file__).resolve().parents[3] / "memory" / "mini_app_builder_skill.md"

SUPPORTED_COMPONENTS = {
    "dashboard_block",
    "quick_action_grid",
    "timeline",
    "progress_ring",
    "streak_view",
    "chart",
    "form",
    "list",
    "bottom_sheet",
    "button",
    "slider",
    "settings",
}
SUPPORTED_ACTIONS = {"create_record", "query_records", "open_screen", "update_record", "delete_record"}
SUPPORTED_CAPABILITIES = {"local_storage", "assistant_actions", "notifications"}
SUPPORTED_FIELDS = {"text", "number", "boolean", "date", "datetime"}


def mini_app_builder_skill_prompt() -> str:
    try:
        return MINI_APP_BUILDER_SKILL_PATH.read_text(encoding="utf-8").strip()
    except OSError:
        return (
            "Create one safe declarative Aura mini app bundle as JSON only. "
            "Do not include executable code, scripts, URLs, webviews, APKs, plugins, or unsupported capabilities. "
            "Build professional app-like bundles with multiple screens, quick actions, local storage, assistant intents, "
            "charts, history, lists, settings, and direct buttons when useful."
        )


def mini_app_builder_system_prompt(repair_error: Optional[str] = None, previous_output: Optional[str] = None) -> str:
    components = ", ".join(sorted(SUPPORTED_COMPONENTS))
    prompt = (
        f"{mini_app_builder_skill_prompt()}\n\n"
        f"Supported component types: {components}. "
        "Use camelCase fields exactly matching the schema: id, version, metadata, theme, icon, dataSchema, screens, actions, assistantIntents, capabilities. "
        "Make the UI polished and app-like with at least two screens when useful, such as Dashboard plus Details, Plan, or Settings. "
        "Use dashboard blocks, quick actions, timeline/history, chart, list, button, slider, settings, bottom_sheet, and assistant intents when useful."
    )
    if repair_error:
        prompt += (
            "\n\nRepair pass:\n"
            f"- Previous bundle error: {repair_error}\n"
            "- Return corrected JSON only.\n"
            f"- Previous output excerpt: {(previous_output or '')[:800]}"
        )
    return prompt


def parse_json_object(raw: str) -> dict:
    text = raw.strip()
    try:
        payload = json.loads(text)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", text, flags=re.DOTALL)
        if not match:
            raise HTTPException(status_code=422, detail="LLM returned malformed JSON")
        try:
            payload = json.loads(match.group(0))
        except json.JSONDecodeError:
            raise HTTPException(status_code=422, detail="LLM returned malformed JSON")
    if not isinstance(payload, dict):
        raise HTTPException(status_code=422, detail="LLM response must be a JSON object")
    return payload


def validate_mini_app_bundle(payload: dict) -> MiniAppBundle:
    try:
        bundle = MiniAppBundle(**payload)
    except Exception as exc:
        raise HTTPException(status_code=422, detail=f"Invalid mini app bundle: {str(exc)[:200]}")

    if not bundle.id.strip():
        raise HTTPException(status_code=422, detail="Mini app id is required")
    if not bundle.metadata.name.strip():
        raise HTTPException(status_code=422, detail="Mini app name is required")
    if not bundle.screens:
        raise HTTPException(status_code=422, detail="At least one screen is required")
    for capability in bundle.capabilities:
        if capability not in SUPPORTED_CAPABILITIES:
            raise HTTPException(status_code=422, detail=f"Unsupported capability: {capability}")
    for field in bundle.dataSchema.fields:
        if field.type not in SUPPORTED_FIELDS:
            raise HTTPException(status_code=422, detail=f"Unsupported field type: {field.type}")
    action_ids = {action.id for action in bundle.actions}
    for action in bundle.actions:
        if not action.id.strip():
            raise HTTPException(status_code=422, detail="Action id is required")
        if action.type not in SUPPORTED_ACTIONS:
            raise HTTPException(status_code=422, detail=f"Unsupported action: {action.type}")
    for screen in bundle.screens:
        if not screen.id.strip():
            raise HTTPException(status_code=422, detail="Screen id is required")
        for component in screen.components:
            if component.type not in SUPPORTED_COMPONENTS:
                raise HTTPException(status_code=422, detail=f"Unsupported component: {component.type}")
            if component.actionId and component.actionId not in action_ids:
                raise HTTPException(status_code=422, detail=f"Unknown action: {component.actionId}")
            for item in component.items:
                if item.actionId and item.actionId not in action_ids:
                    raise HTTPException(status_code=422, detail=f"Unknown action: {item.actionId}")
    for intent in bundle.assistantIntents:
        if intent.actionId and intent.actionId not in action_ids:
            raise HTTPException(status_code=422, detail=f"Unknown intent action: {intent.actionId}")
    return bundle


def fallback_bundle(prompt: str) -> MiniAppBundle:
    cleaned = " ".join(prompt.strip().split())
    name = " ".join(cleaned.split()[:3]).title() or "Mini App"
    slug = re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_") or uuid.uuid4().hex[:8]
    normalized = cleaned.lower()
    if any(word in normalized for word in ("habit", "health", "water", "workout", "wellness")):
        category = "Wellness"
        primary = "#16A34A"
    elif any(word in normalized for word in ("money", "spend", "expense", "budget", "finance")):
        category = "Finance"
        primary = "#0F766E"
    elif any(word in normalized for word in ("focus", "task", "plan", "work", "study")):
        category = "Productivity"
        primary = "#2563EB"
    else:
        category = "Custom"
        primary = "#7C3AED"
    return validate_mini_app_bundle(
        {
            "id": f"generated.{slug}",
            "version": 1,
            "metadata": {"name": name, "description": f"A local {category.lower()} app created from: {cleaned}", "category": category},
            "theme": {"primary": primary, "secondary": "#F59E0B", "surface": "#111827"},
            "icon": {"type": "initial", "value": name[:1].upper(), "background": primary},
            "dataSchema": {
                "recordType": "entry",
                "fields": [
                    {"name": "title", "type": "text", "required": True},
                    {"name": "status", "type": "text", "required": True},
                    {"name": "note", "type": "text"},
                ],
            },
            "actions": [
                {"id": "quick_add", "type": "create_record", "recordType": "entry", "values": {"title": cleaned, "status": "Logged"}},
                {"id": "mark_priority", "type": "create_record", "recordType": "entry", "values": {"title": "Priority", "status": cleaned}},
                {"id": "save_note", "type": "create_record", "recordType": "entry", "values": {"title": "Note", "status": "Captured"}},
            ],
            "assistantIntents": [
                {"name": "quick_add", "utterances": [f"add to {name}", f"log {name}"], "actionId": "quick_add"},
                {"name": "show_dashboard", "utterances": [f"open {name}", f"show {name}"], "screenId": "dashboard"},
            ],
            "screens": [
                {
                    "id": "dashboard",
                    "title": "Dashboard",
                    "components": [
                        {"type": "dashboard_block", "title": "Today", "metric": "today_count"},
                        {"type": "streak_view", "title": "Momentum", "metric": "streak"},
                        {
                            "type": "quick_action_grid",
                            "title": "Actions",
                            "items": [
                                {"label": "Log", "actionId": "quick_add"},
                                {"label": "Priority", "actionId": "mark_priority"},
                                {"label": "Note", "actionId": "save_note"},
                            ],
                        },
                        {"type": "chart", "title": "Last 7 Days", "metric": "weekly_count"},
                        {"type": "timeline", "title": "Activity", "source": "records"},
                        {"type": "slider", "title": "Weekly Pace", "metric": "weekly_count"},
                    ],
                },
                {
                    "id": "details",
                    "title": "Details",
                    "components": [
                        {"type": "form", "title": "Custom Entry", "items": [{"label": "Save entry"}]},
                        {
                            "type": "list",
                            "title": "Shortcuts",
                            "items": [
                                {"label": "Log entry", "actionId": "quick_add", "value": "Capture the default item"},
                                {"label": "Mark priority", "actionId": "mark_priority", "value": "Pin the most important thing"},
                                {"label": "Save note", "actionId": "save_note", "value": "Keep a lightweight note"},
                            ],
                        },
                        {"type": "button", "title": "Log now", "actionId": "quick_add"},
                        {
                            "type": "bottom_sheet",
                            "title": "App note",
                            "items": [
                                {
                                    "label": "This generated app starts with local capture, history, progress, and assistant actions."
                                }
                            ],
                        },
                        {"type": "settings", "title": "App setup"},
                    ],
                },
            ],
            "capabilities": ["local_storage", "assistant_actions"],
        }
    )


def call_builder_llm(data: MiniAppBuildIn, system_prompt: Optional[str] = None) -> str:
    chat = ChatIn(
        message=data.prompt,
        provider=data.provider,
        api_key=data.api_key,
        model=data.model,
    )
    provider = data.provider.lower().strip()
    system = system_prompt or mini_app_builder_system_prompt()
    if provider == "gemini":
        return call_gemini(chat, system)
    if provider == "openai":
        return call_openai(chat, system)
    if provider == "openrouter":
        return call_openrouter(chat, system)
    raise HTTPException(status_code=400, detail="Unsupported provider")
