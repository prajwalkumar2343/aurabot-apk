import uuid
from datetime import datetime, timezone
from fastapi import APIRouter, HTTPException, Depends
from typing import List
from app.core.database import get_db
from app.core.security import get_current_user
from app.models.todo import TodoCreate, TodoUpdate, TodoOut

router = APIRouter(prefix="/todos", tags=["Todos"])

@router.get("", response_model=List[TodoOut])
async def list_todos(user=Depends(get_current_user), db = Depends(get_db)):
    cursor = db.todos.find({"user_id": user["id"]}, {"_id": 0}).sort("created_at", -1)
    items = await cursor.to_list(1000)
    return [TodoOut(id=i["id"], title=i["title"], done=i["done"], created_at=i["created_at"]) for i in items]

@router.post("", response_model=TodoOut)
async def create_todo(data: TodoCreate, user=Depends(get_current_user), db = Depends(get_db)):
    doc = {
        "id": str(uuid.uuid4()),
        "user_id": user["id"],
        "title": data.title,
        "done": False,
        "created_at": datetime.now(timezone.utc).isoformat(),
    }
    await db.todos.insert_one(doc)
    return TodoOut(id=doc["id"], title=doc["title"], done=doc["done"], created_at=doc["created_at"])

@router.patch("/{todo_id}", response_model=TodoOut)
async def update_todo(todo_id: str, data: TodoUpdate, user=Depends(get_current_user), db = Depends(get_db)):
    updates = {k: v for k, v in data.model_dump().items() if v is not None}
    if not updates:
        raise HTTPException(status_code=400, detail="No fields to update")
    res = await db.todos.find_one_and_update(
        {"id": todo_id, "user_id": user["id"]},
        {"$set": updates},
        return_document=True,
        projection={"_id": 0},
    )
    if not res:
        raise HTTPException(status_code=404, detail="Todo not found")
    return TodoOut(id=res["id"], title=res["title"], done=res["done"], created_at=res["created_at"])

@router.delete("/{todo_id}")
async def delete_todo(todo_id: str, user=Depends(get_current_user), db = Depends(get_db)):
    res = await db.todos.delete_one({"id": todo_id, "user_id": user["id"]})
    if res.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Todo not found")
    return {"ok": True}
