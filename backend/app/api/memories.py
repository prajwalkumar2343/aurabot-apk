from fastapi import APIRouter, HTTPException, Depends
from typing import List
from app.core.database import get_db
from app.core.security import get_current_user
from app.models.memory import MemoryCreate, MemoryOut, MemorySearchIn, MemorySearchOut
from app.services.memory import get_memory_service

router = APIRouter(prefix="/memories", tags=["Memories"])

@router.get("", response_model=List[MemoryOut])
async def list_memories(user=Depends(get_current_user), db = Depends(get_db)):
    return await get_memory_service(db).list_memories(user["id"])

@router.post("", response_model=MemoryOut)
async def create_memory(data: MemoryCreate, user=Depends(get_current_user), db = Depends(get_db)):
    return await get_memory_service(db).create_memory(user["id"], data)

@router.post("/search", response_model=List[MemorySearchOut])
async def search_memories(data: MemorySearchIn, user=Depends(get_current_user), db = Depends(get_db)):
    return await get_memory_service(db).search_memories(user["id"], data.query, data.limit)

@router.delete("/{memory_id}")
async def delete_memory(memory_id: str, user=Depends(get_current_user), db = Depends(get_db)):
    deleted = await get_memory_service(db).delete_memory(user["id"], memory_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="Memory not found")
    return {"ok": True}
