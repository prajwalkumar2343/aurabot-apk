import uuid
import asyncio
import requests
import logging
from fastapi import APIRouter, HTTPException, Depends
from app.core.database import get_db
from app.core.security import get_current_user
from app.models.chat import ChatIn, ChatOut, ChatMemoryIn
from app.models.provider import OpenRouterModelsIn, ProviderModelsOut, ProviderModelOut
from app.services.memory import get_memory_service
from app.services.llm import (
    build_system_message,
    call_gemini,
    call_openai,
    call_openrouter,
    parse_tool_response,
)
from app.services.prompt_harness import build_prompt_harness, build_repair_system_message, repair_needed

logger = logging.getLogger(__name__)
router = APIRouter(tags=["Assistant"])


def _call_provider(provider: str, data: ChatIn, system_message: str) -> str:
    if provider == "gemini":
        return call_gemini(data, system_message, True)
    if provider == "openai":
        return call_openai(data, system_message, True)
    if provider == "openrouter":
        return call_openrouter(data, system_message, True)
    raise HTTPException(status_code=400, detail="Unsupported provider")


@router.post("/assistant/chat", response_model=ChatOut)
async def assistant_chat(
    data: ChatIn,
    user=Depends(get_current_user),
    db=Depends(get_db),
):
    if not data.message.strip():
        raise HTTPException(status_code=400, detail="Message is required")

    memory_context = list(data.memories)
    try:
        retrieved = await get_memory_service(db).search_memories(user["id"], data.message, 8)
        memory_context = [
            ChatMemoryIn(title=item.title, content=item.chunk_text)
            for item in retrieved
        ] or memory_context
    except Exception:
        logger.exception("Failed to retrieve cloud memories for chat")

    contextual_data = data.model_copy(update={"memories": memory_context})
    harness = build_prompt_harness(contextual_data)
    routed_data = contextual_data.model_copy(update={"model": harness.routed_model or contextual_data.model})
    session_id = data.session_id or str(uuid.uuid4())
    system_message = build_system_message(routed_data, harness)
    provider = data.provider.lower().strip()

    try:
        raw_reply = await asyncio.to_thread(_call_provider, provider, routed_data, system_message)
        reply, actions = parse_tool_response(raw_reply)
        reason = repair_needed(raw_reply, reply, actions, routed_data)
        attempts = 0
        while reason and attempts < harness.max_repair_attempts:
            attempts += 1
            repair_system_message = build_repair_system_message(system_message, reason, raw_reply)
            raw_reply = await asyncio.to_thread(_call_provider, provider, routed_data, repair_system_message)
            reply, actions = parse_tool_response(raw_reply)
            reason = repair_needed(raw_reply, reply, actions, routed_data)
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("assistant_chat failed")
        raise HTTPException(status_code=500, detail=f"Assistant error: {str(e)[:200]}")

    return ChatOut(reply=reply, session_id=session_id, actions=actions)

@router.post("/providers/openrouter/models", response_model=ProviderModelsOut)
async def openrouter_models(data: OpenRouterModelsIn, user=Depends(get_current_user)):
    try:
        response = await asyncio.to_thread(
            requests.get,
            "https://openrouter.ai/api/v1/models",
            headers={"Authorization": f"Bearer {data.api_key}"},
            timeout=30,
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"OpenRouter request failed: {str(e)[:200]}")
    if response.status_code >= 400:
        raise HTTPException(status_code=response.status_code, detail=f"OpenRouter error: {response.text[:300]}")
    payload = response.json()
    models = [
        ProviderModelOut(id=item.get("id", ""), name=item.get("name") or item.get("id", ""))
        for item in payload.get("data", [])
        if item.get("id")
    ]
    models.sort(key=lambda item: item.name.lower())
    return ProviderModelsOut(data=models)
