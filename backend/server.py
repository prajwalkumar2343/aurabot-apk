from dotenv import load_dotenv
from pathlib import Path

ROOT_DIR = Path(__file__).parent
load_dotenv(ROOT_DIR / ".env")

import os
import uuid
import base64
import logging
import tempfile
from datetime import datetime, timezone, timedelta
from typing import List, Optional

import bcrypt
import jwt
from fastapi import FastAPI, APIRouter, HTTPException, Depends, Request, Response
from starlette.middleware.cors import CORSMiddleware
from motor.motor_asyncio import AsyncIOMotorClient
from pydantic import BaseModel, EmailStr, Field

# MongoDB
mongo_url = os.environ["MONGO_URL"]
client = AsyncIOMotorClient(mongo_url)
db = client[os.environ["DB_NAME"]]

# App + router
app = FastAPI(title="Aura Assistant API")
api_router = APIRouter(prefix="/api")

# Config
JWT_ALGORITHM = "HS256"
ACCESS_MIN = 60 * 24  # 1 day (mobile)
REFRESH_DAYS = 30
GEMINI_MODEL = os.environ.get("GEMINI_MODEL", "gemini-2.0-flash-exp")
EMERGENT_LLM_KEY = os.environ.get("EMERGENT_LLM_KEY", "")


# ---------------- Auth helpers ----------------
def jwt_secret() -> str:
    return os.environ["JWT_SECRET"]


def hash_password(pw: str) -> str:
    return bcrypt.hashpw(pw.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(pw: str, hashed: str) -> bool:
    return bcrypt.checkpw(pw.encode("utf-8"), hashed.encode("utf-8"))


def create_access_token(user_id: str, email: str) -> str:
    payload = {
        "sub": user_id,
        "email": email,
        "exp": datetime.now(timezone.utc) + timedelta(minutes=ACCESS_MIN),
        "type": "access",
    }
    return jwt.encode(payload, jwt_secret(), algorithm=JWT_ALGORITHM)


def create_refresh_token(user_id: str) -> str:
    payload = {
        "sub": user_id,
        "exp": datetime.now(timezone.utc) + timedelta(days=REFRESH_DAYS),
        "type": "refresh",
    }
    return jwt.encode(payload, jwt_secret(), algorithm=JWT_ALGORITHM)


async def get_current_user(request: Request) -> dict:
    token = request.cookies.get("access_token")
    if not token:
        auth = request.headers.get("Authorization", "")
        if auth.startswith("Bearer "):
            token = auth[7:]
    if not token:
        raise HTTPException(status_code=401, detail="Not authenticated")
    try:
        payload = jwt.decode(token, jwt_secret(), algorithms=[JWT_ALGORITHM])
        if payload.get("type") != "access":
            raise HTTPException(status_code=401, detail="Invalid token type")
        user = await db.users.find_one(
            {"id": payload["sub"]}, {"_id": 0, "password_hash": 0}
        )
        if not user:
            raise HTTPException(status_code=401, detail="User not found")
        return user
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="Token expired")
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="Invalid token")


# ---------------- Models ----------------
class RegisterIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=6)
    name: Optional[str] = None


class LoginIn(BaseModel):
    email: EmailStr
    password: str


class UserOut(BaseModel):
    id: str
    email: str
    name: Optional[str] = None
    role: str = "user"


class MemoryCreate(BaseModel):
    title: str
    content: str


class MemoryOut(BaseModel):
    id: str
    title: str
    content: str
    created_at: str


class TodoCreate(BaseModel):
    title: str


class TodoUpdate(BaseModel):
    title: Optional[str] = None
    done: Optional[bool] = None


class TodoOut(BaseModel):
    id: str
    title: str
    done: bool
    created_at: str


class ChatIn(BaseModel):
    message: str
    session_id: Optional[str] = None


class ChatOut(BaseModel):
    reply: str
    session_id: str


class TranscribeIn(BaseModel):
    audio_base64: str
    mime_type: str = "audio/m4a"


class TranscribeOut(BaseModel):
    text: str


class GatewayIn(BaseModel):
    action: str
    payload: Optional[dict] = None


class GatewayOut(BaseModel):
    ok: bool
    action: str
    result: dict
    mocked: bool = True


# ---------------- Auth endpoints ----------------
@api_router.post("/auth/register", response_model=UserOut)
async def register(data: RegisterIn, response: Response):
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
    refresh = create_refresh_token(user_id)
    response.set_cookie("access_token", access, httponly=True, secure=False, samesite="lax", max_age=ACCESS_MIN * 60, path="/")
    response.set_cookie("refresh_token", refresh, httponly=True, secure=False, samesite="lax", max_age=REFRESH_DAYS * 86400, path="/")
    response.headers["X-Access-Token"] = access
    return UserOut(id=user_id, email=email, name=doc["name"], role="user")


@api_router.post("/auth/login")
async def login(data: LoginIn, request: Request, response: Response):
    email = data.email.lower().strip()
    ip = request.client.host if request.client else "unknown"
    identifier = f"{ip}:{email}"

    # brute force check
    now = datetime.now(timezone.utc)
    attempt = await db.login_attempts.find_one({"identifier": identifier})
    if attempt and attempt.get("locked_until"):
        locked_until = attempt["locked_until"]
        if isinstance(locked_until, str):
            locked_until = datetime.fromisoformat(locked_until)
        if locked_until > now:
            raise HTTPException(status_code=429, detail="Too many attempts. Try again later.")

    user = await db.users.find_one({"email": email})
    if not user or not verify_password(data.password, user["password_hash"]):
        # increment failed attempts
        count = (attempt or {}).get("count", 0) + 1
        update = {"identifier": identifier, "count": count, "last_attempt": now.isoformat()}
        if count >= 5:
            update["locked_until"] = (now + timedelta(minutes=15)).isoformat()
        await db.login_attempts.update_one({"identifier": identifier}, {"$set": update}, upsert=True)
        raise HTTPException(status_code=401, detail="Invalid email or password")

    await db.login_attempts.delete_one({"identifier": identifier})

    access = create_access_token(user["id"], email)
    refresh = create_refresh_token(user["id"])
    response.set_cookie("access_token", access, httponly=True, secure=False, samesite="lax", max_age=ACCESS_MIN * 60, path="/")
    response.set_cookie("refresh_token", refresh, httponly=True, secure=False, samesite="lax", max_age=REFRESH_DAYS * 86400, path="/")
    return {
        "id": user["id"],
        "email": email,
        "name": user.get("name"),
        "role": user.get("role", "user"),
        "access_token": access,
    }


@api_router.post("/auth/logout")
async def logout(response: Response):
    response.delete_cookie("access_token", path="/")
    response.delete_cookie("refresh_token", path="/")
    return {"ok": True}


@api_router.get("/auth/me", response_model=UserOut)
async def me(user=Depends(get_current_user)):
    return UserOut(
        id=user["id"],
        email=user["email"],
        name=user.get("name"),
        role=user.get("role", "user"),
    )


@api_router.post("/auth/refresh")
async def refresh_token(request: Request, response: Response):
    token = request.cookies.get("refresh_token")
    if not token:
        raise HTTPException(status_code=401, detail="No refresh token")
    try:
        payload = jwt.decode(token, jwt_secret(), algorithms=[JWT_ALGORITHM])
        if payload.get("type") != "refresh":
            raise HTTPException(status_code=401, detail="Invalid token type")
        user = await db.users.find_one({"id": payload["sub"]}, {"_id": 0})
        if not user:
            raise HTTPException(status_code=401, detail="User not found")
        access = create_access_token(user["id"], user["email"])
        response.set_cookie("access_token", access, httponly=True, secure=False, samesite="lax", max_age=ACCESS_MIN * 60, path="/")
        return {"access_token": access}
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="Invalid refresh token")


# ---------------- Memories ----------------
@api_router.get("/memories", response_model=List[MemoryOut])
async def list_memories(user=Depends(get_current_user)):
    cursor = db.memories.find({"user_id": user["id"]}, {"_id": 0}).sort("created_at", -1)
    items = await cursor.to_list(1000)
    return [MemoryOut(id=i["id"], title=i["title"], content=i["content"], created_at=i["created_at"]) for i in items]


@api_router.post("/memories", response_model=MemoryOut)
async def create_memory(data: MemoryCreate, user=Depends(get_current_user)):
    doc = {
        "id": str(uuid.uuid4()),
        "user_id": user["id"],
        "title": data.title,
        "content": data.content,
        "created_at": datetime.now(timezone.utc).isoformat(),
    }
    await db.memories.insert_one(doc)
    return MemoryOut(id=doc["id"], title=doc["title"], content=doc["content"], created_at=doc["created_at"])


@api_router.delete("/memories/{memory_id}")
async def delete_memory(memory_id: str, user=Depends(get_current_user)):
    res = await db.memories.delete_one({"id": memory_id, "user_id": user["id"]})
    if res.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Memory not found")
    return {"ok": True}


# ---------------- Todos ----------------
@api_router.get("/todos", response_model=List[TodoOut])
async def list_todos(user=Depends(get_current_user)):
    cursor = db.todos.find({"user_id": user["id"]}, {"_id": 0}).sort("created_at", -1)
    items = await cursor.to_list(1000)
    return [TodoOut(id=i["id"], title=i["title"], done=i["done"], created_at=i["created_at"]) for i in items]


@api_router.post("/todos", response_model=TodoOut)
async def create_todo(data: TodoCreate, user=Depends(get_current_user)):
    doc = {
        "id": str(uuid.uuid4()),
        "user_id": user["id"],
        "title": data.title,
        "done": False,
        "created_at": datetime.now(timezone.utc).isoformat(),
    }
    await db.todos.insert_one(doc)
    return TodoOut(id=doc["id"], title=doc["title"], done=doc["done"], created_at=doc["created_at"])


@api_router.patch("/todos/{todo_id}", response_model=TodoOut)
async def update_todo(todo_id: str, data: TodoUpdate, user=Depends(get_current_user)):
    updates = {k: v for k, v in data.dict().items() if v is not None}
    if not updates:
        raise HTTPException(status_code=400, detail="No fields to update")
    res = await db.todos.find_one_and_update(
        {"id": todo_id, "user_id": user["id"]},
        {"$set": updates},
        return_document=True,
        projection={"_id": 0},
    )
    if not res:
        raise HTTPException(status_code=404, detail="Todo not found")
    return TodoOut(id=res["id"], title=res["title"], done=res["done"], created_at=res["created_at"])


@api_router.delete("/todos/{todo_id}")
async def delete_todo(todo_id: str, user=Depends(get_current_user)):
    res = await db.todos.delete_one({"id": todo_id, "user_id": user["id"]})
    if res.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Todo not found")
    return {"ok": True}


# ---------------- Assistant chat (Gemini 3.1 Flash Lite) ----------------
@api_router.post("/assistant/chat", response_model=ChatOut)
async def assistant_chat(data: ChatIn, user=Depends(get_current_user)):
    from emergentintegrations.llm.chat import LlmChat, UserMessage

    session_id = data.session_id or str(uuid.uuid4())
    system_msg = (
        "You are Aura, a calm, minimalist voice assistant. "
        "Reply concisely (1-3 short sentences) in plain text. "
        "No markdown, no emoji. Be direct and helpful."
    )
    chat = (
        LlmChat(api_key=EMERGENT_LLM_KEY, session_id=f"user-{user['id']}-{session_id}", system_message=system_msg)
        .with_model("gemini", GEMINI_MODEL)
        .with_params(max_tokens=400)
    )
    try:
        reply = await chat.send_message(UserMessage(text=data.message))
    except Exception as e:
        logging.exception("assistant_chat failed")
        raise HTTPException(status_code=500, detail=f"Assistant error: {str(e)[:200]}")
    return ChatOut(reply=str(reply), session_id=session_id)


# ---------------- Transcription (audio → text via Gemini) ----------------
@api_router.post("/transcribe", response_model=TranscribeOut)
async def transcribe(data: TranscribeIn, user=Depends(get_current_user)):
    from emergentintegrations.llm.chat import LlmChat, UserMessage, FileContentWithMimeType

    try:
        audio_bytes = base64.b64decode(data.audio_base64)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid base64 audio")

    # Write to a temp file so emergentintegrations can attach it
    suffix = ".m4a"
    if "wav" in data.mime_type:
        suffix = ".wav"
    elif "mp3" in data.mime_type or "mpeg" in data.mime_type:
        suffix = ".mp3"
    elif "webm" in data.mime_type:
        suffix = ".webm"

    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
    try:
        tmp.write(audio_bytes)
        tmp.flush()
        tmp.close()

        chat = (
            LlmChat(
                api_key=EMERGENT_LLM_KEY,
                session_id=f"transcribe-{user['id']}-{uuid.uuid4()}",
                system_message="You are a strict audio transcriber. Return only the spoken words in plain text, no punctuation explanations, no preamble.",
            )
            .with_model("gemini", GEMINI_MODEL)
            .with_params(max_tokens=500)
        )
        attachment = FileContentWithMimeType(file_path=tmp.name, mime_type=data.mime_type)
        msg = UserMessage(
            text="Transcribe this audio. Return ONLY the transcription text, nothing else.",
            file_contents=[attachment],
        )
        result = await chat.send_message(msg)
        text = str(result).strip().strip('"')
        return TranscribeOut(text=text)
    except Exception as e:
        logging.exception("transcribe failed")
        raise HTTPException(status_code=500, detail=f"Transcription error: {str(e)[:200]}")
    finally:
        try:
            os.unlink(tmp.name)
        except Exception:
            pass


# ---------------- Supabase Gateway (MOCKED) ----------------
@api_router.post("/gateway/supabase", response_model=GatewayOut)
async def gateway_supabase(data: GatewayIn, user=Depends(get_current_user)):
    """Mocked Supabase gateway. Replace with real Supabase client later."""
    return GatewayOut(
        ok=True,
        action=data.action,
        result={
            "message": f"Mocked response for '{data.action}'",
            "user_id": user["id"],
            "echo": data.payload or {},
            "timestamp": datetime.now(timezone.utc).isoformat(),
        },
        mocked=True,
    )


# ---------------- Health ----------------
@api_router.get("/")
async def root():
    return {"status": "ok", "service": "aura-assistant"}


@api_router.get("/health")
async def health():
    return {"status": "healthy", "model": GEMINI_MODEL}


# Include router
app.include_router(api_router)

app.add_middleware(
    CORSMiddleware,
    allow_credentials=True,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)


@app.on_event("startup")
async def startup():
    # Indexes
    try:
        await db.users.create_index("email", unique=True)
        await db.users.create_index("id", unique=True)
        await db.memories.create_index([("user_id", 1), ("created_at", -1)])
        await db.todos.create_index([("user_id", 1), ("created_at", -1)])
        await db.login_attempts.create_index("identifier")
    except Exception as e:
        logger.warning(f"Index creation warning: {e}")

    # Seed admin
    admin_email = os.environ.get("ADMIN_EMAIL", "admin@aura.app").lower().strip()
    admin_password = os.environ.get("ADMIN_PASSWORD", "admin123")
    existing = await db.users.find_one({"email": admin_email})
    if not existing:
        await db.users.insert_one({
            "id": str(uuid.uuid4()),
            "email": admin_email,
            "name": "Admin",
            "role": "admin",
            "password_hash": hash_password(admin_password),
            "created_at": datetime.now(timezone.utc).isoformat(),
        })
        logger.info(f"Seeded admin user: {admin_email}")
    elif not verify_password(admin_password, existing["password_hash"]):
        await db.users.update_one(
            {"email": admin_email},
            {"$set": {"password_hash": hash_password(admin_password)}},
        )
        logger.info("Updated admin password hash from .env")


@app.on_event("shutdown")
async def shutdown():
    client.close()
