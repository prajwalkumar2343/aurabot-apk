import json
from datetime import timedelta

import pytest

from app.models.chat import ChatIn
from app.services.agent_harness import execute_agent_run
from app.services.agent_credentials import seal_agent_credential
from app.services.agent_queue import AgentRunQueue, AgentRunWorker
from app.services.agent_runs import AgentRunStore, reconcile_interrupted_runs, utc_now
from app.services.llm import parse_tool_response


class MemoryCursor:
    def __init__(self, documents):
        self.documents = [dict(document) for document in documents]

    def sort(self, key, direction=-1):
        self.documents.sort(key=lambda item: item.get(key), reverse=direction == -1)
        return self

    async def to_list(self, limit):
        return self.documents[:limit]


class MemoryCollection:
    def __init__(self):
        self.documents = []

    @staticmethod
    def _matches(document, query):
        for key, value in query.items():
            actual = document.get(key)
            if isinstance(value, dict):
                if "$lte" in value and not (
                    actual is not None and actual <= value["$lte"]
                ):
                    return False
                continue
            if actual != value:
                return False
        return True

    async def insert_one(self, document):
        self.documents.append(dict(document))

    async def find_one(self, query, projection=None):
        return next(
            (dict(item) for item in self.documents if self._matches(item, query)), None
        )

    async def find_one_and_update(
        self, query, update, return_document=None, upsert=False, sort=None
    ):
        for document in self.documents:
            if not self._matches(document, query):
                continue
            for key, value in update.get("$inc", {}).items():
                document[key] = document.get(key, 0) + value
            document.update(update.get("$set", {}))
            for key in update.get("$unset", {}):
                document.pop(key, None)
            return dict(document)
        if upsert:
            document = dict(update.get("$setOnInsert", {}))
            self.documents.append(document)
            return dict(document)
        return None

    def find(self, query, projection=None):
        return MemoryCursor(
            item for item in self.documents if self._matches(item, query)
        )


class MemoryDatabase:
    def __init__(self):
        self.agent_runs = MemoryCollection()
        self.agent_run_events = MemoryCollection()


@pytest.fixture
def anyio_backend():
    return "asyncio"


@pytest.mark.anyio
async def test_agent_run_delegates_in_parallel_and_persists_ordered_lifecycle():
    db = MemoryDatabase()
    data = ChatIn(message="Plan and review a launch", api_key="secret", model="model")
    store = AgentRunStore(db)
    root = await store.create_root("user-1", data)
    calls = []

    def provider(provider, request, system, use_tools):
        calls.append((request.message, use_tools))
        if use_tools:
            return json.dumps(
                {
                    "reply": "{thinking} I am splitting this up.",
                    "actions": [
                        {
                            "type": "delegate_tasks",
                            "calls": [
                                {"agent": "planner", "task": "Create the launch plan"},
                                {"agent": "reviewer", "task": "Review launch risks"},
                            ],
                        }
                    ],
                }
            )
        if "Original request:" in request.message:
            return "{happy} The plan is ready and the main risks are covered."
        return f"Report for: {request.message}"

    await execute_agent_run(
        db=db,
        user_id="user-1",
        run_id=root["id"],
        data=data,
        provider_call=provider,
    )

    snapshot = await store.snapshot("user-1", root["id"])
    assert snapshot.state == "completed"
    assert snapshot.phase == "completed"
    assert snapshot.reply.startswith("{happy}")
    assert snapshot.emotion == "happy"
    assert [child.agent for child in snapshot.children] == ["planner", "reviewer"]
    assert all(child.state == "completed" for child in snapshot.children)
    assert [use_tools for _, use_tools in calls] == [True, False, False, False]
    assert "secret" not in repr(db.agent_runs.documents)

    root_events = [
        event
        for event in db.agent_run_events.documents
        if event["run_id"] == root["id"]
    ]
    assert [event["sequence"] for event in root_events] == list(
        range(1, len(root_events) + 1)
    )
    assert {event["type"] for event in root_events} >= {
        "input_admitted",
        "root_agent_started",
        "subagents_scheduled",
        "synthesis_started",
        "root_agent_completed",
    }


@pytest.mark.anyio
async def test_failed_child_is_recorded_without_replaying_or_failing_successful_children():
    db = MemoryDatabase()
    data = ChatIn(message="Compare two approaches", api_key="secret", model="model")
    store = AgentRunStore(db)
    root = await store.create_root("user-1", data)

    def provider(provider, request, system, use_tools):
        if use_tools:
            return json.dumps(
                {
                    "reply": "{thinking} Comparing them.",
                    "actions": [
                        {
                            "type": "delegate_tasks",
                            "calls": [
                                {"agent": "researcher", "task": "good child"},
                                {"agent": "reviewer", "task": "bad child"},
                            ],
                        }
                    ],
                }
            )
        if request.message == "bad child":
            raise RuntimeError("provider unavailable")
        if "Original request:" in request.message:
            return "{neutral} One specialist completed; one was unavailable."
        return "useful report"

    await execute_agent_run(
        db=db,
        user_id="user-1",
        run_id=root["id"],
        data=data,
        provider_call=provider,
    )

    snapshot = await store.snapshot("user-1", root["id"])
    assert snapshot.state == "completed"
    assert [child.state for child in snapshot.children] == ["completed", "failed"]
    assert snapshot.children[1].error == "provider unavailable"


@pytest.mark.anyio
async def test_recovery_marks_active_runs_interrupted_without_provider_replay():
    db = MemoryDatabase()
    store = AgentRunStore(db)
    root = await store.create_root(
        "user-1",
        ChatIn(message="Long task", api_key="secret", model="model"),
        credential_ciphertext="encrypted",
    )
    queue = AgentRunQueue(db, lease_seconds=30)
    leased = await queue.claim("worker-1")
    await store.transition(
        root["id"],
        state="running",
        phase="planning",
        event_type="root_agent_started",
        execution_token=leased["lease_token"],
    )
    for document in db.agent_runs.documents:
        if document["id"] == root["id"]:
            document["lease_expires_at"] = utc_now() - timedelta(seconds=1)

    assert await reconcile_interrupted_runs(db) == 1
    snapshot = await store.snapshot("user-1", root["id"])
    assert snapshot.state == "interrupted"
    assert "retry is required" in snapshot.error
    event_types = [event["type"] for event in db.agent_run_events.documents]
    assert event_types[-1] == "run_interrupted_after_lease_expiry"


@pytest.mark.anyio
async def test_idempotent_admission_returns_the_original_queued_run():
    db = MemoryDatabase()
    store = AgentRunStore(db)
    data = ChatIn(message="Run once", api_key="secret", model="model")

    first = await store.create_root(
        "user-1", data, idempotency_key="mobile-request-1", credential_ciphertext="one"
    )
    second = await store.create_root(
        "user-1", data, idempotency_key="mobile-request-1", credential_ciphertext="two"
    )

    assert second["id"] == first["id"]
    assert len(db.agent_runs.documents) == 1
    assert db.agent_runs.documents[0]["credential_ciphertext"] == "one"


@pytest.mark.anyio
async def test_reclaimed_queue_run_rejects_the_stale_worker_fence():
    db = MemoryDatabase()
    store = AgentRunStore(db)
    root = await store.create_root(
        "user-1",
        ChatIn(message="Lease me", api_key="secret", model="model"),
        credential_ciphertext="encrypted",
    )
    queue = AgentRunQueue(db, lease_seconds=30)
    first = await queue.claim("worker-1")
    for document in db.agent_runs.documents:
        if document["id"] == root["id"]:
            document["lease_expires_at"] = utc_now() - timedelta(seconds=1)

    assert await queue.recover_expired_leases() == 1
    second = await queue.claim("worker-2")
    assert second["lease_token"] != first["lease_token"]

    with pytest.raises(RuntimeError, match="lease lost"):
        await store.transition(
            root["id"],
            state="running",
            phase="planning",
            event_type="stale_worker_started",
            execution_token=first["lease_token"],
        )


@pytest.mark.anyio
async def test_cancellation_removes_queued_credentials_and_payload():
    db = MemoryDatabase()
    store = AgentRunStore(db)
    root = await store.create_root(
        "user-1",
        ChatIn(message="Cancel me", api_key="secret", model="model"),
        credential_ciphertext="encrypted-secret",
    )

    cancelled = await store.cancel("user-1", root["id"])

    assert cancelled.state == "cancelled"
    stored = await store.runs.find_one({"id": root["id"]})
    assert stored["queue_state"] == "cancelled"
    assert "credential_ciphertext" not in stored
    assert "request_payload" not in stored


@pytest.mark.anyio
async def test_worker_decrypts_claimed_request_and_scrubs_it_after_completion():
    db = MemoryDatabase()
    store = AgentRunStore(db)
    data = ChatIn(message="Durable work", api_key="provider-secret", model="model")
    root = await store.create_root(
        "user-1", data, credential_ciphertext=seal_agent_credential(data.api_key)
    )
    observed_keys = []

    def provider(provider_name, request, system, use_tools):
        observed_keys.append(request.api_key)
        return "{happy} Durable work completed."

    assert await AgentRunWorker(
        db, worker_id="worker-1", provider_call=provider
    ).run_once()

    snapshot = await store.snapshot("user-1", root["id"])
    stored = await store.runs.find_one({"id": root["id"]})
    assert snapshot.state == "completed"
    assert observed_keys == ["provider-secret"]
    assert stored["queue_state"] == "completed"
    assert "credential_ciphertext" not in stored
    assert "request_payload" not in stored


@pytest.mark.anyio
async def test_cancel_is_user_scoped_and_terminal_state_cannot_be_overwritten():
    db = MemoryDatabase()
    store = AgentRunStore(db)
    root = await store.create_root(
        "user-1", ChatIn(message="Long task", api_key="secret", model="model")
    )
    await store.transition(
        root["id"], state="running", phase="planning", event_type="root_agent_started"
    )

    assert await store.cancel("different-user", root["id"]) is None
    cancelled = await store.cancel("user-1", root["id"])
    assert cancelled.state == "cancelled"

    settled = await store.transition(
        root["id"],
        state="completed",
        phase="completed",
        event_type="late_provider_completion",
        values={"reply": "must not win"},
    )
    assert settled["state"] == "cancelled"
    snapshot = await store.snapshot("user-1", root["id"])
    assert snapshot.state == "cancelled"
    assert snapshot.reply is None


@pytest.mark.anyio
async def test_completed_session_turns_are_loaded_as_bounded_conversation_history():
    db = MemoryDatabase()
    store = AgentRunStore(db)
    first_data = ChatIn(
        message="Remember the blue launch plan",
        session_id="session-1",
        api_key="secret",
        model="model",
    )
    first = await store.create_root("user-1", first_data)

    def first_provider(provider, request, system, use_tools):
        return "{neutral} I will remember the blue launch plan."

    await execute_agent_run(
        db=db,
        user_id="user-1",
        run_id=first["id"],
        data=first_data,
        provider_call=first_provider,
    )

    observed_systems = []
    second_data = first_data.model_copy(update={"message": "What was the plan?"})
    second = await store.create_root("user-1", second_data)

    def second_provider(provider, request, system, use_tools):
        observed_systems.append(system)
        return "{happy} The plan was blue."

    await execute_agent_run(
        db=db,
        user_id="user-1",
        run_id=second["id"],
        data=second_data,
        provider_call=second_provider,
    )

    assert "Prior turns from this durable Aura session" in observed_systems[0]
    assert "Remember the blue launch plan" in observed_systems[0]
    assert "I will remember the blue launch plan" in observed_systems[0]


def test_delegate_tool_rejects_missing_or_oversized_call_lists():
    _, missing = parse_tool_response(
        json.dumps({"reply": "delegate", "actions": [{"type": "delegate_tasks"}]})
    )
    _, oversized = parse_tool_response(
        json.dumps(
            {
                "reply": "delegate",
                "actions": [
                    {
                        "type": "delegate_tasks",
                        "calls": [
                            {"agent": "planner", "task": f"task-{index}"}
                            for index in range(4)
                        ],
                    }
                ],
            }
        )
    )

    assert missing == []
    assert oversized == []


def test_durable_agent_run_routes_are_registered():
    from app.main import app

    paths = app.openapi()["paths"]
    assert "/api/assistant/runs" in paths
    assert "/api/assistant/runs/{run_id}" in paths
    assert "/api/assistant/runs/{run_id}/cancel" in paths
