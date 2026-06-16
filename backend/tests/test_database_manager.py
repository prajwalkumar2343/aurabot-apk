from app.core.database import DatabaseManager


class FakeMongoClient:
    def __init__(self):
        self.closed = False

    def close(self):
        self.closed = True


def test_close_resets_client_and_database_references():
    manager = DatabaseManager()
    client = FakeMongoClient()
    manager.client = client
    manager.db = object()

    manager.close()

    assert client.closed is True
    assert manager.client is None
    assert manager.db is None
