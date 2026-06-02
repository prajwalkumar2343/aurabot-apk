import json
import re
import logging
import requests
from fastapi import HTTPException
from typing import Any, Iterable, List, Optional, Tuple
from app.models.chat import ChatIn, ChatActionOut
from app.services.prompt_harness import (
    PromptHarness,
    build_prompt_harness,
    format_activated_skills,
    format_context_snippets,
    format_skill_summaries,
)

logger = logging.getLogger(__name__)

ASSISTANT_TOOL_NAMES = {
    "block_app",
    "open_mini_app",
    "create_mini_app_record",
    "query_mini_app_records",
}


def _context_list(items: Iterable[str]) -> str:
    return "\n".join(items) or "- none"


def build_system_message(data: ChatIn, harness: Optional[PromptHarness] = None) -> str:
    harness = harness or build_prompt_harness(data)
    memories = _context_list(f"- {item.title}: {item.content}" for item in data.memories[:8])
    todos = _context_list(
        f"- [{'done' if item.done else 'open'}] {item.title}" for item in data.todos[:12]
    )
    apps = _context_list(f"- {item.label} ({item.package_name})" for item in data.apps[:80])
    mini_apps = _context_list(
        f"- {item.name} ({item.id}); assistant intents: {', '.join(item.intents[:8]) or 'none'}"
        for item in data.mini_apps[:40]
    )
    return (
        "You are Aura, a calm launcher assistant inside an Android home app. "
        "Your responses will be read aloud by a Text-to-Speech (TTS) synthesizer. "
        "Always start your reply with one expression tag from this set: "
        "{happy}, {sad}, {excited}, {thinking}, {angry}, {neutral}. "
        "Keep replies short, natural, plain text, and suitable for speech. "
        "Use the available tools when the user asks for an action Aura can perform locally. "
        "Do not claim an action has completed unless you request the matching tool. "
        "Use app blocking only when the user asks to block, restrict, pause, or limit an app. "
        "Use mini app tools when the user asks to open an Aura mini app, log or check in a mini app item, show a streak, or query mini app records. "
        "When blocking an app, prefer an exact package_name from the installed app list and choose the requested duration in minutes. "
        "If no duration is given, use 30 minutes. "
        f"Planning mode is {harness.planning_mode}. "
        "When planning mode is plan, include a concise user-visible plan in the reply before the final action summary. "
        f"Model routing: {harness.route_reason}. "
        "If a provider cannot use tools, return ONLY JSON with this shape: "
        '{"reply":"{neutral} short reply","actions":[{"type":"block_app","package_name":"exact.package","app_query":"fallback app name","duration_minutes":30},{"type":"open_mini_app","mini_app_id":"id","mini_app_query":"name"},{"type":"create_mini_app_record","mini_app_id":"id","action_id":"action","record_type":"record","values":{"field":"value"}},{"type":"query_mini_app_records","mini_app_id":"id"}]}. '
        "No markdown, no emoji.\n\n"
        f"Local memories:\n{memories}\n\n"
        f"Local tasks:\n{todos}\n\n"
        f"Installed apps:\n{apps}\n\n"
        f"Installed Aura mini apps:\n{mini_apps}\n\n"
        f"Loaded file context:\n{format_context_snippets(harness.context_snippets)}\n\n"
        f"Available skill summaries:\n{format_skill_summaries(harness.skill_summaries)}\n\n"
        f"Activated skill details:\n{format_activated_skills(harness.activated_skills)}"
    )


def assistant_tool_definitions() -> list[dict[str, Any]]:
    return [
        {
            "name": "block_app",
            "description": "Block or restrict one installed Android app for a number of minutes.",
            "parameters": {
                "type": "object",
                "properties": {
                    "package_name": {
                        "type": "string",
                        "description": "Exact installed Android package name when known.",
                    },
                    "app_query": {
                        "type": "string",
                        "description": "Fallback app label or package search query.",
                    },
                    "duration_minutes": {
                        "type": "integer",
                        "minimum": 1,
                        "maximum": 1440,
                        "description": "Block duration in minutes. Use 30 if the user did not specify a duration.",
                    },
                },
                "required": ["duration_minutes"],
                "additionalProperties": False,
            },
        },
        {
            "name": "open_mini_app",
            "description": "Open an installed Aura mini app.",
            "parameters": {
                "type": "object",
                "properties": {
                    "mini_app_id": {"type": "string", "description": "Exact mini app id when known."},
                    "mini_app_query": {"type": "string", "description": "Fallback mini app name query."},
                },
                "additionalProperties": False,
            },
        },
        {
            "name": "create_mini_app_record",
            "description": "Create a local record or run a declared action in an installed Aura mini app.",
            "parameters": {
                "type": "object",
                "properties": {
                    "mini_app_id": {"type": "string", "description": "Exact mini app id when known."},
                    "mini_app_query": {"type": "string", "description": "Fallback mini app name query."},
                    "action_id": {"type": "string", "description": "Declared mini app action id when known."},
                    "record_type": {"type": "string", "description": "Mini app record type. Defaults to record."},
                    "values": {
                        "type": "object",
                        "description": "Record values as string key/value pairs.",
                        "additionalProperties": {"type": "string"},
                    },
                },
                "additionalProperties": False,
            },
        },
        {
            "name": "query_mini_app_records",
            "description": "Query local record count/history for an installed Aura mini app.",
            "parameters": {
                "type": "object",
                "properties": {
                    "mini_app_id": {"type": "string", "description": "Exact mini app id when known."},
                    "mini_app_query": {"type": "string", "description": "Fallback mini app name query."},
                },
                "additionalProperties": False,
            },
        },
    ]


def openai_assistant_tools() -> list[dict[str, Any]]:
    return [
        {
            "type": "function",
            "name": tool["name"],
            "description": tool["description"],
            "parameters": tool["parameters"],
        }
        for tool in assistant_tool_definitions()
    ]


def chat_assistant_tools() -> list[dict[str, Any]]:
    return [
        {
            "type": "function",
            "function": {
                "name": tool["name"],
                "description": tool["description"],
                "parameters": tool["parameters"],
            },
        }
        for tool in assistant_tool_definitions()
    ]


def gemini_assistant_tools() -> list[dict[str, Any]]:
    return [
        {
            "functionDeclarations": [
                {
                    "name": tool["name"],
                    "description": tool["description"],
                    "parameters": tool["parameters"],
                }
                for tool in assistant_tool_definitions()
            ]
        }
    ]


def _coerce_values(values: Any) -> Optional[dict[str, str]]:
    if not isinstance(values, dict):
        return None
    return {str(key): str(value) for key, value in values.items()}


def _action_from_tool_call(name: str, args: Any) -> Optional[ChatActionOut]:
    if name not in ASSISTANT_TOOL_NAMES:
        return None
    if isinstance(args, str):
        try:
            args = json.loads(args or "{}")
        except json.JSONDecodeError:
            args = {}
    if not isinstance(args, dict):
        args = {}
    return ChatActionOut(
        type=name,
        package_name=args.get("package_name"),
        app_query=args.get("app_query"),
        duration_minutes=args.get("duration_minutes"),
        mini_app_id=args.get("mini_app_id"),
        mini_app_query=args.get("mini_app_query"),
        action_id=args.get("action_id"),
        record_type=args.get("record_type"),
        values=_coerce_values(args.get("values")),
    )


def _tool_response(reply: str, actions: list[ChatActionOut]) -> str:
    return json.dumps(
        {
            "reply": reply.strip() or "{neutral} Done.",
            "actions": [action.model_dump(exclude_none=True) for action in actions],
        }
    )


def parse_tool_response(raw: str) -> Tuple[str, List[ChatActionOut]]:
    text = raw.strip()
    try:
        payload = json.loads(text)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", text, flags=re.DOTALL)
        if not match:
            return text, []
        try:
            payload = json.loads(match.group(0))
        except json.JSONDecodeError:
            return text, []

    reply = str(payload.get("reply") or "").strip() or "Done."
    actions = []
    for item in payload.get("actions") or []:
        if not isinstance(item, dict):
            continue
        actions.append(
            ChatActionOut(
                type=str(item.get("type") or "").strip(),
                package_name=item.get("package_name"),
                app_query=item.get("app_query"),
                duration_minutes=item.get("duration_minutes"),
                mini_app_id=item.get("mini_app_id"),
                mini_app_query=item.get("mini_app_query"),
                action_id=item.get("action_id"),
                record_type=item.get("record_type"),
                values=item.get("values") if isinstance(item.get("values"), dict) else None,
            )
        )
    return reply, [action for action in actions if action.type]

def extract_openai_text(payload: dict) -> str:
    output = payload.get("output", [])
    text_parts: List[str] = []
    actions: list[ChatActionOut] = []
    for item in output:
        if item.get("type") == "function_call":
            action = _action_from_tool_call(item.get("name", ""), item.get("arguments"))
            if action:
                actions.append(action)
        for content in item.get("content", []):
            if content.get("type") == "output_text" and content.get("text"):
                text_parts.append(content["text"])
    if text_parts:
        text = "\n".join(text_parts).strip()
        return _tool_response(text, actions) if actions else text
    if actions:
        return _tool_response("{neutral} Done.", actions)
    raise HTTPException(status_code=502, detail="OpenAI response did not include text output")

def _extract_gemini_text(payload: dict) -> str:
    candidates = payload.get("candidates") or []
    parts = candidates[0].get("content", {}).get("parts", []) if candidates else []
    text_parts: list[str] = []
    actions: list[ChatActionOut] = []
    for part in parts:
        if part.get("text"):
            text_parts.append(part["text"])
        function_call = part.get("functionCall") or part.get("function_call")
        if function_call:
            action = _action_from_tool_call(function_call.get("name", ""), function_call.get("args", {}))
            if action:
                actions.append(action)
    text = "".join(text_parts).strip()
    if text:
        return _tool_response(text, actions) if actions else text
    if actions:
        return _tool_response("{neutral} Done.", actions)
    raise HTTPException(status_code=502, detail="Gemini response did not include text")


def call_gemini(data: ChatIn, system_message: str, use_assistant_tools: bool = False) -> str:
    if not data.api_key:
        raise HTTPException(status_code=400, detail="Gemini API Key is required")
    try:
        payload = {
            "contents": [
                {
                    "role": "user",
                    "parts": [
                        {
                            "text": data.message if use_assistant_tools else f"{system_message}\n\nUser request:\n{data.message}"
                        }
                    ],
                }
            ],
        }
        if use_assistant_tools:
            payload["systemInstruction"] = {"parts": [{"text": system_message}]}
            payload["tools"] = gemini_assistant_tools()
        response = requests.post(
            f"https://generativelanguage.googleapis.com/v1beta/models/{data.model}:generateContent",
            headers={
                "x-goog-api-key": data.api_key,
                "Content-Type": "application/json",
            },
            json=payload,
            timeout=60,
        )
    except requests.exceptions.RequestException as e:
        logger.exception("Gemini request failed")
        raise HTTPException(status_code=502, detail=f"Failed to connect to Gemini: {str(e)}")

    if response.status_code >= 400:
        raise HTTPException(status_code=response.status_code, detail=f"Gemini error: {response.text[:300]}")
    return _extract_gemini_text(response.json())

def call_openai(data: ChatIn, system_message: str, use_assistant_tools: bool = False) -> str:
    if not data.api_key:
        raise HTTPException(status_code=400, detail="OpenAI API Key is required")
    try:
        payload = {
            "model": data.model,
            "input": [
                {
                    "role": "system",
                    "content": [
                        {
                            "type": "input_text",
                            "text": system_message,
                        }
                    ],
                },
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "input_text",
                            "text": data.message,
                        }
                    ],
                },
            ],
        }
        if use_assistant_tools:
            payload["tools"] = openai_assistant_tools()
        response = requests.post(
            "https://api.openai.com/v1/responses",
            headers={
                "Authorization": f"Bearer {data.api_key}",
                "Content-Type": "application/json",
            },
            json=payload,
            timeout=60,
        )
    except requests.exceptions.RequestException as e:
        logger.exception("OpenAI request failed")
        raise HTTPException(status_code=502, detail=f"Failed to connect to OpenAI: {str(e)}")

    if response.status_code >= 400:
        raise HTTPException(status_code=response.status_code, detail=f"OpenAI error: {response.text[:300]}")
    return extract_openai_text(response.json())

def call_openrouter(data: ChatIn, system_message: str, use_assistant_tools: bool = False) -> str:
    if not data.api_key:
        raise HTTPException(status_code=400, detail="OpenRouter API Key is required")
    try:
        payload = {
            "model": data.model,
            "messages": [
                {"role": "system", "content": system_message},
                {"role": "user", "content": data.message},
            ],
        }
        if use_assistant_tools:
            payload["tools"] = chat_assistant_tools()
        response = requests.post(
            "https://openrouter.ai/api/v1/chat/completions",
            headers={
                "Authorization": f"Bearer {data.api_key}",
                "Content-Type": "application/json",
            },
            json=payload,
            timeout=60,
        )
    except requests.exceptions.RequestException as e:
        logger.exception("OpenRouter request failed")
        raise HTTPException(status_code=502, detail=f"Failed to connect to OpenRouter: {str(e)}")

    if response.status_code >= 400:
        raise HTTPException(status_code=response.status_code, detail=f"OpenRouter error: {response.text[:300]}")
    payload = response.json()
    choices = payload.get("choices") or []
    message = choices[0].get("message", {}) if choices else {}
    text = (message.get("content") or "").strip()
    actions: list[ChatActionOut] = []
    for tool_call in message.get("tool_calls") or []:
        function = tool_call.get("function", {})
        action = _action_from_tool_call(function.get("name", ""), function.get("arguments"))
        if action:
            actions.append(action)
    if actions:
        return _tool_response(text or "{neutral} Done.", actions)
    if not text:
        raise HTTPException(status_code=502, detail="OpenRouter response did not include text")
    return text
