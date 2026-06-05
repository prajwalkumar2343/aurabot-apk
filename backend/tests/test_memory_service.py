import asyncio
from unittest.mock import AsyncMock

from app.services import memory


def test_supermemory_post_runs_in_worker_thread(monkeypatch):
    to_thread = AsyncMock(return_value="ok")
    monkeypatch.setattr(memory.asyncio, "to_thread", to_thread)

    result = asyncio.run(
        memory._post_supermemory("https://api.example.test/v4/search", headers={"Authorization": "Bearer test"})
    )

    assert result == "ok"
    to_thread.assert_awaited_once()
    assert to_thread.call_args.args[0] is memory.requests.post
