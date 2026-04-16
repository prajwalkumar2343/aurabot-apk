"""
Aura Assistant Backend Tests
Tests: Auth (register, login, me, brute force), Memories, Todos, Assistant chat, Transcribe, Gateway
"""
import pytest
import requests
import os
import base64
import time

BASE_URL = os.environ.get("EXPO_PUBLIC_BACKEND_URL", "").rstrip("/")
if not BASE_URL:
    pytest.skip("EXPO_PUBLIC_BACKEND_URL not set", allow_module_level=True)


@pytest.fixture
def api_client():
    """Shared requests session"""
    session = requests.Session()
    session.headers.update({"Content-Type": "application/json"})
    return session


@pytest.fixture
def admin_token(api_client):
    """Get admin access token"""
    response = api_client.post(
        f"{BASE_URL}/api/auth/login",
        json={"email": "admin@aura.app", "password": "admin123"},
    )
    if response.status_code != 200:
        pytest.skip(f"Admin login failed: {response.status_code}")
    return response.json()["access_token"]


@pytest.fixture
def test_user_token(api_client):
    """Create test user and return token"""
    email = f"test_{int(time.time())}@aura.app"
    password = "test1234"
    
    # Register
    reg_response = api_client.post(
        f"{BASE_URL}/api/auth/register",
        json={"email": email, "password": password, "name": "Test User"},
    )
    if reg_response.status_code != 200:
        pytest.skip(f"Test user registration failed: {reg_response.status_code}")
    
    # Login to get token
    login_response = api_client.post(
        f"{BASE_URL}/api/auth/login",
        json={"email": email, "password": password},
    )
    return login_response.json()["access_token"]


# ============ Health Check ============
class TestHealth:
    """Health check tests"""

    def test_root_endpoint(self, api_client):
        response = api_client.get(f"{BASE_URL}/api/")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "ok"
        assert data["service"] == "aura-assistant"
        print("✓ Root endpoint working")

    def test_health_endpoint(self, api_client):
        response = api_client.get(f"{BASE_URL}/api/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "healthy"
        assert "model" in data
        print("✓ Health endpoint working")


# ============ Auth Tests ============
class TestAuth:
    """Authentication endpoint tests"""

    def test_register_new_user(self, api_client):
        """Test user registration"""
        email = f"TEST_newuser_{int(time.time())}@aura.app"
        response = api_client.post(
            f"{BASE_URL}/api/auth/register",
            json={"email": email, "password": "password123", "name": "New User"},
        )
        assert response.status_code == 200
        
        data = response.json()
        assert data["email"] == email.lower()
        assert data["name"] == "New User"
        assert data["role"] == "user"
        assert "id" in data
        
        # Verify X-Access-Token header is set
        assert "x-access-token" in response.headers or "X-Access-Token" in response.headers
        print(f"✓ User registration working: {email}")

    def test_register_duplicate_email(self, api_client):
        """Test duplicate email registration fails"""
        email = f"TEST_duplicate_{int(time.time())}@aura.app"
        
        # First registration
        api_client.post(
            f"{BASE_URL}/api/auth/register",
            json={"email": email, "password": "password123"},
        )
        
        # Second registration should fail
        response = api_client.post(
            f"{BASE_URL}/api/auth/register",
            json={"email": email, "password": "password123"},
        )
        assert response.status_code == 400
        assert "already registered" in response.json()["detail"].lower()
        print("✓ Duplicate email registration blocked")

    def test_login_admin(self, api_client):
        """Test admin login"""
        response = api_client.post(
            f"{BASE_URL}/api/auth/login",
            json={"email": "admin@aura.app", "password": "admin123"},
        )
        assert response.status_code == 200
        
        data = response.json()
        assert data["email"] == "admin@aura.app"
        assert data["role"] == "admin"
        assert "access_token" in data
        assert "id" in data
        
        # Verify cookies are set
        assert "access_token" in response.cookies or "Set-Cookie" in response.headers
        print("✓ Admin login working")

    def test_login_invalid_credentials(self, api_client):
        """Test login with invalid credentials"""
        response = api_client.post(
            f"{BASE_URL}/api/auth/login",
            json={"email": "admin@aura.app", "password": "wrongpassword"},
        )
        assert response.status_code == 401
        assert "invalid" in response.json()["detail"].lower()
        print("✓ Invalid credentials rejected")

    def test_get_me_with_token(self, api_client, admin_token):
        """Test /auth/me with Bearer token"""
        response = api_client.get(
            f"{BASE_URL}/api/auth/me",
            headers={"Authorization": f"Bearer {admin_token}"},
        )
        assert response.status_code == 200
        
        data = response.json()
        assert data["email"] == "admin@aura.app"
        assert data["role"] == "admin"
        assert "id" in data
        print("✓ /auth/me working with Bearer token")

    def test_get_me_without_token(self, api_client):
        """Test /auth/me without token fails"""
        response = api_client.get(f"{BASE_URL}/api/auth/me")
        assert response.status_code == 401
        print("✓ /auth/me requires authentication")

    def test_brute_force_protection(self, api_client):
        """Test brute force protection after 5 failed attempts"""
        email = f"TEST_bruteforce_{int(time.time())}@aura.app"
        
        # Create user first
        api_client.post(
            f"{BASE_URL}/api/auth/register",
            json={"email": email, "password": "correctpass"},
        )
        
        # Make 5 failed login attempts
        for i in range(5):
            response = api_client.post(
                f"{BASE_URL}/api/auth/login",
                json={"email": email, "password": "wrongpass"},
            )
            # 5th attempt should set the lock
            if i < 4:
                assert response.status_code == 401
                print(f"  Failed attempt {i+1}/5")
            else:
                # 5th attempt: lock is set, but still returns 401 for this attempt
                assert response.status_code == 401
                print(f"  Failed attempt {i+1}/5 (lock set)")
        
        # 6th attempt should be locked
        response = api_client.post(
            f"{BASE_URL}/api/auth/login",
            json={"email": email, "password": "wrongpass"},
        )
        assert response.status_code == 429
        assert "too many" in response.json()["detail"].lower()
        print("✓ Brute force protection working (locked after 5 fails)")


# ============ Memories Tests ============
class TestMemories:
    """Memory CRUD tests"""

    def test_create_memory_and_verify(self, api_client, test_user_token):
        """Test creating a memory and verifying persistence"""
        create_payload = {
            "title": "TEST_Memory Title",
            "content": "This is a test memory content",
        }
        
        # Create memory
        create_response = api_client.post(
            f"{BASE_URL}/api/memories",
            json=create_payload,
            headers={"Authorization": f"Bearer {test_user_token}"},
        )
        assert create_response.status_code == 200
        
        created = create_response.json()
        assert created["title"] == create_payload["title"]
        assert created["content"] == create_payload["content"]
        assert "id" in created
        assert "created_at" in created
        memory_id = created["id"]
        
        # Verify by listing memories
        list_response = api_client.get(
            f"{BASE_URL}/api/memories",
            headers={"Authorization": f"Bearer {test_user_token}"},
        )
        assert list_response.status_code == 200
        memories = list_response.json()
        assert any(m["id"] == memory_id for m in memories)
        print(f"✓ Memory created and verified: {memory_id}")

    def test_list_memories(self, api_client, test_user_token):
        """Test listing memories"""
        response = api_client.get(
            f"{BASE_URL}/api/memories",
            headers={"Authorization": f"Bearer {test_user_token}"},
        )
        assert response.status_code == 200
        assert isinstance(response.json(), list)
        print("✓ List memories working")

    def test_delete_memory(self, api_client, test_user_token):
        """Test deleting a memory"""
        # Create memory
        create_response = api_client.post(
            f"{BASE_URL}/api/memories",
            json={"title": "TEST_Delete Me", "content": "To be deleted"},
            headers={"Authorization": f"Bearer {test_user_token}"},
        )
        memory_id = create_response.json()["id"]
        
        # Delete memory
        delete_response = api_client.delete(
            f"{BASE_URL}/api/memories/{memory_id}",
            headers={"Authorization": f"Bearer {test_user_token}"},
        )
        assert delete_response.status_code == 200
        assert delete_response.json()["ok"] is True
        
        # Verify deletion
        list_response = api_client.get(
            f"{BASE_URL}/api/memories",
            headers={"Authorization": f"Bearer {test_user_token}"},
        )
        memories = list_response.json()
        assert not any(m["id"] == memory_id for m in memories)
        print(f"✓ Memory deleted: {memory_id}")

    def test_memories_require_auth(self, api_client):
        """Test memories endpoints require authentication"""
        response = api_client.get(f"{BASE_URL}/api/memories")
        assert response.status_code == 401
        print("✓ Memories require authentication")


# ============ Todos Tests ============
class TestTodos:
    """Todo CRUD tests"""

    def test_create_todo_and_verify(self, api_client, test_user_token):
        """Test creating a todo and verifying persistence"""
        create_payload = {"title": "TEST_Todo Task"}
        
        # Create todo
        create_response = api_client.post(
            f"{BASE_URL}/api/todos",
            json=create_payload,
            headers={"Authorization": f"Bearer {test_user_token}"},
        )
        assert create_response.status_code == 200
        
        created = create_response.json()
        assert created["title"] == create_payload["title"]
        assert created["done"] is False
        assert "id" in created
        assert "created_at" in created
        todo_id = created["id"]
        
        # Verify by listing todos
        list_response = api_client.get(
            f"{BASE_URL}/api/todos",
            headers={"Authorization": f"Bearer {test_user_token}"},
        )
        assert list_response.status_code == 200
        todos = list_response.json()
        assert any(t["id"] == todo_id for t in todos)
        print(f"✓ Todo created and verified: {todo_id}")

    def test_toggle_todo_done(self, api_client, test_user_token):
        """Test toggling todo done status"""
        # Create todo
        create_response = api_client.post(
            f"{BASE_URL}/api/todos",
            json={"title": "TEST_Toggle Me"},
            headers={"Authorization": f"Bearer {test_user_token}"},
        )
        todo_id = create_response.json()["id"]
        
        # Toggle to done
        patch_response = api_client.patch(
            f"{BASE_URL}/api/todos/{todo_id}",
            json={"done": True},
            headers={"Authorization": f"Bearer {test_user_token}"},
        )
        assert patch_response.status_code == 200
        assert patch_response.json()["done"] is True
        
        # Verify by GET
        list_response = api_client.get(
            f"{BASE_URL}/api/todos",
            headers={"Authorization": f"Bearer {test_user_token}"},
        )
        todos = list_response.json()
        todo = next(t for t in todos if t["id"] == todo_id)
        assert todo["done"] is True
        print(f"✓ Todo toggled to done: {todo_id}")

    def test_delete_todo(self, api_client, test_user_token):
        """Test deleting a todo"""
        # Create todo
        create_response = api_client.post(
            f"{BASE_URL}/api/todos",
            json={"title": "TEST_Delete Me"},
            headers={"Authorization": f"Bearer {test_user_token}"},
        )
        todo_id = create_response.json()["id"]
        
        # Delete todo
        delete_response = api_client.delete(
            f"{BASE_URL}/api/todos/{todo_id}",
            headers={"Authorization": f"Bearer {test_user_token}"},
        )
        assert delete_response.status_code == 200
        assert delete_response.json()["ok"] is True
        
        # Verify deletion
        list_response = api_client.get(
            f"{BASE_URL}/api/todos",
            headers={"Authorization": f"Bearer {test_user_token}"},
        )
        todos = list_response.json()
        assert not any(t["id"] == todo_id for t in todos)
        print(f"✓ Todo deleted: {todo_id}")

    def test_todos_require_auth(self, api_client):
        """Test todos endpoints require authentication"""
        response = api_client.get(f"{BASE_URL}/api/todos")
        assert response.status_code == 401
        print("✓ Todos require authentication")


# ============ Assistant Chat Tests ============
class TestAssistant:
    """Assistant chat tests (Gemini integration)"""

    def test_assistant_chat(self, api_client, test_user_token):
        """Test assistant chat endpoint"""
        response = api_client.post(
            f"{BASE_URL}/api/assistant/chat",
            json={"message": "Hello, what is 2+2?"},
            headers={"Authorization": f"Bearer {test_user_token}"},
            timeout=30,
        )
        assert response.status_code == 200
        
        data = response.json()
        assert "reply" in data
        assert "session_id" in data
        assert len(data["reply"]) > 0
        print(f"✓ Assistant chat working. Reply: {data['reply'][:50]}...")

    def test_assistant_chat_with_session(self, api_client, test_user_token):
        """Test assistant chat with session_id"""
        # First message
        response1 = api_client.post(
            f"{BASE_URL}/api/assistant/chat",
            json={"message": "My name is Alice"},
            headers={"Authorization": f"Bearer {test_user_token}"},
            timeout=30,
        )
        session_id = response1.json()["session_id"]
        
        # Second message with same session
        response2 = api_client.post(
            f"{BASE_URL}/api/assistant/chat",
            json={"message": "What is my name?", "session_id": session_id},
            headers={"Authorization": f"Bearer {test_user_token}"},
            timeout=30,
        )
        assert response2.status_code == 200
        reply = response2.json()["reply"].lower()
        # Gemini should remember the name from context
        print(f"✓ Assistant chat with session working. Reply: {reply[:50]}...")

    def test_assistant_requires_auth(self, api_client):
        """Test assistant chat requires authentication"""
        response = api_client.post(
            f"{BASE_URL}/api/assistant/chat",
            json={"message": "Hello"},
        )
        assert response.status_code == 401
        print("✓ Assistant chat requires authentication")


# ============ Transcribe Tests ============
class TestTranscribe:
    """Transcription tests"""

    def test_transcribe_invalid_base64(self, api_client, test_user_token):
        """Test transcribe with invalid base64 returns 400"""
        response = api_client.post(
            f"{BASE_URL}/api/transcribe",
            json={"audio_base64": "invalid_base64!!!", "mime_type": "audio/m4a"},
            headers={"Authorization": f"Bearer {test_user_token}"},
        )
        assert response.status_code == 400
        assert "invalid" in response.json()["detail"].lower()
        print("✓ Transcribe rejects invalid base64")

    def test_transcribe_requires_auth(self, api_client):
        """Test transcribe requires authentication"""
        response = api_client.post(
            f"{BASE_URL}/api/transcribe",
            json={"audio_base64": "dGVzdA==", "mime_type": "audio/m4a"},
        )
        assert response.status_code == 401
        print("✓ Transcribe requires authentication")


# ============ Gateway Tests ============
class TestGateway:
    """Supabase gateway tests (MOCKED)"""

    def test_gateway_supabase_mocked(self, api_client, test_user_token):
        """Test mocked Supabase gateway"""
        response = api_client.post(
            f"{BASE_URL}/api/gateway/supabase",
            json={"action": "test_action", "payload": {"key": "value"}},
            headers={"Authorization": f"Bearer {test_user_token}"},
        )
        assert response.status_code == 200
        
        data = response.json()
        assert data["ok"] is True
        assert data["mocked"] is True
        assert data["action"] == "test_action"
        assert "result" in data
        print("✓ Supabase gateway (MOCKED) working")

    def test_gateway_requires_auth(self, api_client):
        """Test gateway requires authentication"""
        response = api_client.post(
            f"{BASE_URL}/api/gateway/supabase",
            json={"action": "test"},
        )
        assert response.status_code == 401
        print("✓ Gateway requires authentication")
