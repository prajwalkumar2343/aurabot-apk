from app.models.chat import ChatIn, ChatMiniAppIn
from app.services.llm import build_system_message
from app.services.prompt_harness import (
    build_prompt_harness,
    load_context_files,
    repair_needed,
    route_model,
)


def test_file_context_loading_uses_allowlisted_workspace_files():
    snippets = load_context_files(["README.md", "../secrets.txt", "memory/PRD.md"], "")
    paths = [snippet.path for snippet in snippets]

    assert "README.md" in paths
    assert "memory/PRD.md" in paths
    assert "../secrets.txt" not in paths
    assert all(snippet.content for snippet in snippets)


def test_skill_discovery_uses_progressive_disclosure():
    neutral = ChatIn(message="hello", api_key="key", model="model")
    active = ChatIn(
        message="block Instagram and open my habit tracker",
        api_key="key",
        model="model",
        mini_apps=[ChatMiniAppIn(id="builtin.habit_tracker", name="Habit Tracker", intents=["mark_workout_done"], actions=["check_workout"])],
    )

    neutral_system = build_system_message(neutral, build_prompt_harness(neutral))
    active_system = build_system_message(active, build_prompt_harness(active))

    assert "Available skill summaries:" in neutral_system
    assert "launcher_actions: When the user asks to block" not in neutral_system
    assert "launcher_actions: When the user asks to block" in active_system
    assert "mini_app_actions: For installed mini apps" in active_system
    assert "declared actions: check_workout" in active_system


def test_mini_app_builder_skill_guides_assistant_toward_react_runtime():
    chat = ChatIn(message="create a client tracker mini app", api_key="key", model="model")
    system = build_system_message(chat, build_prompt_harness(chat))

    assert "mini_app_builder: When the user asks to create" in system
    assert "asks for runtime react" in system
    assert "Generated mini apps must stay declarative" not in system


def test_planning_mode_auto_enables_plan_for_complex_requests():
    simple = build_prompt_harness(ChatIn(message="hi", api_key="key", model="model"))
    complex_request = build_prompt_harness(ChatIn(message="build a morning workflow", api_key="key", model="model"))
    forced = build_prompt_harness(ChatIn(message="hi", api_key="key", model="model", planning_mode="plan"))

    assert simple.planning_mode == "off"
    assert complex_request.planning_mode == "plan"
    assert forced.planning_mode == "plan"


def test_model_routing_selects_fast_or_deep_models_when_enabled():
    fast_model, fast_reason = route_model(
        ChatIn(message="hi", provider="gemini", api_key="key", model="auto", model_route="auto"),
        "off",
    )
    deep_model, deep_reason = route_model(
        ChatIn(message="implement a complex workflow", provider="gemini", api_key="key", model="auto", model_route="auto"),
        "plan",
    )

    assert fast_model == "gemini-2.5-flash"
    assert "fast" in fast_reason
    assert deep_model == "gemini-2.5-pro"
    assert "deep" in deep_reason


def test_repair_needed_detects_claimed_action_without_tool_call():
    chat = ChatIn(message="block Instagram", api_key="key", model="model")
    reason = repair_needed("Done, blocked it.", "Done, blocked it.", [], chat)

    assert reason == "reply claimed a local action without calling the matching tool"


def test_repair_needed_detects_mini_app_creation_without_tool_call():
    chat = ChatIn(message="create a client tracker mini app", api_key="key", model="model")
    reason = repair_needed("Done, created it.", "Done, created it.", [], chat)

    assert reason == "reply claimed a local action without calling the matching tool"
