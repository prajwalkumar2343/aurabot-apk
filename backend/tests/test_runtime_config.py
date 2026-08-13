import pytest

from app.core.config import settings


def _configure_valid_production(monkeypatch) -> None:
    monkeypatch.setattr(settings, "ENVIRONMENT", "production")
    monkeypatch.setattr(settings, "JWT_SECRET", "x" * 64)
    monkeypatch.setattr(settings, "COOKIE_SECURE", True)
    monkeypatch.setattr(settings, "CORS_ORIGINS", ("https://aura.example",))
    monkeypatch.setattr(
        settings,
        "MONGO_URL",
        "mongodb+srv://aura-user:secret@cluster.example/aura",
    )
    monkeypatch.setattr(settings, "AGENT_CREDENTIAL_KEY", "x" * 32)
    monkeypatch.setattr(settings, "AGENT_EMBEDDED_WORKER", False)
    monkeypatch.setattr(settings, "GOOGLE_WEB_CLIENT_ID", "web-client-id")
    monkeypatch.setattr(settings, "MANAGED_GEMINI_API_KEY", "managed-key")


def test_production_runtime_accepts_authenticated_tls_mongo(monkeypatch):
    _configure_valid_production(monkeypatch)
    settings.validate_for_runtime()


@pytest.mark.parametrize(
    "mongo_url",
    (
        "mongodb://aura-user:secret@cluster.example/aura",
        "mongodb://cluster.example/aura?tls=true",
        "mongodb://aura-user:secret@cluster.example/aura?tls=true&tlsInsecure=true",
    ),
)
def test_production_runtime_rejects_insecure_mongo(monkeypatch, mongo_url):
    _configure_valid_production(monkeypatch)
    monkeypatch.setattr(settings, "MONGO_URL", mongo_url)

    with pytest.raises(RuntimeError, match="MONGO_URL"):
        settings.validate_for_runtime()
