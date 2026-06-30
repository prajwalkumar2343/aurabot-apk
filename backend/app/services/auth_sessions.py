import hashlib
import hmac
from datetime import datetime, timezone
from typing import Any

from fastapi import HTTPException

from app.core.config import settings


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
            "expires_at": expires_at.isoformat(),
            "created_at": datetime.now(timezone.utc).isoformat(),
        }
    )


async def require_active_refresh_session(db: Any, user_id: str, jti: str) -> dict:
    session = await db.refresh_sessions.find_one(
        {"jti_hash": refresh_token_fingerprint(jti), "user_id": user_id}
    )
    if not session or session.get("revoked_at"):
        raise HTTPException(status_code=401, detail="Invalid refresh token")
    return session


async def revoke_refresh_session(db: Any, jti: str) -> None:
    await db.refresh_sessions.update_one(
        {"jti_hash": refresh_token_fingerprint(jti)},
        {"$set": {"revoked_at": datetime.now(timezone.utc).isoformat()}},
    )
