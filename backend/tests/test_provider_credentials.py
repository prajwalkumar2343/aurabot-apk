from unittest.mock import patch

import pytest
from fastapi import HTTPException
from pydantic import BaseModel

from app.core.config import settings
from app.services.provider_credentials import resolve_provider_credentials


class ProviderRequest(BaseModel):
    provider: str = "gemini"
    api_key: str = ""
    model: str = ""


def test_managed_account_receives_only_server_owned_gemini_configuration():
    with patch.object(
        settings, "MANAGED_GEMINI_API_KEY", "server-secret"
    ), patch.object(settings, "GEMINI_MODEL", "gemini-managed"):
        resolved = resolve_provider_credentials(
            ProviderRequest(),
            {"id": "user-1", "service_mode": "managed"},
        )

    assert resolved.provider == "gemini"
    assert resolved.api_key == "server-secret"
    assert resolved.model == "gemini-managed"


def test_local_account_cannot_request_server_owned_provider_key():
    with patch.object(settings, "MANAGED_GEMINI_API_KEY", "server-secret"):
        with pytest.raises(HTTPException) as raised:
            resolve_provider_credentials(
                ProviderRequest(),
                {"id": "user-1", "service_mode": "local"},
            )
    assert raised.value.status_code == 400


def test_user_supplied_key_is_not_replaced():
    request = ProviderRequest(
        provider="openai",
        api_key="user-secret",
        model="gpt-model",
    )
    assert resolve_provider_credentials(request, {"service_mode": "local"}) == request


def test_managed_account_cannot_override_server_provider_or_key():
    request = ProviderRequest(
        provider="openai",
        api_key="client-supplied",
        model="client-model",
    )
    with patch.object(settings, "MANAGED_GEMINI_API_KEY", "server-secret"):
        resolved = resolve_provider_credentials(request, {"service_mode": "managed"})
    assert resolved.provider == "gemini"
    assert resolved.api_key == "server-secret"
    assert resolved.model == settings.GEMINI_MODEL
