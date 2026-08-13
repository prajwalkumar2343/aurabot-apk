import json
import re
import logging
import requests
from fastapi import HTTPException
from dataclasses import dataclass
from typing import Any, Iterable, List, Optional, Tuple
from pydantic import ValidationError

from app.models.chat import (
    AURA_EMOTION_NAMES,
    DEFAULT_AURA_EMOTION,
    ChatActionOut,
    ChatIn,
)
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
    "present_widget",
    "delegate_tasks",
}
MAX_ASSISTANT_ACTIONS_PER_RESPONSE = 16
MAX_WIDGETS_PER_RESPONSE = 4
EMOTION_VALUES_TEXT = ", ".join(AURA_EMOTION_NAMES)


@dataclass(frozen=True)
class ParsedAssistantResponse:
    """Validated model response used at the harness boundary.

    ``parse_tool_response`` below remains as a two-value compatibility wrapper
    for existing callers; new harness paths should keep the emotion value.
    """

    reply: str
    actions: list[ChatActionOut]
    emotion: str = DEFAULT_AURA_EMOTION
    created_emotion: Optional[str] = None

AUTOMATION_ACTION_TYPES = [
    "notify",
    "draft_message",
    "eta_message",
    "direct_sms",
]

AUTOMATION_FLOW_STEP_TYPES = ["action", "condition", "wait", "checkpoint"]
AUTOMATION_CONCURRENCY_POLICIES = ["skip_if_running", "allow_parallel"]
AUTOMATION_MAX_RETRY_ATTEMPTS = 5
AUTOMATION_MAX_RETRY_BACKOFF_MILLIS = 30_000
AUTOMATION_MAX_FLOW_WAIT_MILLIS = 604_800_000
AUTOMATION_MAX_INTERVAL_MINUTES = 525_600
AUTOMATION_MAX_NAME_LENGTH = 120
AUTOMATION_MAX_DESCRIPTION_LENGTH = 2_000
AUTOMATION_MAX_ID_LENGTH = 128
AUTOMATION_MAX_RECIPIENT_ADDRESS_LENGTH = 320
AUTOMATION_MAX_TEXT_LENGTH = 4_096
AUTOMATION_MAX_METADATA_KEY_LENGTH = 64
AUTOMATION_MAX_METADATA_ENTRIES = 32
AUTOMATION_MAX_CONDITIONS = 20
AUTOMATION_MAX_ACTIONS = 20
AUTOMATION_MAX_FLOW_STEPS = 40


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
        "Return one JSON object with a short speech-ready reply, an emotion, and an actions array. "
        f"The emotion must be exactly one of: {EMOTION_VALUES_TEXT}. "
        "Choose the emotion that matches the feeling conveyed by your reply, not merely the user's wording. "
        "If none of those emotions fits, add a bounded field named created_emotion whose value starts exactly with "
        "create followed by one to six descriptive words, for example create dreamily curious or create tiny villain. "
        "Use created_emotion only for a new one-off eye feeling; never put create directives in actions. "
        "Do not put the emotion name in the spoken reply. Keep replies short, natural, plain text, and suitable for speech. "
        "Use the available tools when the user asks for an action Aura can perform locally. "
        "Use delegate_tasks only when independent specialist research, planning, or review will materially improve a multi-step request. Delegate at most three focused tasks. Subagents are reasoning-only and cannot perform device actions. Prefer fresh context; use fork only when the child needs the loaded project context, and use a stable session name only for work that should continue later. "
        "Do not claim an action has completed unless you request the matching tool. "
        "Use present_widget when a choice, reminder, itinerary, suggested order, progress update, meeting-notes control, approval, or report should remain visible on the Aura home canvas. Use compact or expanded presentation for glanceable tools. For a rich report, use kind report, presentation fullscreen, content_format html, and self-contained static HTML; scripts, remote assets, forms, and external navigation will be blocked by the phone. Widgets are proposals, not proof that an external action completed. Use assistant_message actions to continue through the normal assistant tool and permission flow, and always mark those actions as requiring confirmation because their message is model-authored. Mark payments, purchases, sends, deletes, and other consequential actions high risk and require confirmation. "
        "Use app blocking only when the user asks to block, restrict, pause, or limit an app. "
        "Use mini app tools when the user asks to create/build/generate an Aura mini app, revise/upgrade/change an installed Aura mini app, open an Aura mini app, log or check in a mini app item, show a streak, or query mini app records. "
        "Use create_automation when the user asks Aura to do something later, repeatedly, on a schedule, when a place is entered/left, or from device context. The phone always stores model-authored automations as new disabled drafts with a fresh local id; never claim the rule is armed or that an existing rule was changed. Tell the user to review permissions and explicitly enable the draft. "
        "For multi-step automations, prefer automation_spec.flow.steps with clear step ids and names. Use action steps for supported device actions, condition steps for event/context checks, checkpoint steps when the flow should pause for a later resume/confirmation, and wait steps only when a real delay is required. Keep each step shape exclusive: action steps include action only, condition steps include condition only, wait steps include waitMillis only, and checkpoint steps include only checkpoint metadata. Always use maxAttempts=1 for unconfirmed direct_sms because irreversible side effects must not be retried. Keep waitMillis at or below 604800000 (7 days); use a schedule trigger for longer delays. Keep the legacy actions array as a simple summary/fallback when possible. "
        "Automation actions must be permission-aware and user-safe: for messaging, prefer draft_message or eta_message with requireConfirmation true unless the user explicitly asks for direct SMS and provides the recipient address; then use direct_sms with requireConfirmation false. "
        "For a request like messaging a spouse when leaving work, create a geofence automation with transition exit, a reasonable radius, cooldownMillis near 18 hours for daily behavior, and an eta_message or direct_sms action whose template can use {{placeName}}, {{etaMinutes}}, {{etaDistanceKm}}, {{etaProvider}}, and {{etaConfidence}}. Include destinationLatitude, destinationLongitude, travelMode, averageSpeedKph, and needsEta=true metadata when the user has provided enough home/destination context. If exact coordinates or recipient address are missing, explain what is needed instead of inventing private details. "
        "When creating a mini app from chat, call create_mini_app with a professional mini_app_prompt that asks for runtime react unless the user explicitly requested native/declarative output, and captures the user's workflow, data model, local records, polished React UI, actions, assistant intents, and a required Aura home widget representing the app's main purpose. The widget must open the full mini app when tapped. "
        "When revising an installed mini app from chat, call revise_mini_app with the target mini app and a specific revision_instruction that preserves or improves its Aura home widget. "
        "When blocking an app, prefer an exact package_name from the installed app list and choose the requested duration in minutes. "
        "If no duration is given, use 30 minutes. "
        f"Planning mode is {harness.planning_mode}. "
        "When planning mode is plan, include a concise user-visible plan in the reply before the final action summary. "
        f"Model routing: {harness.route_reason}. "
        "If a provider cannot use tools, return ONLY JSON with this shape: "
        '{"reply":"Short speech-ready reply","emotion":"encouraging","created_emotion":null,"actions":[]}. '
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
            "name": "delegate_tasks",
            "description": "Delegate up to three independent reasoning tasks to bounded Aura specialists. Subagents have no device or external-action tools.",
            "parameters": {
                "type": "object",
                "properties": {
                    "calls": {
                        "type": "array",
                        "minItems": 1,
                        "maxItems": 3,
                        "items": {
                            "type": "object",
                            "properties": {
                                "agent": {
                                    "type": "string",
                                    "enum": ["researcher", "planner", "reviewer"],
                                },
                                "task": {"type": "string", "minLength": 1, "maxLength": 4000},
                                "context": {"type": "string", "enum": ["fresh", "fork"]},
                                "session": {
                                    "type": "string",
                                    "maxLength": 120,
                                    "pattern": "^[a-zA-Z0-9_.:-]+$",
                                },
                            },
                            "required": ["agent", "task"],
                            "additionalProperties": False,
                        },
                    }
                },
                "required": ["calls"],
                "additionalProperties": False,
            },
        },
        {
            "name": "present_widget",
            "description": "Present one temporary, typed surface on Aura's home canvas. Surfaces can be compact tools, expanded cards, or full-screen static HTML reports. This creates a durable user-visible proposal; it never proves an external action completed.",
            "parameters": {
                "type": "object",
                "properties": {
                    "widget": {
                        "type": "object",
                        "properties": {
                            "kind": {
                                "type": "string",
                                "enum": ["message", "confirmation", "itinerary", "food_order", "reminder", "progress", "report", "meeting_notes"],
                            },
                            "title": {"type": "string", "minLength": 1, "maxLength": 80},
                            "message": {"type": "string", "minLength": 1, "maxLength": 280},
                            "details": {
                                "type": "array",
                                "maxItems": 6,
                                "items": {"type": "string", "maxLength": 120},
                            },
                            "actions": {
                                "type": "array",
                                "maxItems": 2,
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "id": {
                                            "type": "string",
                                            "minLength": 1,
                                            "maxLength": 64,
                                            "pattern": "^[a-z0-9_-]+$",
                                        },
                                        "label": {"type": "string", "minLength": 1, "maxLength": 40},
                                        "type": {
                                            "type": "string",
                                            "enum": ["assistant_message", "open_app", "dismiss"],
                                        },
                                        "payload": {
                                            "type": "object",
                                            "maxProperties": 8,
                                            "additionalProperties": {"type": "string", "maxLength": 500},
                                        },
                                        "requires_confirmation": {"type": "boolean"},
                                    },
                                    "required": ["id", "label", "type", "requires_confirmation"],
                                    "additionalProperties": False,
                                },
                            },
                            "presentation": {
                                "type": "string",
                                "enum": ["compact", "expanded", "fullscreen"],
                            },
                            "content_format": {
                                "type": "string",
                                "enum": ["plain_text", "html"],
                            },
                            "content": {
                                "type": "string",
                                "maxLength": 60000,
                                "description": "Required for fullscreen surfaces. HTML must be static and self-contained.",
                            },
                            "risk": {"type": "string", "enum": ["low", "medium", "high"]},
                            "priority": {"type": "integer", "minimum": 0, "maximum": 100},
                            "expires_in_minutes": {
                                "type": "integer",
                                "minimum": 1,
                                "maximum": 10_080,
                            },
                            "dedupe_key": {"type": "string", "maxLength": 120},
                        },
                        "required": [
                            "kind",
                            "title",
                            "message",
                            "actions",
                            "presentation",
                            "content_format",
                            "risk",
                            "priority",
                            "expires_in_minutes",
                        ],
                        "additionalProperties": False,
                    }
                },
                "required": ["widget"],
                "additionalProperties": False,
            },
        },
        {
            "name": "create_automation",
            "description": "Author a complete deterministic Aura automation draft from a user request. The phone assigns a fresh id, validates it, stores it disabled, and requires explicit user review before arming triggers.",
            "parameters": {
                "type": "object",
                "properties": {
                    "automation_spec": {
                        "type": "object",
                        "description": "Typed automation spec. Use geofence for enter/exit place triggers, schedule for time triggers, manual for testable automations.",
                        "properties": {
                            "id": {
                                "type": "string",
                                "maxLength": AUTOMATION_MAX_ID_LENGTH,
                                "description": "Always leave blank. The phone assigns a fresh local id and never overwrites an existing automation through this tool.",
                            },
                            "name": {"type": "string", "minLength": 1, "maxLength": AUTOMATION_MAX_NAME_LENGTH},
                            "description": {"type": "string", "maxLength": AUTOMATION_MAX_DESCRIPTION_LENGTH},
                            "enabled": {"type": "boolean"},
                            "trigger": {
                                "type": "object",
                                "properties": {
                                    "type": {"type": "string", "enum": ["geofence", "schedule", "manual"]},
                                    "geofence": {
                                        "type": "object",
                                        "properties": {
                                            "placeName": {"type": "string", "minLength": 1, "maxLength": AUTOMATION_MAX_NAME_LENGTH},
                                            "latitude": {"type": "number", "minimum": -90, "maximum": 90},
                                            "longitude": {"type": "number", "minimum": -180, "maximum": 180},
                                            "radiusMeters": {"type": "number", "minimum": 50, "maximum": 10_000},
                                            "transition": {"type": "string", "enum": ["enter", "exit"]},
                                        },
                                        "required": ["placeName", "latitude", "longitude", "radiusMeters", "transition"],
                                    },
                                    "schedule": {
                                        "type": "object",
                                        "properties": {
                                            "mode": {"type": "string", "enum": ["daily", "interval"]},
                                            "localTime": {
                                                "type": "string",
                                                "pattern": "^(?:[01]\\d|2[0-3]):[0-5]\\d$",
                                                "description": "HH:mm local time for daily schedules.",
                                            },
                                            "intervalMinutes": {
                                                "type": "integer",
                                                "minimum": 1,
                                                "maximum": AUTOMATION_MAX_INTERVAL_MINUTES,
                                            },
                                            "daysOfWeek": {
                                                "type": "array",
                                                "maxItems": 7,
                                                "items": {"type": "integer", "minimum": 1, "maximum": 7},
                                            },
                                        },
                                    },
                                    "manual": {
                                        "type": "object",
                                        "properties": {
                                            "eventName": {
                                                "type": "string",
                                                "minLength": 1,
                                                "maxLength": AUTOMATION_MAX_ID_LENGTH,
                                            }
                                        },
                                    },
                                },
                                "required": ["type"],
                            },
                            "conditions": {
                                "type": "array",
                                "maxItems": AUTOMATION_MAX_CONDITIONS,
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "type": {"type": "string", "maxLength": AUTOMATION_MAX_ID_LENGTH},
                                        "key": {"type": "string", "minLength": 1, "maxLength": AUTOMATION_MAX_METADATA_KEY_LENGTH},
                                        "operator": {"type": "string", "enum": ["exists", "equals", "not_equals", "contains"]},
                                        "value": {"type": "string", "maxLength": AUTOMATION_MAX_TEXT_LENGTH},
                                    },
                                    "required": ["type", "key", "operator"],
                                },
                            },
                            "actions": {
                                "type": "array",
                                "maxItems": AUTOMATION_MAX_ACTIONS,
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "type": {"type": "string", "enum": AUTOMATION_ACTION_TYPES},
                                        "title": {"type": "string", "maxLength": AUTOMATION_MAX_NAME_LENGTH},
                                        "messageTemplate": {"type": "string", "maxLength": AUTOMATION_MAX_TEXT_LENGTH},
                                        "recipientName": {"type": "string", "maxLength": AUTOMATION_MAX_NAME_LENGTH},
                                        "recipientAddress": {"type": "string", "maxLength": AUTOMATION_MAX_RECIPIENT_ADDRESS_LENGTH},
                                        "requireConfirmation": {"type": "boolean"},
                                        "metadata": {
                                            "type": "object",
                                            "maxProperties": AUTOMATION_MAX_METADATA_ENTRIES,
                                            "description": "String metadata for executors. ETA actions can use destinationLatitude, destinationLongitude, travelMode, averageSpeedKph, and needsEta=true.",
                                            "additionalProperties": {"type": "string", "maxLength": AUTOMATION_MAX_TEXT_LENGTH},
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
                                        "enum": AUTOMATION_CONCURRENCY_POLICIES,
                                        "description": "Use skip_if_running unless the user clearly wants overlapping runs.",
                                    },
                                    "steps": {
                                        "type": "array",
                                        "maxItems": AUTOMATION_MAX_FLOW_STEPS,
                                        "items": {
                                            "type": "object",
                                            "description": "Exclusive step shape: action steps use action only, condition steps use condition only, wait steps use waitMillis only, and checkpoint steps use checkpoint metadata only.",
                                            "properties": {
                                                "id": {
                                                    "type": "string",
                                                    "minLength": 1,
                                                    "maxLength": AUTOMATION_MAX_ID_LENGTH,
                                                    "description": "Stable kebab-case step id, such as check-context or send-message.",
                                                },
                                                "name": {"type": "string", "maxLength": AUTOMATION_MAX_NAME_LENGTH},
                                                "type": {"type": "string", "enum": AUTOMATION_FLOW_STEP_TYPES},
                                                "action": {
                                                    "type": "object",
                                                    "properties": {
                                                        "type": {"type": "string", "enum": AUTOMATION_ACTION_TYPES},
                                                        "title": {"type": "string", "maxLength": AUTOMATION_MAX_NAME_LENGTH},
                                                        "messageTemplate": {"type": "string", "maxLength": AUTOMATION_MAX_TEXT_LENGTH},
                                                        "recipientName": {"type": "string", "maxLength": AUTOMATION_MAX_NAME_LENGTH},
                                                        "recipientAddress": {"type": "string", "maxLength": AUTOMATION_MAX_RECIPIENT_ADDRESS_LENGTH},
                                                        "requireConfirmation": {"type": "boolean"},
                                                        "metadata": {
                                                            "type": "object",
                                                            "maxProperties": AUTOMATION_MAX_METADATA_ENTRIES,
                                                            "description": "String metadata for supported executors and ETA calculations.",
                                                            "additionalProperties": {"type": "string", "maxLength": AUTOMATION_MAX_TEXT_LENGTH},
                                                        },
                                                    },
                                                    "required": ["type", "requireConfirmation"],
                                                },
                                                "condition": {
                                                    "type": "object",
                                                    "properties": {
                                                        "type": {"type": "string", "maxLength": AUTOMATION_MAX_ID_LENGTH},
                                                        "key": {"type": "string", "minLength": 1, "maxLength": AUTOMATION_MAX_METADATA_KEY_LENGTH},
                                                        "operator": {"type": "string", "enum": ["exists", "equals", "not_equals", "contains"]},
                                                        "value": {"type": "string", "maxLength": AUTOMATION_MAX_TEXT_LENGTH},
                                                    },
                                                    "required": ["type", "key", "operator"],
                                                },
                                                "waitMillis": {
                                                    "type": "integer",
                                                    "minimum": 1,
                                                    "maximum": AUTOMATION_MAX_FLOW_WAIT_MILLIS,
                                                },
                                                "retryPolicy": {
                                                    "type": "object",
                                                    "properties": {
                                                        "maxAttempts": {
                                                            "type": "integer",
                                                            "minimum": 1,
                                                            "maximum": AUTOMATION_MAX_RETRY_ATTEMPTS,
                                                        },
                                                        "backoffMillis": {
                                                            "type": "integer",
                                                            "minimum": 0,
                                                            "maximum": AUTOMATION_MAX_RETRY_BACKOFF_MILLIS,
                                                        },
                                                    },
                                                },
                                                "continueOnFailure": {"type": "boolean"},
                                                "metadata": {
                                                    "type": "object",
                                                    "maxProperties": AUTOMATION_MAX_METADATA_ENTRIES,
                                                    "additionalProperties": {"type": "string", "maxLength": AUTOMATION_MAX_TEXT_LENGTH},
                                                },
                                            },
                                            "required": ["id", "type"],
                                        },
                                    },
                                },
                                "required": ["steps"],
                            },
                            "cooldownMillis": {"type": "integer", "minimum": 0},
                            "createdBy": {"type": "string", "maxLength": AUTOMATION_MAX_ID_LENGTH},
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
            "description": "Create, install, and optionally open a professional Aura mini app with a required Aura home widget. Ask for a React runtime mini app unless the user explicitly requested native/declarative output.",
            "parameters": {
                "type": "object",
                "properties": {
                    "mini_app_prompt": {
                        "type": "string",
                        "description": "A concise but specific prompt describing the mini app to build. Include runtime react for normal assistant-built apps, plus workflow, data to track, polished React UI, local records, actions, assistant intents, and a required Aura home widget that represents the app's main purpose and opens the full app when tapped.",
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
            "description": "Revise, upgrade, or patch an installed Aura mini app while preserving its local records and Aura home widget. Use this when the user asks to add fields, charts, actions, assistant intents, widget changes, or workflow changes to an existing mini app.",
            "parameters": {
                "type": "object",
                "properties": {
                    "mini_app_id": {"type": "string", "description": "Exact installed mini app id when known."},
                    "mini_app_query": {"type": "string", "description": "Fallback mini app name query."},
                    "revision_instruction": {
                        "type": "string",
                        "description": "Specific change request for the existing mini app, including fields, screens, charts, actions, assistant intents, and Aura home widget behavior to add, preserve, or adjust.",
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
        "present_widget": 7,
        "delegate_tasks": 8,
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
    if name == "present_widget" and not isinstance(args.get("widget"), dict):
        return None
    try:
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
            widget=args.get("widget") if isinstance(args.get("widget"), dict) else None,
            calls=args.get("calls") if isinstance(args.get("calls"), list) else None,
        )
    except ValidationError:
        logger.warning("Rejected invalid assistant tool call", extra={"tool_name": name})
        return None


def normalize_emotion(value: Any, reply: str = "") -> str:
    candidate = str(value or "").strip().lower()
    if candidate in AURA_EMOTION_NAMES:
        return candidate
    tag = re.search(r"\{([a-zA-Z0-9_-]+)\}", reply)
    tagged = tag.group(1).lower() if tag else ""
    return tagged if tagged in AURA_EMOTION_NAMES else DEFAULT_AURA_EMOTION


CREATED_EMOTION_PATTERN = re.compile(
    r"(?im)(?:^|\n)\s*create\s+([a-z][a-z0-9 _-]{1,63})\s*(?=\n|$)"
)


def normalize_created_emotion(
    value: Any = None, reply: str = ""
) -> tuple[str, Optional[str]]:
    """Extract a bounded ``create <emotion>`` directive and remove it from speech."""

    candidates = []
    if value is not None and str(value).strip().lower().startswith("create "):
        candidates.append(str(value).strip())
    match = CREATED_EMOTION_PATTERN.search(reply)
    if match:
        candidates.append(f"create {match.group(1).strip()}")
    for candidate in candidates:
        if candidate.lower().startswith("create "):
            candidate = candidate[7:].strip()
        words = re.findall(r"[a-zA-Z0-9_-]+", candidate.lower())
        if not words or len(words) > 6:
            continue
        label = " ".join(words).strip()
        if len(label) < 2 or len(label) > 64:
            continue
        cleaned_reply = CREATED_EMOTION_PATTERN.sub("", reply).strip()
        cleaned_reply = re.sub(r"\n{3,}", "\n\n", cleaned_reply)
        return cleaned_reply, f"create {label}"
    return reply.strip(), None


def _tool_response(
    reply: str, actions: list[ChatActionOut], emotion: Any = None
) -> str:
    actions = _bounded_actions(actions)
    structured = _json_payload(reply)
    normalized_reply = (
        str(structured.get("reply") or "").strip()
        if structured is not None
        else reply.strip()
    ) or "{neutral} Done."
    resolved_emotion = emotion
    created_value = None
    if resolved_emotion is None and structured is not None:
        resolved_emotion = structured.get("emotion")
        created_value = structured.get("created_emotion") or structured.get("create_emotion")
        if created_value is None and str(resolved_emotion or "").lower().startswith("create "):
            created_value = resolved_emotion
    normalized_reply, created_value = normalize_created_emotion(
        created_value, normalized_reply
    )
    return json.dumps(
        {
            "reply": normalized_reply,
            "emotion": normalize_emotion(resolved_emotion, normalized_reply),
            "created_emotion": created_value,
            "actions": [action.model_dump(exclude_none=True) for action in actions],
        }
    )


def _bounded_actions(actions: list[ChatActionOut]) -> list[ChatActionOut]:
    bounded: list[ChatActionOut] = []
    widget_count = 0
    for action in actions:
        if len(bounded) >= MAX_ASSISTANT_ACTIONS_PER_RESPONSE:
            break
        if action.type == "present_widget":
            if widget_count >= MAX_WIDGETS_PER_RESPONSE:
                continue
            widget_count += 1
        bounded.append(action)
    return bounded


def _invalid_tool_response(reply: str, tool_names: list[str]) -> str:
    normalized_reply = reply.strip() or "{neutral} I need to correct that action."
    return json.dumps(
        {
            "reply": normalized_reply,
            "emotion": normalize_emotion(None, normalized_reply),
            "actions": [
                {"type": name}
                for name in tool_names[:MAX_ASSISTANT_ACTIONS_PER_RESPONSE]
            ],
        }
    )


def _json_payload(raw: str) -> Optional[dict[str, Any]]:
    text = raw.strip()
    try:
        payload = json.loads(text)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", text, flags=re.DOTALL)
        if not match:
            return None
        try:
            payload = json.loads(match.group(0))
        except json.JSONDecodeError:
            return None
    return payload if isinstance(payload, dict) else None


def parse_assistant_response(raw: str) -> ParsedAssistantResponse:
    text = raw.strip()
    payload = _json_payload(text)
    if payload is None:
        reply, created_emotion = normalize_created_emotion(None, text)
        return ParsedAssistantResponse(
            reply=reply,
            actions=[],
            emotion=normalize_emotion(None, reply),
            created_emotion=created_emotion,
        )

    reply = str(payload.get("reply") or "").strip() or "Done."
    created_value = payload.get("created_emotion") or payload.get("create_emotion")
    if created_value is None and str(payload.get("emotion") or "").lower().startswith("create "):
        created_value = payload.get("emotion")
    reply, created_emotion = normalize_created_emotion(created_value, reply)
    actions: list[ChatActionOut] = []
    for item in payload.get("actions") or []:
        if not isinstance(item, dict):
            continue
        action = _action_from_tool_call(str(item.get("type") or "").strip(), item)
        if action is not None:
            actions.append(action)
    return ParsedAssistantResponse(
        reply=reply,
        actions=_bounded_actions(actions),
        emotion=normalize_emotion(payload.get("emotion"), reply),
        created_emotion=created_emotion,
    )


def parse_tool_response(raw: str) -> Tuple[str, List[ChatActionOut]]:
    """Compatibility parser for existing callers that do not need emotion."""

    parsed = parse_assistant_response(raw)
    return parsed.reply, parsed.actions

def extract_openai_text(payload: dict) -> str:
    output = payload.get("output", [])
    text_parts: List[str] = []
    actions: list[ChatActionOut] = []
    invalid_tool_names: list[str] = []
    for item in output:
        if item.get("type") == "function_call":
            tool_name = item.get("name", "")
            action = _action_from_tool_call(tool_name, item.get("arguments"))
            if action:
                actions.append(action)
            elif tool_name in ASSISTANT_TOOL_NAMES:
                invalid_tool_names.append(tool_name)
        for content in item.get("content", []):
            if content.get("type") == "output_text" and content.get("text"):
                text_parts.append(content["text"])
    if text_parts:
        text = "\n".join(text_parts).strip()
        if invalid_tool_names:
            return _invalid_tool_response(text, invalid_tool_names)
        return _tool_response(text, actions) if actions else text
    if invalid_tool_names:
        return _invalid_tool_response("", invalid_tool_names)
    if actions:
        return _tool_response("{neutral} Done.", actions)
    raise HTTPException(status_code=502, detail="OpenAI response did not include text output")

def _extract_gemini_text(payload: dict) -> str:
    candidates = payload.get("candidates") or []
    parts = candidates[0].get("content", {}).get("parts", []) if candidates else []
    text_parts: list[str] = []
    actions: list[ChatActionOut] = []
    invalid_tool_names: list[str] = []
    for part in parts:
        if part.get("text"):
            text_parts.append(part["text"])
        function_call = part.get("functionCall") or part.get("function_call")
        if function_call:
            tool_name = function_call.get("name", "")
            action = _action_from_tool_call(tool_name, function_call.get("args", {}))
            if action:
                actions.append(action)
            elif tool_name in ASSISTANT_TOOL_NAMES:
                invalid_tool_names.append(tool_name)
    text = "".join(text_parts).strip()
    if text:
        if invalid_tool_names:
            return _invalid_tool_response(text, invalid_tool_names)
        return _tool_response(text, actions) if actions else text
    if invalid_tool_names:
        return _invalid_tool_response("", invalid_tool_names)
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
    invalid_tool_names: list[str] = []
    for tool_call in message.get("tool_calls") or []:
        function = tool_call.get("function", {})
        tool_name = function.get("name", "")
        action = _action_from_tool_call(tool_name, function.get("arguments"))
        if action:
            actions.append(action)
        elif tool_name in ASSISTANT_TOOL_NAMES:
            invalid_tool_names.append(tool_name)
    if invalid_tool_names:
        return _invalid_tool_response(text, invalid_tool_names)
    if actions:
        return _tool_response(text or "{neutral} Done.", actions)
    if not text:
        raise HTTPException(status_code=502, detail="OpenRouter response did not include text")
    return text
