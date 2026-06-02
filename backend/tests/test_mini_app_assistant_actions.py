import json
from unittest.mock import MagicMock, patch

from app.models.chat import ChatIn
from app.services.llm import (
    assistant_tool_definitions,
    call_gemini,
    call_openrouter,
    extract_openai_text,
    parse_tool_response,
)


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
        "open_mini_app",
        "create_mini_app_record",
        "query_mini_app_records",
    }
    block_app = next(tool for tool in tools if tool["name"] == "block_app")
    assert block_app["parameters"]["required"] == ["duration_minutes"]
    assert block_app["parameters"]["properties"]["duration_minutes"]["maximum"] == 1440


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
    chat = ChatIn(message="open habit tracker", api_key="key", model="gemini-test")

    raw = call_gemini(chat, "system prompt", use_assistant_tools=True)
    request_payload = mock_post.call_args.kwargs["json"]
    reply, actions = parse_tool_response(raw)

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
