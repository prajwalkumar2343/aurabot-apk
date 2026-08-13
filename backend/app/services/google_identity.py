import hmac
from typing import Any

from app.core.config import settings


class GoogleIdentityError(ValueError):
    pass


class GoogleIdentityUnavailableError(GoogleIdentityError):
    pass


def verify_google_id_token(token: str, expected_nonce: str) -> dict[str, Any]:
    """Verify a Google ID token for Aura's server-side OAuth client."""
    if not settings.GOOGLE_WEB_CLIENT_ID:
        raise GoogleIdentityUnavailableError("Google sign-in is not configured")

    try:
        from google.auth import exceptions as google_exceptions
        from google.auth.transport import requests as google_requests
        from google.oauth2 import id_token
    except ImportError as error:
        raise GoogleIdentityUnavailableError(
            "Google identity verification is unavailable"
        ) from error
    try:
        claims = id_token.verify_oauth2_token(
            token,
            google_requests.Request(),
            settings.GOOGLE_WEB_CLIENT_ID,
        )
    except (ValueError, TypeError) as error:
        raise GoogleIdentityError("Invalid Google ID token") from error
    except google_exceptions.GoogleAuthError as error:
        raise GoogleIdentityUnavailableError(
            "Google identity verification is unavailable"
        ) from error

    subject = str(claims.get("sub") or "").strip()
    email = str(claims.get("email") or "").lower().strip()
    issuer = str(claims.get("iss") or "")
    if issuer not in {"accounts.google.com", "https://accounts.google.com"}:
        raise GoogleIdentityError("Google ID token issuer is invalid")
    if not subject or not email or claims.get("email_verified") is not True:
        raise GoogleIdentityError("Google account email is not verified")
    token_nonce = str(claims.get("nonce") or "")
    if not token_nonce or not hmac.compare_digest(token_nonce, expected_nonce):
        raise GoogleIdentityError("Google sign-in nonce is invalid")
    return claims
