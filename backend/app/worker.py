import asyncio
import logging

from app.core.config import settings
from app.core.database import db_manager, get_db
from app.services.agent_queue import AgentRunWorker


async def main() -> None:
    settings.validate_for_runtime()
    if not settings.AGENT_CREDENTIAL_KEY:
        raise RuntimeError(
            "AGENT_CREDENTIAL_KEY is required when the agent worker runs in a separate process"
        )
    db_manager.connect()
    try:
        await AgentRunWorker(get_db()).run_forever()
    finally:
        db_manager.close()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    asyncio.run(main())
