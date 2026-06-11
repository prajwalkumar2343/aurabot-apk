import json

from app.services.llm import assistant_tool_definitions, parse_tool_response


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
