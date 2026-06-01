from datetime import datetime, timezone
from fastapi import APIRouter, Depends
from app.core.security import get_current_user
from app.models.gateway import GatewayIn, GatewayOut

router = APIRouter(prefix="/gateway", tags=["Gateway"])

@router.post("/supabase", response_model=GatewayOut)
async def gateway_supabase(data: GatewayIn, user=Depends(get_current_user)):
    """Mocked Supabase gateway. Replace with real Supabase client later."""
    return GatewayOut(
        ok=True,
        action=data.action,
        result={
            "message": f"Mocked response for '{data.action}'",
            "user_id": user["id"],
            "echo": data.payload or {},
            "timestamp": datetime.now(timezone.utc).isoformat(),
        },
        mocked=True,
    )
