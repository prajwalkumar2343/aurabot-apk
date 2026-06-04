import uuid
from datetime import datetime, timezone

import pytest
from fastapi.testclient import TestClient

from app.core.database import get_db
from app.core.security import create_access_token, hash_password
from app.main import app


class MockCursor:
    def __init__(self, data):
        self.data = list(data)
        self.sort_key = None
        self.sort_dir = -1

    def sort(self, key, direction=-1):
        self.sort_key = key
        self.sort_dir = direction
        return self

    async def to_list(self, limit=500):
        data = list(self.data)
        if self.sort_key:
            data.sort(key=lambda item: item.get(self.sort_key, ""), reverse=self.sort_dir == -1)
        return data[:limit]


class MockCollection:
    def __init__(self):
        self.store = []

    async def find_one(self, query, projection=None):
        for item in self.store:
            if all(item.get(key) == value for key, value in query.items()):
                return dict(item)
        return None

    async def insert_one(self, doc):
        self.store.append(dict(doc))
        return doc

    def find(self, query, projection=None):
        return MockCursor(item for item in self.store if all(item.get(key) == value for key, value in query.items()))

    async def find_one_and_update(self, query, update, return_document=True, projection=None):
        for item in self.store:
            if all(item.get(key) == value for key, value in query.items()):
                item.update(update.get("$set", {}))
                return dict(item)
        return None

    async def delete_one(self, query):
        before = len(self.store)
        self.store = [item for item in self.store if not all(item.get(key) == value for key, value in query.items())]

        class Result:
            deleted_count = before - len(self.store)

        return Result()


class MockDatabase:
    def __init__(self):
        self.users = MockCollection()
        self.mini_app_records = MockCollection()


@pytest.fixture
def mock_db():
    db = MockDatabase()
    app.dependency_overrides[get_db] = lambda: db
    yield db
    app.dependency_overrides.pop(get_db, None)


@pytest.fixture
def client(mock_db):
    return TestClient(app, raise_server_exceptions=False)


@pytest.fixture
def auth_header(mock_db):
    user_id = str(uuid.uuid4())
    email = "miniapp-user@aura.app"
    mock_db.users.store.append(
        {
            "id": user_id,
            "email": email,
            "name": "Mini App User",
            "password_hash": hash_password("password123"),
            "created_at": datetime.now(timezone.utc).isoformat(),
        }
    )
    return {"Authorization": f"Bearer {create_access_token(user_id, email)}"}


def test_mini_app_records_are_scoped_to_user_and_app(client, mock_db, auth_header):
    response = client.post(
        "/api/mini-apps/generated.react.notes/records",
        headers=auth_header,
        json={"recordType": "note", "values": {"title": "First", "pinned": True}},
    )

    assert response.status_code == 200
    record = response.json()
    assert record["miniAppId"] == "generated.react.notes"
    assert record["recordType"] == "note"
    assert record["values"]["title"] == "First"

    mock_db.mini_app_records.store.append(
        {
            "id": "other-app-record",
            "user_id": mock_db.users.store[0]["id"],
            "mini_app_id": "generated.react.other",
            "record_type": "note",
            "values": {"title": "Hidden"},
            "created_at": "2026-01-01T00:00:00+00:00",
            "updated_at": "2026-01-01T00:00:00+00:00",
        }
    )

    list_response = client.get("/api/mini-apps/generated.react.notes/records?recordType=note", headers=auth_header)

    assert list_response.status_code == 200
    records = list_response.json()
    assert len(records) == 1
    assert records[0]["id"] == record["id"]


def test_mini_app_record_update_and_delete_require_same_app(client, auth_header):
    created = client.post(
        "/api/mini-apps/generated.react.notes/records",
        headers=auth_header,
        json={"recordType": "note", "values": {"title": "Draft"}},
    ).json()

    wrong_app = client.patch(
        f"/api/mini-apps/generated.react.other/records/{created['id']}",
        headers=auth_header,
        json={"values": {"title": "Wrong"}},
    )
    updated = client.patch(
        f"/api/mini-apps/generated.react.notes/records/{created['id']}",
        headers=auth_header,
        json={"values": {"title": "Done"}},
    )
    deleted = client.delete(f"/api/mini-apps/generated.react.notes/records/{created['id']}", headers=auth_header)

    assert wrong_app.status_code == 404
    assert updated.status_code == 200
    assert updated.json()["values"]["title"] == "Done"
    assert deleted.status_code == 200
