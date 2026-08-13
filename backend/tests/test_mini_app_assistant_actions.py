import json
from unittest.mock import MagicMock, patch

from fastapi.testclient import TestClient

from app.main import app
from app.core.security import get_current_user
from app.models.chat import ChatIn
from app.services.llm import (
    assistant_tool_definitions,
    call_gemini,
    call_openrouter,
    extract_openai_text,
    parse_tool_response,
)


def client():
    app.dependency_overrides[get_current_user] = lambda: {
        "id": "mini-app-assistant-test-user",
        "email": "mini@app.test",
        "role": "user",
    }
    return TestClient(app, raise_server_exceptions=False)


def teardown_function(_):
    app.dependency_overrides.pop(get_current_user, None)


def test_parse_mini_app_assistant_actions():
    reply, actions = parse_tool_response(
        json.dumps(
            {
                "reply": "{happy} Logged your workout.",
                "actions": [
                    {
                        "type": "create_mini_app_record",
                        "mini_app_id": "builtin.habit_tracker",
                        "action_id": "check_workout",
                        "record_type": "habit_checkin",
                        "values": {"habit": "Workout", "done": "true"},
                    },
                    {
                        "type": "query_mini_app_records",
                        "mini_app_query": "Habit Tracker",
                    },
                ],
            }
        )
    )

    assert reply == "{happy} Logged your workout."
    assert actions[0].type == "create_mini_app_record"
    assert actions[0].mini_app_id == "builtin.habit_tracker"
    assert actions[0].action_id == "check_workout"
    assert actions[0].values == {"habit": "Workout", "done": "true"}
    assert actions[1].type == "query_mini_app_records"
    assert actions[1].mini_app_query == "Habit Tracker"


def test_assistant_tool_registry_exposes_intended_tools():
    tools = assistant_tool_definitions()
    names = {tool["name"] for tool in tools}

    assert names == {
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
    block_app = next(tool for tool in tools if tool["name"] == "block_app")
    assert block_app["parameters"]["required"] == ["duration_minutes"]
    assert block_app["parameters"]["properties"]["duration_minutes"]["maximum"] == 1440
    create_mini_app = next(tool for tool in tools if tool["name"] == "create_mini_app")
    assert create_mini_app["parameters"]["required"] == ["mini_app_prompt"]
    assert "React runtime" in create_mini_app["description"]
    assert "required Aura home widget" in create_mini_app["description"]
    assert "runtime react" in create_mini_app["parameters"]["properties"]["mini_app_prompt"]["description"]
    assert "opens the full app when tapped" in create_mini_app["parameters"]["properties"]["mini_app_prompt"]["description"]
    revise_mini_app = next(tool for tool in tools if tool["name"] == "revise_mini_app")
    assert revise_mini_app["parameters"]["required"] == ["revision_instruction"]
    assert "preserving its local records" in revise_mini_app["description"]
    assert "Aura home widget" in revise_mini_app["description"]

    present_widget = next(tool for tool in tools if tool["name"] == "present_widget")
    widget_schema = present_widget["parameters"]["properties"]["widget"]
    assert present_widget["parameters"]["required"] == ["widget"]
    assert set(widget_schema["required"]) == {
        "kind",
        "title",
        "message",
        "actions",
        "presentation",
        "content_format",
        "risk",
        "priority",
        "expires_in_minutes",
    }
    assert widget_schema["properties"]["actions"]["maxItems"] == 2
    assert set(widget_schema["properties"]["risk"]["enum"]) == {"low", "medium", "high"}
    assert {"report", "meeting_notes"}.issubset(widget_schema["properties"]["kind"]["enum"])
    assert widget_schema["properties"]["content"]["maxLength"] == 60_000
    delegate_tasks = next(tool for tool in tools if tool["name"] == "delegate_tasks")
    calls = delegate_tasks["parameters"]["properties"]["calls"]
    assert calls["maxItems"] == 3
    assert set(calls["items"]["properties"]["agent"]["enum"]) == {
        "researcher",
        "planner",
        "reviewer",
    }


def test_parse_present_widget_assistant_action():
    reply, actions = parse_tool_response(
        json.dumps(
            {
                "reply": "{neutral} Lunch is ready to review.",
                "actions": [
                    {
                        "type": "present_widget",
                        "widget": {
                            "kind": "food_order",
                            "title": "Order lunch?",
                            "message": "Chole bhature from your usual place",
                            "details": ["₹240", "25–35 min"],
                            "actions": [
                                {
                                    "id": "review-order",
                                    "label": "Review order",
                                    "type": "assistant_message",
                                    "payload": {"message": "Review my lunch order"},
                                    "requires_confirmation": True,
                                }
                            ],
                            "risk": "high",
                            "priority": 80,
                            "expires_in_minutes": 30,
                            "dedupe_key": "lunch-today",
                        },
                    }
                ],
            }
        )
    )

    assert reply == "{neutral} Lunch is ready to review."
    assert actions[0].type == "present_widget"
    assert actions[0].widget.kind == "food_order"
    assert actions[0].widget.actions[0].requires_confirmation is True
    assert actions[0].widget.dedupe_key == "lunch-today"


def test_fullscreen_html_report_is_a_bounded_typed_surface():
    _, actions = parse_tool_response(
        json.dumps(
            {
                "reply": "The report is ready.",
                "actions": [
                    {
                        "type": "present_widget",
                        "widget": {
                            "kind": "report",
                            "title": "Weekly review",
                            "message": "Open the full report",
                            "actions": [],
                            "presentation": "fullscreen",
                            "content_format": "html",
                            "content": "<h1>Weekly review</h1><p>Three tasks completed.</p>",
                            "risk": "low",
                            "priority": 40,
                            "expires_in_minutes": 1440,
                        },
                    }
                ],
            }
        )
    )

    report = actions[0].widget
    assert report.kind == "report"
    assert report.presentation == "fullscreen"
    assert report.content_format == "html"


def test_html_is_rejected_outside_fullscreen_reports():
    reply, actions = parse_tool_response(
        json.dumps(
            {
                "reply": "Notes ready.",
                "actions": [
                    {
                        "type": "present_widget",
                        "widget": {
                            "kind": "meeting_notes",
                            "title": "Meeting notes",
                            "message": "Capture decisions",
                            "actions": [],
                            "presentation": "compact",
                            "content_format": "html",
                            "content": "<p>Unsafe shape</p>",
                            "risk": "low",
                            "priority": 20,
                            "expires_in_minutes": 60,
                        },
                    }
                ],
            }
        )
    )

    assert reply == "Notes ready."
    assert actions == []


def test_invalid_or_unknown_widget_actions_are_rejected_without_crashing():
    reply, actions = parse_tool_response(
        json.dumps(
            {
                "reply": "I made a widget.",
                "actions": [
                    {"type": "unknown_widget_tool"},
                    {
                        "type": "present_widget",
                        "widget": {
                            "kind": "food_order",
                            "title": "Order lunch?",
                            "message": "Review this order",
                            "actions": [
                                {
                                    "id": "order",
                                    "label": "Order",
                                    "type": "assistant_message",
                                    "payload": {},
                                    "requires_confirmation": False,
                                }
                            ],
                            "risk": "high",
                            "priority": 90,
                            "expires_in_minutes": 30,
                        },
                    },
                ],
            }
        )
    )

    assert reply == "I made a widget."
    assert actions == []


def test_invalid_provider_widget_call_is_preserved_for_repair():
    raw = extract_openai_text(
        {
            "output": [
                {
                    "type": "message",
                    "content": [{"type": "output_text", "text": "I made the widget."}],
                },
                {
                    "type": "function_call",
                    "name": "present_widget",
                    "arguments": json.dumps({"widget": {"kind": "food_order"}}),
                },
            ]
        }
    )

    reply, actions = parse_tool_response(raw)

    assert reply == "I made the widget."
    assert actions == []
    assert '"type": "present_widget"' in raw


def test_medium_risk_widget_confirmation_is_enforced_by_the_backend_contract():
    _, actions = parse_tool_response(
        json.dumps(
            {
                "reply": "Review this.",
                "actions": [
                    {
                        "type": "present_widget",
                        "widget": {
                            "kind": "confirmation",
                            "title": "Send update?",
                            "message": "Ask Aura to prepare the update",
                            "actions": [
                                {
                                    "id": "prepare",
                                    "label": "Prepare",
                                    "type": "assistant_message",
                                    "payload": {"message": "Prepare my project update"},
                                    "requires_confirmation": False,
                                }
                            ],
                            "risk": "medium",
                            "priority": 50,
                            "expires_in_minutes": 20,
                        },
                    }
                ],
            }
        )
    )

    assert actions[0].widget.actions[0].requires_confirmation is True


def test_model_authored_assistant_message_always_requires_confirmation():
    _, actions = parse_tool_response(
        json.dumps(
            {
                "reply": "Open the details.",
                "actions": [
                    {
                        "type": "present_widget",
                        "widget": {
                            "kind": "itinerary",
                            "title": "Day plan",
                            "message": "Three stops",
                            "actions": [
                                {
                                    "id": "details",
                                    "label": "See details",
                                    "type": "assistant_message",
                                    "payload": {"message": "Show my detailed itinerary"},
                                    "requires_confirmation": False,
                                }
                            ],
                            "risk": "low",
                            "priority": 20,
                            "expires_in_minutes": 60,
                        },
                    }
                ],
            }
        )
    )

    assert actions[0].widget.actions[0].requires_confirmation is True


def test_widget_actions_per_response_are_bounded():
    reply, actions = parse_tool_response(
        json.dumps(
            {
                "reply": "Here are the widgets.",
                "actions": [
                    {
                        "type": "present_widget",
                        "widget": {
                            "kind": "message",
                            "title": f"Widget {index}",
                            "message": "Bounded widget",
                            "actions": [],
                            "risk": "low",
                            "priority": index,
                            "expires_in_minutes": 30,
                        },
                    }
                    for index in range(8)
                ],
            }
        )
    )

    assert reply == "Here are the widgets."
    assert len(actions) == 4
    assert all(action.widget.dedupe_key.startswith("auto:") for action in actions)


def test_parse_create_mini_app_assistant_action():
    reply, actions = parse_tool_response(
        json.dumps(
            {
                "reply": "{excited} Creating that mini app.",
                "actions": [
                    {
                        "type": "create_mini_app",
                        "mini_app_prompt": "Build a professional study planner with tasks, streaks, history, and settings.",
                        "open_after_create": True,
                    }
                ],
            }
        )
    )

    assert reply == "{excited} Creating that mini app."
    assert actions[0].type == "create_mini_app"
    assert actions[0].mini_app_prompt.startswith("Build a professional study planner")
    assert actions[0].open_after_create is True


def test_parse_revise_mini_app_assistant_action():
    reply, actions = parse_tool_response(
        json.dumps(
            {
                "reply": "{thinking} Drafting that upgrade.",
                "actions": [
                    {
                        "type": "revise_mini_app",
                        "mini_app_id": "generated.gym",
                        "revision_instruction": "Add soreness tracking with a chart and voice logging intent.",
                    }
                ],
            }
        )
    )

    assert reply == "{thinking} Drafting that upgrade."
    assert actions[0].type == "revise_mini_app"
    assert actions[0].mini_app_id == "generated.gym"
    assert actions[0].revision_instruction.startswith("Add soreness")


def test_openai_function_calls_are_converted_to_chat_actions():
    raw = extract_openai_text(
        {
            "output": [
                {
                    "type": "message",
                    "content": [{"type": "output_text", "text": "{thinking} Blocking it now."}],
                },
                {
                    "type": "function_call",
                    "name": "block_app",
                    "arguments": json.dumps(
                        {
                            "package_name": "com.example.focus",
                            "app_query": "Focus",
                            "duration_minutes": 45,
                        }
                    ),
                },
            ]
        }
    )

    reply, actions = parse_tool_response(raw)
    assert reply == "{thinking} Blocking it now."
    assert actions[0].type == "block_app"
    assert actions[0].package_name == "com.example.focus"
    assert actions[0].duration_minutes == 45


@patch("app.services.llm.requests.post")
def test_gemini_chat_uses_tool_harness(mock_post):
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "candidates": [
            {
                "content": {
                    "parts": [
                        {"text": "{happy} Opening it."},
                        {
                            "functionCall": {
                                "name": "open_mini_app",
                                "args": {"mini_app_id": "builtin.habit_tracker"},
                            }
                        },
                    ]
                }
            }
        ]
    }
    mock_post.return_value = mock_response
    chat = ChatIn(message="open habit tracker", api_key="key", model="gemini/gemini-test")

    raw = call_gemini(chat, "system prompt", use_assistant_tools=True)
    request_url = mock_post.call_args.args[0]
    request_payload = mock_post.call_args.kwargs["json"]
    reply, actions = parse_tool_response(raw)

    assert request_url.endswith("/models/gemini-test:generateContent")
    assert request_payload["systemInstruction"]["parts"][0]["text"] == "system prompt"
    assert request_payload["contents"][0]["parts"][0]["text"] == "open habit tracker"
    assert request_payload["tools"][0]["functionDeclarations"][0]["name"] == "block_app"
    assert reply == "{happy} Opening it."
    assert actions[0].type == "open_mini_app"
    assert actions[0].mini_app_id == "builtin.habit_tracker"


@patch("app.services.llm.requests.post")
def test_openrouter_tool_calls_are_converted_to_chat_actions(mock_post):
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "choices": [
            {
                "message": {
                    "content": "{neutral} Saved it.",
                    "tool_calls": [
                        {
                            "function": {
                                "name": "create_mini_app_record",
                                "arguments": json.dumps(
                                    {
                                        "mini_app_query": "Habit Tracker",
                                        "record_type": "habit_checkin",
                                        "values": {"habit": "Workout", "done": True},
                                    }
                                ),
                            }
                        }
                    ],
                }
            }
        ]
    }
    mock_post.return_value = mock_response
    chat = ChatIn(message="log workout", api_key="key", model="openrouter-test")

    raw = call_openrouter(chat, "system prompt", use_assistant_tools=True)
    request_payload = mock_post.call_args.kwargs["json"]
    reply, actions = parse_tool_response(raw)

    assert request_payload["tools"][0]["function"]["name"] == "block_app"
    assert reply == "{neutral} Saved it."
    assert actions[0].type == "create_mini_app_record"
    assert actions[0].values == {"habit": "Workout", "done": "True"}


@patch("app.services.llm.requests.post")
def test_assistant_chat_repairs_claimed_action_without_tool_call(mock_post):
    first_response = MagicMock()
    first_response.status_code = 200
    first_response.json.return_value = {
        "candidates": [{"content": {"parts": [{"text": "Done, blocked it."}]}}]
    }
    repaired_response = MagicMock()
    repaired_response.status_code = 200
    repaired_response.json.return_value = {
        "candidates": [
            {
                "content": {
                    "parts": [
                        {"text": "{neutral} Blocking it now."},
                        {
                            "functionCall": {
                                "name": "block_app",
                                "args": {
                                    "package_name": "com.example.focus",
                                    "duration_minutes": 30,
                                },
                            }
                        },
                    ]
                }
            }
        ]
    }
    mock_post.side_effect = [first_response, repaired_response]

    response = client().post(
        "/api/assistant/chat",
        json={
            "message": "block Focus",
            "provider": "gemini",
            "api_key": "dummy",
            "model": "gemini-test",
            "apps": [{"label": "Focus", "package_name": "com.example.focus"}],
        },
    )

    assert response.status_code == 200
    assert mock_post.call_count == 2
    assert response.json()["reply"] == "{neutral} Blocking it now."
    assert response.json()["actions"][0]["type"] == "block_app"


@patch("app.services.llm.requests.post")
def test_assistant_chat_returns_gemini_mini_app_tool_actions(mock_post):
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "candidates": [
            {
                "content": {
                    "parts": [
                        {"text": "{happy} Logged it."},
                        {
                            "functionCall": {
                                "name": "create_mini_app_record",
                                "args": {
                                    "mini_app_id": "builtin.habit_tracker",
                                    "action_id": "check_workout",
                                    "record_type": "habit_checkin",
                                    "values": {"habit": "Workout", "done": True},
                                },
                            }
                        },
                    ]
                }
            }
        ]
    }
    mock_post.return_value = mock_response

    response = client().post(
        "/api/assistant/chat",
        json={
            "message": "log workout in habit tracker",
            "provider": "gemini",
            "api_key": "dummy",
            "model": "models/gemini-2.5-flash",
            "mini_apps": [
                {
                    "id": "builtin.habit_tracker",
                    "name": "Habit Tracker",
                    "intents": ["mark_workout_done"],
                    "actions": ["check_workout"],
                }
            ],
        },
    )

    assert response.status_code == 200
    assert mock_post.call_args.args[0].endswith("/models/gemini-2.5-flash:generateContent")
    action = response.json()["actions"][0]
    assert response.json()["reply"] == "{happy} Logged it."
    assert action["type"] == "create_mini_app_record"
    assert action["mini_app_id"] == "builtin.habit_tracker"
    assert action["action_id"] == "check_workout"
    assert action["values"] == {"habit": "Workout", "done": "True"}
