import logging
from motor.motor_asyncio import AsyncIOMotorClient
from app.core.config import settings

logger = logging.getLogger(__name__)

class DatabaseManager:
    def __init__(self):
        self.client = None
        self.db = None

    def connect(self):
        logger.info("Connecting to MongoDB...")
        self.client = AsyncIOMotorClient(settings.MONGO_URL)
        self.db = self.client[settings.DB_NAME]
        logger.info("Connected to database successfully.")

    def close(self):
        if self.client:
            logger.info("Closing MongoDB connection...")
            self.client.close()
            logger.info("MongoDB connection closed.")

db_manager = DatabaseManager()

def get_db():
    if db_manager.db is None:
        db_manager.connect()
    return db_manager.db
