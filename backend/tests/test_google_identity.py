from unittest.mock import patch

import pytest

from app.core.config import settings
from app.services.google_identity import GoogleIdentityError, verify_google_id_token


def test_google_identity_requires_matching_nonce():
    claims = {
        "sub": "subject-1",
        "email": "person@example.com",
        "email_verified": True,
        "iss": "https://accounts.google.com",
        "nonce": "expected-nonce",
    }
    with patch.object(settings, "GOOGLE_WEB_CLIENT_ID", "web-client-id"), patch(
        "google.oauth2.id_token.verify_oauth2_token", return_value=claims
    ):
        assert verify_google_id_token("token", "expected-nonce") == claims
        with pytest.raises(GoogleIdentityError, match="nonce"):
            verify_google_id_token("token", "different-nonce")


def test_google_identity_rejects_unverified_email():
    claims = {
        "sub": "subject-1",
        "email": "person@example.com",
        "email_verified": False,
        "iss": "https://accounts.google.com",
        "nonce": "expected-nonce",
    }
    with patch.object(settings, "GOOGLE_WEB_CLIENT_ID", "web-client-id"), patch(
        "google.oauth2.id_token.verify_oauth2_token", return_value=claims
    ):
        with pytest.raises(GoogleIdentityError, match="not verified"):
            verify_google_id_token("token", "expected-nonce")


def test_google_identity_rejects_unexpected_issuer():
    claims = {
        "sub": "subject-1",
        "email": "person@example.com",
        "email_verified": True,
        "iss": "https://attacker.example",
        "nonce": "expected-nonce",
    }
    with patch.object(settings, "GOOGLE_WEB_CLIENT_ID", "web-client-id"), patch(
        "google.oauth2.id_token.verify_oauth2_token", return_value=claims
    ):
        with pytest.raises(GoogleIdentityError, match="issuer"):
            verify_google_id_token("token", "expected-nonce")
