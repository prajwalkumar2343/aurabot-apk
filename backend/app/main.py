import logging
import uuid
import asyncio
from contextlib import suppress
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from fastapi import APIRouter, FastAPI, Request
from fastapi.responses import JSONResponse
from starlette.middleware.cors import CORSMiddleware
from app.core.config import settings
from app.core.database import db_manager, get_db
from app.core.security import hash_password
from app.services.memory import init_memory_backend, close_memory_backend
from app.services.agent_queue import AgentRunWorker

# Import API routers
from app.api.auth import router as auth_router
from app.api.memories import router as memories_router
from app.api.todos import router as todos_router
from app.api.assistant import router as assistant_router
from app.api.transcribe import router as transcribe_router
from app.api.gateway import router as gateway_router
from app.api.health import router as health_router
from app.api.mini_apps import router as mini_apps_router

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)
_embedded_worker_task = None


async def startup():
    global _embedded_worker_task
    settings.validate_for_runtime()

    # Connect database
    db_manager.connect()
    db = get_db()

    await db.users.create_index("email", unique=True)
    await db.users.create_index("id", unique=True)
    await db.users.create_index(
        "google_subject", unique=True, sparse=True
    )
    await db.google_auth_challenges.create_index("nonce_hash", unique=True)
    await db.google_auth_challenges.create_index("expires_at", expireAfterSeconds=0)
    await db.memories.create_index([("user_id", 1), ("created_at", -1)])
    await db.todos.create_index([("user_id", 1), ("created_at", -1)])
    await db.mini_app_records.create_index(
        [("user_id", 1), ("mini_app_id", 1), ("record_type", 1), ("created_at", -1)]
    )
    await db.login_attempts.create_index("identifier")
    await db.refresh_sessions.create_index("jti_hash", unique=True)
    await db.refresh_sessions.create_index("expires_at", expireAfterSeconds=0)
    await db.agent_runs.create_index("id", unique=True)
    await db.agent_runs.create_index([("user_id", 1), ("created_at", -1)])
    await db.agent_runs.create_index([("parent_run_id", 1), ("created_at", 1)])
    await db.agent_runs.create_index(
        [("user_id", 1), ("idempotency_key", 1)],
        unique=True,
        partialFilterExpression={"kind": "root", "idempotency_key": {"$exists": True}},
    )
    await db.agent_runs.create_index(
        [("queue_state", 1), ("available_at", 1), ("created_at", 1)]
    )
    await db.agent_runs.create_index("expires_at", expireAfterSeconds=0)
    await db.agent_runs.create_index(
        [("user_id", 1), ("agent", 1), ("agent_session_key", 1), ("updated_at", -1)]
    )
    await db.agent_run_events.create_index(
        [("run_id", 1), ("sequence", 1)], unique=True
    )
    await db.agent_run_events.create_index("expires_at", expireAfterSeconds=0)
    logger.info("Database indexes verified/created successfully.")

    # Bootstrap is explicit and one-way: startup must never reset credentials.
    admin_email = settings.ADMIN_EMAIL.lower().strip()
    admin_password = settings.ADMIN_PASSWORD
    existing = (
        await db.users.find_one({"email": admin_email}) if admin_password else None
    )
    if admin_password and not existing:
        await db.users.insert_one(
            {
                "id": str(uuid.uuid4()),
                "email": admin_email,
                "name": "Admin",
                "role": "admin",
                "password_hash": hash_password(admin_password),
                "created_at": datetime.now(timezone.utc).isoformat(),
            }
        )
        logger.info(f"Seeded admin user: {admin_email}")

    try:
        await init_memory_backend()
        logger.info("Memory backend verified successfully.")
    except Exception as e:
        logger.error(f"Failed to initialize memory backend: {e}")
    if settings.AGENT_EMBEDDED_WORKER:
        _embedded_worker_task = asyncio.create_task(
            AgentRunWorker(db, worker_id="embedded-api-worker").run_forever()
        )
        logger.info("Embedded agent worker started for local development")


async def shutdown():
    global _embedded_worker_task
    if _embedded_worker_task:
        _embedded_worker_task.cancel()
        with suppress(asyncio.CancelledError):
            await _embedded_worker_task
        _embedded_worker_task = None
    await close_memory_backend()
    db_manager.close()


@asynccontextmanager
async def lifespan(app: FastAPI):
    await startup()
    try:
        yield
    finally:
        await shutdown()


# FastAPI app
app = FastAPI(title="Aura Assistant API", lifespan=lifespan)

# Mount standard API sub-router under "/api" prefix
api_router = APIRouter(prefix="/api")
api_router.include_router(auth_router)
api_router.include_router(memories_router)
api_router.include_router(todos_router)
api_router.include_router(assistant_router)
api_router.include_router(transcribe_router)
api_router.include_router(gateway_router)
api_router.include_router(health_router)
api_router.include_router(mini_apps_router)

app.include_router(api_router)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_credentials=True,
    allow_origins=list(settings.CORS_ORIGINS),
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.exception(f"Unhandled exception occurred: {exc}")
    return JSONResponse(
        status_code=500,
        content={
            "detail": "An internal server error occurred. Please try again later."
        },
    )
