from app.models.chat import AURA_EMOTION_NAMES, ChatIn, ChatOut
from app.services.llm import build_system_message, parse_assistant_response


def test_emotion_vocabulary_has_more_than_thirty_states_and_is_protocol_safe():
    assert len(AURA_EMOTION_NAMES) >= 30
    assert len(set(AURA_EMOTION_NAMES)) == len(AURA_EMOTION_NAMES)
    assert "cute" in AURA_EMOTION_NAMES
    assert "angry" in AURA_EMOTION_NAMES
    assert ChatOut(reply="hello", session_id="session").emotion == "neutral"


def test_structured_emotion_is_returned_and_legacy_tag_is_supported():
    structured = parse_assistant_response(
        '{"reply":"You can do this.","emotion":"encouraging","actions":[]}'
    )
    legacy = parse_assistant_response("{happy} You can do this.")

    assert structured.reply == "You can do this."
    assert structured.emotion == "encouraging"
    assert legacy.emotion == "happy"


def test_unknown_emotion_fails_closed_to_neutral():
    parsed = parse_assistant_response(
        '{"reply":"I am here.","emotion":"not-a-real-emotion","actions":[]}'
    )

    assert parsed.emotion == "neutral"


def test_created_emotion_is_carried_as_a_bounded_one_off_directive():
    structured = parse_assistant_response(
        '{"reply":"Let us make it magical.","emotion":"neutral",'
        '"created_emotion":"create dreamily curious","actions":[]}'
    )
    plain = parse_assistant_response("create tiny villain\nI have a plan.")
    invalid = parse_assistant_response(
        '{"reply":"Nope","created_emotion":"create one two three four five six seven",'
        '"actions":[]}'
    )
    bare = parse_assistant_response(
        '{"reply":"Nope","created_emotion":"dreamily curious","actions":[]}'
    )

    assert structured.created_emotion == "create dreamily curious"
    assert plain.reply == "I have a plan."
    assert plain.created_emotion == "create tiny villain"
    assert invalid.created_emotion is None
    assert bare.created_emotion is None


def test_prompt_requires_structured_emotion_output():
    system = build_system_message(ChatIn(message="hello", api_key="key", model="model"))

    assert "Return one JSON object" in system
    assert "emotion" in system
    assert "cute" in system
    assert "furious" in system
    assert "created_emotion" in system
    assert "create dreamily curious" in system
