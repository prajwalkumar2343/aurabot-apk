import uuid
from datetime import datetime, timedelta, timezone
from typing import Any, Optional

from pymongo import ReturnDocument

from app.models.agent_runs import AgentChildRunOut, AgentRunOut
from app.core.config import settings
from app.models.chat import ChatActionOut, ChatIn, ChatSubagentCall as SubagentCall


TERMINAL_STATES = {"completed", "failed", "interrupted", "cancelled"}
ACTIVE_STATES = {"queued", "running"}
MAX_STORED_OUTPUT_CHARS = 8_000
AGENT_HARNESS_VERSION = "durable-supervisor-v4-dynamic-emotions"
TOOL_REGISTRY_VERSION = "assistant-tools-v1"


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class AgentRunStore:
    """Owns durable agent run state and its append-only lifecycle event stream."""

    def __init__(self, db: Any):
        self.runs = db.agent_runs
        self.events = db.agent_run_events

    async def assert_execution_owner(
        self, run_id: str, execution_token: Optional[str]
    ) -> None:
        if not execution_token:
            return
        run = await self.runs.find_one({"id": run_id})
        if (
            run is None
            or run.get("execution_token") != execution_token
            or run.get("state") in TERMINAL_STATES
        ):
            raise RuntimeError(f"Execution lease lost for agent run {run_id}")

    async def create_root(
        self,
        user_id: str,
        data: ChatIn,
        *,
        idempotency_key: Optional[str] = None,
        credential_ciphertext: Optional[str] = None,
    ) -> dict[str, Any]:
        now = utc_now()
        admission_nonce = str(uuid.uuid4())
        key = idempotency_key or str(uuid.uuid4())
        run = {
            "id": str(uuid.uuid4()),
            "trace_id": str(uuid.uuid4()),
            "user_id": user_id,
            "parent_run_id": None,
            "session_id": data.session_id or str(uuid.uuid4()),
            "kind": "root",
            "state": "queued",
            "phase": "admitted",
            "input": data.message,
            "provider": data.provider,
            "model": data.model,
            "harness_version": AGENT_HARNESS_VERSION,
            "tool_registry_version": TOOL_REGISTRY_VERSION,
            "reply": None,
            "emotion": "neutral",
            "created_emotion": None,
            "actions": [],
            "error": None,
            "event_sequence": 0,
            "idempotency_key": key,
            "admission_nonce": admission_nonce,
            "request_payload": data.model_dump(exclude={"api_key"}),
            "credential_ciphertext": credential_ciphertext,
            "queue_state": "queued" if credential_ciphertext else "legacy",
            "available_at": now,
            "lease_owner": None,
            "lease_token": None,
            "lease_expires_at": None,
            "heartbeat_at": None,
            "attempt": 0,
            "expires_at": now + timedelta(days=settings.AGENT_RUN_RETENTION_DAYS),
            "created_at": now,
            "updated_at": now,
        }
        if idempotency_key:
            stored = await self.runs.find_one_and_update(
                {"user_id": user_id, "kind": "root", "idempotency_key": key},
                {"$setOnInsert": run},
                upsert=True,
                return_document=ReturnDocument.AFTER,
            )
            if stored["admission_nonce"] != admission_nonce:
                return stored
        else:
            await self.runs.insert_one(run)
            stored = run
        await self.append_event(
            stored["id"], "input_admitted", {"input_chars": len(data.message)}
        )
        return stored

    async def create_child(
        self,
        root: dict[str, Any],
        call: SubagentCall,
    ) -> dict[str, Any]:
        now = utc_now()
        child = {
            "id": str(uuid.uuid4()),
            "trace_id": root["trace_id"],
            "user_id": root["user_id"],
            "parent_run_id": root["id"],
            "session_id": root["session_id"],
            "agent_session_key": call.session,
            "kind": "child",
            "agent": call.agent,
            "context_mode": call.context,
            "state": "queued",
            "phase": "admitted",
            "input": call.task,
            "provider": root["provider"],
            "model": root["model"],
            "output": None,
            "error": None,
            "event_sequence": 0,
            "execution_token": root.get("execution_token"),
            "expires_at": root.get("expires_at"),
            "created_at": now,
            "updated_at": now,
        }
        await self.runs.insert_one(child)
        await self.append_event(child["id"], "subagent_admitted", {"agent": call.agent})
        return child

    async def append_event(
        self,
        run_id: str,
        event_type: str,
        details: Optional[dict[str, Any]] = None,
    ) -> None:
        now = utc_now()
        run = await self.runs.find_one_and_update(
            {"id": run_id},
            {"$inc": {"event_sequence": 1}, "$set": {"updated_at": now}},
            return_document=ReturnDocument.AFTER,
        )
        if run is None:
            raise LookupError(f"Agent run {run_id} not found")
        await self.events.insert_one(
            {
                "id": str(uuid.uuid4()),
                "run_id": run_id,
                "trace_id": run["trace_id"],
                "sequence": run["event_sequence"],
                "type": event_type,
                "details": details or {},
                "created_at": now,
                "expires_at": run.get("expires_at"),
            }
        )

    async def transition(
        self,
        run_id: str,
        *,
        state: str,
        phase: str,
        event_type: str,
        values: Optional[dict[str, Any]] = None,
        execution_token: Optional[str] = None,
    ) -> dict[str, Any]:
        ownership = {"id": run_id}
        if execution_token:
            ownership["execution_token"] = execution_token
        current = await self.runs.find_one(ownership)
        if current is None:
            if execution_token and await self.runs.find_one({"id": run_id}):
                raise RuntimeError(f"Execution lease lost for agent run {run_id}")
            raise LookupError(f"Agent run {run_id} not found")
        if current["state"] in TERMINAL_STATES:
            return current
        now = utc_now()
        update = {"state": state, "phase": phase, "updated_at": now, **(values or {})}
        query = {"id": run_id, "state": current["state"]}
        if execution_token:
            query["execution_token"] = execution_token
        mutation: dict[str, Any] = {"$set": update}
        if state in TERMINAL_STATES:
            mutation["$unset"] = {
                "credential_ciphertext": "",
                "request_payload": "",
            }
        updated = await self.runs.find_one_and_update(
            query,
            mutation,
            return_document=ReturnDocument.AFTER,
        )
        if updated is None:
            raise RuntimeError(f"Concurrent transition rejected for agent run {run_id}")
        await self.append_event(run_id, event_type, {"state": state, "phase": phase})
        return updated

    async def cancel(self, user_id: str, run_id: str) -> Optional[AgentRunOut]:
        root = await self.runs.find_one(
            {"id": run_id, "user_id": user_id, "kind": "root"}
        )
        if root is None:
            return None
        if root["state"] in ACTIVE_STATES:
            await self.transition(
                run_id,
                state="cancelled",
                phase="cancelled",
                event_type="user_cancelled_run",
                values={"error": "Run cancelled by user."},
            )
        await self.runs.find_one_and_update(
            {"id": run_id, "user_id": user_id},
            {
                "$set": {"queue_state": "cancelled", "updated_at": utc_now()},
                "$unset": {"credential_ciphertext": "", "request_payload": ""},
            },
            return_document=ReturnDocument.AFTER,
        )
        children = await self.runs.find({"parent_run_id": run_id}).to_list(3)
        for child in children:
            if child["state"] in ACTIVE_STATES:
                await self.transition(
                    child["id"],
                    state="cancelled",
                    phase="cancelled",
                    event_type="parent_run_cancelled",
                    values={"error": "Parent run cancelled by user."},
                )
        return await self.snapshot(user_id, run_id)

    async def snapshot(self, user_id: str, run_id: str) -> Optional[AgentRunOut]:
        root = await self.runs.find_one(
            {"id": run_id, "user_id": user_id, "kind": "root"}
        )
        if root is None:
            return None
        children = (
            await self.runs.find({"parent_run_id": run_id})
            .sort("created_at", 1)
            .to_list(3)
        )
        return AgentRunOut(
            id=root["id"],
            session_id=root["session_id"],
            state=root["state"],
            phase=root["phase"],
            reply=root.get("reply"),
            emotion=root.get("emotion", "neutral"),
            created_emotion=root.get("created_emotion"),
            actions=[
                ChatActionOut.model_validate(action)
                for action in root.get("actions", [])
            ],
            children=[
                AgentChildRunOut(
                    id=child["id"],
                    agent=child["agent"],
                    state=child["state"],
                    phase=child["phase"],
                    output=child.get("output"),
                    error=child.get("error"),
                )
                for child in children
            ],
            error=root.get("error"),
            created_at=root["created_at"],
            updated_at=root["updated_at"],
        )

    async def previous_session_output(
        self,
        *,
        user_id: str,
        agent: str,
        session: Optional[str],
    ) -> Optional[str]:
        if not session:
            return None
        candidates = (
            await self.runs.find(
                {
                    "user_id": user_id,
                    "kind": "child",
                    "agent": agent,
                    "agent_session_key": session,
                    "state": "completed",
                }
            )
            .sort("updated_at", -1)
            .to_list(1)
        )
        if not candidates:
            return None
        output = candidates[0].get("output")
        return str(output)[:MAX_STORED_OUTPUT_CHARS] if output else None

    async def session_history(self, *, user_id: str, session_id: str) -> str:
        turns = (
            await self.runs.find(
                {
                    "user_id": user_id,
                    "kind": "root",
                    "session_id": session_id,
                    "state": "completed",
                }
            )
            .sort("created_at", -1)
            .to_list(6)
        )
        rendered = [
            f"User: {turn.get('input', '')}\nAura: {turn.get('reply', '')}"
            for turn in reversed(turns)
        ]
        return "\n\n".join(rendered)[:16_000]


async def reconcile_interrupted_runs(db: Any) -> int:
    """Compatibility wrapper; only expired leases are eligible for recovery."""

    from app.services.agent_queue import AgentRunQueue

    return await AgentRunQueue(db).recover_expired_leases()
