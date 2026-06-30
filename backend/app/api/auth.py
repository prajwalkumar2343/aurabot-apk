import uuid
import jwt
from datetime import datetime, timezone, timedelta
from typing import Optional
from fastapi import APIRouter, HTTPException, Depends, Request, Response
from pymongo import ReturnDocument
from app.core.database import get_db
from app.core.security import (
    hash_password,
    verify_password,
    create_access_token,
    create_refresh_token,
    get_current_user,
)
from app.models.auth import RegisterIn, LoginIn, UserOut
from app.core.config import settings
from app.services.auth_sessions import (
    require_active_refresh_session,
    revoke_refresh_session,
    store_refresh_session,
)

router = APIRouter(prefix="/auth", tags=["Authentication"])
LOCKOUT_THRESHOLD = 5
LOCKOUT_DURATION = timedelta(minutes=15)


def _parse_datetime(value):
    if isinstance(value, datetime):
        return value
    if isinstance(value, str):
        return datetime.fromisoformat(value)
    return None


def _set_auth_cookies(response: Response, access: str, refresh: Optional[str] = None) -> None:
    response.set_cookie(
        "access_token",
        access,
        httponly=True,
        secure=settings.COOKIE_SECURE,
        samesite="lax",
        max_age=settings.ACCESS_MIN * 60,
        path="/",
    )
    if refresh is not None:
        response.set_cookie(
            "refresh_token",
            refresh,
            httponly=True,
            secure=settings.COOKIE_SECURE,
            samesite="lax",
            max_age=settings.REFRESH_DAYS * 86400,
            path="/",
        )


async def _issue_refresh_token(db, user_id: str) -> str:
    jti = str(uuid.uuid4())
    expires_at = datetime.now(timezone.utc) + timedelta(days=settings.REFRESH_DAYS)
    token = create_refresh_token(user_id, jti)
    await store_refresh_session(db, user_id, jti, expires_at)
    return token


async def _load_unlocked_attempt(db, identifier: str, now: datetime):
    attempt = await db.login_attempts.find_one({"identifier": identifier})
    locked_until = _parse_datetime(attempt.get("locked_until")) if attempt else None
    if locked_until and locked_until > now:
        raise HTTPException(status_code=429, detail="Too many attempts. Try again later.")
    if locked_until and locked_until <= now:
        await db.login_attempts.update_one(
            {"identifier": identifier},
            {"$set": {"count": 0, "last_attempt": now.isoformat()}, "$unset": {"locked_until": ""}},
        )
    return attempt


async def _record_failed_login(db, identifier: str, now: datetime) -> int:
    attempt = await db.login_attempts.find_one_and_update(
        {"identifier": identifier},
        {
            "$set": {"identifier": identifier, "last_attempt": now.isoformat()},
            "$inc": {"count": 1},
        },
        upsert=True,
        return_document=ReturnDocument.AFTER,
    )
    count = int((attempt or {}).get("count", 1))
    if count >= LOCKOUT_THRESHOLD:
        await db.login_attempts.update_one(
            {"identifier": identifier},
            {"$set": {"locked_until": (now + LOCKOUT_DURATION).isoformat()}},
            upsert=True,
        )
    return count

@router.post("/register", response_model=UserOut)
async def register(data: RegisterIn, response: Response, db = Depends(get_db)):
    email = data.email.lower().strip()
    if await db.users.find_one({"email": email}):
        raise HTTPException(status_code=400, detail="Email already registered")
    user_id = str(uuid.uuid4())
    doc = {
        "id": user_id,
        "email": email,
        "name": data.name or email.split("@")[0],
        "role": "user",
        "password_hash": hash_password(data.password),
        "created_at": datetime.now(timezone.utc).isoformat(),
    }
    await db.users.insert_one(doc)
    access = create_access_token(user_id, email)
    refresh = await _issue_refresh_token(db, user_id)
    _set_auth_cookies(response, access, refresh)
    response.headers["X-Access-Token"] = access
    return UserOut(id=user_id, email=email, name=doc["name"], role="user")

@router.post("/login")
async def login(data: LoginIn, request: Request, response: Response, db = Depends(get_db)):
    email = data.email.lower().strip()
    ip = request.client.host if request.client else "unknown"
    identifier = f"{ip}:{email}"

    now = datetime.now(timezone.utc)
    await _load_unlocked_attempt(db, identifier, now)

    user = await db.users.find_one({"email": email})
    if not user or not verify_password(data.password, user["password_hash"]):
        count = await _record_failed_login(db, identifier, now)
        if count >= LOCKOUT_THRESHOLD:
            raise HTTPException(status_code=429, detail="Too many attempts. Try again later.")
        raise HTTPException(status_code=401, detail="Invalid email or password")

    await db.login_attempts.delete_one({"identifier": identifier})

    access = create_access_token(user["id"], email)
    refresh = await _issue_refresh_token(db, user["id"])
    _set_auth_cookies(response, access, refresh)
    return {
        "id": user["id"],
        "email": email,
        "name": user.get("name"),
        "role": user.get("role", "user"),
        "access_token": access,
    }

@router.post("/logout")
async def logout(request: Request, response: Response, db = Depends(get_db)):
    token = request.cookies.get("refresh_token")
    if token:
        try:
            payload = jwt.decode(token, settings.JWT_SECRET, algorithms=[settings.JWT_ALGORITHM])
            if payload.get("type") == "refresh" and payload.get("jti"):
                await revoke_refresh_session(db, str(payload["jti"]))
        except jwt.InvalidTokenError:
            pass
    response.delete_cookie("access_token", path="/")
    response.delete_cookie("refresh_token", path="/")
    return {"ok": True}

@router.get("/me", response_model=UserOut)
async def me(user=Depends(get_current_user)):
    return UserOut(
        id=user["id"],
        email=user["email"],
        name=user.get("name"),
        role=user.get("role", "user"),
    )

@router.post("/refresh")
async def refresh_token(request: Request, response: Response, db = Depends(get_db)):
    token = request.cookies.get("refresh_token")
    if not token:
        raise HTTPException(status_code=401, detail="No refresh token")
    try:
        payload = jwt.decode(token, settings.JWT_SECRET, algorithms=[settings.JWT_ALGORITHM])
        if payload.get("type") != "refresh":
            raise HTTPException(status_code=401, detail="Invalid token type")
        user_id = str(payload.get("sub") or "")
        jti = str(payload.get("jti") or "")
        if not user_id or not jti:
            raise HTTPException(status_code=401, detail="Invalid refresh token")
        await require_active_refresh_session(db, user_id, jti)
        user = await db.users.find_one({"id": user_id}, {"_id": 0})
        if not user:
            raise HTTPException(status_code=401, detail="User not found")
        await revoke_refresh_session(db, jti)
        access = create_access_token(user["id"], user["email"])
        refresh = await _issue_refresh_token(db, user["id"])
        _set_auth_cookies(response, access, refresh)
        return {"access_token": access}
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="Invalid refresh token")
