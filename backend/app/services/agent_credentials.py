import base64
import hashlib

from cryptography.fernet import Fernet, InvalidToken

from app.core.config import settings


class AgentCredentialError(RuntimeError):
    pass


def _cipher() -> Fernet:
    secret = settings.AGENT_CREDENTIAL_KEY or settings.JWT_SECRET
    if not secret:
        raise AgentCredentialError("Agent credential encryption is not configured")
    key = base64.urlsafe_b64encode(hashlib.sha256(secret.encode("utf-8")).digest())
    return Fernet(key)


def seal_agent_credential(api_key: str) -> str:
    if not api_key.strip():
        raise AgentCredentialError("Provider API key is required")
    return _cipher().encrypt(api_key.encode("utf-8")).decode("ascii")


def open_agent_credential(ciphertext: str) -> str:
    try:
        return _cipher().decrypt(ciphertext.encode("ascii")).decode("utf-8")
    except (InvalidToken, ValueError, UnicodeError) as error:
        raise AgentCredentialError("Agent credential could not be decrypted") from error
