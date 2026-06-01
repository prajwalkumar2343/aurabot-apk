import json
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture
def client():
    return TestClient(app, raise_server_exceptions=False)


def valid_bundle():
    return {
        "id": "generated.habits",
        "metadata": {"name": "Workout Water", "description": "Track workouts and water", "category": "Wellness"},
        "icon": {"type": "initial", "value": "W", "background": "#16A34A"},
        "dataSchema": {"recordType": "habit_checkin", "fields": [{"name": "habit", "type": "text", "required": True}]},
        "actions": [{"id": "check_workout", "type": "create_record", "recordType": "habit_checkin", "values": {"habit": "Workout"}}],
        "assistantIntents": [{"name": "mark_workout_done", "utterances": ["mark workout done"], "actionId": "check_workout"}],
        "screens": [
            {
                "id": "dashboard",
                "title": "Today",
                "components": [
                    {"type": "dashboard_block", "title": "Momentum", "metric": "today_count"},
                    {"type": "quick_action_grid", "title": "Check in", "items": [{"label": "Workout", "actionId": "check_workout"}]},
                    {"type": "timeline", "title": "History", "source": "records"},
                ],
            }
        ],
        "capabilities": ["local_storage", "assistant_actions"],
    }


def build_payload(prompt="make me a habit tracker for workouts and water"):
    return {"prompt": prompt, "provider": "gemini", "api_key": "test", "model": "gemini-test"}


def test_build_mini_app_validates_llm_bundle(client):
    with patch("app.api.mini_apps.call_builder_llm", return_value=json.dumps(valid_bundle())):
        response = client.post("/api/mini-apps/build", json=build_payload())

    assert response.status_code == 200
    data = response.json()
    assert data["bundle"]["id"] == "generated.habits"
    assert data["bundle"]["screens"][0]["components"][1]["type"] == "quick_action_grid"


def test_build_mini_app_rejects_empty_prompt(client):
    response = client.post("/api/mini-apps/build", json=build_payload("   "))

    assert response.status_code == 400


def test_build_mini_app_rejects_malformed_json(client):
    with patch("app.api.mini_apps.call_builder_llm", return_value="not json"):
        response = client.post("/api/mini-apps/build", json=build_payload())

    assert response.status_code == 422


def test_build_mini_app_rejects_unsupported_component(client):
    bundle = valid_bundle()
    bundle["screens"][0]["components"][0]["type"] = "webview"
    with patch("app.api.mini_apps.call_builder_llm", return_value=json.dumps(bundle)):
        response = client.post("/api/mini-apps/build", json=build_payload())

    assert response.status_code == 422
    assert "Unsupported component" in response.json()["detail"]


def test_build_mini_app_rejects_forbidden_capability(client):
    bundle = valid_bundle()
    bundle["capabilities"] = ["local_storage", "execute_code"]
    with patch("app.api.mini_apps.call_builder_llm", return_value=json.dumps(bundle)):
        response = client.post("/api/mini-apps/build", json=build_payload())

    assert response.status_code == 422
    assert "Unsupported capability" in response.json()["detail"]
