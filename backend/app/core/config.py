import os
import secrets
from pathlib import Path
from dotenv import load_dotenv
from app.services.prompt_harness import DEFAULT_GEMINI_FAST_MODEL

# Define directories
APP_DIR = Path(__file__).resolve().parent.parent
ROOT_DIR = APP_DIR.parent

# Load .env file if it exists
load_dotenv(ROOT_DIR / ".env")

LOCAL_ENVIRONMENTS = {"development", "test"}
GENERATED_JWT_SECRET = secrets.token_urlsafe(48)
LEGACY_INSECURE_JWT_SECRET = "super_secret_default_jwt_key_please_change_in_production"


def _csv(value: str) -> tuple[str, ...]:
    return tuple(item.strip() for item in value.split(",") if item.strip())


def _bool_env(name: str, default: bool = False) -> bool:
    value = os.environ.get(name)
    if value is None:
        return default
    return value.lower().strip() in {"1", "true", "yes", "on"}


class Settings:
    ENVIRONMENT: str = os.environ.get("ENVIRONMENT", os.environ.get("AURA_ENV", "development")).lower().strip()
    MONGO_URL: str = os.environ.get("MONGO_URL", "mongodb://localhost:27017")
    DB_NAME: str = os.environ.get("DB_NAME", "aura_assistant")
    SUPERMEMORY_API_KEY: str = os.environ.get("SUPERMEMORY_API_KEY", "")
    SUPERMEMORY_BASE_URL: str = os.environ.get("SUPERMEMORY_BASE_URL", "https://api.supermemory.ai")
    JWT_SECRET: str = os.environ.get("JWT_SECRET", GENERATED_JWT_SECRET)
    JWT_SECRET_CONFIGURED: bool = bool(os.environ.get("JWT_SECRET"))
    JWT_ALGORITHM: str = "HS256"
    ACCESS_MIN: int = 60 * 24  # 1 day (mobile)
    REFRESH_DAYS: int = 30
    CORS_ORIGINS: tuple[str, ...] = _csv(os.environ.get(
        "CORS_ORIGINS",
        "http://localhost:3000,http://localhost:8000,http://127.0.0.1:3000,"
        "http://127.0.0.1:8000,http://10.0.2.2:8001",
    ))
    COOKIE_SECURE: bool = _bool_env("COOKIE_SECURE", ENVIRONMENT in {"prod", "production"})
    
    # LLM Settings
    GEMINI_MODEL: str = os.environ.get("GEMINI_MODEL", DEFAULT_GEMINI_FAST_MODEL)
    GEMINI_API_KEY: str = os.environ.get("GEMINI_API_KEY", os.environ.get("EMERGENT_LLM_KEY", ""))
    OPENAI_TRANSCRIPTION_MODEL: str = os.environ.get("OPENAI_TRANSCRIPTION_MODEL", "gpt-4o-mini-transcribe")
    
    # Admin Settings
    ADMIN_EMAIL: str = os.environ.get("ADMIN_EMAIL", "admin@aura.app")
    ADMIN_PASSWORD: str = os.environ.get("ADMIN_PASSWORD", "")

    def validate_for_runtime(self) -> None:
        if self.ENVIRONMENT in LOCAL_ENVIRONMENTS:
            return
        if self.JWT_SECRET in {GENERATED_JWT_SECRET, LEGACY_INSECURE_JWT_SECRET}:
            raise RuntimeError("JWT_SECRET must be configured outside local development")
        if not self.COOKIE_SECURE:
            raise RuntimeError("COOKIE_SECURE must be enabled outside local development")
        if "*" in self.CORS_ORIGINS:
            raise RuntimeError("CORS_ORIGINS cannot contain '*' outside local development")

settings = Settings()
