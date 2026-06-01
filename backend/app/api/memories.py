import uuid
from datetime import datetime, timezone
from fastapi import APIRouter, HTTPException, Depends
from typing import List
from app.core.database import get_db
from app.core.security import get_current_user
from app.models.memory import MemoryCreate, MemoryOut

router = APIRouter(prefix="/memories", tags=["Memories"])

@router.get("", response_model=List[MemoryOut])
async def list_memories(user=Depends(get_current_user), db = Depends(get_db)):
    cursor = db.memories.find({"user_id": user["id"]}, {"_id": 0}).sort("created_at", -1)
    items = await cursor.to_list(1000)
    return [MemoryOut(id=i["id"], title=i["title"], content=i["content"], created_at=i["created_at"]) for i in items]

@router.post("", response_model=MemoryOut)
async def create_memory(data: MemoryCreate, user=Depends(get_current_user), db = Depends(get_db)):
    doc = {
        "id": str(uuid.uuid4()),
        "user_id": user["id"],
        "title": data.title,
        "content": data.content,
        "created_at": datetime.now(timezone.utc).isoformat(),
    }
    await db.memories.insert_one(doc)
    return MemoryOut(id=doc["id"], title=doc["title"], content=doc["content"], created_at=doc["created_at"])

@router.delete("/{memory_id}")
async def delete_memory(memory_id: str, user=Depends(get_current_user), db = Depends(get_db)):
    res = await db.memories.delete_one({"id": memory_id, "user_id": user["id"]})
    if res.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Memory not found")
    return {"ok": True}
