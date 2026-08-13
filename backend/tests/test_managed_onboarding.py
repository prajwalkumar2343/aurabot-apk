import pytest
from fastapi import HTTPException

from app.core.config import settings
from app.models.chat import ChatIn
from app.services.google_identity import (
    GoogleIdentityUnavailableError,
    verify_google_id_token,
)
from app.services.provider_credentials import resolve_provider_credentials


def test_managed_account_receives_server_gemini_key(monkeypatch):
    monkeypatch.setattr(settings, "MANAGED_GEMINI_API_KEY", "server-owned-key")
    request = ChatIn(message="hello", model="gemini-test")

    resolved = resolve_provider_credentials(request, {"service_mode": "managed"})

    assert resolved.api_key == "server-owned-key"
    assert resolved.provider == "gemini"
    assert request.api_key == ""


def test_managed_account_cannot_override_server_credentials(monkeypatch):
    monkeypatch.setattr(settings, "MANAGED_GEMINI_API_KEY", "server-owned-key")
    monkeypatch.setattr(settings, "GEMINI_MODEL", "managed-model")
    request = ChatIn(
        message="hello",
        provider="openai",
        api_key="client-supplied-key",
        model="client-model",
    )

    resolved = resolve_provider_credentials(request, {"service_mode": "managed"})

    assert resolved.provider == "gemini"
    assert resolved.api_key == "server-owned-key"
    assert resolved.model == "managed-model"


def test_local_account_must_supply_its_own_key():
    request = ChatIn(message="hello", model="gemini-test")

    with pytest.raises(HTTPException) as error:
        resolve_provider_credentials(request, {"service_mode": "local"})

    assert error.value.status_code == 400
    assert error.value.detail == "Provider API key is required"


def test_local_account_key_is_never_replaced(monkeypatch):
    monkeypatch.setattr(settings, "MANAGED_GEMINI_API_KEY", "server-owned-key")
    request = ChatIn(message="hello", api_key="user-owned-key", model="gemini-test")

    resolved = resolve_provider_credentials(request, {"service_mode": "local"})

    assert resolved is request
    assert resolved.api_key == "user-owned-key"


def test_google_verification_fails_closed_when_not_configured(monkeypatch):
    monkeypatch.setattr(settings, "GOOGLE_WEB_CLIENT_ID", "")

    with pytest.raises(GoogleIdentityUnavailableError, match="not configured"):
        verify_google_id_token("untrusted-token", "nonce")
