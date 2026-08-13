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
    ENVIRONMENT: str = (
        os.environ.get("ENVIRONMENT", os.environ.get("AURA_ENV", "development"))
        .lower()
        .strip()
    )
    MONGO_URL: str = os.environ.get("MONGO_URL", "mongodb://localhost:27017")
    DB_NAME: str = os.environ.get("DB_NAME", "aura_assistant")
    SUPERMEMORY_API_KEY: str = os.environ.get("SUPERMEMORY_API_KEY", "")
    SUPERMEMORY_BASE_URL: str = os.environ.get(
        "SUPERMEMORY_BASE_URL", "https://api.supermemory.ai"
    )
    JWT_SECRET: str = os.environ.get("JWT_SECRET", GENERATED_JWT_SECRET)
    JWT_SECRET_CONFIGURED: bool = bool(os.environ.get("JWT_SECRET"))
    JWT_ALGORITHM: str = "HS256"
    JWT_ISSUER: str = os.environ.get("JWT_ISSUER", "aura-backend")
    JWT_AUDIENCE: str = os.environ.get("JWT_AUDIENCE", "aura-android")
    ACCESS_MIN: int = int(os.environ.get("ACCESS_MIN", "15"))
    REFRESH_DAYS: int = int(os.environ.get("REFRESH_DAYS", "30"))
    CORS_ORIGINS: tuple[str, ...] = _csv(
        os.environ.get(
            "CORS_ORIGINS",
            "http://localhost:3000,http://localhost:8000,http://127.0.0.1:3000,"
            "http://127.0.0.1:8000,http://10.0.2.2:8001",
        )
    )
    COOKIE_SECURE: bool = _bool_env(
        "COOKIE_SECURE", ENVIRONMENT in {"prod", "production"}
    )

    # LLM Settings
    GEMINI_MODEL: str = os.environ.get("GEMINI_MODEL", DEFAULT_GEMINI_FAST_MODEL)
    GEMINI_API_KEY: str = os.environ.get(
        "GEMINI_API_KEY", os.environ.get("EMERGENT_LLM_KEY", "")
    )
    MANAGED_GEMINI_API_KEY: str = os.environ.get(
        "MANAGED_GEMINI_API_KEY", GEMINI_API_KEY
    )
    GOOGLE_WEB_CLIENT_ID: str = os.environ.get("GOOGLE_WEB_CLIENT_ID", "").strip()
    OPENAI_TRANSCRIPTION_MODEL: str = os.environ.get(
        "OPENAI_TRANSCRIPTION_MODEL", "gpt-4o-mini-transcribe"
    )
    AGENT_CREDENTIAL_KEY: str = os.environ.get("AGENT_CREDENTIAL_KEY", "")
    AGENT_EMBEDDED_WORKER: bool = _bool_env(
        "AGENT_EMBEDDED_WORKER", ENVIRONMENT in LOCAL_ENVIRONMENTS
    )
    AGENT_CONTINUOUS_CPU: bool = _bool_env("AGENT_CONTINUOUS_CPU", False)
    AGENT_WORKER_POLL_SECONDS: float = float(
        os.environ.get("AGENT_WORKER_POLL_SECONDS", "0.5")
    )
    AGENT_LEASE_SECONDS: int = int(os.environ.get("AGENT_LEASE_SECONDS", "90"))
    AGENT_RUN_RETENTION_DAYS: int = int(
        os.environ.get("AGENT_RUN_RETENTION_DAYS", "30")
    )

    # Admin Settings
    ADMIN_EMAIL: str = os.environ.get("ADMIN_EMAIL", "admin@aura.app")
    ADMIN_PASSWORD: str = os.environ.get("ADMIN_PASSWORD", "")

    def validate_for_runtime(self) -> None:
        if self.ACCESS_MIN < 1 or self.REFRESH_DAYS < 1:
            raise RuntimeError("Token lifetimes must be positive")
        if self.AGENT_LEASE_SECONDS < 15:
            raise RuntimeError("AGENT_LEASE_SECONDS must be at least 15")
        if self.AGENT_WORKER_POLL_SECONDS <= 0:
            raise RuntimeError("AGENT_WORKER_POLL_SECONDS must be positive")
        if self.AGENT_RUN_RETENTION_DAYS < 1:
            raise RuntimeError("AGENT_RUN_RETENTION_DAYS must be positive")
        if self.ENVIRONMENT in LOCAL_ENVIRONMENTS:
            return
        if self.JWT_SECRET in {GENERATED_JWT_SECRET, LEGACY_INSECURE_JWT_SECRET}:
            raise RuntimeError(
                "JWT_SECRET must be configured outside local development"
            )
        if not self.COOKIE_SECURE:
            raise RuntimeError(
                "COOKIE_SECURE must be enabled outside local development"
            )
        if "*" in self.CORS_ORIGINS:
            raise RuntimeError(
                "CORS_ORIGINS cannot contain '*' outside local development"
            )
        mongo_url = self.MONGO_URL.lower()
        insecure_mongo_flags = (
            "tls=false",
            "ssl=false",
            "tlsinsecure=true",
            "tlsallowinvalidcertificates=true",
            "tlsallowinvalidhostnames=true",
        )
        mongo_tls_enabled = mongo_url.startswith("mongodb+srv://") or any(
            flag in mongo_url for flag in ("tls=true", "ssl=true")
        )
        mongo_authority = mongo_url.split("//", 1)[-1].split("/", 1)[0]
        if (
            not mongo_tls_enabled
            or any(flag in mongo_url for flag in insecure_mongo_flags)
            or "@" not in mongo_authority
        ):
            raise RuntimeError(
                "Production MONGO_URL must include credentials and verified TLS"
            )
        if len(self.AGENT_CREDENTIAL_KEY) < 32:
            raise RuntimeError(
                "AGENT_CREDENTIAL_KEY must contain at least 32 characters"
            )
        if self.AGENT_EMBEDDED_WORKER and not self.AGENT_CONTINUOUS_CPU:
            raise RuntimeError(
                "Production embedded workers require AGENT_CONTINUOUS_CPU=true and "
                "Cloud Run instance-based billing with at least one warm instance"
            )
        if not self.GOOGLE_WEB_CLIENT_ID:
            raise RuntimeError("GOOGLE_WEB_CLIENT_ID must be configured in production")
        if not self.MANAGED_GEMINI_API_KEY:
            raise RuntimeError(
                "MANAGED_GEMINI_API_KEY must be configured in production"
            )


settings = Settings()
