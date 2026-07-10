import logging
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from fastapi import FastAPI, APIRouter
from starlette.middleware.cors import CORSMiddleware
from app.core.config import settings
from app.core.database import db_manager, get_db
from app.core.security import hash_password, verify_password
from app.services.memory import init_memory_backend, close_memory_backend

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

async def startup():
    settings.validate_for_runtime()

    # Connect database
    db_manager.connect()
    db = get_db()
    
    await db.users.create_index("email", unique=True)
    await db.users.create_index("id", unique=True)
    await db.memories.create_index([("user_id", 1), ("created_at", -1)])
    await db.todos.create_index([("user_id", 1), ("created_at", -1)])
    await db.mini_app_records.create_index([("user_id", 1), ("mini_app_id", 1), ("record_type", 1), ("created_at", -1)])
    await db.login_attempts.create_index("identifier")
    await db.refresh_sessions.create_index("jti_hash", unique=True)
    await db.refresh_sessions.create_index("expires_at", expireAfterSeconds=0)
    logger.info("Database indexes verified/created successfully.")

    # Bootstrap is explicit and one-way: startup must never reset credentials.
    admin_email = settings.ADMIN_EMAIL.lower().strip()
    admin_password = settings.ADMIN_PASSWORD
    existing = await db.users.find_one({"email": admin_email}) if admin_password else None
    if admin_password and not existing:
        await db.users.insert_one({
            "id": str(uuid.uuid4()),
            "email": admin_email,
            "name": "Admin",
            "role": "admin",
            "password_hash": hash_password(admin_password),
            "created_at": datetime.now(timezone.utc).isoformat(),
        })
        logger.info(f"Seeded admin user: {admin_email}")

    try:
        await init_memory_backend()
        logger.info("Memory backend verified successfully.")
    except Exception as e:
        logger.error(f"Failed to initialize memory backend: {e}")

async def shutdown():
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

from fastapi.responses import JSONResponse
from fastapi import Request

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.exception(f"Unhandled exception occurred: {exc}")
    return JSONResponse(
        status_code=500,
        content={"detail": "An internal server error occurred. Please try again later."},
    )
