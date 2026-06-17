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
    normalize_model_id,
)

logger = logging.getLogger(__name__)

ASSISTANT_TOOL_NAMES = {
    "block_app",
    "create_automation",
    "create_mini_app",
    "revise_mini_app",
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
        (
            f"- {item.name} ({item.id}); assistant intents: {', '.join(item.intents[:8]) or 'none'}; "
            f"declared actions: {', '.join(item.actions[:12]) or 'none'}"
        )
        for item in data.mini_apps[:40]
    )
    automations = _context_list(
        (
            f"- {item.name} ({item.id}); enabled: {item.enabled}; trigger: {item.trigger_type}; "
            f"actions: {', '.join(item.action_types[:8]) or 'none'}"
        )
        for item in data.automations[:40]
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
        "Use mini app tools when the user asks to create/build/generate an Aura mini app, revise/upgrade/change an installed Aura mini app, open an Aura mini app, log or check in a mini app item, show a streak, or query mini app records. "
        "Use create_automation when the user asks Aura to do something later, repeatedly, on a schedule, when a place is entered/left, or from device context. "
        "For multi-step automations, prefer automation_spec.flow.steps with clear step ids and names. Use action steps for device actions, condition steps for event/context checks, checkpoint steps when the flow should pause for a later resume/confirmation, and wait steps only when a real delay is required. Use retryPolicy with a modest backoffMillis for transient cross-app UI steps that may fail while screens settle. Keep the legacy actions array as a simple summary/fallback when possible. "
        "For cross-app automations, use open_app, wait_for_app, inspect_screen, wait_for_target, wait_for_text, scroll_until_target, tap_target, tap_text, tap_bounds, type_text, clear_text, scroll, swipe, press_back, and press_home steps. Prefer open_app with packageName from the installed apps list; open_app waits for the package when accessibility is enabled, and wait_for_app can explicitly guard later transitions. Then use inspect_screen for unknown screens, wait_for_target for stable viewId/contentDescription/className selectors, or wait_for_text for simple visible text before tap/type steps. Use scroll_until_target with maxScrolls when the target may be lower in a list or settings screen. Prefer stable selectors from screen inspection in this order: viewId, contentDescription, exact text, className plus occurrence. Add timeoutMillis for slow screens, maxNodes for bounded inspections, diagnosticMaxNodes for failure snapshots, and settleMillis after app transitions. Cross-app UI control requires the user to enable Aura's Accessibility Service. Direct sends, purchases, deletes, posts, payments, or irreversible actions must be in flow.steps with a checkpoint before the high-impact action; set metadata.riskLevel=high when a selector is risky even if the label is ambiguous. "
        "Automation actions must be permission-aware and user-safe: for messaging, prefer draft_message or eta_message with requireConfirmation true unless the user explicitly asks for direct SMS and provides the recipient address; then use direct_sms with requireConfirmation false. "
        "For a request like messaging a spouse when leaving work, create a geofence automation with transition exit, a reasonable radius, cooldownMillis near 18 hours for daily behavior, and an eta_message or direct_sms action whose template can use {{placeName}}, {{etaMinutes}}, {{etaDistanceKm}}, {{etaProvider}}, and {{etaConfidence}}. Include destinationLatitude, destinationLongitude, travelMode, averageSpeedKph, and needsEta=true metadata when the user has provided enough home/destination context. If exact coordinates or recipient address are missing, explain what is needed instead of inventing private details. "
        "When creating a mini app from chat, call create_mini_app with a professional mini_app_prompt that asks for runtime react unless the user explicitly requested native/declarative output, and captures the user's workflow, data model, local records, polished React UI, actions, and assistant intents. "
        "When revising an installed mini app from chat, call revise_mini_app with the target mini app and a specific revision_instruction. "
        "When blocking an app, prefer an exact package_name from the installed app list and choose the requested duration in minutes. "
        "If no duration is given, use 30 minutes. "
        f"Planning mode is {harness.planning_mode}. "
        "When planning mode is plan, include a concise user-visible plan in the reply before the final action summary. "
        f"Model routing: {harness.route_reason}. "
        "If a provider cannot use tools, return ONLY JSON with this shape: "
        '{"reply":"{neutral} short reply","actions":[{"type":"block_app","package_name":"exact.package","app_query":"fallback app name","duration_minutes":30},{"type":"create_automation","automation_spec":{"id":"","name":"Leave work ETA","description":"Drafts an ETA message when leaving work.","enabled":true,"trigger":{"type":"geofence","geofence":{"placeName":"Work","latitude":0.0,"longitude":0.0,"radiusMeters":150.0,"transition":"exit"}},"conditions":[],"actions":[{"type":"eta_message","title":"Send ETA","messageTemplate":"I just left {{placeName}}. My ETA is {{etaMinutes}} minutes.","recipientName":"Spouse","recipientAddress":"","requireConfirmation":true,"metadata":{}}],"flow":{"concurrencyPolicy":"skip_if_running","steps":[{"id":"send-eta","name":"Draft ETA","type":"action","action":{"type":"eta_message","title":"Send ETA","messageTemplate":"I just left {{placeName}}. My ETA is {{etaMinutes}} minutes.","recipientName":"Spouse","recipientAddress":"","requireConfirmation":true,"metadata":{}},"retryPolicy":{"maxAttempts":1,"backoffMillis":0},"continueOnFailure":false,"metadata":{}}]},"cooldownMillis":64800000,"createdBy":"assistant"}},{"type":"create_mini_app","mini_app_prompt":"professional app request","open_after_create":true},{"type":"revise_mini_app","mini_app_id":"id","mini_app_query":"name","revision_instruction":"specific requested app change"},{"type":"open_mini_app","mini_app_id":"id","mini_app_query":"name"},{"type":"create_mini_app_record","mini_app_id":"id","action_id":"action","record_type":"record","values":{"field":"value"}},{"type":"query_mini_app_records","mini_app_id":"id"}]}. '
        "No markdown, no emoji.\n\n"
        f"Local memories:\n{memories}\n\n"
        f"Local tasks:\n{todos}\n\n"
        f"Installed apps:\n{apps}\n\n"
        f"Installed Aura mini apps:\n{mini_apps}\n\n"
        f"Saved Aura automations:\n{automations}\n\n"
        f"Loaded file context:\n{format_context_snippets(harness.context_snippets)}\n\n"
        f"Available skill summaries:\n{format_skill_summaries(harness.skill_summaries)}\n\n"
        f"Activated skill details:\n{format_activated_skills(harness.activated_skills)}"
    )


def assistant_tool_definitions() -> list[dict[str, Any]]:
    tools = [
        {
            "name": "create_automation",
            "description": "Create a durable local Aura automation from a user request. The phone runtime validates permissions, stores it, restores triggers after reboot, and executes actions.",
            "parameters": {
                "type": "object",
                "properties": {
                    "automation_spec": {
                        "type": "object",
                        "description": "Typed automation spec. Use geofence for enter/exit place triggers, schedule for time triggers, manual for testable automations.",
                        "properties": {
                            "id": {"type": "string", "description": "Leave blank for a new automation."},
                            "name": {"type": "string"},
                            "description": {"type": "string"},
                            "enabled": {"type": "boolean"},
                            "trigger": {
                                "type": "object",
                                "properties": {
                                    "type": {"type": "string", "enum": ["geofence", "schedule", "manual"]},
                                    "geofence": {
                                        "type": "object",
                                        "properties": {
                                            "placeName": {"type": "string"},
                                            "latitude": {"type": "number"},
                                            "longitude": {"type": "number"},
                                            "radiusMeters": {"type": "number"},
                                            "transition": {"type": "string", "enum": ["enter", "exit"]},
                                        },
                                        "required": ["placeName", "latitude", "longitude", "radiusMeters", "transition"],
                                    },
                                    "schedule": {
                                        "type": "object",
                                        "properties": {
                                            "mode": {"type": "string", "enum": ["daily", "interval"]},
                                            "localTime": {"type": "string", "description": "HH:mm local time for daily schedules."},
                                            "intervalMinutes": {"type": "integer"},
                                            "daysOfWeek": {"type": "array", "items": {"type": "integer"}},
                                        },
                                    },
                                    "manual": {
                                        "type": "object",
                                        "properties": {"eventName": {"type": "string"}},
                                    },
                                },
                                "required": ["type"],
                            },
                            "conditions": {
                                "type": "array",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "type": {"type": "string"},
                                        "key": {"type": "string"},
                                        "operator": {"type": "string", "enum": ["exists", "equals", "not_equals", "contains"]},
                                        "value": {"type": "string"},
                                    },
                                    "required": ["type", "key", "operator"],
                                },
                            },
                            "actions": {
                                "type": "array",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                            "type": {"type": "string", "enum": ["notify", "draft_message", "eta_message", "direct_sms", "open_app", "wait_for_app", "tap_text", "tap_bounds", "type_text", "wait_for_text", "wait_for_target", "tap_target", "long_press_target", "clear_text", "scroll", "scroll_until_target", "swipe", "inspect_screen", "press_back", "press_home"]},
                                        "title": {"type": "string"},
                                        "messageTemplate": {"type": "string"},
                                        "recipientName": {"type": "string"},
                                        "recipientAddress": {"type": "string"},
                                        "requireConfirmation": {"type": "boolean"},
                                        "metadata": {
                                            "type": "object",
                                            "description": "String metadata for executors. ETA actions can use destinationLatitude, destinationLongitude, travelMode, averageSpeedKph, and needsEta=true. Cross-app actions use packageName/appQuery, text, targetText, contentDescription, viewId, className, occurrence, partialMatch, timeoutMillis, settleMillis, maxNodes, maxScrolls, includeDiagnostics, diagnosticMaxNodes, riskLevel, direction, or gesture bounds/points.",
                                            "additionalProperties": {"type": "string"},
                                        },
                                    },
                                    "required": ["type", "requireConfirmation"],
                                },
                            },
                            "flow": {
                                "type": "object",
                                "description": "Optional durable multi-step flow. Use this for ordered or resumable automations; legacy actions remain a simple fallback.",
                                "properties": {
                                    "concurrencyPolicy": {
                                        "type": "string",
                                        "enum": ["skip_if_running", "allow_parallel"],
                                        "description": "Use skip_if_running unless the user clearly wants overlapping runs.",
                                    },
                                    "steps": {
                                        "type": "array",
                                        "items": {
                                            "type": "object",
                                            "properties": {
                                                "id": {"type": "string", "description": "Stable kebab-case step id, such as check-context or send-message."},
                                                "name": {"type": "string"},
                                                "type": {"type": "string", "enum": ["action", "condition", "wait", "checkpoint"]},
                                                "action": {
                                                    "type": "object",
                                                    "properties": {
                                                        "type": {"type": "string", "enum": ["notify", "draft_message", "eta_message", "direct_sms", "open_app", "wait_for_app", "tap_text", "tap_bounds", "type_text", "wait_for_text", "wait_for_target", "tap_target", "long_press_target", "clear_text", "scroll", "scroll_until_target", "swipe", "inspect_screen", "press_back", "press_home"]},
                                                        "title": {"type": "string"},
                                                        "messageTemplate": {"type": "string"},
                                                        "recipientName": {"type": "string"},
                                                        "recipientAddress": {"type": "string"},
                                                        "requireConfirmation": {"type": "boolean"},
                                                        "metadata": {
                                                            "type": "object",
                                                            "description": "String metadata. Cross-app actions use packageName/appQuery, text, targetText, contentDescription, viewId, className, occurrence, partialMatch, timeoutMillis, settleMillis, maxNodes, maxScrolls, includeDiagnostics, diagnosticMaxNodes, riskLevel, direction, or gesture bounds/points.",
                                                            "additionalProperties": {"type": "string"},
                                                        },
                                                    },
                                                    "required": ["type", "requireConfirmation"],
                                                },
                                                "condition": {
                                                    "type": "object",
                                                    "properties": {
                                                        "type": {"type": "string"},
                                                        "key": {"type": "string"},
                                                        "operator": {"type": "string", "enum": ["exists", "equals", "not_equals", "contains"]},
                                                        "value": {"type": "string"},
                                                    },
                                                    "required": ["type", "key", "operator"],
                                                },
                                                "waitMillis": {"type": "integer"},
                                                "retryPolicy": {
                                                    "type": "object",
                                                    "properties": {
                                                        "maxAttempts": {"type": "integer"},
                                                        "backoffMillis": {"type": "integer"},
                                                    },
                                                },
                                                "continueOnFailure": {"type": "boolean"},
                                                "metadata": {
                                                    "type": "object",
                                                    "additionalProperties": {"type": "string"},
                                                },
                                            },
                                            "required": ["id", "type"],
                                        },
                                    },
                                },
                                "required": ["steps"],
                            },
                            "cooldownMillis": {"type": "integer"},
                            "createdBy": {"type": "string"},
                        },
                        "required": ["name", "enabled", "trigger", "actions"],
                    }
                },
                "required": ["automation_spec"],
                "additionalProperties": False,
            },
        },
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
            "name": "create_mini_app",
            "description": "Create, install, and optionally open a professional Aura mini app from a user request. Ask for a React runtime mini app unless the user explicitly requested native/declarative output.",
            "parameters": {
                "type": "object",
                "properties": {
                    "mini_app_prompt": {
                        "type": "string",
                        "description": "A concise but specific prompt describing the mini app to build. Include runtime react for normal assistant-built apps, plus workflow, data to track, polished React UI, local records, actions, and assistant intents.",
                    },
                    "open_after_create": {
                        "type": "boolean",
                        "description": "Whether Aura should open the new mini app after installing it. Defaults to true.",
                    },
                },
                "required": ["mini_app_prompt"],
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
            "name": "revise_mini_app",
            "description": "Revise, upgrade, or patch an installed Aura mini app while preserving its local records. Use this when the user asks to add fields, charts, actions, assistant intents, or workflow changes to an existing mini app.",
            "parameters": {
                "type": "object",
                "properties": {
                    "mini_app_id": {"type": "string", "description": "Exact installed mini app id when known."},
                    "mini_app_query": {"type": "string", "description": "Fallback mini app name query."},
                    "revision_instruction": {
                        "type": "string",
                        "description": "Specific change request for the existing mini app, including fields, screens, charts, actions, and assistant intents to add or adjust.",
                    },
                },
                "required": ["revision_instruction"],
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
    preferred_order = {
        "block_app": 0,
        "create_automation": 1,
        "create_mini_app": 2,
        "revise_mini_app": 3,
        "open_mini_app": 4,
        "create_mini_app_record": 5,
        "query_mini_app_records": 6,
    }
    return sorted(tools, key=lambda tool: preferred_order.get(tool["name"], 99))


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


def _image_data_url(data: ChatIn) -> Optional[str]:
    if not data.image_base64 or not data.image_mime_type:
        return None
    b64 = data.image_base64.strip()
    if not b64:
        return None
    if b64.startswith("data:"):
        return b64
    if "," in b64:
        b64 = b64.split(",", 1)[1]
    return f"data:{data.image_mime_type};base64,{b64}"


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
        mini_app_prompt=args.get("mini_app_prompt"),
        revision_instruction=args.get("revision_instruction"),
        open_after_create=args.get("open_after_create"),
        action_id=args.get("action_id"),
        record_type=args.get("record_type"),
        values=_coerce_values(args.get("values")),
        automation_spec=args.get("automation_spec") if isinstance(args.get("automation_spec"), dict) else None,
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
                mini_app_prompt=item.get("mini_app_prompt"),
                revision_instruction=item.get("revision_instruction"),
                open_after_create=item.get("open_after_create"),
                action_id=item.get("action_id"),
                record_type=item.get("record_type"),
                values=item.get("values") if isinstance(item.get("values"), dict) else None,
                automation_spec=item.get("automation_spec") if isinstance(item.get("automation_spec"), dict) else None,
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
    model = normalize_model_id("gemini", data.model)
    try:
        parts: list[dict[str, Any]] = [
            {
                "text": data.message if use_assistant_tools else f"{system_message}\n\nUser request:\n{data.message}"
            }
        ]
        payload: dict[str, Any] = {
            "contents": [
                {
                    "role": "user",
                    "parts": parts,
                }
            ],
        }
        image_base64 = data.image_base64
        image_mime_type = data.image_mime_type
        if image_base64 and image_mime_type:
            b64 = image_base64.strip()
            if "," in b64:
                b64 = b64.split(",", 1)[1]
            parts.append({
                "inlineData": {
                    "mimeType": image_mime_type,
                    "data": b64
                }
            })
        if use_assistant_tools:
            payload["systemInstruction"] = {"parts": [{"text": system_message}]}
            payload["tools"] = gemini_assistant_tools()
        response = requests.post(
            f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
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
        user_content = [
            {
                "type": "input_text",
                "text": data.message,
            }
        ]
        image_url = _image_data_url(data)
        if image_url:
            user_content.append(
                {
                    "type": "input_image",
                    "image_url": image_url,
                }
            )
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
                    "content": user_content,
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
        image_url = _image_data_url(data)
        user_content: Any = data.message
        if image_url:
            user_content = [
                {"type": "text", "text": data.message},
                {"type": "image_url", "image_url": {"url": image_url}},
            ]
        payload = {
            "model": data.model,
            "messages": [
                {"role": "system", "content": system_message},
                {"role": "user", "content": user_content},
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
