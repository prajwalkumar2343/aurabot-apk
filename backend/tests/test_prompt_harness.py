from app.models.chat import ChatIn, ChatMiniAppIn
from app.services.llm import build_system_message
from app.services.prompt_harness import (
    build_prompt_harness,
    load_skill_definitions,
    load_context_files,
    normalize_model_id,
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
    assert "### launcher_actions" not in neutral_system
    assert "### launcher_actions" in active_system
    assert "When the user asks to block" in active_system
    assert "### mini_app_actions" in active_system
    assert "For installed mini apps" in active_system
    assert "declared actions: check_workout" in active_system


def test_mini_app_builder_skill_guides_assistant_toward_react_runtime():
    chat = ChatIn(message="create a client tracker mini app", api_key="key", model="model")
    system = build_system_message(chat, build_prompt_harness(chat))

    assert "### mini_app_builder" in system
    assert "When the user asks to create" in system
    assert "asks for runtime react" in system
    assert "Generated mini apps must stay declarative" not in system


def test_repo_skill_discovery_loads_ai_harness_from_skill_file():
    skills = load_skill_definitions()
    skill = next(item for item in skills if item.name == "ai-harness-architect")

    assert skill.path == "skills/ai-harness-architect/SKILL.md"
    assert "prompt/context systems" in skill.summary
    assert "automation" in skill.triggers
    assert "Convert natural language to structured tool calls" in skill.detail


def test_ai_harness_rules_affect_automation_prompts():
    chat = ChatIn(message="create an automation to text me every morning", api_key="key", model="model")
    system = build_system_message(chat, build_prompt_harness(chat))

    assert "### ai-harness-architect (skills/ai-harness-architect/SKILL.md)" in system
    assert "Convert natural language to structured tool calls" in system
    assert "Record a tool call before side effects begin" in system
    assert "Use create_automation when the user asks Aura to do something later" in system


def test_ai_harness_rules_affect_mini_app_prompts():
    chat = ChatIn(message="build a field notes mini app", api_key="key", model="model")
    system = build_system_message(chat, build_prompt_harness(chat))

    assert "### ai-harness-architect (skills/ai-harness-architect/SKILL.md)" in system
    assert "Use progressive disclosure for skills" in system
    assert "### mini_app_builder" in system
    assert "call create_mini_app with a specific professional mini_app_prompt" in system
    assert "Every mini app bundle must include that widget" in system
    assert "required Aura home widget representing the app's main purpose" in system


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


def test_gemini_model_names_are_normalized_for_google_api_routes():
    assert normalize_model_id("gemini", "gemini/gemini-2.5-flash") == "gemini-2.5-flash"
    assert normalize_model_id("gemini", "models/gemini-2.5-flash") == "gemini-2.5-flash"
    assert normalize_model_id("openrouter", "google/gemini-2.5-flash") == "google/gemini-2.5-flash"

    explicit, reason = route_model(
        ChatIn(
            message="hi",
            provider="gemini",
            api_key="key",
            model="gemini/gemini-2.5-flash",
            model_route="off",
        ),
        "off",
    )
    assert explicit == "gemini-2.5-flash"
    assert reason == "model routing disabled"


def test_repair_needed_detects_claimed_action_without_tool_call():
    chat = ChatIn(message="block Instagram", api_key="key", model="model")
    reason = repair_needed("Done, blocked it.", "Done, blocked it.", [], chat)

    assert reason == "reply claimed a local action without calling the matching tool"


def test_repair_needed_detects_mini_app_creation_without_tool_call():
    chat = ChatIn(message="create a client tracker mini app", api_key="key", model="model")
    reason = repair_needed("Done, created it.", "Done, created it.", [], chat)

    assert reason == "reply claimed a local action without calling the matching tool"


def test_repair_needed_detects_invalid_structured_action():
    chat = ChatIn(message="make a lunch widget", api_key="key", model="model")
    raw = '{"reply":"Done.","actions":[{"type":"present_widget","widget":{"kind":"food_order"}}]}'

    reason = repair_needed(raw, "Done.", [], chat)

    assert reason == "response contained an unsupported or invalid action structure"
