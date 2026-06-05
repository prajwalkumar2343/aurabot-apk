import os
from pathlib import Path
from dotenv import load_dotenv

# Define directories
APP_DIR = Path(__file__).resolve().parent.parent
ROOT_DIR = APP_DIR.parent

# Load .env file if it exists
load_dotenv(ROOT_DIR / ".env")

class Settings:
    MONGO_URL: str = os.environ.get("MONGO_URL", "mongodb://localhost:27017")
    DB_NAME: str = os.environ.get("DB_NAME", "aura_assistant")
    SUPERMEMORY_API_KEY: str = os.environ.get("SUPERMEMORY_API_KEY", "")
    SUPERMEMORY_BASE_URL: str = os.environ.get("SUPERMEMORY_BASE_URL", "https://api.supermemory.ai")
    JWT_SECRET: str = os.environ.get("JWT_SECRET", "super_secret_default_jwt_key_please_change_in_production")
    JWT_ALGORITHM: str = "HS256"
    ACCESS_MIN: int = 60 * 24  # 1 day (mobile)
    REFRESH_DAYS: int = 30
    
    # LLM Settings
    GEMINI_MODEL: str = os.environ.get("GEMINI_MODEL", "gemini-3-flash-preview")
    GEMINI_API_KEY: str = os.environ.get("GEMINI_API_KEY", os.environ.get("EMERGENT_LLM_KEY", ""))
    
    # Admin Settings
    ADMIN_EMAIL: str = os.environ.get("ADMIN_EMAIL", "admin@aura.app")
    ADMIN_PASSWORD: str = os.environ.get("ADMIN_PASSWORD", "admin123")

settings = Settings()
