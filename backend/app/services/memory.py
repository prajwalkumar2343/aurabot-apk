import logging
import re
import uuid
from datetime import datetime, timezone
from typing import Optional

import requests

from app.core.config import settings
from app.models.memory import MemoryCreate, MemoryOut, MemorySearchOut

logger = logging.getLogger(__name__)

MAX_SEARCH_LIMIT = 20


def supermemory_enabled() -> bool:
    return bool(settings.SUPERMEMORY_API_KEY.strip())


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _clamp_limit(limit: int) -> int:
    return max(1, min(limit or 8, MAX_SEARCH_LIMIT))


def _headers() -> dict[str, str]:
    return {
        "Authorization": f"Bearer {settings.SUPERMEMORY_API_KEY}",
        "Content-Type": "application/json",
    }


def _base_url() -> str:
    return settings.SUPERMEMORY_BASE_URL.rstrip("/")


def _memory_text(title: str, content: str) -> str:
    title = title.strip()
    content = content.strip()
    if title and content:
        return f"{title}\n\n{content}"
    return title or content


def _supermemory_container_tag(user_id: str) -> str:
    return f"aura_user:{user_id}"


class MemoryService:
    """Aura memory adapter.

    Supermemory is the cloud search provider when configured. Mongo remains a
    small compatibility mirror for Aura's existing list/delete UI and for local
    development without a Supermemory key.
    """

    def __init__(self, db):
        self.db = db

    async def list_memories(self, user_id: str) -> list[MemoryOut]:
        cursor = self.db.memories.find({"user_id": user_id}, {"_id": 0}).sort("created_at", -1)
        items = await cursor.to_list(1000)
        return [
            MemoryOut(id=i["id"], title=i["title"], content=i["content"], created_at=i["created_at"])
            for i in items
        ]

    async def create_memory(self, user_id: str, data: MemoryCreate) -> MemoryOut:
        memory_id = str(uuid.uuid4())
        created_at = _now_iso()
        doc = {
            "id": memory_id,
            "user_id": user_id,
            "title": data.title,
            "content": data.content,
            "created_at": created_at,
        }

        if supermemory_enabled():
            supermemory_id = await self._create_supermemory(user_id, memory_id, data)
            if supermemory_id:
                doc["supermemory_id"] = supermemory_id

        await self.db.memories.insert_one(doc)
        return MemoryOut(id=doc["id"], title=doc["title"], content=doc["content"], created_at=doc["created_at"])

    async def delete_memory(self, user_id: str, memory_id: str) -> bool:
        existing = await self.db.memories.find_one({"id": memory_id, "user_id": user_id}, {"_id": 0})
        if not existing:
            return False

        supermemory_id = existing.get("supermemory_id")
        if supermemory_enabled() and supermemory_id:
            await self._forget_supermemory(supermemory_id)

        res = await self.db.memories.delete_one({"id": memory_id, "user_id": user_id})
        return res.deleted_count > 0

    async def search_memories(self, user_id: str, query: str, limit: int = 8) -> list[MemorySearchOut]:
        limit = _clamp_limit(limit)
        if not query.strip():
            return []
        if supermemory_enabled():
            results = await self._search_supermemory(user_id, query, limit)
            if results:
                return results
        return await self._search_local_mirror(user_id, query, limit)

    async def _create_supermemory(self, user_id: str, memory_id: str, data: MemoryCreate) -> Optional[str]:
        try:
            response = requests.post(
                f"{_base_url()}/v4/memories",
                headers=_headers(),
                json={
                    "memories": [
                        {
                            "content": _memory_text(data.title, data.content),
                            "isStatic": False,
                            "metadata": {
                                "source": "aura_manual_memory",
                                "aura_memory_id": memory_id,
                                "title": data.title,
                            },
                        }
                    ],
                    "containerTag": _supermemory_container_tag(user_id),
                },
                timeout=30,
            )
            if response.status_code >= 400:
                logger.warning("Supermemory create failed: %s %s", response.status_code, response.text[:200])
                return None
            payload = response.json()
            memories = payload.get("memories") or []
            if memories and isinstance(memories[0], dict):
                return memories[0].get("id")
        except Exception as exc:
            logger.warning("Supermemory create failed: %s", exc)
        return None

    async def _forget_supermemory(self, supermemory_id: str) -> None:
        try:
            response = requests.post(
                f"{_base_url()}/v4/memories/{supermemory_id}/forget",
                headers={"Authorization": f"Bearer {settings.SUPERMEMORY_API_KEY}"},
                timeout=20,
            )
            if response.status_code >= 400:
                logger.warning("Supermemory forget failed: %s %s", response.status_code, response.text[:200])
        except Exception as exc:
            logger.warning("Supermemory forget failed: %s", exc)

    async def _search_supermemory(self, user_id: str, query: str, limit: int) -> list[MemorySearchOut]:
        try:
            response = requests.post(
                f"{_base_url()}/v4/search",
                headers=_headers(),
                json={
                    "q": query,
                    "containerTag": _supermemory_container_tag(user_id),
                    "searchMode": "hybrid",
                    "limit": limit,
                },
                timeout=30,
            )
            if response.status_code >= 400:
                logger.warning("Supermemory search failed: %s %s", response.status_code, response.text[:200])
                return []
            payload = response.json()
            output: list[MemorySearchOut] = []
            for item in payload.get("results") or []:
                if not isinstance(item, dict):
                    continue
                text = item.get("memory") or item.get("chunk") or ""
                if not text:
                    continue
                metadata = item.get("metadata") if isinstance(item.get("metadata"), dict) else {}
                output.append(
                    MemorySearchOut(
                        memory_id=str(metadata.get("aura_memory_id") or item.get("id") or ""),
                        title=str(metadata.get("title") or "Memory"),
                        chunk_text=str(text),
                        score=float(item.get("similarity") or item.get("score") or 0),
                        source_type="supermemory",
                    )
                )
            return output[:limit]
        except Exception as exc:
            logger.warning("Supermemory search failed: %s", exc)
            return []

    async def _search_local_mirror(self, user_id: str, query: str, limit: int) -> list[MemorySearchOut]:
        words = [word for word in re.findall(r"\w+", query.lower()) if word]
        if not words:
            return []
        cursor = self.db.memories.find({"user_id": user_id}, {"_id": 0}).sort("created_at", -1)
        memories = await cursor.to_list(1000)
        ranked: list[MemorySearchOut] = []
        for memory in memories:
            haystack = f"{memory.get('title', '')} {memory.get('content', '')}".lower()
            score = sum(1 for word in words if word in haystack)
            if score <= 0:
                continue
            ranked.append(
                MemorySearchOut(
                    memory_id=memory["id"],
                    title=memory.get("title", ""),
                    chunk_text=memory.get("content", ""),
                    score=float(score),
                    source_type="keyword",
                )
            )
        ranked.sort(key=lambda item: item.score, reverse=True)
        return ranked[:limit]


def get_memory_service(db=None) -> MemoryService:
    return MemoryService(db)


async def init_memory_backend() -> None:
    return None


async def close_memory_backend() -> None:
    return None
