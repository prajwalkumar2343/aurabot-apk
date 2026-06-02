import asyncio
from fastapi import APIRouter, HTTPException

from app.models.mini_apps import MiniAppBuildIn, MiniAppBuildOut
from app.services.mini_apps import (
    call_builder_llm,
    mini_app_builder_system_prompt,
    parse_json_object,
    validate_mini_app_bundle,
)

router = APIRouter(prefix="/mini-apps", tags=["Mini Apps"])


@router.post("/build", response_model=MiniAppBuildOut)
async def build_mini_app(data: MiniAppBuildIn):
    if not data.prompt.strip():
        raise HTTPException(status_code=400, detail="Prompt is required")
    raw = await asyncio.to_thread(call_builder_llm, data)
    try:
        bundle = validate_mini_app_bundle(parse_json_object(raw))
    except HTTPException as first_error:
        repair_prompt = mini_app_builder_system_prompt(str(first_error.detail), raw)
        repaired = await asyncio.to_thread(call_builder_llm, data, repair_prompt)
        bundle = validate_mini_app_bundle(parse_json_object(repaired))
    return MiniAppBuildOut(bundle=bundle)
