import json
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.core.security import get_current_user
from app.services.mini_apps import (
    compile_mini_app_bundle,
    fallback_bundle,
    mini_app_builder_system_prompt,
    mini_app_revision_system_prompt,
    react_fallback_bundle,
    validate_mini_app_bundle,
)


@pytest.fixture
def client():
    app.dependency_overrides[get_current_user] = lambda: {
        "id": "mini-app-builder-test-user",
        "email": "mini@app.test",
        "role": "user",
    }
    yield TestClient(app, raise_server_exceptions=False)
    app.dependency_overrides.pop(get_current_user, None)


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


def valid_react_bundle():
    return {
        "id": "generated.react.notes",
        "runtime": "react",
        "metadata": {"name": "React Notes", "description": "Take notes", "category": "Productivity"},
        "icon": {"type": "initial", "value": "R", "background": "#2563EB"},
        "dataSchema": {"recordType": "note", "fields": [{"name": "title", "type": "text", "required": True}]},
        "screens": [],
        "actions": [],
        "assistantIntents": [{"name": "open_notes", "utterances": ["open notes"]}],
        "capabilities": ["local_storage", "assistant_actions", "react_runtime", "scoped_storage"],
        "codeBundle": {
            "entry": "App.jsx",
            "appJsx": "export default function App({ records }) { return <main><h1>Notes</h1></main>; }",
            "css": "body { margin: 0; }",
            "allowedApis": ["records"],
        },
    }


def build_payload(prompt="make me a habit tracker for workouts and water"):
    return {"prompt": prompt, "provider": "gemini", "api_key": "test", "model": "gemini-test"}


def revision_payload(instruction="track soreness too"):
    return {
        "instruction": instruction,
        "currentBundle": valid_bundle(),
        "recordSample": [{"recordType": "habit_checkin", "values": {"habit": "Workout"}}],
        "provider": "gemini",
        "api_key": "test",
        "model": "gemini-test",
    }


def test_build_mini_app_validates_llm_bundle(client):
    with patch("app.api.mini_apps.call_builder_llm", return_value=json.dumps(valid_bundle())):
        response = client.post("/api/mini-apps/build", json=build_payload())

    assert response.status_code == 200
    data = response.json()
    assert data["bundle"]["id"] == "generated.habits"
    assert data["bundle"]["screens"][0]["components"][1]["type"] == "quick_action_grid"


def test_builder_system_prompt_loads_skill_markdown():
    prompt = mini_app_builder_system_prompt()

    assert "Aura Mini App Builder Skill" in prompt
    assert "Professional App Shape" in prompt
    assert "Supported component types:" in prompt
    assert "runtime, metadata" in prompt
    assert "codeBundle" in prompt
    assert "Generated mini apps must stay declarative" not in prompt
    assert "runtime to react" in mini_app_builder_system_prompt(runtime="react")
    react_prompt = mini_app_builder_system_prompt(runtime="react")
    assert "entry App.jsx, appJsx source, css, allowedApis" in react_prompt
    assert "react_runtime and scoped_storage" in react_prompt


def test_builder_system_prompt_can_request_native_runtime():
    prompt = mini_app_builder_system_prompt(runtime="native")

    assert "The requested runtime is native" in prompt
    assert "omit codeBundle" in prompt


def test_revision_system_prompt_preserves_existing_app_contract():
    prompt = mini_app_revision_system_prompt(
        type("Revision", (), {
            "currentBundle": compile_mini_app_bundle(fallback_bundle("gym tracker")),
            "recordSample": [{"values": {"title": "Leg day"}}],
            "instruction": "track soreness too",
            "runtime": None,
        })()
    )

    assert "revising an existing installed Aura mini app" in prompt
    assert "keep the same id" in prompt
    assert "increment version by exactly 1" in prompt
    assert "track soreness too" in prompt


def test_build_mini_app_rejects_empty_prompt(client):
    response = client.post("/api/mini-apps/build", json=build_payload("   "))

    assert response.status_code == 400


def test_revise_mini_app_returns_next_version_and_migration_plan(client):
    revised = valid_bundle()
    revised["id"] = "model.tried.to.rename"
    revised["version"] = 99
    revised["dataSchema"]["fields"].append({"name": "soreness", "type": "number"})
    revised["actions"].append(
        {
            "id": "log_soreness",
            "type": "create_record",
            "recordType": "habit_checkin",
            "values": {"habit": "Workout", "soreness": "3"},
        }
    )
    revised["assistantIntents"].append(
        {"name": "log_soreness", "utterances": ["log soreness"], "actionId": "log_soreness"}
    )
    payload = {"bundle": revised, "summary": "Added soreness tracking.", "migrationPlan": ["Old workout records remain valid."]}
    with patch("app.api.mini_apps.call_revision_llm", return_value=json.dumps(payload)):
        response = client.post("/api/mini-apps/revise", json=revision_payload())

    assert response.status_code == 200
    data = response.json()
    assert data["bundle"]["id"] == "generated.habits"
    assert data["bundle"]["version"] == 2
    assert data["bundle"]["metadata"]["builtIn"] is False
    assert data["bundle"]["dataSchema"]["fields"][-1]["name"] == "soreness"
    assert data["migrationPlan"] == ["Old workout records remain valid."]


def test_revise_mini_app_repairs_invalid_revision(client):
    fixed = valid_bundle()
    fixed["dataSchema"]["fields"].append({"name": "soreness", "type": "number"})
    payload = {"bundle": fixed, "summary": "Added soreness.", "migrationPlan": ["Records stay attached."]}
    with patch("app.api.mini_apps.call_revision_llm", side_effect=["not json", json.dumps(payload)]) as mock_call:
        response = client.post("/api/mini-apps/revise", json=revision_payload())

    assert response.status_code == 200
    assert mock_call.call_count == 2
    assert "Repair pass" in mock_call.call_args.args[1]
    assert response.json()["bundle"]["version"] == 2


def test_build_mini_app_rejects_malformed_json(client):
    with patch("app.api.mini_apps.call_builder_llm", return_value="not json"):
        response = client.post("/api/mini-apps/build", json=build_payload())

    assert response.status_code == 422


def test_build_mini_app_repairs_invalid_first_bundle(client):
    with patch("app.api.mini_apps.call_builder_llm", side_effect=["not json", json.dumps(valid_bundle())]) as mock_call:
        response = client.post("/api/mini-apps/build", json=build_payload())

    assert response.status_code == 200
    assert mock_call.call_count == 2
    assert "Repair pass" in mock_call.call_args.args[1]
    assert response.json()["bundle"]["id"] == "generated.habits"


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


def test_validate_mini_app_rejects_duplicate_identifiers_and_unknown_screen():
    bundle = valid_bundle()
    bundle["dataSchema"]["fields"].append({"name": "habit", "type": "text"})
    with pytest.raises(Exception) as field_error:
        validate_mini_app_bundle(bundle)
    assert "Field names must be unique" in str(field_error.value)

    bundle = valid_bundle()
    bundle["actions"].append(bundle["actions"][0])
    with pytest.raises(Exception) as action_error:
        validate_mini_app_bundle(bundle)
    assert "Action ids must be unique" in str(action_error.value)

    bundle = valid_bundle()
    bundle["screens"].append(bundle["screens"][0])
    with pytest.raises(Exception) as screen_error:
        validate_mini_app_bundle(bundle)
    assert "Screen ids must be unique" in str(screen_error.value)

    bundle = valid_bundle()
    bundle["assistantIntents"].append(bundle["assistantIntents"][0])
    with pytest.raises(Exception) as intent_error:
        validate_mini_app_bundle(bundle)
    assert "Intent names must be unique" in str(intent_error.value)

    bundle = valid_bundle()
    bundle["assistantIntents"].append({"name": "open_missing", "utterances": ["open missing"], "screenId": "missing"})
    with pytest.raises(Exception) as missing_screen_error:
        validate_mini_app_bundle(bundle)
    assert "Unknown intent screen" in str(missing_screen_error.value)


def test_validate_mini_app_rejects_invalid_create_record_action_contract():
    bundle = valid_bundle()
    bundle["dataSchema"]["recordType"] = " "
    with pytest.raises(Exception) as record_type_error:
        validate_mini_app_bundle(bundle)
    assert "dataSchema.recordType is required" in str(record_type_error.value)

    bundle = valid_bundle()
    bundle["actions"][0]["recordType"] = "expense"
    with pytest.raises(Exception) as action_type_error:
        validate_mini_app_bundle(bundle)
    assert "Unsupported action record type" in str(action_type_error.value)

    bundle = valid_bundle()
    bundle["actions"][0]["values"]["surprise"] = "nope"
    with pytest.raises(Exception) as action_field_error:
        validate_mini_app_bundle(bundle)
    assert "Unknown action field" in str(action_field_error.value)


def test_build_mini_app_compiles_requested_react_bundle(client):
    with patch("app.api.mini_apps.call_builder_llm", return_value=json.dumps(valid_react_bundle())):
        response = client.post("/api/mini-apps/build", json={**build_payload("make a real notes app"), "runtime": "react"})

    assert response.status_code == 200
    bundle = response.json()["bundle"]
    assert bundle["runtime"] == "react"
    assert "window.__AuraMiniAppMount" in bundle["codeBundle"]["compiledJs"]


def test_build_mini_app_rejects_react_code_with_blocked_browser_api(client):
    bundle = valid_react_bundle()
    bundle["codeBundle"]["appJsx"] = "export default function App() { fetch('https://example.com'); return <main />; }"
    with patch("app.api.mini_apps.call_builder_llm", return_value=json.dumps(bundle)):
        response = client.post("/api/mini-apps/build", json={**build_payload("make a real notes app"), "runtime": "react"})

    assert response.status_code == 422
    assert "Blocked React code pattern" in response.json()["detail"]


def test_build_mini_app_rejects_supplied_compiled_react_code(client):
    bundle = valid_react_bundle()
    bundle["codeBundle"]["compiledJs"] = "window.__AuraMiniAppMount = function() { fetch('https://example.com/leak') }"
    with patch("app.api.mini_apps.call_builder_llm", return_value=json.dumps(bundle)):
        response = client.post("/api/mini-apps/build", json={**build_payload("make a real notes app"), "runtime": "react"})

    assert response.status_code == 422
    assert "compiledJs is generated by Aura" in response.json()["detail"]


def test_react_fallback_bundle_compiles():
    bundle = compile_mini_app_bundle(react_fallback_bundle("field notes"))

    assert bundle.runtime == "react"
    assert bundle.codeBundle is not None
    assert "window.__AuraMiniAppMount" in bundle.codeBundle.compiledJs


def test_fallback_bundle_has_real_app_structure():
    bundle = fallback_bundle("focus planner for deep work")

    assert bundle.metadata.category == "Productivity"
    assert bundle.theme.primary == "#2563EB"
    assert len(bundle.actions) == 3
    assert len(bundle.screens) == 2
    component_types = [component.type for component in bundle.screens[0].components]
    assert component_types == ["dashboard_block", "streak_view", "quick_action_grid", "chart", "timeline", "slider"]
    assert len(bundle.screens[0].components[2].items) == 3
    detail_types = [component.type for component in bundle.screens[1].components]
    assert detail_types == ["form", "list", "button", "bottom_sheet", "settings"]
