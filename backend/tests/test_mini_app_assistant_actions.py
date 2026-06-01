import json

from app.services.llm import parse_tool_response


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
