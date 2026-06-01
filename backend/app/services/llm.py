import json
import re
import logging
import requests
from fastapi import HTTPException
from typing import List, Tuple
from app.models.chat import ChatIn, ChatActionOut

logger = logging.getLogger(__name__)

def build_system_message(data: ChatIn) -> str:
    memories = "\n".join(f"- {item.title}: {item.content}" for item in data.memories[:8]) or "- none"
    todos = "\n".join(
        f"- [{'done' if item.done else 'open'}] {item.title}" for item in data.todos[:12]
    ) or "- none"
    apps = "\n".join(
        f"- {item.label} ({item.package_name})" for item in data.apps[:80]
    ) or "- none"
    return (
        "You are Aura, a calm launcher assistant inside an Android home app. "
        "Your responses will be read aloud by a Text-to-Speech (TTS) synthesizer. "
        "You MUST naturally embed expression tags in curly brackets in your reply text to display your expressions (emotions). The allowed tags are: {happy}, {sad}, {excited}, {thinking}, {angry}, {neutral}. For example: '{happy} I would love to help you! {excited} Let's find your apps.' or '{thinking} Let me see... {neutral} Here are your items.' Always start your reply with an expression tag. "
        "You can chat normally and you can request local tools by returning actions. "
        "Return ONLY valid JSON with this shape: "
        '{"reply":"short plain-text reply","actions":[{"type":"block_app","package_name":"exact.package","app_query":"fallback app name","duration_minutes":30}]}. '
        "Use actions only when the user asks to block, restrict, pause, or limit an app. "
        "When blocking an app, prefer an exact package_name from the installed app list and choose the requested duration in minutes. "
        "If no duration is given, use 30 minutes. No markdown, no emoji.\n\n"
        f"Local memories:\n{memories}\n\n"
        f"Local tasks:\n{todos}\n\n"
        f"Installed apps:\n{apps}"
    )

def parse_tool_response(raw: str) -> Tuple[str, List[ChatActionOut]]:
    text = raw.strip()
    try:
        payload = json.loads(text)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", text, flags=re.DOTALL)
        if not match:
            return text, []
        try:
            payload = json.loads(match.group(0))
        except json.JSONDecodeError:
            return text, []

    reply = str(payload.get("reply") or "").strip() or "Done."
    actions = []
    for item in payload.get("actions") or []:
        if not isinstance(item, dict):
            continue
        actions.append(
            ChatActionOut(
                type=str(item.get("type") or "").strip(),
                package_name=item.get("package_name"),
                app_query=item.get("app_query"),
                duration_minutes=item.get("duration_minutes"),
            )
        )
    return reply, [action for action in actions if action.type]

def extract_openai_text(payload: dict) -> str:
    output = payload.get("output", [])
    text_parts: List[str] = []
    for item in output:
        for content in item.get("content", []):
            if content.get("type") == "output_text" and content.get("text"):
                text_parts.append(content["text"])
    if text_parts:
        return "\n".join(text_parts).strip()
    raise HTTPException(status_code=502, detail="OpenAI response did not include text output")

def call_gemini(data: ChatIn, system_message: str) -> str:
    if not data.api_key:
        raise HTTPException(status_code=400, detail="Gemini API Key is required")
    try:
        response = requests.post(
            f"https://generativelanguage.googleapis.com/v1beta/models/{data.model}:generateContent",
            headers={
                "x-goog-api-key": data.api_key,
                "Content-Type": "application/json",
            },
            json={
                "contents": [
                    {
                        "parts": [
                            {
                                "text": f"{system_message}\n\nUser request:\n{data.message}"
                            }
                        ]
                    }
                ]
            },
            timeout=60,
        )
    except requests.exceptions.RequestException as e:
        logger.exception("Gemini request failed")
        raise HTTPException(status_code=502, detail=f"Failed to connect to Gemini: {str(e)}")

    if response.status_code >= 400:
        raise HTTPException(status_code=response.status_code, detail=f"Gemini error: {response.text[:300]}")
    payload = response.json()
    candidates = payload.get("candidates") or []
    parts = candidates[0].get("content", {}).get("parts", []) if candidates else []
    text = "".join(part.get("text", "") for part in parts).strip()
    if not text:
        raise HTTPException(status_code=502, detail="Gemini response did not include text")
    return text

def call_openai(data: ChatIn, system_message: str) -> str:
    if not data.api_key:
        raise HTTPException(status_code=400, detail="OpenAI API Key is required")
    try:
        response = requests.post(
            "https://api.openai.com/v1/responses",
            headers={
                "Authorization": f"Bearer {data.api_key}",
                "Content-Type": "application/json",
            },
            json={
                "model": data.model,
                "input": [
                    {
                        "role": "system",
                        "content": [
                            {
                                "type": "input_text",
                                "text": system_message,
                            }
                        ],
                    },
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "input_text",
                                "text": data.message,
                            }
                        ],
                    },
                ],
            },
            timeout=60,
        )
    except requests.exceptions.RequestException as e:
        logger.exception("OpenAI request failed")
        raise HTTPException(status_code=502, detail=f"Failed to connect to OpenAI: {str(e)}")

    if response.status_code >= 400:
        raise HTTPException(status_code=response.status_code, detail=f"OpenAI error: {response.text[:300]}")
    return extract_openai_text(response.json())

def call_openrouter(data: ChatIn, system_message: str) -> str:
    if not data.api_key:
        raise HTTPException(status_code=400, detail="OpenRouter API Key is required")
    try:
        response = requests.post(
            "https://openrouter.ai/api/v1/chat/completions",
            headers={
                "Authorization": f"Bearer {data.api_key}",
                "Content-Type": "application/json",
            },
            json={
                "model": data.model,
                "messages": [
                    {"role": "system", "content": system_message},
                    {"role": "user", "content": data.message},
                ],
            },
            timeout=60,
        )
    except requests.exceptions.RequestException as e:
        logger.exception("OpenRouter request failed")
        raise HTTPException(status_code=502, detail=f"Failed to connect to OpenRouter: {str(e)}")

    if response.status_code >= 400:
        raise HTTPException(status_code=response.status_code, detail=f"OpenRouter error: {response.text[:300]}")
    payload = response.json()
    choices = payload.get("choices") or []
    text = choices[0].get("message", {}).get("content", "").strip() if choices else ""
    if not text:
        raise HTTPException(status_code=502, detail="OpenRouter response did not include text")
    return text
