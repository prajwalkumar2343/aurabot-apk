import uuid
import asyncio
import requests
import logging
from fastapi import APIRouter, HTTPException
from app.models.chat import ChatIn, ChatOut
from app.models.provider import OpenRouterModelsIn, ProviderModelsOut, ProviderModelOut
from app.services.llm import (
    build_system_message,
    call_gemini,
    call_openai,
    call_openrouter,
    parse_tool_response,
)

logger = logging.getLogger(__name__)
router = APIRouter(tags=["Assistant"])

@router.post("/assistant/chat", response_model=ChatOut)
async def assistant_chat(data: ChatIn):
    if not data.message.strip():
        raise HTTPException(status_code=400, detail="Message is required")

    session_id = data.session_id or str(uuid.uuid4())
    system_message = build_system_message(data)
    provider = data.provider.lower().strip()

    try:
        if provider == "gemini":
            raw_reply = await asyncio.to_thread(call_gemini, data, system_message, True)
        elif provider == "openai":
            raw_reply = await asyncio.to_thread(call_openai, data, system_message, True)
        elif provider == "openrouter":
            raw_reply = await asyncio.to_thread(call_openrouter, data, system_message, True)
        else:
            raise HTTPException(status_code=400, detail="Unsupported provider")
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("assistant_chat failed")
        raise HTTPException(status_code=500, detail=f"Assistant error: {str(e)[:200]}")

    reply, actions = parse_tool_response(raw_reply)
    return ChatOut(reply=reply, session_id=session_id, actions=actions)

@router.post("/providers/openrouter/models", response_model=ProviderModelsOut)
async def openrouter_models(data: OpenRouterModelsIn):
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
