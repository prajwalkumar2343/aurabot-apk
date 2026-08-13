import hashlib
import hmac
import secrets
import uuid
import jwt
from datetime import datetime, timezone, timedelta
from typing import Optional
from fastapi import APIRouter, HTTPException, Depends, Request, Response
from pymongo import ReturnDocument
from pymongo.errors import DuplicateKeyError
from app.core.database import get_db
from app.core.security import (
    hash_password,
    verify_password,
    create_access_token,
    create_refresh_token,
    decode_token,
    get_current_user,
)
from app.models.auth import (
    AuthOut,
    GoogleChallengeOut,
    GoogleLoginIn,
    LoginIn,
    RefreshIn,
    RegisterIn,
    UserOut,
)
from app.core.config import settings
from app.services.auth_sessions import (
    consume_active_refresh_session,
    revoke_refresh_session,
    store_refresh_session,
)
from app.services.google_identity import (
    GoogleIdentityError,
    GoogleIdentityUnavailableError,
    verify_google_id_token,
)

router = APIRouter(prefix="/auth", tags=["Authentication"])
LOCKOUT_THRESHOLD = 5
LOCKOUT_DURATION = timedelta(minutes=15)
GOOGLE_CHALLENGE_DURATION = timedelta(minutes=5)


def _nonce_fingerprint(nonce: str) -> str:
    return hashlib.sha256(nonce.encode("utf-8")).hexdigest()


def _issue_google_nonce(now: datetime) -> str:
    random_value = secrets.token_urlsafe(32)
    expires_at = int((now + GOOGLE_CHALLENGE_DURATION).timestamp())
    payload = f"{random_value}.{expires_at}"
    signature = hmac.new(
        settings.JWT_SECRET.encode("utf-8"), payload.encode("utf-8"), hashlib.sha256
    ).hexdigest()
    return f"{payload}.{signature}"


def _validate_google_nonce(nonce: str, now: datetime) -> None:
    try:
        random_value, expires_at_raw, supplied_signature = nonce.split(".", 2)
        expires_at = int(expires_at_raw)
    except (TypeError, ValueError):
        raise HTTPException(
            status_code=401, detail="Google sign-in challenge is invalid or expired"
        )
    payload = f"{random_value}.{expires_at_raw}"
    expected_signature = hmac.new(
        settings.JWT_SECRET.encode("utf-8"), payload.encode("utf-8"), hashlib.sha256
    ).hexdigest()
    if (
        len(random_value) < 32
        or expires_at <= int(now.timestamp())
        or not hmac.compare_digest(supplied_signature, expected_signature)
    ):
        raise HTTPException(
            status_code=401, detail="Google sign-in challenge is invalid or expired"
        )


def _parse_datetime(value):
    if isinstance(value, datetime):
        return value
    if isinstance(value, str):
        return datetime.fromisoformat(value)
    return None


def _set_auth_cookies(
    response: Response, access: str, refresh: Optional[str] = None
) -> None:
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


def _refresh_token_from_request(
    request: Request, data: Optional[RefreshIn]
) -> Optional[str]:
    if data and data.refresh_token:
        return data.refresh_token
    return request.cookies.get("refresh_token")


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
        raise HTTPException(
            status_code=429, detail="Too many attempts. Try again later."
        )
    if locked_until and locked_until <= now:
        await db.login_attempts.update_one(
            {"identifier": identifier},
            {
                "$set": {"count": 0, "last_attempt": now.isoformat()},
                "$unset": {"locked_until": ""},
            },
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


@router.post("/register", response_model=AuthOut)
async def register(data: RegisterIn, response: Response, db=Depends(get_db)):
    email = data.email.lower().strip()
    if await db.users.find_one({"email": email}):
        raise HTTPException(status_code=400, detail="Email already registered")
    user_id = str(uuid.uuid4())
    doc = {
        "id": user_id,
        "email": email,
        "name": data.name or email.split("@")[0],
        "role": "user",
        "service_mode": "local",
        "password_hash": hash_password(data.password),
        "created_at": datetime.now(timezone.utc).isoformat(),
    }
    await db.users.insert_one(doc)
    access = create_access_token(user_id, email)
    refresh = await _issue_refresh_token(db, user_id)
    _set_auth_cookies(response, access, refresh)
    response.headers["X-Access-Token"] = access
    return {
        "id": user_id,
        "email": email,
        "name": doc["name"],
        "role": "user",
        "service_mode": "local",
        "access_token": access,
        "refresh_token": refresh,
    }


@router.post("/login", response_model=AuthOut)
async def login(
    data: LoginIn, request: Request, response: Response, db=Depends(get_db)
):
    email = data.email.lower().strip()
    ip = request.client.host if request.client else "unknown"
    identifier = f"{ip}:{email}"

    now = datetime.now(timezone.utc)
    await _load_unlocked_attempt(db, identifier, now)

    user = await db.users.find_one({"email": email})
    if (
        not user
        or not user.get("password_hash")
        or not verify_password(data.password, user["password_hash"])
    ):
        count = await _record_failed_login(db, identifier, now)
        if count >= LOCKOUT_THRESHOLD:
            raise HTTPException(
                status_code=429, detail="Too many attempts. Try again later."
            )
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
        "service_mode": user.get("service_mode", "local"),
        "access_token": access,
        "refresh_token": refresh,
    }


@router.post("/google/challenge", response_model=GoogleChallengeOut)
async def google_challenge():
    if not settings.GOOGLE_WEB_CLIENT_ID:
        raise HTTPException(status_code=503, detail="Google sign-in is not configured")
    now = datetime.now(timezone.utc)
    nonce = _issue_google_nonce(now)
    return GoogleChallengeOut(
        nonce=nonce,
        expires_in_seconds=int(GOOGLE_CHALLENGE_DURATION.total_seconds()),
    )


@router.post("/google", response_model=AuthOut)
async def google_login(data: GoogleLoginIn, response: Response, db=Depends(get_db)):
    now = datetime.now(timezone.utc)
    _validate_google_nonce(data.nonce, now)
    nonce_hash = _nonce_fingerprint(data.nonce)
    if await db.google_auth_challenges.find_one({"nonce_hash": nonce_hash}):
        raise HTTPException(
            status_code=401, detail="Google sign-in challenge is invalid or expired"
        )
    try:
        claims = verify_google_id_token(data.id_token, data.nonce)
    except GoogleIdentityUnavailableError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    except GoogleIdentityError as error:
        raise HTTPException(status_code=401, detail=str(error)) from error

    try:
        await db.google_auth_challenges.insert_one(
            {
                "nonce_hash": nonce_hash,
                "used_at": now,
                "expires_at": now + GOOGLE_CHALLENGE_DURATION,
            }
        )
    except DuplicateKeyError:
        raise HTTPException(
            status_code=401, detail="Google sign-in challenge is invalid or expired"
        )

    subject = str(claims["sub"])
    email = str(claims["email"]).lower().strip()
    user = await db.users.find_one({"google_subject": subject})
    if user is None:
        existing_email = await db.users.find_one({"email": email})
        if existing_email is not None:
            raise HTTPException(
                status_code=409,
                detail="An Aura account already exists for this email. Sign in to that account first.",
            )
        candidate = {
            "id": str(uuid.uuid4()),
            "email": email,
            "name": claims.get("name") or email.split("@")[0],
            "role": "user",
            "service_mode": "managed",
            "google_subject": subject,
            "created_at": datetime.now(timezone.utc).isoformat(),
        }
        try:
            await db.users.insert_one(candidate)
            user = candidate
        except DuplicateKeyError:
            # Two valid callbacks for the same Google account may arrive together.
            # Only accept the winner if it has the same immutable Google subject.
            user = await db.users.find_one({"google_subject": subject})
            if user is None:
                raise HTTPException(
                    status_code=409,
                    detail="An Aura account already exists for this email. Sign in to that account first.",
                )

    if user.get("service_mode") != "managed":
        raise HTTPException(
            status_code=409,
            detail="This account is not configured for managed Google sign-in.",
        )

    access = create_access_token(user["id"], user["email"])
    refresh = await _issue_refresh_token(db, user["id"])
    _set_auth_cookies(response, access, refresh)
    return {
        "id": user["id"],
        "email": user["email"],
        "name": user.get("name"),
        "role": user.get("role", "user"),
        "service_mode": user.get("service_mode", "managed"),
        "access_token": access,
        "refresh_token": refresh,
    }


@router.post("/logout")
async def logout(
    request: Request,
    response: Response,
    data: Optional[RefreshIn] = None,
    db=Depends(get_db),
):
    token = _refresh_token_from_request(request, data)
    if token:
        try:
            payload = decode_token(token)
            if payload.get("type") == "refresh" and payload.get("jti"):
                user_id = str(payload.get("sub") or "")
                await revoke_refresh_session(db, str(payload["jti"]), user_id or None)
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
        service_mode=user.get("service_mode", "local"),
    )


@router.post("/refresh")
async def refresh_token(
    request: Request,
    response: Response,
    data: Optional[RefreshIn] = None,
    db=Depends(get_db),
):
    token = _refresh_token_from_request(request, data)
    if not token:
        raise HTTPException(status_code=401, detail="No refresh token")
    try:
        payload = decode_token(token)
        if payload.get("type") != "refresh":
            raise HTTPException(status_code=401, detail="Invalid token type")
        user_id = str(payload.get("sub") or "")
        jti = str(payload.get("jti") or "")
        if not user_id or not jti:
            raise HTTPException(status_code=401, detail="Invalid refresh token")
        await consume_active_refresh_session(db, user_id, jti)
        user = await db.users.find_one({"id": user_id}, {"_id": 0})
        if not user:
            raise HTTPException(status_code=401, detail="User not found")
        access = create_access_token(user["id"], user["email"])
        refresh = await _issue_refresh_token(db, user["id"])
        _set_auth_cookies(response, access, refresh)
        return {"access_token": access, "refresh_token": refresh}
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="Invalid refresh token")
