import asyncio
from fastapi import APIRouter, HTTPException

from app.models.mini_apps import MiniAppBuildIn, MiniAppBuildOut
from app.services.mini_apps import call_builder_llm, parse_json_object, validate_mini_app_bundle

router = APIRouter(prefix="/mini-apps", tags=["Mini Apps"])


@router.post("/build", response_model=MiniAppBuildOut)
async def build_mini_app(data: MiniAppBuildIn):
    if not data.prompt.strip():
        raise HTTPException(status_code=400, detail="Prompt is required")
    raw = await asyncio.to_thread(call_builder_llm, data)
    bundle = validate_mini_app_bundle(parse_json_object(raw))
    return MiniAppBuildOut(bundle=bundle)
