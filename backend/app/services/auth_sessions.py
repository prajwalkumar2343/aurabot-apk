import hashlib
import hmac
from datetime import datetime, timezone
from typing import Any, Optional

from fastapi import HTTPException
from pymongo import ReturnDocument

from app.core.config import settings


def _parse_datetime(value: Any) -> Optional[datetime]:
    if isinstance(value, datetime):
        parsed = value
    elif isinstance(value, str):
        try:
            parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        except ValueError:
            return None
    else:
        return None
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=timezone.utc)
    return parsed


def refresh_token_fingerprint(jti: str) -> str:
    return hmac.new(
        settings.JWT_SECRET.encode("utf-8"),
        jti.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()


async def store_refresh_session(db: Any, user_id: str, jti: str, expires_at: datetime) -> None:
    await db.refresh_sessions.insert_one(
        {
            "jti_hash": refresh_token_fingerprint(jti),
            "user_id": user_id,
            "expires_at": expires_at,
            "created_at": datetime.now(timezone.utc),
        }
    )


async def require_active_refresh_session(db: Any, user_id: str, jti: str) -> dict:
    session = await db.refresh_sessions.find_one(
        {"jti_hash": refresh_token_fingerprint(jti), "user_id": user_id}
    )
    expires_at = _parse_datetime(session.get("expires_at")) if session else None
    if not session or session.get("revoked_at") or not expires_at or expires_at <= datetime.now(timezone.utc):
        raise HTTPException(status_code=401, detail="Invalid refresh token")
    return session


async def revoke_refresh_session(db: Any, jti: str, user_id: Optional[str] = None) -> None:
    query = {"jti_hash": refresh_token_fingerprint(jti)}
    if user_id:
        query["user_id"] = user_id
    await db.refresh_sessions.update_one(
        query,
        {"$set": {"revoked_at": datetime.now(timezone.utc)}},
    )


async def consume_active_refresh_session(db: Any, user_id: str, jti: str) -> dict:
    """Atomically mark one unexpired refresh session as consumed."""
    jti_hash = refresh_token_fingerprint(jti)
    candidate = await db.refresh_sessions.find_one({"jti_hash": jti_hash, "user_id": user_id})
    expires_at = _parse_datetime(candidate.get("expires_at")) if candidate else None
    if not candidate or candidate.get("revoked_at") or not expires_at or expires_at <= datetime.now(timezone.utc):
        raise HTTPException(status_code=401, detail="Invalid refresh token")
    expiry_guard: dict = (
        {"$gt": datetime.now(timezone.utc)}
        if isinstance(candidate.get("expires_at"), datetime)
        else candidate["expires_at"]
    )
    session = await db.refresh_sessions.find_one_and_update(
        {
            "jti_hash": jti_hash,
            "user_id": user_id,
            "revoked_at": {"$exists": False},
            "expires_at": expiry_guard,
        },
        {"$set": {"revoked_at": datetime.now(timezone.utc)}},
        return_document=ReturnDocument.AFTER,
    )
    if not session:
        raise HTTPException(status_code=401, detail="Invalid refresh token")
    return session
