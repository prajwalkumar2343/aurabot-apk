import uuid
import asyncio
import requests
import logging
from typing import Optional
from fastapi import APIRouter, Header, HTTPException, Depends
from app.core.database import get_db
from app.core.security import get_current_user
from app.models.agent_runs import AgentRunAcceptedOut, AgentRunCreateIn, AgentRunOut
from app.models.chat import ChatIn, ChatOut, ChatMemoryIn
from app.models.provider import OpenRouterModelsIn, ProviderModelsOut, ProviderModelOut
from app.services.memory import get_memory_service
from app.services.llm import (
    build_system_message,
    parse_assistant_response,
)
from app.services.prompt_harness import (
    build_prompt_harness,
    build_repair_system_message,
    repair_needed,
)
from app.services.agent_credentials import AgentCredentialError, seal_agent_credential
from app.services.agent_harness import call_provider
from app.services.agent_runs import AgentRunStore
from app.services.provider_credentials import resolve_provider_credentials

logger = logging.getLogger(__name__)
router = APIRouter(tags=["Assistant"])


def _call_provider(provider: str, data: ChatIn, system_message: str) -> str:
    return call_provider(provider, data, system_message, True)


@router.post("/assistant/runs", response_model=AgentRunAcceptedOut, status_code=202)
async def create_assistant_run(
    data: AgentRunCreateIn,
    idempotency_key: Optional[str] = Header(default=None, alias="Idempotency-Key"),
    user=Depends(get_current_user),
    db=Depends(get_db),
):
    data = resolve_provider_credentials(data, user)
    if not data.message.strip() and not data.image_base64:
        raise HTTPException(status_code=400, detail="Message or image is required")
    if idempotency_key is not None:
        idempotency_key = idempotency_key.strip()
        if not idempotency_key or len(idempotency_key) > 200:
            raise HTTPException(status_code=400, detail="Invalid Idempotency-Key")
    memory_context = list(data.memories)
    if data.message.strip():
        try:
            retrieved = await get_memory_service(db).search_memories(
                user["id"], data.message, 8
            )
            memory_context = [
                ChatMemoryIn(title=item.title, content=item.chunk_text)
                for item in retrieved
            ] or memory_context
        except Exception:
            logger.exception("Failed to retrieve cloud memories for agent run")
    contextual_data = data.model_copy(update={"memories": memory_context})
    store = AgentRunStore(db)
    try:
        credential = seal_agent_credential(contextual_data.api_key)
    except AgentCredentialError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    run = await store.create_root(
        user["id"],
        contextual_data,
        idempotency_key=idempotency_key,
        credential_ciphertext=credential,
    )
    return AgentRunAcceptedOut(
        run_id=run["id"],
        session_id=run["session_id"],
        state=run["state"],
    )


@router.get("/assistant/runs/{run_id}", response_model=AgentRunOut)
async def get_assistant_run(
    run_id: str,
    user=Depends(get_current_user),
    db=Depends(get_db),
):
    snapshot = await AgentRunStore(db).snapshot(user["id"], run_id)
    if snapshot is None:
        raise HTTPException(status_code=404, detail="Agent run not found")
    return snapshot


@router.post("/assistant/runs/{run_id}/cancel", response_model=AgentRunOut)
async def cancel_assistant_run(
    run_id: str,
    user=Depends(get_current_user),
    db=Depends(get_db),
):
    snapshot = await AgentRunStore(db).cancel(user["id"], run_id)
    if snapshot is None:
        raise HTTPException(status_code=404, detail="Agent run not found")
    return snapshot


@router.post("/assistant/chat", response_model=ChatOut)
async def assistant_chat(
    data: ChatIn,
    user=Depends(get_current_user),
    db=Depends(get_db),
):
    data = resolve_provider_credentials(data, user)
    if not data.message.strip():
        raise HTTPException(status_code=400, detail="Message is required")

    memory_context = list(data.memories)
    try:
        retrieved = await get_memory_service(db).search_memories(
            user["id"], data.message, 8
        )
        memory_context = [
            ChatMemoryIn(title=item.title, content=item.chunk_text)
            for item in retrieved
        ] or memory_context
    except Exception:
        logger.exception("Failed to retrieve cloud memories for chat")

    contextual_data = data.model_copy(update={"memories": memory_context})
    harness = build_prompt_harness(contextual_data)
    routed_data = contextual_data.model_copy(
        update={"model": harness.routed_model or contextual_data.model}
    )
    session_id = data.session_id or str(uuid.uuid4())
    system_message = build_system_message(routed_data, harness)
    provider = data.provider.lower().strip()

    try:
        raw_reply = await asyncio.to_thread(
            _call_provider, provider, routed_data, system_message
        )
        parsed = parse_assistant_response(raw_reply)
        reason = repair_needed(raw_reply, parsed.reply, parsed.actions, routed_data)
        attempts = 0
        while reason and attempts < harness.max_repair_attempts:
            attempts += 1
            repair_system_message = build_repair_system_message(
                system_message, reason, raw_reply
            )
            raw_reply = await asyncio.to_thread(
                _call_provider, provider, routed_data, repair_system_message
            )
            parsed = parse_assistant_response(raw_reply)
            reason = repair_needed(raw_reply, parsed.reply, parsed.actions, routed_data)
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("assistant_chat failed")
        raise HTTPException(status_code=500, detail=f"Assistant error: {str(e)[:200]}")

    return ChatOut(
        reply=parsed.reply,
        session_id=session_id,
        emotion=parsed.emotion,
        created_emotion=parsed.created_emotion,
        actions=parsed.actions,
    )


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
        raise HTTPException(
            status_code=500, detail=f"OpenRouter request failed: {str(e)[:200]}"
        )
    if response.status_code >= 400:
        raise HTTPException(
            status_code=response.status_code,
            detail=f"OpenRouter error: {response.text[:300]}",
        )
    payload = response.json()
    models = [
        ProviderModelOut(
            id=item.get("id", ""), name=item.get("name") or item.get("id", "")
        )
        for item in payload.get("data", [])
        if item.get("id")
    ]
    models.sort(key=lambda item: item.name.lower())
    return ProviderModelsOut(data=models)
