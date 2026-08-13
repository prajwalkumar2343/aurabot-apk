import json

from app.models.chat import ChatIn
from app.services.llm import (
    AUTOMATION_ACTION_TYPES,
    AUTOMATION_MAX_ACTIONS,
    AUTOMATION_MAX_CONDITIONS,
    AUTOMATION_MAX_DESCRIPTION_LENGTH,
    AUTOMATION_MAX_FLOW_WAIT_MILLIS,
    AUTOMATION_MAX_FLOW_STEPS,
    AUTOMATION_MAX_ID_LENGTH,
    AUTOMATION_MAX_INTERVAL_MINUTES,
    AUTOMATION_MAX_METADATA_ENTRIES,
    AUTOMATION_MAX_NAME_LENGTH,
    AUTOMATION_MAX_RECIPIENT_ADDRESS_LENGTH,
    AUTOMATION_MAX_RETRY_ATTEMPTS,
    AUTOMATION_MAX_RETRY_BACKOFF_MILLIS,
    AUTOMATION_MAX_TEXT_LENGTH,
    assistant_tool_definitions,
    build_system_message,
    parse_tool_response,
)


def test_parse_create_automation_action_preserves_spec():
    reply, actions = parse_tool_response(
        json.dumps(
            {
                "reply": "{happy} I can set that up.",
                "actions": [
                    {
                        "type": "create_automation",
                        "automation_spec": {
                            "id": "",
                            "name": "Leave work ETA",
                            "enabled": True,
                            "trigger": {
                                "type": "geofence",
                                "geofence": {
                                    "placeName": "Work",
                                    "latitude": 12.9716,
                                    "longitude": 77.5946,
                                    "radiusMeters": 150,
                                    "transition": "exit",
                                },
                            },
                            "conditions": [],
                            "actions": [
                                {
                                    "type": "eta_message",
                                    "title": "Send ETA",
                                    "messageTemplate": "I left {{placeName}}. ETA {{etaMinutes}} minutes.",
                                    "recipientName": "Wife",
                                    "recipientAddress": "",
                                    "requireConfirmation": True,
                                    "metadata": {
                                        "destinationLatitude": "12.9352",
                                        "destinationLongitude": "77.6245",
                                        "averageSpeedKph": "28",
                                    },
                                }
                            ],
                            "cooldownMillis": 64800000,
                            "createdBy": "assistant",
                        },
                    }
                ],
            }
        )
    )

    assert reply == "{happy} I can set that up."
    assert actions[0].type == "create_automation"
    assert actions[0].automation_spec["name"] == "Leave work ETA"
    assert actions[0].automation_spec["trigger"]["geofence"]["transition"] == "exit"


def test_create_automation_tool_schema_is_available():
    tools = assistant_tool_definitions()
    names = {tool["name"] for tool in tools}

    assert "create_automation" in names
    create_automation = next(tool for tool in tools if tool["name"] == "create_automation")
    assert create_automation["parameters"]["required"] == ["automation_spec"]


def test_create_automation_tool_schema_supports_flow_steps():
    tools = assistant_tool_definitions()
    create_automation = next(tool for tool in tools if tool["name"] == "create_automation")

    spec = create_automation["parameters"]["properties"]["automation_spec"]
    flow = spec["properties"]["flow"]
    step = flow["properties"]["steps"]["items"]

    assert flow["properties"]["concurrencyPolicy"]["enum"] == ["skip_if_running", "allow_parallel"]
    assert step["properties"]["type"]["enum"] == ["action", "condition", "wait", "checkpoint"]
    assert "Exclusive step shape" in step["description"]
    assert "retryPolicy" in step["properties"]


def test_create_automation_tool_schema_reuses_action_type_enum():
    tools = assistant_tool_definitions()
    create_automation = next(tool for tool in tools if tool["name"] == "create_automation")

    spec = create_automation["parameters"]["properties"]["automation_spec"]
    top_level_action_types = spec["properties"]["actions"]["items"]["properties"]["type"]["enum"]
    flow_action_types = spec["properties"]["flow"]["properties"]["steps"]["items"]["properties"]["action"]["properties"]["type"]["enum"]

    assert top_level_action_types == AUTOMATION_ACTION_TYPES
    assert flow_action_types == AUTOMATION_ACTION_TYPES
    assert top_level_action_types == flow_action_types


def test_create_automation_tool_schema_bounds_flow_retries_and_waits():
    tools = assistant_tool_definitions()
    create_automation = next(tool for tool in tools if tool["name"] == "create_automation")

    spec = create_automation["parameters"]["properties"]["automation_spec"]
    step = spec["properties"]["flow"]["properties"]["steps"]["items"]
    wait_millis = step["properties"]["waitMillis"]
    retry = step["properties"]["retryPolicy"]["properties"]

    assert wait_millis["minimum"] == 1
    assert wait_millis["maximum"] == AUTOMATION_MAX_FLOW_WAIT_MILLIS
    assert retry["maxAttempts"] == {
        "type": "integer",
        "minimum": 1,
        "maximum": AUTOMATION_MAX_RETRY_ATTEMPTS,
    }
    assert retry["backoffMillis"] == {
        "type": "integer",
        "minimum": 0,
        "maximum": AUTOMATION_MAX_RETRY_BACKOFF_MILLIS,
    }


def test_create_automation_tool_schema_bounds_spec_resources():
    create_automation = next(tool for tool in assistant_tool_definitions() if tool["name"] == "create_automation")
    spec = create_automation["parameters"]["properties"]["automation_spec"]["properties"]
    schedule = spec["trigger"]["properties"]["schedule"]["properties"]
    action = spec["actions"]["items"]["properties"]
    step = spec["flow"]["properties"]["steps"]["items"]["properties"]
    flow_action = step["action"]["properties"]

    assert spec["id"]["maxLength"] == AUTOMATION_MAX_ID_LENGTH
    assert spec["name"]["maxLength"] == AUTOMATION_MAX_NAME_LENGTH
    assert spec["description"]["maxLength"] == AUTOMATION_MAX_DESCRIPTION_LENGTH
    assert spec["conditions"]["maxItems"] == AUTOMATION_MAX_CONDITIONS
    assert spec["actions"]["maxItems"] == AUTOMATION_MAX_ACTIONS
    assert spec["flow"]["properties"]["steps"]["maxItems"] == AUTOMATION_MAX_FLOW_STEPS
    assert schedule["intervalMinutes"]["maximum"] == AUTOMATION_MAX_INTERVAL_MINUTES
    assert schedule["daysOfWeek"]["maxItems"] == 7
    assert schedule["daysOfWeek"]["items"] == {"type": "integer", "minimum": 1, "maximum": 7}
    assert action["messageTemplate"]["maxLength"] == AUTOMATION_MAX_TEXT_LENGTH
    assert action["recipientAddress"]["maxLength"] == AUTOMATION_MAX_RECIPIENT_ADDRESS_LENGTH
    assert action["metadata"]["maxProperties"] == AUTOMATION_MAX_METADATA_ENTRIES
    assert action["metadata"]["additionalProperties"]["maxLength"] == AUTOMATION_MAX_TEXT_LENGTH
    assert flow_action["metadata"]["maxProperties"] == AUTOMATION_MAX_METADATA_ENTRIES
    assert step["metadata"]["maxProperties"] == AUTOMATION_MAX_METADATA_ENTRIES


def test_system_prompt_forbids_retries_for_irreversible_automation_actions():
    prompt = build_system_message(ChatIn(message="", api_key="", model="gemini-test"))

    assert "Always use maxAttempts=1 for unconfirmed direct_sms" in prompt
    assert "Accessibility Service" not in prompt
    assert "open_app" not in prompt
    assert "tap_target" not in prompt


def test_system_prompt_keeps_llm_authored_automations_as_new_disabled_drafts():
    prompt = build_system_message(ChatIn(message="", api_key="", model="gemini-test"))
    create_automation = next(tool for tool in assistant_tool_definitions() if tool["name"] == "create_automation")
    id_schema = create_automation["parameters"]["properties"]["automation_spec"]["properties"]["id"]

    assert "new disabled drafts with a fresh local id" in prompt
    assert "explicitly enable the draft" in prompt
    assert "Always leave blank" in id_schema["description"]


def test_create_automation_tool_schema_excludes_retired_cross_app_actions():
    tools = assistant_tool_definitions()
    create_automation = next(tool for tool in tools if tool["name"] == "create_automation")

    spec = create_automation["parameters"]["properties"]["automation_spec"]
    action_types = spec["properties"]["flow"]["properties"]["steps"]["items"]["properties"]["action"]["properties"]["type"]["enum"]

    assert action_types == ["notify", "draft_message", "eta_message", "direct_sms"]
