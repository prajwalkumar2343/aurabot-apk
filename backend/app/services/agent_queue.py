import asyncio
import logging
import uuid
from contextlib import suppress
from datetime import timedelta
from typing import Any, Optional

from pymongo import ReturnDocument

from app.core.config import settings
from app.models.chat import ChatIn
from app.services.agent_credentials import open_agent_credential
from app.services.agent_harness import execute_agent_run
from app.services.agent_runs import AgentRunStore, TERMINAL_STATES, utc_now


logger = logging.getLogger(__name__)


class AgentRunQueue:
    """Atomic Mongo lease queue over root run documents."""

    def __init__(self, db: Any, lease_seconds: Optional[int] = None):
        self.db = db
        self.runs = db.agent_runs
        self.lease_seconds = lease_seconds or settings.AGENT_LEASE_SECONDS

    async def claim(self, worker_id: str) -> Optional[dict[str, Any]]:
        now = utc_now()
        token = str(uuid.uuid4())
        return await self.runs.find_one_and_update(
            {
                "kind": "root",
                "state": "queued",
                "queue_state": "queued",
                "available_at": {"$lte": now},
            },
            {
                "$set": {
                    "queue_state": "leased",
                    "lease_owner": worker_id,
                    "lease_token": token,
                    "execution_token": token,
                    "lease_expires_at": now + timedelta(seconds=self.lease_seconds),
                    "heartbeat_at": now,
                    "updated_at": now,
                },
                "$inc": {"attempt": 1},
            },
            sort=[("available_at", 1), ("created_at", 1)],
            return_document=ReturnDocument.AFTER,
        )

    async def heartbeat(self, run_id: str, token: str) -> bool:
        now = utc_now()
        updated = await self.runs.find_one_and_update(
            {"id": run_id, "queue_state": "leased", "lease_token": token},
            {
                "$set": {
                    "heartbeat_at": now,
                    "lease_expires_at": now + timedelta(seconds=self.lease_seconds),
                    "updated_at": now,
                }
            },
            return_document=ReturnDocument.AFTER,
        )
        return updated is not None

    async def settle(self, run_id: str, token: str, state: str) -> bool:
        updated = await self.runs.find_one_and_update(
            {"id": run_id, "queue_state": "leased", "lease_token": token},
            {
                "$set": {"queue_state": state, "updated_at": utc_now()},
                "$unset": {
                    "lease_owner": "",
                    "lease_token": "",
                    "lease_expires_at": "",
                    "heartbeat_at": "",
                    "credential_ciphertext": "",
                    "request_payload": "",
                },
            },
            return_document=ReturnDocument.AFTER,
        )
        return updated is not None

    async def recover_expired_leases(self) -> int:
        now = utc_now()
        expired = await self.runs.find(
            {
                "kind": "root",
                "queue_state": "leased",
                "lease_expires_at": {"$lte": now},
            }
        ).to_list(1_000)
        recovered = 0
        store = AgentRunStore(self.db)
        for run in expired:
            token = run.get("lease_token")
            if not token:
                continue
            if run["state"] == "queued":
                updated = await self.runs.find_one_and_update(
                    {"id": run["id"], "queue_state": "leased", "lease_token": token},
                    {
                        "$set": {
                            "queue_state": "queued",
                            "available_at": now,
                            "updated_at": now,
                        },
                        "$unset": {
                            "lease_owner": "",
                            "lease_token": "",
                            "execution_token": "",
                            "lease_expires_at": "",
                            "heartbeat_at": "",
                        },
                    },
                    return_document=ReturnDocument.AFTER,
                )
                if updated:
                    await store.append_event(
                        run["id"], "queue_lease_recovered_before_execution"
                    )
                    recovered += 1
                continue
            if run["state"] not in TERMINAL_STATES:
                try:
                    await store.transition(
                        run["id"],
                        state="interrupted",
                        phase="interrupted",
                        event_type="run_interrupted_after_lease_expiry",
                        values={
                            "error": (
                                "Worker lease expired during provider execution; "
                                "retry is required."
                            )
                        },
                        execution_token=token,
                    )
                    children = await self.runs.find(
                        {"parent_run_id": run["id"]}
                    ).to_list(3)
                    for child in children:
                        if child.get("state") not in TERMINAL_STATES:
                            await store.transition(
                                child["id"],
                                state="interrupted",
                                phase="interrupted",
                                event_type="parent_lease_expired",
                                values={"error": "Parent worker lease expired."},
                                execution_token=token,
                            )
                except RuntimeError:
                    continue
            if await self.settle(run["id"], token, "interrupted"):
                recovered += 1
        return recovered


class AgentRunWorker:
    def __init__(
        self,
        db: Any,
        worker_id: Optional[str] = None,
        provider_call: Optional[Any] = None,
    ):
        self.db = db
        self.worker_id = worker_id or f"worker-{uuid.uuid4()}"
        self.queue = AgentRunQueue(db)
        self.provider_call = provider_call

    async def _heartbeat_loop(self, run_id: str, token: str) -> None:
        interval = max(1.0, self.queue.lease_seconds / 3)
        while True:
            await asyncio.sleep(interval)
            if not await self.queue.heartbeat(run_id, token):
                return

    async def run_once(self) -> bool:
        await self.queue.recover_expired_leases()
        run = await self.queue.claim(self.worker_id)
        if run is None:
            return False
        token = run["lease_token"]
        heartbeat = asyncio.create_task(self._heartbeat_loop(run["id"], token))
        try:
            payload = dict(run.get("request_payload") or {})
            payload["api_key"] = open_agent_credential(
                run.get("credential_ciphertext") or ""
            )
            data = ChatIn.model_validate(payload)
            execution = {
                "db": self.db,
                "user_id": run["user_id"],
                "run_id": run["id"],
                "data": data,
                "execution_token": token,
            }
            if self.provider_call is not None:
                execution["provider_call"] = self.provider_call
            await execute_agent_run(
                **execution,
            )
            current = await self.queue.runs.find_one({"id": run["id"]})
            state = current.get("state", "failed") if current else "failed"
            await self.queue.settle(run["id"], token, state)
        except Exception as error:
            logger.exception("Agent worker failed", extra={"run_id": run["id"]})
            store = AgentRunStore(self.db)
            with suppress(Exception):
                await store.transition(
                    run["id"],
                    state="failed",
                    phase="failed",
                    event_type="worker_failed",
                    values={"error": str(error)[:500] or error.__class__.__name__},
                    execution_token=token,
                )
            await self.queue.settle(run["id"], token, "failed")
        finally:
            heartbeat.cancel()
            with suppress(asyncio.CancelledError):
                await heartbeat
        return True

    async def run_forever(self, stop: Optional[asyncio.Event] = None) -> None:
        stop = stop or asyncio.Event()
        while not stop.is_set():
            if not await self.run_once():
                try:
                    await asyncio.wait_for(
                        stop.wait(), timeout=settings.AGENT_WORKER_POLL_SECONDS
                    )
                except asyncio.TimeoutError:
                    pass
