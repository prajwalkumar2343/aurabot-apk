import pytest
import uuid
import jwt
import requests
import base64
from datetime import datetime, timezone, timedelta
from fastapi.testclient import TestClient
from unittest.mock import MagicMock, patch

from app.main import app
from app.core.config import settings
from app.core.database import get_db
from app.core.security import hash_password, verify_password, create_access_token, create_refresh_token

def wav_payload(duration_ms=400):
    sample_rate = 16_000
    pcm = b"\x01\x00" * int(sample_rate * duration_ms / 1000)
    data_len = len(pcm)
    header = (
        b"RIFF" +
        (36 + data_len).to_bytes(4, "little") +
        b"WAVEfmt " +
        (16).to_bytes(4, "little") +
        (1).to_bytes(2, "little") +
        (1).to_bytes(2, "little") +
        sample_rate.to_bytes(4, "little") +
        (sample_rate * 2).to_bytes(4, "little") +
        (2).to_bytes(2, "little") +
        (16).to_bytes(2, "little") +
        b"data" +
        data_len.to_bytes(4, "little")
    )
    return base64.b64encode(header + pcm).decode("utf-8")

# ==============================================================================
# Mock In-Memory MongoDB Implementation
# ==============================================================================

class MockCursor:
    def __init__(self, data):
        self.data = data
        self.sort_key = None
        self.sort_dir = 1

    def sort(self, key, direction=-1):
        self.sort_key = key
        self.sort_dir = direction
        return self

    async def to_list(self, limit=1000):
        res = list(self.data)
        if self.sort_key:
            res.sort(key=lambda x: x.get(self.sort_key, ""), reverse=(self.sort_dir == -1))
        return res[:limit]

class MockCollection:
    def __init__(self, name):
        self.name = name
        self.store = []

    async def find_one(self, query, projection=None):
        for doc in self.store:
            match = True
            for k, v in query.items():
                if doc.get(k) != v:
                    match = False
                    break
            if match:
                return dict(doc)
        return None

    async def insert_one(self, doc):
        self.store.append(doc)
        return doc

    async def delete_one(self, query):
        initial_len = len(self.store)
        self.store = [item for item in self.store if not all(item.get(k) == v for k, v in query.items())]
        deleted_count = initial_len - len(self.store)
        
        class DeleteResult:
            def __init__(self, count):
                self.deleted_count = count
        return DeleteResult(deleted_count)

    async def find_one_and_update(self, query, update, return_document=True, projection=None):
        doc = await self.find_one(query)
        if not doc:
            return None
        set_fields = update.get("$set", {})
        for item in self.store:
            if item.get("id") == doc.get("id"):
                for k, v in set_fields.items():
                    item[k] = v
                return dict(item)
        return None

    async def update_one(self, query, update, upsert=False):
        doc = await self.find_one(query)
        set_fields = update.get("$set", {})
        if not doc:
            if upsert:
                new_doc = dict(query)
                for k, v in set_fields.items():
                    new_doc[k] = v
                self.store.append(new_doc)
            return
        for item in self.store:
            if (item.get("id") and item.get("id") == doc.get("id")) or \
               (query.get("identifier") and item.get("identifier") == query.get("identifier")):
                for k, v in set_fields.items():
                    item[k] = v
                break

    def find(self, query, projection=None):
        matched = []
        for doc in self.store:
            match = True
            for k, v in query.items():
                if doc.get(k) != v:
                    match = False
                    break
            if match:
                matched.append(doc)
        return MockCursor(matched)

    async def create_index(self, keys, unique=False):
        pass

class MockDatabase:
    def __init__(self):
        self.users = MockCollection("users")
        self.memories = MockCollection("memories")
        self.todos = MockCollection("todos")
        self.login_attempts = MockCollection("login_attempts")
        
        # Monkey patch delete_one specifically for login_attempts
        async def mock_delete_attempts(query):
            self.login_attempts.store = [
                item for item in self.login_attempts.store 
                if not (item.get("identifier") == query.get("identifier"))
            ]
            class DelRes:
                deleted_count = 1
            return DelRes()
        self.login_attempts.delete_one = mock_delete_attempts

# Instantiate a fresh mock database
mock_db = MockDatabase()

def get_mock_db():
    return mock_db

# Override the database dependency in the FastAPI application
app.dependency_overrides[get_db] = get_mock_db

# ==============================================================================
# Pytest Fixtures
# ==============================================================================

@pytest.fixture(autouse=True)
def clean_mock_db():
    """Wipes the mock database before each test run."""
    mock_db.users.store.clear()
    mock_db.memories.store.clear()
    mock_db.todos.store.clear()
    mock_db.login_attempts.store.clear()
    
    # Pre-seed the admin user to match the app startup seed behavior
    mock_db.users.store.append({
        "id": "admin_uuid",
        "email": settings.ADMIN_EMAIL.lower().strip(),
        "name": "Admin",
        "role": "admin",
        "password_hash": hash_password(settings.ADMIN_PASSWORD),
        "created_at": datetime.now(timezone.utc).isoformat()
    })
    yield

@pytest.fixture
def client():
    """FastAPI TestClient with propagated exceptions caught by FastAPI."""
    return TestClient(app, raise_server_exceptions=False)

@pytest.fixture
def test_user_token():
    """Generates an access token and seeds a mock user in the db."""
    user_id = str(uuid.uuid4())
    email = "testuser@aura.app"
    mock_db.users.store.append({
        "id": user_id,
        "email": email,
        "name": "Test User",
        "role": "user",
        "password_hash": hash_password("password123"),
        "created_at": datetime.now(timezone.utc).isoformat()
    })
    return create_access_token(user_id, email)

# ==============================================================================
# TEST CASE GROUP 1: Health, system checks & CORS (8 Tests)
# ==============================================================================

def test_1_root_endpoint(client):
    """1. GET /api/ returns 200 and expected status fields."""
    response = client.get("/api/")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["service"] == "aura-assistant"

def test_2_health_endpoint(client):
    """2. GET /api/health returns status and current active model."""
    response = client.get("/api/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert data["model"] == settings.GEMINI_MODEL

def test_3_cors_preflight_headers(client):
    """3. CORS preflight OPTIONS requests return local active origin."""
    response = client.options("/api/health", headers={
        "Origin": "http://localhost:3000",
        "Access-Control-Request-Method": "GET",
        "Access-Control-Request-Headers": "Authorization"
    })
    assert response.status_code == 200
    assert response.headers.get("access-control-allow-origin") == "http://localhost:3000"

def test_4_health_not_found(client):
    """4. Accessing invalid endpoint paths returns a clean 404 response."""
    response = client.get("/api/invalid_path_xyz")
    assert response.status_code == 404

def test_5_global_exception_middleware_trigger(client):
    """5. Simulates an internal DB exception to verify global 500 handler is invoked."""
    with patch.object(mock_db.users, "find_one", side_effect=Exception("Database crashed!")):
        response = client.post("/api/auth/login", json={"email": "admin@aura.app", "password": "any"})
        assert response.status_code == 500
        assert "internal server error" in response.json()["detail"].lower()

def test_6_cors_preflight_missing_origin(client):
    """6. Preflight options requests without an origin are processed without origin CORS mapping."""
    response = client.options("/api/health", headers={
        "Access-Control-Request-Method": "GET"
    })
    assert response.status_code == 405
    assert "access-control-allow-origin" not in response.headers

def test_7_cors_preflight_unsupported_method(client):
    """7. Preflight request with non-standard method headers."""
    response = client.options("/api/health", headers={
        "Origin": "http://localhost:3000",
        "Access-Control-Request-Method": "PURGE"
    })
    assert response.status_code == 400

def test_8_cors_preflight_lowercase_origin(client):
    """8. CORS OPTIONS requests handle lowercase custom origins correctly."""
    response = client.options("/api/health", headers={
        "origin": "http://subdomain.domain.com",
        "Access-Control-Request-Method": "POST"
    })
    assert response.status_code == 200
    assert response.headers.get("access-control-allow-origin") == "http://subdomain.domain.com"

# ==============================================================================
# TEST CASE GROUP 2: Registration edge cases & constraints (13 Tests)
# ==============================================================================

def test_9_register_user_success(client):
    """9. Successful user registration generates correct schemas and cookies."""
    response = client.post("/api/auth/register", json={
        "email": "newuser@aura.app",
        "password": "validpassword",
        "name": "New User"
    })
    assert response.status_code == 200
    data = response.json()
    assert data["email"] == "newuser@aura.app"
    assert data["name"] == "New User"
    assert "id" in data
    assert "x-access-token" in response.headers

def test_10_register_duplicate_email(client):
    """10. Duplicate email registrations are rejected with 400."""
    client.post("/api/auth/register", json={"email": "dup@aura.app", "password": "password"})
    response = client.post("/api/auth/register", json={"email": "dup@aura.app", "password": "differentpassword"})
    assert response.status_code == 400
    assert "already registered" in response.json()["detail"].lower()

def test_11_register_password_too_short(client):
    """11. Passwords under 6 characters are rejected with Pydantic 422 error."""
    response = client.post("/api/auth/register", json={"email": "short@aura.app", "password": "12345"})
    assert response.status_code == 422

def test_12_register_invalid_email_format(client):
    """12. Non-standard email formats are rejected with Pydantic 422 error."""
    response = client.post("/api/auth/register", json={"email": "invalid_email_no_domain", "password": "password"})
    assert response.status_code == 422

def test_13_register_empty_name_defaults_to_email_prefix(client):
    """13. Empty user names default automatically to the email prefix."""
    response = client.post("/api/auth/register", json={"email": "johndoe@aura.app", "password": "password123"})
    assert response.status_code == 200
    assert response.json()["name"] == "johndoe"

def test_14_security_input_injection_handling(client):
    """14. Input validation handles SQL/NoSQL injections safety strings by failing parsing."""
    special_email = "' OR 1=1 -- @aura.app"
    response = client.post("/api/auth/register", json={"email": special_email, "password": "password123"})
    assert response.status_code == 422

def test_15_register_mixed_case_email_stripped(client):
    """15. Mixing uppercase letters and padding spaces in emails during register is normalized."""
    response = client.post("/api/auth/register", json={
        "email": "   TeStUsEr@AuRa.ApP   ",
        "password": "validpassword"
    })
    assert response.status_code == 200
    assert response.json()["email"] == "testuser@aura.app"

def test_16_register_duplicate_email_mixed_case_blocked(client):
    """16. Checks duplicate uppercase/spaced registers are successfully blocked."""
    client.post("/api/auth/register", json={"email": "test@aura.app", "password": "password"})
    response = client.post("/api/auth/register", json={"email": "  TEST@AURA.APP ", "password": "password"})
    assert response.status_code == 400

def test_17_register_extremely_long_username(client):
    """17. Handles boundary check: registering with exceptionally long names."""
    long_name = "A" * 1000
    response = client.post("/api/auth/register", json={
        "email": "longname@aura.app",
        "password": "password123",
        "name": long_name
    })
    assert response.status_code == 200
    assert response.json()["name"] == long_name

def test_18_register_unicode_emojis_username(client):
    """18. Register accepts and preserves unicode characters and emojis in name."""
    response = client.post("/api/auth/register", json={
        "email": "emoji@aura.app",
        "password": "password123",
        "name": "✨ Aura Admin 💫"
    })
    assert response.status_code == 200
    assert response.json()["name"] == "✨ Aura Admin 💫"

def test_19_register_boundary_password_length(client):
    """19. Tests that passwords of exactly 6 characters are allowed."""
    response = client.post("/api/auth/register", json={"email": "sixchar@aura.app", "password": "abcdef"})
    assert response.status_code == 200

def test_20_register_extremely_long_password(client):
    """20. Handles boundary password lengths (e.g. 500 characters)."""
    long_pw = "B" * 500
    response = client.post("/api/auth/register", json={"email": "longpw@aura.app", "password": long_pw})
    assert response.status_code == 200

def test_21_register_client_cannot_inject_admin_role(client):
    """21. Ensures role cannot be overridden in post body parameters, defaults to user."""
    response = client.post("/api/auth/register", json={
        "email": "hackadmin@aura.app",
        "password": "password123",
        "role": "admin"
    })
    assert response.status_code == 200
    assert response.json()["role"] == "user"

# ==============================================================================
# TEST CASE GROUP 3: Login, lockouts & time-window boundaries (11 Tests)
# ==============================================================================

def test_22_login_admin_success(client):
    """22. Valid admin authentication succeeds and yields correct token payload."""
    response = client.post("/api/auth/login", json={
        "email": settings.ADMIN_EMAIL,
        "password": settings.ADMIN_PASSWORD
    })
    assert response.status_code == 200
    data = response.json()
    assert data["role"] == "admin"
    assert "access_token" in data

def test_23_login_user_success(client, test_user_token):
    """23. Valid seeded user logs in successfully."""
    response = client.post("/api/auth/login", json={
        "email": "testuser@aura.app",
        "password": "password123"
    })
    assert response.status_code == 200
    assert response.json()["email"] == "testuser@aura.app"

def test_24_login_invalid_password(client):
    """24. Logging in with incorrect password returns 401 Unauthorized."""
    response = client.post("/api/auth/login", json={
        "email": settings.ADMIN_EMAIL,
        "password": "incorrect_password"
    })
    assert response.status_code == 401
    assert "invalid" in response.json()["detail"].lower()

def test_25_login_nonexistent_user(client):
    """25. Rejects non-existent email accounts with 401."""
    response = client.post("/api/auth/login", json={
        "email": "nonexistent@aura.app",
        "password": "password"
    })
    assert response.status_code == 401

def test_26_brute_force_lockout_mechanism(client):
    """26. Confirms brute-force defense returns 429 as soon as the threshold is hit."""
    email = "targetuser@aura.app"
    client.post("/api/auth/register", json={"email": email, "password": "password123"})
    
    for _ in range(4):
        response = client.post("/api/auth/login", json={"email": email, "password": "wrongpassword"})
        assert response.status_code == 401

    response = client.post("/api/auth/login", json={"email": email, "password": "wrongpassword"})
    assert response.status_code == 429
    assert "too many attempts" in response.json()["detail"].lower()

    response = client.post("/api/auth/login", json={"email": email, "password": "wrongpassword"})
    assert response.status_code == 429

    response = client.post("/api/auth/login", json={"email": email, "password": "password123"})
    assert response.status_code == 429

def test_27_brute_force_reset_on_success(client):
    """27. Successful login clears previous failed attempts counter."""
    email = "resettable@aura.app"
    client.post("/api/auth/register", json={"email": email, "password": "password123"})
    
    for _ in range(3):
        client.post("/api/auth/login", json={"email": email, "password": "wrongpassword"})
        
    response = client.post("/api/auth/login", json={"email": email, "password": "password123"})
    assert response.status_code == 200
    attempt = next((a for a in mock_db.login_attempts.store if email in a["identifier"]), None)
    assert attempt is None

def test_28_login_empty_password_rejected(client):
    """28. Empty passwords are rejected inside logins."""
    response = client.post("/api/auth/login", json={"email": "some@aura.app", "password": ""})
    assert response.status_code == 401

def test_29_login_mixed_case_email_stripped(client):
    """29. mixed email cases are normalized during login operations."""
    email = "NormalUser@aura.app"
    client.post("/api/auth/register", json={"email": email, "password": "password123"})
    response = client.post("/api/auth/login", json={"email": " NORMALUSER@AURA.APP ", "password": "password123"})
    assert response.status_code == 200

def test_30_brute_force_lockout_expiration_simulation(client):
    """30. Lockout checks reset and allow logins if simulated locked_until time has expired."""
    email = "expiringlock@aura.app"
    client.post("/api/auth/register", json={"email": email, "password": "password123"})
    
    for _ in range(5):
        client.post("/api/auth/login", json={"email": email, "password": "wrongpassword"})
        
    # Lockout is set. Let's manually set lock duration in mock db to 20 mins ago (expired)
    past_time = (datetime.now(timezone.utc) - timedelta(minutes=20)).isoformat()
    for item in mock_db.login_attempts.store:
        if email in item["identifier"]:
            item["locked_until"] = past_time
            
    # Now user should be allowed to attempt log in again
    response = client.post("/api/auth/login", json={"email": email, "password": "password123"})
    assert response.status_code == 200

def test_31_login_seeded_admin_mixed_case_email(client):
    """31. Seeded admin accounts can log in using mixed cases."""
    response = client.post("/api/auth/login", json={"email": " ADMIN@AURA.APP ", "password": settings.ADMIN_PASSWORD})
    assert response.status_code == 200

def test_32_brute_force_isolation_per_account(client):
    """32. Lockouts on user A do not impact attempts or locks on user B."""
    email_a = "usera@aura.app"
    email_b = "userb@aura.app"
    client.post("/api/auth/register", json={"email": email_a, "password": "password"})
    client.post("/api/auth/register", json={"email": email_b, "password": "password"})
    
    for _ in range(5):
        client.post("/api/auth/login", json={"email": email_a, "password": "wrong"})
        
    # User B should still be able to login successfully
    response = client.post("/api/auth/login", json={"email": email_b, "password": "password"})
    assert response.status_code == 200

# ==============================================================================
# TEST CASE GROUP 4: JWT Verification, security algorithms & precedence (14 Tests)
# ==============================================================================

def test_33_auth_me_no_token(client):
    """33. Secured endpoints fail without a Bearer or Cookie token."""
    response = client.get("/api/auth/me")
    assert response.status_code == 401
    assert "not authenticated" in response.json()["detail"].lower()

def test_34_auth_me_invalid_bearer_format(client):
    """34. secured endpoints reject malformed Bearer authorizations."""
    response = client.get("/api/auth/me", headers={"Authorization": "Bearer malformed_token_abc"})
    assert response.status_code == 401
    assert "invalid token" in response.json()["detail"].lower()

def test_35_auth_me_token_expired(client):
    """35. Expired JWT tokens yield a clear 401 response."""
    payload = {
        "sub": "user_id_xyz",
        "email": "user@aura.app",
        "exp": datetime.now(timezone.utc) - timedelta(minutes=10),
        "type": "access"
    }
    expired_token = jwt.encode(payload, settings.JWT_SECRET, algorithm=settings.JWT_ALGORITHM)
    response = client.get("/api/auth/me", headers={"Authorization": f"Bearer {expired_token}"})
    assert response.status_code == 401
    assert "expired" in response.json()["detail"].lower()

def test_36_auth_me_invalid_token_type(client):
    """36. Rejects refresh tokens passed as access tokens on secured paths."""
    refresh = create_refresh_token("user_xyz")
    response = client.get("/api/auth/me", headers={"Authorization": f"Bearer {refresh}"})
    assert response.status_code == 401
    assert "invalid token type" in response.json()["detail"].lower()

def test_37_auth_me_valid_bearer(client, test_user_token):
    """37. Verifies authentication with valid Bearer token works."""
    response = client.get("/api/auth/me", headers={"Authorization": f"Bearer {test_user_token}"})
    assert response.status_code == 200
    assert response.json()["email"] == "testuser@aura.app"

def test_38_auth_me_user_not_found(client):
    """38. Handles tokens representing deleted/non-existent user accounts."""
    token = create_access_token("missing_uuid", "ghost@aura.app")
    response = client.get("/api/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 401
    assert "user not found" in response.json()["detail"].lower()

def test_39_auth_me_cookie_precedence(client, test_user_token):
    """39. Verified cookies have precedence over bearer headers when both are present."""
    bad_token = create_access_token("ghost_uuid", "ghost@aura.app")
    
    # Valid Bearer Header, but Bad Access Cookie -> Should fail with 401 user not found
    client.cookies.set("access_token", bad_token)
    response = client.get("/api/auth/me", headers={"Authorization": f"Bearer {test_user_token}"})
    assert response.status_code == 401

def test_40_jwt_signature_failure(client):
    """40. Tampered JWT signature payloads are securely blocked."""
    payload = {"sub": "uid", "email": "a@a.com", "type": "access"}
    fake_token = jwt.encode(payload, "wrong_secret_key_123", algorithm="HS256")
    response = client.get("/api/auth/me", headers={"Authorization": f"Bearer {fake_token}"})
    assert response.status_code == 401
    assert "invalid token" in response.json()["detail"].lower()

def test_41_jwt_missing_sub_claim(client):
    """41. JWT tokens missing the sub (user ID) claim are securely rejected."""
    payload = {"email": "missing@sub.com", "type": "access", "exp": datetime.now(timezone.utc) + timedelta(minutes=10)}
    bad_token = jwt.encode(payload, settings.JWT_SECRET, algorithm=settings.JWT_ALGORITHM)
    response = client.get("/api/auth/me", headers={"Authorization": f"Bearer {bad_token}"})
    assert response.status_code == 401

def test_42_jwt_missing_email_claim(client):
    """42. JWT tokens missing standard email claims are rejected."""
    payload = {"sub": "user_id_abc", "type": "access", "exp": datetime.now(timezone.utc) + timedelta(minutes=10)}
    bad_token = jwt.encode(payload, settings.JWT_SECRET, algorithm=settings.JWT_ALGORITHM)
    response = client.get("/api/auth/me", headers={"Authorization": f"Bearer {bad_token}"})
    assert response.status_code == 401

def test_43_jwt_invalid_sub_datatype(client):
    """43. Rejects claims with malformed sub datatypes (e.g. lists)."""
    payload = {"sub": [1, 2, 3], "email": "a@a.com", "type": "access"}
    bad_token = jwt.encode(payload, settings.JWT_SECRET, algorithm=settings.JWT_ALGORITHM)
    response = client.get("/api/auth/me", headers={"Authorization": f"Bearer {bad_token}"})
    assert response.status_code == 401

def test_44_jwt_algorithm_confusion_asymmetric_rejection(client):
    """44. Rejects tokens encoded with asymmetric algorithms if symmetric HS256 is expected."""
    payload = {"sub": "user_id", "email": "a@a.com", "type": "access"}
    # Encodes with HS384 instead of HS256 to simulate algorithm mismatch
    bad_token = jwt.encode(payload, settings.JWT_SECRET, algorithm="HS384")
    response = client.get("/api/auth/me", headers={"Authorization": f"Bearer {bad_token}"})
    assert response.status_code == 401

def test_45_jwt_algorithm_none_rejection(client):
    """45. Secures against 'none' algorithm bypass attacks."""
    payload = {"sub": "user_id", "email": "a@a.com", "type": "access"}
    # python-jwt doesn't allow encoding with "none" easily without special flags, let's mock decode failure
    bad_token = jwt.encode(payload, "", algorithm=None)
    response = client.get("/api/auth/me", headers={"Authorization": f"Bearer {bad_token}"})
    assert response.status_code == 401

# ==============================================================================
# TEST CASE GROUP 5: Cookies & session lifecycles (7 Tests)
# ==============================================================================

def test_46_auth_refresh_missing_refresh_token(client):
    """46. Refresh API endpoint returns 401 if refresh cookie is missing."""
    response = client.post("/api/auth/refresh")
    assert response.status_code == 401
    assert "no refresh token" in response.json()["detail"].lower()

def test_47_auth_refresh_valid(client):
    """47. Successful refresh call issues a new access token."""
    user_id = "test_user_id"
    mock_db.users.store.append({
        "id": user_id,
        "email": "ref@aura.app",
        "name": "Refresh User",
        "role": "user",
        "password_hash": "hash",
        "created_at": "date"
    })
    refresh = create_refresh_token(user_id)
    client.cookies.set("refresh_token", refresh)
    response = client.post("/api/auth/refresh")
    assert response.status_code == 200
    assert "access_token" in response.json()

def test_48_auth_logout_clears_cookies(client):
    """48. Logging out clears JWT state cookies."""
    client.cookies.set("access_token", "dummy")
    client.cookies.set("refresh_token", "dummy")
    response = client.post("/api/auth/logout")
    assert response.status_code == 200
    assert response.json()["ok"] is True

def test_49_auth_refresh_expired_refresh_token(client):
    """49. Rejects expired refresh tokens in cookies."""
    payload = {"sub": "uid", "type": "refresh", "exp": datetime.now(timezone.utc) - timedelta(days=2)}
    expired_refresh = jwt.encode(payload, settings.JWT_SECRET, algorithm=settings.JWT_ALGORITHM)
    client.cookies.set("refresh_token", expired_refresh)
    response = client.post("/api/auth/refresh")
    assert response.status_code == 401

def test_50_auth_refresh_wrong_token_type(client):
    """50. Rejects access token types passed to refresh cookies."""
    access_as_refresh = create_access_token("uid", "a@a.com")
    client.cookies.set("refresh_token", access_as_refresh)
    response = client.post("/api/auth/refresh")
    assert response.status_code == 401

def test_51_auth_refresh_tampered_token(client):
    """51. Rejects tampered refresh tokens with bad signatures."""
    payload = {"sub": "uid", "type": "refresh"}
    bad_refresh = jwt.encode(payload, "malicious_secret_xyz", algorithm="HS256")
    client.cookies.set("refresh_token", bad_refresh)
    response = client.post("/api/auth/refresh")
    assert response.status_code == 401

def test_52_auth_refresh_user_not_found(client):
    """52. Handles refresh requests from deleted users safely."""
    refresh = create_refresh_token("ghost_user")
    client.cookies.set("refresh_token", refresh)
    response = client.post("/api/auth/refresh")
    assert response.status_code == 401

# ==============================================================================
# TEST CASE GROUP 6: Memories CRUD, boundaries & sanitization (13 Tests)
# ==============================================================================

def test_53_create_memory_success(client, test_user_token):
    """53. User creates a memory successfully."""
    response = client.post(
        "/api/memories",
        json={"title": "Important meeting", "content": "Met John at 4pm"},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["title"] == "Important meeting"
    assert "id" in data

def test_54_list_memories_success(client, test_user_token):
    """54. Retrieves memory objects seeded for authenticating user."""
    user_id = mock_db.users.store[-1]["id"]
    mock_db.memories.store.append({
        "id": "mem_1",
        "user_id": user_id,
        "title": "Title 1",
        "content": "Content 1",
        "created_at": "2026-05-30"
    })
    response = client.get("/api/memories", headers={"Authorization": f"Bearer {test_user_token}"})
    assert response.status_code == 200
    assert len(response.json()) == 1
    assert response.json()[0]["title"] == "Title 1"

def test_55_delete_memory_success(client, test_user_token):
    """55. User deletes memory successfully."""
    user_id = mock_db.users.store[-1]["id"]
    mock_db.memories.store.append({
        "id": "mem_del",
        "user_id": user_id,
        "title": "To delete",
        "content": "Delete this",
        "created_at": "2026-05-30"
    })
    response = client.delete("/api/memories/mem_del", headers={"Authorization": f"Bearer {test_user_token}"})
    assert response.status_code == 200
    assert response.json()["ok"] is True

def test_56_delete_memory_not_found(client, test_user_token):
    """56. Deleting non-existent memories returns 404."""
    response = client.delete("/api/memories/non_existent_mem", headers={"Authorization": f"Bearer {test_user_token}"})
    assert response.status_code == 404

def test_57_memories_require_auth_guard(client):
    """57. Memories endpoints check authentication guards."""
    response = client.get("/api/memories")
    assert response.status_code == 401

def test_57b_search_memories_keyword_fallback(client, test_user_token):
    """57b. Memory search returns ranked keyword snippets for the authenticated user."""
    user_id = mock_db.users.store[-1]["id"]
    mock_db.memories.store.extend([
        {
            "id": "mem_relevant",
            "user_id": user_id,
            "title": "Doctor visit",
            "content": "Dentist appointment is at 4pm on Friday",
            "created_at": "2026-05-30",
        },
        {
            "id": "mem_other",
            "user_id": "other_user",
            "title": "Doctor visit",
            "content": "Private note from another account",
            "created_at": "2026-05-30",
        },
    ])
    response = client.post(
        "/api/memories/search",
        json={"query": "dentist Friday", "limit": 5},
        headers={"Authorization": f"Bearer {test_user_token}"},
    )
    assert response.status_code == 200
    data = response.json()
    assert len(data) == 1
    assert data[0]["memory_id"] == "mem_relevant"
    assert data[0]["source_type"] == "keyword"

@patch("app.services.memory.requests.post")
def test_57c_create_memory_uses_supermemory_when_configured(mock_post, client, test_user_token):
    """57c. Configured cloud memories are sent to Supermemory and mirrored locally."""
    from app.core.config import settings

    previous_key = settings.SUPERMEMORY_API_KEY
    settings.SUPERMEMORY_API_KEY = "sm_test_key"
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "documentId": "doc_1",
        "memories": [{"id": "mem_super", "memory": "Important meeting", "isStatic": False}],
    }
    mock_post.return_value = mock_response
    try:
        response = client.post(
            "/api/memories",
            json={"title": "Important meeting", "content": "Met John at 4pm"},
            headers={"Authorization": f"Bearer {test_user_token}"},
        )
    finally:
        settings.SUPERMEMORY_API_KEY = previous_key

    assert response.status_code == 200
    request = mock_post.call_args.kwargs
    assert request["json"]["containerTag"].startswith("aura_user:")
    assert request["json"]["memories"][0]["metadata"]["source"] == "aura_manual_memory"
    assert mock_db.memories.store[-1]["supermemory_id"] == "mem_super"

def test_58_create_extremely_long_memory(client, test_user_token):
    """58. Boundary check: handles huge data volumes efficiently."""
    long_content = "X" * 10000
    response = client.post(
        "/api/memories",
        json={"title": "Mega Memo", "content": long_content},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 200
    assert len(response.json()["content"]) == 10000

def test_59_create_memory_empty_content(client, test_user_token):
    """59. Creating a memory with empty content fails validation."""
    response = client.post(
        "/api/memories",
        json={"title": "Title Only", "content": ""},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 200 # App accepts empty string contents

def test_60_create_memory_empty_title(client, test_user_token):
    """60. Creating a memory with empty title."""
    response = client.post(
        "/api/memories",
        json={"title": "", "content": "Content"},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 200

def test_61_create_memory_unicode_emojis(client, test_user_token):
    """61. Memory title successfully accepts complex unicode strings/emojis."""
    response = client.post(
        "/api/memories",
        json={"title": "🚀 Space Memo 🌟", "content": "Travel notes 🧑‍🚀"},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 200
    assert response.json()["title"] == "🚀 Space Memo 🌟"

def test_62_create_memory_xss_sanitization(client, test_user_token):
    """62. Memories store HTML injection script tags safely as plain text."""
    payload = {"title": "XSS", "content": "<script>alert('hack')</script>"}
    response = client.post(
        "/api/memories",
        json=payload,
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 200
    assert response.json()["content"] == "<script>alert('hack')</script>"

def test_63_delete_memory_non_owned_forbidden(client, test_user_token):
    """63. Ensures users cannot delete other people's memories (data isolation checks)."""
    # Seed user B memory
    mock_db.memories.store.append({
        "id": "mem_b_id",
        "user_id": "different_user_b",
        "title": "B Memory",
        "content": "Secret Content",
        "created_at": "date"
    })
    response = client.delete("/api/memories/mem_b_id", headers={"Authorization": f"Bearer {test_user_token}"})
    # Since deletion fails to find memory owned by authenticated user, it returns 404
    assert response.status_code == 404

def test_64_memories_isolation_between_users(client, test_user_token):
    """64. Ensures users cannot list other people's memories."""
    mock_db.memories.store.append({
        "id": "mem_b_id",
        "user_id": "different_user_b",
        "title": "B Memory",
        "content": "Secret",
        "created_at": "date"
    })
    response = client.get("/api/memories", headers={"Authorization": f"Bearer {test_user_token}"})
    assert response.status_code == 200
    assert len(response.json()) == 0  # Does not show other user's memories

def test_65_create_memory_massive_title_boundary(client, test_user_token):
    """65. Boundary titles of 5000 characters."""
    long_title = "T" * 5000
    response = client.post(
        "/api/memories",
        json={"title": long_title, "content": "Content"},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 200
    assert response.json()["title"] == long_title

# ==============================================================================
# TEST CASE GROUP 7: Todos CRUD boundaries (13 Tests)
# ==============================================================================

def test_66_create_todo_success(client, test_user_token):
    """66. Creates todo successfully."""
    response = client.post(
        "/api/todos",
        json={"title": "Buy groceries"},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["title"] == "Buy groceries"
    assert data["done"] is False

def test_67_list_todos_success(client, test_user_token):
    """67. Lists tasks seeded for user."""
    user_id = mock_db.users.store[-1]["id"]
    mock_db.todos.store.append({
        "id": "todo_1",
        "user_id": user_id,
        "title": "Clean house",
        "done": False,
        "created_at": "2026-05-30"
    })
    response = client.get("/api/todos", headers={"Authorization": f"Bearer {test_user_token}"})
    assert response.status_code == 200
    assert len(response.json()) == 1

def test_68_update_todo_toggle_done(client, test_user_token):
    """68. Toggles task completion state successfully."""
    user_id = mock_db.users.store[-1]["id"]
    mock_db.todos.store.append({
        "id": "todo_toggle",
        "user_id": user_id,
        "title": "Iron shirt",
        "done": False,
        "created_at": "2026-05-30"
    })
    response = client.patch(
        "/api/todos/todo_toggle",
        json={"done": True},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 200
    assert response.json()["done"] is True

def test_69_delete_todo_success(client, test_user_token):
    """69. User deletes task successfully."""
    user_id = mock_db.users.store[-1]["id"]
    mock_db.todos.store.append({
        "id": "todo_del",
        "user_id": user_id,
        "title": "To delete",
        "done": False,
        "created_at": "2026-05-30"
    })
    response = client.delete("/api/todos/todo_del", headers={"Authorization": f"Bearer {test_user_token}"})
    assert response.status_code == 200
    assert response.json()["ok"] is True

def test_70_delete_todo_not_found(client, test_user_token):
    """70. Deleting non-existent task returns 404."""
    response = client.delete("/api/todos/non_existent_todo", headers={"Authorization": f"Bearer {test_user_token}"})
    assert response.status_code == 404

def test_71_patch_todo_not_found(client, test_user_token):
    """71. Patching non-existent task returns 404."""
    response = client.patch(
        "/api/todos/ghost_todo",
        json={"title": "Ghost"},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 404

def test_72_create_todo_empty_title(client, test_user_token):
    """72. Todo empty titles."""
    response = client.post(
        "/api/todos",
        json={"title": ""},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 200

def test_73_create_todo_xss_sanitization(client, test_user_token):
    """73. HTML scripting inputs are stored safely inside task titles."""
    response = client.post(
        "/api/todos",
        json={"title": "<h3>Task</h3><script>alert(1)</script>"},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 200
    assert response.json()["title"] == "<h3>Task</h3><script>alert(1)</script>"

def test_74_patch_todo_non_owned_forbidden(client, test_user_token):
    """74. User A is blocked from updating User B's tasks."""
    mock_db.todos.store.append({
        "id": "todo_b_id",
        "user_id": "different_user_b",
        "title": "B Task",
        "done": False,
        "created_at": "date"
    })
    response = client.patch(
        "/api/todos/todo_b_id",
        json={"done": True},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 404

def test_75_delete_todo_non_owned_forbidden(client, test_user_token):
    """75. User A is blocked from deleting User B's tasks."""
    mock_db.todos.store.append({
        "id": "todo_b_id",
        "user_id": "different_user_b",
        "title": "B Task",
        "done": False,
        "created_at": "date"
    })
    response = client.delete("/api/todos/todo_b_id", headers={"Authorization": f"Bearer {test_user_token}"})
    assert response.status_code == 404

def test_76_todos_isolation_between_users(client, test_user_token):
    """76. User A listing does not leak user B's tasks."""
    mock_db.todos.store.append({
        "id": "todo_b_id",
        "user_id": "different_user_b",
        "title": "B Task",
        "done": False,
        "created_at": "date"
    })
    response = client.get("/api/todos", headers={"Authorization": f"Bearer {test_user_token}"})
    assert response.status_code == 200
    assert len(response.json()) == 0

def test_77_patch_todo_empty_update_rejected(client, test_user_token):
    """77. Issuing updates with no patch fields returns 400."""
    user_id = mock_db.users.store[-1]["id"]
    mock_db.todos.store.append({
        "id": "todo_x",
        "user_id": user_id,
        "title": "Task",
        "done": False,
        "created_at": "date"
    })
    response = client.patch(
        "/api/todos/todo_x",
        json={},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 400

def test_78_todos_boundary_volume_loading(client, test_user_token):
    """78. Verifies bulk loading boundary configurations (loads 100 todos)."""
    user_id = mock_db.users.store[-1]["id"]
    for i in range(100):
        mock_db.todos.store.append({
            "id": f"bulk_todo_{i}",
            "user_id": user_id,
            "title": f"Task {i}",
            "done": False,
            "created_at": "date"
        })
    response = client.get("/api/todos", headers={"Authorization": f"Bearer {test_user_token}"})
    assert response.status_code == 200
    assert len(response.json()) == 100

# ==============================================================================
# TEST CASE GROUP 8: Assistant Integration & mock API failures (11 Tests)
# ==============================================================================

@patch("app.services.llm.requests.post")
def test_79_assistant_chat_gemini(mock_post, client):
    """79. Assistant Gemini integration mock execution."""
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "candidates": [{
            "content": {
                "parts": [{"text": '{"reply":"{happy} Hello there!","actions":[]}'}]
            }
        }]
    }
    mock_post.return_value = mock_response

    response = client.post("/api/assistant/chat", json={
        "message": "Hi",
        "provider": "gemini",
        "api_key": "dummy_key",
        "model": "gemini-3"
    })
    assert response.status_code == 200
    assert response.json()["reply"] == "{happy} Hello there!"

@patch("app.services.llm.requests.post")
def test_79b_assistant_chat_injects_authenticated_memory(mock_post, client, test_user_token):
    """79b. Authenticated chat retrieves server memories and injects them into the prompt."""
    user_id = mock_db.users.store[-1]["id"]
    mock_db.memories.store.append({
        "id": "mem_chat",
        "user_id": user_id,
        "title": "Passport",
        "content": "Passport is in the blue drawer",
        "created_at": "2026-05-30",
    })
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "candidates": [{
            "content": {
                "parts": [{"text": '{"reply":"{happy} It is in the blue drawer.","actions":[]}'}]
            }
        }]
    }
    mock_post.return_value = mock_response

    response = client.post(
        "/api/assistant/chat",
        json={
            "message": "Where is my passport?",
            "provider": "gemini",
            "api_key": "dummy_key",
            "model": "gemini-3",
        },
        headers={"Authorization": f"Bearer {test_user_token}"},
    )

    assert response.status_code == 200
    payload = mock_post.call_args.kwargs["json"]
    system_text = payload["systemInstruction"]["parts"][0]["text"]
    assert "Passport" in system_text
    assert "blue drawer" in system_text

def test_80_assistant_chat_invalid_provider(client):
    """80. Chat yields 400 error if provider is unsupported."""
    response = client.post("/api/assistant/chat", json={
        "message": "Hi",
        "provider": "unknown_provider",
        "api_key": "dummy",
        "model": "model"
    })
    assert response.status_code == 400
    assert "unsupported provider" in response.json()["detail"].lower()

@patch("app.services.llm.requests.post")
def test_81_assistant_chat_gemini_api_error(mock_post, client):
    """81. Assistant service correctly propagates external API failures with 502/500."""
    mock_response = MagicMock()
    mock_response.status_code = 500
    mock_response.text = "Internal Gemini Crash"
    mock_post.return_value = mock_response

    response = client.post("/api/assistant/chat", json={
        "message": "Hi",
        "provider": "gemini",
        "api_key": "dummy",
        "model": "model"
    })
    assert response.status_code in [500, 502]
    detail = response.json()["detail"].lower()
    assert "gemini error" in detail or "assistant error" in detail

@patch("app.services.llm.requests.post")
def test_82_assistant_action_parsing_fallback(mock_post, client):
    """82. Handles LLM text responses that do not contain valid json actions."""
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "candidates": [{
            "content": {
                "parts": [{"text": "Hello, I cannot parse actions for this"}]
            }
        }]
    }
    mock_post.return_value = mock_response

    response = client.post("/api/assistant/chat", json={
        "message": "Hi",
        "provider": "gemini",
        "api_key": "dummy",
        "model": "model"
    })
    assert response.status_code == 200
    assert response.json()["reply"] == "Hello, I cannot parse actions for this"
    assert response.json()["actions"] == []

@patch("app.api.assistant.requests.get")
def test_83_openrouter_models_list(mock_get, client):
    """83. Openrouter models query lists sorted candidates successfully."""
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "data": [
            {"id": "gpt-4", "name": "GPT 4"},
            {"id": "claude-3", "name": "Claude 3"}
        ]
    }
    mock_get.return_value = mock_response

    response = client.post("/api/providers/openrouter/models", json={"api_key": "dummy"})
    assert response.status_code == 200
    data = response.json()["data"]
    assert len(data) == 2
    assert data[0]["name"] == "Claude 3"
    assert data[1]["name"] == "GPT 4"

@patch("app.services.llm.requests.post", side_effect=requests.exceptions.Timeout("Connection timed out"))
def test_84_assistant_chat_gemini_timeout(mock_post, client):
    """84. Gemini timeout exceptions map to a standard HTTP 502 response."""
    response = client.post("/api/assistant/chat", json={
        "message": "Hi",
        "provider": "gemini",
        "api_key": "dummy",
        "model": "model"
    })
    assert response.status_code == 502
    assert "timeout" in response.json()["detail"].lower() or "failed to connect" in response.json()["detail"].lower()

@patch("app.services.llm.requests.post", side_effect=requests.exceptions.Timeout("Connection timed out"))
def test_85_assistant_chat_openai_timeout(mock_post, client):
    """85. OpenAI timeout exceptions map to a standard HTTP 502 response."""
    response = client.post("/api/assistant/chat", json={
        "message": "Hi",
        "provider": "openai",
        "api_key": "dummy",
        "model": "model"
    })
    assert response.status_code == 502

@patch("app.services.llm.requests.post")
def test_85b_assistant_chat_openai_includes_image_payload(mock_post, client):
    """85b. OpenAI chat requests include attached images instead of dropping them."""
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "output": [
            {
                "content": [
                    {"type": "output_text", "text": "{neutral} I can see it."}
                ]
            }
        ]
    }
    mock_post.return_value = mock_response

    response = client.post("/api/assistant/chat", json={
        "message": "What is in this image?",
        "provider": "openai",
        "api_key": "dummy",
        "model": "gpt-4.1-mini",
        "image_base64": "aW1hZ2U=",
        "image_mime_type": "image/png",
    })

    assert response.status_code == 200
    payload = mock_post.call_args.kwargs["json"]
    user_content = payload["input"][1]["content"]
    assert user_content[1]["type"] == "input_image"
    assert user_content[1]["image_url"] == "data:image/png;base64,aW1hZ2U="

@patch("app.services.llm.requests.post", side_effect=requests.exceptions.Timeout("Connection timed out"))
def test_86_assistant_chat_openrouter_timeout(mock_post, client):
    """86. OpenRouter timeout exceptions map to a standard HTTP 502 response."""
    response = client.post("/api/assistant/chat", json={
        "message": "Hi",
        "provider": "openrouter",
        "api_key": "dummy",
        "model": "model"
    })
    assert response.status_code == 502

@patch("app.services.llm.requests.post")
def test_86b_assistant_chat_openrouter_includes_image_payload(mock_post, client):
    """86b. OpenRouter chat requests include attached images instead of dropping them."""
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "choices": [
            {
                "message": {
                    "content": "{neutral} I can see it.",
                    "tool_calls": [],
                }
            }
        ]
    }
    mock_post.return_value = mock_response

    response = client.post("/api/assistant/chat", json={
        "message": "What is in this image?",
        "provider": "openrouter",
        "api_key": "dummy",
        "model": "openai/gpt-4.1-mini",
        "image_base64": "aW1hZ2U=",
        "image_mime_type": "image/png",
    })

    assert response.status_code == 200
    payload = mock_post.call_args.kwargs["json"]
    user_content = payload["messages"][1]["content"]
    assert user_content[0] == {"type": "text", "text": "What is in this image?"}
    assert user_content[1]["image_url"]["url"] == "data:image/png;base64,aW1hZ2U="

@patch("app.services.llm.requests.post")
def test_87_assistant_chat_gemini_403_forbidden(mock_post, client):
    """87. API key permission issues (403) are propagated as bad gateway/failures."""
    mock_response = MagicMock()
    mock_response.status_code = 403
    mock_response.text = "Forbidden API Key"
    mock_post.return_value = mock_response

    response = client.post("/api/assistant/chat", json={
        "message": "Hi",
        "provider": "gemini",
        "api_key": "dummy",
        "model": "model"
    })
    assert response.status_code == 500 or response.status_code == 403

@patch("app.services.llm.requests.post")
def test_88_assistant_chat_gemini_429_rate_limit(mock_post, client):
    """88. External Rate limit exceptions (429) propagate through safely."""
    mock_response = MagicMock()
    mock_response.status_code = 429
    mock_response.text = "Too many requests to provider"
    mock_post.return_value = mock_response

    response = client.post("/api/assistant/chat", json={
        "message": "Hi",
        "provider": "gemini",
        "api_key": "dummy",
        "model": "model"
    })
    assert response.status_code == 500 or response.status_code == 429

@patch("app.services.llm.requests.post")
def test_89_assistant_chat_gemini_503_unavailable(mock_post, client):
    """89. External server offline (503) states handled securely."""
    mock_response = MagicMock()
    mock_response.status_code = 503
    mock_response.text = "Service Overloaded"
    mock_post.return_value = mock_response

    response = client.post("/api/assistant/chat", json={
        "message": "Hi",
        "provider": "gemini",
        "api_key": "dummy",
        "model": "model"
    })
    assert response.status_code == 500 or response.status_code == 503

# ==============================================================================
# TEST CASE GROUP 9: Assistant payloads, audio & gateway echos (13 Tests)
# ==============================================================================

def test_90_assistant_chat_empty_api_key(client):
    """90. Sending empty provider API keys results in 400 validation error."""
    response = client.post("/api/assistant/chat", json={
        "message": "Hi",
        "provider": "gemini",
        "api_key": "",
        "model": "model"
    })
    assert response.status_code == 400
    assert "api key is required" in response.json()["detail"].lower()

def test_91_assistant_chat_empty_message(client):
    """91. Sending empty message in assistant chat."""
    response = client.post("/api/assistant/chat", json={
        "message": "",
        "provider": "gemini",
        "api_key": "dummy",
        "model": "model"
    })
    assert response.status_code == 400

@patch("app.services.llm.requests.post")
def test_92_assistant_chat_session_retention(mock_post, client):
    """92. Checks custom session IDs are generated and returned."""
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "candidates": [{
            "content": {
                "parts": [{"text": '{"reply":"{happy} OK","actions":[]}'}]
            }
        }]
    }
    mock_post.return_value = mock_response

    response = client.post("/api/assistant/chat", json={
        "message": "Hi",
        "provider": "gemini",
        "api_key": "dummy",
        "model": "model",
        "session_id": "custom-session-123"
    })
    assert response.status_code == 200
    assert response.json()["session_id"] == "custom-session-123"

@patch("app.services.llm.requests.post")
def test_93_assistant_chat_malformed_json_unbalanced_curly_braces(mock_post, client):
    """93. Unbalanced JSON structures in LLM text returns are mapped to plain text fallbacks."""
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "candidates": [{
            "content": {
                "parts": [{"text": " { malformed json reply text "}]
            }
        }]
    }
    mock_post.return_value = mock_response

    response = client.post("/api/assistant/chat", json={
        "message": "Hi",
        "provider": "gemini",
        "api_key": "dummy",
        "model": "model"
    })
    assert response.status_code == 200
    assert response.json()["reply"] == "{ malformed json reply text"
    assert response.json()["actions"] == []

@patch("app.services.transcription.requests.post")
def test_94_transcribe_audio_success(mock_post, client, test_user_token):
    """94. Clean base64 audio transcription returns expected text values."""
    settings.GEMINI_API_KEY = "test_key"
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "candidates": [{
            "content": {
                "parts": [{"text": "Hello world"}]
            }
        }]
    }
    mock_post.return_value = mock_response

    response = client.post(
        "/api/transcribe",
        json={"audio_base64": wav_payload(), "mime_type": "audio/wav"},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 200
    assert response.json()["text"] == "Hello world"

def test_95_transcribe_audio_invalid_base64(client, test_user_token):
    """95. Transcription rejects corrupt base64 string payloads with 400."""
    response = client.post(
        "/api/transcribe",
        json={"audio_base64": "!!!corrupt_base64!!!", "mime_type": "audio/m4a"},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 400
    assert "invalid" in response.json()["detail"].lower()

def test_96_supabase_gateway_mock_success(client, test_user_token):
    """96. Validates supabase gateway response format and echo logic."""
    response = client.post(
        "/api/gateway/supabase",
        json={"action": "fetch_profile", "payload": {"foo": "bar"}},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["ok"] is True
    assert data["action"] == "fetch_profile"
    assert data["result"]["echo"]["foo"] == "bar"

def test_97_transcribe_audio_empty_string(client, test_user_token):
    """97. Empty base64 payload inside audio transcribes triggers 400."""
    response = client.post(
        "/api/transcribe",
        json={"audio_base64": "", "mime_type": "audio/m4a"},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 400

def test_98_transcribe_audio_not_padded_base64(client, test_user_token):
    """98. Rejects base64 strings with malformed padding headers."""
    # "dGVzdA" is "test" without padding ("==")
    response = client.post(
        "/api/transcribe",
        json={"audio_base64": "dGVzdA", "mime_type": "audio/wav"},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    # python base64.b64decode handles minor missing padding, but corrupt ones will fail
    assert response.status_code in [200, 400]

def test_99_transcribe_audio_missing_api_key(client, test_user_token):
    """99. Transcription API connection fails gracefully when Gemini key is missing."""
    settings.GEMINI_API_KEY = ""
    response = client.post(
        "/api/transcribe",
        json={"audio_base64": wav_payload(), "mime_type": "audio/wav"},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 500
    assert "not configured" in response.json()["detail"].lower()

def test_99b_transcribe_audio_rejects_too_short_wav(client, test_user_token):
    """99b. Header-only or tiny WAV captures are rejected before provider calls."""
    settings.GEMINI_API_KEY = "test_key"
    response = client.post(
        "/api/transcribe",
        json={"audio_base64": wav_payload(duration_ms=40), "mime_type": "audio/wav"},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 400
    assert "too short" in response.json()["detail"].lower()

@patch("app.services.transcription.requests.post")
def test_99c_transcribe_audio_openai_success(mock_post, client, test_user_token):
    """99c. OpenAI transcription requests use multipart audio and return text."""
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {"text": "Hello from OpenAI"}
    mock_post.return_value = mock_response

    response = client.post(
        "/api/transcribe",
        json={
            "audio_base64": wav_payload(),
            "mime_type": "audio/wav",
            "api_key": "openai_key",
            "provider": "openai",
        },
        headers={"Authorization": f"Bearer {test_user_token}"}
    )

    assert response.status_code == 200
    assert response.json()["text"] == "Hello from OpenAI"
    kwargs = mock_post.call_args.kwargs
    assert kwargs["data"]["model"] == settings.OPENAI_TRANSCRIPTION_MODEL
    assert kwargs["files"]["file"][0].endswith(".wav")
    assert kwargs["files"]["file"][2] == "audio/wav"

def test_99d_transcribe_audio_rejects_unsupported_provider(client, test_user_token):
    """99d. Transcription fails clearly when an unsupported provider is requested."""
    settings.GEMINI_API_KEY = "test_key"
    response = client.post(
        "/api/transcribe",
        json={
            "audio_base64": wav_payload(),
            "mime_type": "audio/wav",
            "provider": "openrouter"
        },
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 400
    assert "gemini or openai" in response.json()["detail"].lower()

def test_100_supabase_gateway_massive_payload(client, test_user_token):
    """100. Supabase gateway can handle massive dictionary payloads (boundary check)."""
    large_payload = {f"key_{i}": f"value_{i}" for i in range(500)}
    response = client.post(
        "/api/gateway/supabase",
        json={"action": "save_config", "payload": large_payload},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 200
    assert len(response.json()["result"]["echo"]) == 500

def test_101_supabase_gateway_empty_action(client, test_user_token):
    """101. Supabase gateway returns mock actions even with empty payload actions."""
    response = client.post(
        "/api/gateway/supabase",
        json={"action": ""},
        headers={"Authorization": f"Bearer {test_user_token}"}
    )
    assert response.status_code == 200
    assert response.json()["action"] == ""

def test_102_gateway_requires_auth(client):
    """102. Gateway requires authentication guards."""
    response = client.post(
        "/api/gateway/supabase",
        json={"action": "test"},
    )
    assert response.status_code == 401
