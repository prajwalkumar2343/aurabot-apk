import json
import re
import subprocess
import tempfile
import uuid
from fastapi import HTTPException
from pathlib import Path
from typing import Optional

from app.models.chat import ChatIn
from app.models.mini_apps import MiniAppBuildIn, MiniAppBundle, MiniAppRevisionIn
from app.services.llm import call_gemini, call_openai, call_openrouter


MINI_APP_BUILDER_SKILL_PATH = Path(__file__).resolve().parents[3] / "memory" / "mini_app_builder_skill.md"
BACKEND_ROOT = Path(__file__).resolve().parents[2]
ESBUILD_BIN = BACKEND_ROOT / "node_modules" / ".bin" / "esbuild"

SUPPORTED_COMPONENTS = {
    "dashboard_block",
    "quick_action_grid",
    "timeline",
    "progress_ring",
    "streak_view",
    "chart",
    "form",
    "list",
    "bottom_sheet",
    "button",
    "slider",
    "settings",
}
SUPPORTED_ACTIONS = {"create_record", "query_records", "open_screen", "update_record", "delete_record"}
SUPPORTED_CAPABILITIES = {"local_storage", "assistant_actions", "notifications", "react_runtime", "scoped_storage"}
SUPPORTED_FIELDS = {"text", "number", "boolean", "date", "datetime"}
SUPPORTED_RUNTIMES = {"native", "react"}
SUPPORTED_CODE_APIS = {"records"}
MAX_APP_JSX_CHARS = 30000
MAX_CSS_CHARS = 16000
MAX_COMPILED_JS_CHARS = 1_500_000
BLOCKED_CODE_PATTERNS = (
    "addEventListener('message'",
    'addEventListener("message"',
    "document.cookie",
    "localStorage",
    "sessionStorage",
    "indexedDB",
    "navigator.serviceWorker",
    "new Function",
    "eval(",
    "WebSocket",
    "XMLHttpRequest",
    "fetch(",
    "import(",
    "import ",
    "<script",
    "</script",
    "<iframe",
    "window.open",
    "document.write",
    "location.href",
)


def mini_app_builder_skill_prompt() -> str:
    try:
        return MINI_APP_BUILDER_SKILL_PATH.read_text(encoding="utf-8").strip()
    except OSError:
        return (
            "Create one safe Aura mini app bundle as JSON only. Prefer the React runtime for user-requested "
            "real apps, custom workflows, polished tools, or mini apps created from assistant chat unless the "
            "caller explicitly asks for native declarative output. React bundles include codeBundle.appJsx and "
            "css source that Aura compiles; native bundles use schema-bound screens and components. Do not include "
            "URLs, webviews, APKs, plugins, imported packages, network calls, browser storage APIs, script tags, "
            "or unsupported capabilities."
        )


def mini_app_builder_system_prompt(
    repair_error: Optional[str] = None,
    previous_output: Optional[str] = None,
    runtime: Optional[str] = None,
) -> str:
    components = ", ".join(sorted(SUPPORTED_COMPONENTS))
    if runtime == "react":
        runtime_rule = (
            "The requested runtime is react. Set runtime to react and include codeBundle with entry App.jsx, "
            "appJsx source, css, allowedApis, and empty/omitted compiledJs. "
        )
    elif runtime == "native":
        runtime_rule = (
            "The requested runtime is native. Set runtime to native, omit codeBundle, and build the UI with "
            "supported declarative screens and components. "
        )
    else:
        runtime_rule = (
            "Choose runtime react for real apps, custom workflows, polished tools, or assistant-chat mini app "
            "creation; choose native only for explicitly declarative/simple tracker requests. "
        )
    prompt = (
        f"{mini_app_builder_skill_prompt()}\n\n"
        f"Supported component types: {components}. "
        f"{runtime_rule}"
        "Use camelCase fields exactly matching the schema: id, version, runtime, metadata, theme, icon, dataSchema, screens, actions, assistantIntents, capabilities, codeBundle. "
        "When creating React mini apps, set runtime to react, include react_runtime and scoped_storage capabilities, and include codeBundle with appJsx and css. "
        "React code must declare `export default function App(props)` and use only React plus the provided Aura APIs from props: records.list, records.create, records.update, records.delete. "
        "Do not import packages, fetch URLs, use cookies, localStorage, sessionStorage, indexedDB, WebSocket, eval, new Function, script tags, or global message listeners. "
        "Make the UI polished and app-like with at least two screens when useful, such as Dashboard plus Details, Plan, or Settings. "
        "Use dashboard blocks, quick actions, timeline/history, chart, list, button, slider, settings, bottom_sheet, and assistant intents when useful."
    )
    if repair_error:
        prompt += (
            "\n\nRepair pass:\n"
            f"- Previous bundle error: {repair_error}\n"
            "- Return corrected JSON only.\n"
            f"- Previous output excerpt: {(previous_output or '')[:800]}"
        )
    return prompt


def mini_app_revision_system_prompt(data: MiniAppRevisionIn, repair_error: Optional[str] = None, previous_output: Optional[str] = None) -> str:
    current = data.currentBundle.model_dump(exclude_none=True)
    record_sample = data.recordSample[:8]
    requested_runtime = data.runtime or data.currentBundle.runtime
    prompt = (
        f"{mini_app_builder_system_prompt(runtime=requested_runtime)}\n\n"
        "You are revising an existing installed Aura mini app. Return JSON only with this exact top-level shape: "
        '{"bundle":{...},"summary":"short user-facing summary","migrationPlan":["step"]}. '
        "The bundle must keep the same id as the current app, increment version by exactly 1, and remain compatible "
        "with existing local records. Preserve existing record fields unless the user explicitly asks to remove them. "
        "When the user asks to track a new attribute, add it to dataSchema.fields, add useful UI for entering/viewing it, "
        "add or update chart/list/timeline components when helpful, add quick actions when safe, and add assistantIntents "
        "for natural voice use. For React apps, revise codeBundle.appJsx and css while keeping only the allowed records API. "
        "Do not include compiledJs; Aura will compile it. Do not use imports, network calls, browser storage, eval, or script tags.\n\n"
        f"Current bundle JSON:\n{json.dumps(current, ensure_ascii=False)}\n\n"
        f"Recent record sample JSON:\n{json.dumps(record_sample, ensure_ascii=False)}\n\n"
        f"User revision instruction:\n{data.instruction.strip()}"
    )
    if repair_error:
        prompt += (
            "\n\nRepair pass:\n"
            f"- Previous revision error: {repair_error}\n"
            "- Return corrected JSON only with bundle, summary, and migrationPlan.\n"
            f"- Previous output excerpt: {(previous_output or '')[:800]}"
        )
    return prompt


def parse_json_object(raw: str) -> dict:
    text = raw.strip()
    try:
        payload = json.loads(text)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", text, flags=re.DOTALL)
        if not match:
            raise HTTPException(status_code=422, detail="LLM returned malformed JSON")
        try:
            payload = json.loads(match.group(0))
        except json.JSONDecodeError:
            raise HTTPException(status_code=422, detail="LLM returned malformed JSON")
    if not isinstance(payload, dict):
        raise HTTPException(status_code=422, detail="LLM response must be a JSON object")
    return payload


def validate_mini_app_bundle(payload: dict) -> MiniAppBundle:
    try:
        bundle = MiniAppBundle(**payload)
    except Exception as exc:
        raise HTTPException(status_code=422, detail=f"Invalid mini app bundle: {str(exc)[:200]}")

    if not bundle.id.strip():
        raise HTTPException(status_code=422, detail="Mini app id is required")
    if bundle.runtime not in SUPPORTED_RUNTIMES:
        raise HTTPException(status_code=422, detail=f"Unsupported runtime: {bundle.runtime}")
    if not bundle.metadata.name.strip():
        raise HTTPException(status_code=422, detail="Mini app name is required")
    if bundle.runtime == "native" and not bundle.screens:
        raise HTTPException(status_code=422, detail="At least one screen is required")
    if bundle.runtime == "react":
        validate_react_code_bundle(bundle)
    for capability in bundle.capabilities:
        if capability not in SUPPORTED_CAPABILITIES:
            raise HTTPException(status_code=422, detail=f"Unsupported capability: {capability}")
    if not bundle.dataSchema.recordType.strip():
        raise HTTPException(status_code=422, detail="dataSchema.recordType is required")
    for field in bundle.dataSchema.fields:
        if not field.name.strip():
            raise HTTPException(status_code=422, detail="Field names are required")
        if field.type not in SUPPORTED_FIELDS:
            raise HTTPException(status_code=422, detail=f"Unsupported field type: {field.type}")
    field_names = [field.name for field in bundle.dataSchema.fields]
    if len(set(field_names)) != len(field_names):
        raise HTTPException(status_code=422, detail="Field names must be unique")
    schema_field_names = set(field_names)
    action_ids = {action.id for action in bundle.actions}
    if len(action_ids) != len(bundle.actions):
        raise HTTPException(status_code=422, detail="Action ids must be unique")
    for action in bundle.actions:
        if not action.id.strip():
            raise HTTPException(status_code=422, detail="Action id is required")
        if action.type not in SUPPORTED_ACTIONS:
            raise HTTPException(status_code=422, detail=f"Unsupported action: {action.type}")
        if action.type == "create_record":
            if action.recordType not in {"record", bundle.dataSchema.recordType}:
                raise HTTPException(status_code=422, detail=f"Unsupported action record type: {action.recordType}")
            for field_name in action.values:
                if field_name not in schema_field_names:
                    raise HTTPException(status_code=422, detail=f"Unknown action field: {field_name}")
    screen_ids = {screen.id for screen in bundle.screens}
    if len(screen_ids) != len(bundle.screens):
        raise HTTPException(status_code=422, detail="Screen ids must be unique")
    for screen in bundle.screens:
        if not screen.id.strip():
            raise HTTPException(status_code=422, detail="Screen id is required")
        for component in screen.components:
            if component.type not in SUPPORTED_COMPONENTS:
                raise HTTPException(status_code=422, detail=f"Unsupported component: {component.type}")
            if component.actionId and component.actionId not in action_ids:
                raise HTTPException(status_code=422, detail=f"Unknown action: {component.actionId}")
            for item in component.items:
                if item.actionId and item.actionId not in action_ids:
                    raise HTTPException(status_code=422, detail=f"Unknown action: {item.actionId}")
    intent_names = {intent.name for intent in bundle.assistantIntents}
    if len(intent_names) != len(bundle.assistantIntents):
        raise HTTPException(status_code=422, detail="Intent names must be unique")
    for intent in bundle.assistantIntents:
        if not intent.name.strip():
            raise HTTPException(status_code=422, detail="Intent names are required")
        if intent.actionId and intent.actionId not in action_ids:
            raise HTTPException(status_code=422, detail=f"Unknown intent action: {intent.actionId}")
        if intent.screenId and intent.screenId not in screen_ids:
            raise HTTPException(status_code=422, detail=f"Unknown intent screen: {intent.screenId}")
    return bundle


def compile_mini_app_bundle(bundle: MiniAppBundle) -> MiniAppBundle:
    if bundle.runtime != "react" or bundle.codeBundle is None:
        return bundle
    if not ESBUILD_BIN.exists():
        raise HTTPException(status_code=503, detail="React mini app compiler is not installed. Run npm install in backend.")
    source = (
        'import React from "react";\n'
        'import { createRoot } from "react-dom/client";\n'
        f"{bundle.codeBundle.appJsx}\n"
        "window.__AuraMiniAppMount = function(container, auraApi) {\n"
        "  createRoot(container).render(React.createElement(App, auraApi));\n"
        "};\n"
    )
    with tempfile.TemporaryDirectory(prefix=".aura-mini-app-", dir=str(BACKEND_ROOT)) as tmp:
        entry = Path(tmp) / "App.jsx"
        outfile = Path(tmp) / "bundle.js"
        entry.write_text(source, encoding="utf-8")
        result = subprocess.run(
            [
                str(ESBUILD_BIN),
                str(entry),
                "--bundle",
                "--format=iife",
                "--platform=browser",
                "--loader:.jsx=jsx",
                "--target=es2018",
                f"--outfile={outfile}",
            ],
            cwd=str(BACKEND_ROOT),
            text=True,
            capture_output=True,
            timeout=10,
            check=False,
        )
        if result.returncode != 0:
            message = (result.stderr or result.stdout or "React compiler failed").strip()
            raise HTTPException(status_code=422, detail=f"React compile failed: {message[:300]}")
        compiled = outfile.read_text(encoding="utf-8")
    if len(compiled) > MAX_COMPILED_JS_CHARS:
        raise HTTPException(status_code=422, detail="Compiled React mini app is too large")
    bundle.codeBundle.compiledJs = compiled
    return bundle


def validate_react_code_bundle(bundle: MiniAppBundle) -> None:
    code = bundle.codeBundle
    if code is None:
        raise HTTPException(status_code=422, detail="React mini apps require codeBundle")
    if code.entry != "App.jsx":
        raise HTTPException(status_code=422, detail="React codeBundle entry must be App.jsx")
    if len(code.appJsx) > MAX_APP_JSX_CHARS:
        raise HTTPException(status_code=422, detail="React appJsx is too large")
    if len(code.css) > MAX_CSS_CHARS:
        raise HTTPException(status_code=422, detail="React css is too large")
    if code.compiledJs.strip():
        raise HTTPException(status_code=422, detail="React compiledJs is generated by Aura and must not be supplied")
    if "export default function App" not in code.appJsx:
        raise HTTPException(status_code=422, detail="React appJsx must export a default function App component")
    for api in code.allowedApis:
        if api not in SUPPORTED_CODE_APIS:
            raise HTTPException(status_code=422, detail=f"Unsupported React API: {api}")
    combined = f"{code.appJsx}\n{code.css}".lower()
    for pattern in BLOCKED_CODE_PATTERNS:
        if pattern.lower() in combined:
            raise HTTPException(status_code=422, detail=f"Blocked React code pattern: {pattern}")


def react_fallback_bundle(prompt: str) -> MiniAppBundle:
    cleaned = " ".join(prompt.strip().split())
    name = " ".join(cleaned.split()[:3]).title() or "React Mini App"
    slug = re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_") or uuid.uuid4().hex[:8]
    app_jsx = """
export default function App({ records }) {
  const [items, setItems] = React.useState([]);
  const [title, setTitle] = React.useState("");
  const [note, setNote] = React.useState("");
  const [saving, setSaving] = React.useState(false);

  async function refresh() {
    const next = await records.list("entry");
    setItems(next);
  }

  React.useEffect(() => {
    refresh();
  }, []);

  async function saveEntry(event) {
    event.preventDefault();
    if (!title.trim()) return;
    setSaving(true);
    await records.create("entry", { title: title.trim(), note: note.trim(), status: "Active" });
    setTitle("");
    setNote("");
    await refresh();
    setSaving(false);
  }

  async function deleteEntry(id) {
    await records.delete(id);
    await refresh();
  }

  return (
    <main className="app-shell">
      <section className="hero">
        <div>
          <p className="eyebrow">Aura React mini app</p>
          <h1>""" + name + """</h1>
          <p>""" + cleaned.replace('"', '\\"') + """</p>
        </div>
        <strong>{items.length}</strong>
      </section>

      <form className="entry-form" onSubmit={saveEntry}>
        <input value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Title" />
        <textarea value={note} onChange={(event) => setNote(event.target.value)} placeholder="Note" />
        <button disabled={saving || !title.trim()}>{saving ? "Saving..." : "Save entry"}</button>
      </form>

      <section className="list">
        {items.length === 0 ? <p className="empty">No entries yet.</p> : items.map((item) => (
          <article key={item.id}>
            <div>
              <h2>{item.values.title || "Untitled"}</h2>
              <p>{item.values.note || item.values.status || "Saved"}</p>
            </div>
            <button onClick={() => deleteEntry(item.id)} aria-label="Delete entry">Delete</button>
          </article>
        ))}
      </section>
    </main>
  );
}
""".strip()
    css = """
:root { color-scheme: light; font-family: Inter, system-ui, sans-serif; background: #f7f8fb; color: #172033; }
* { box-sizing: border-box; }
body { margin: 0; }
button, input, textarea { font: inherit; }
.app-shell { min-height: 100vh; padding: 18px; display: grid; gap: 16px; background: linear-gradient(180deg, #f7f8fb, #eef7f4); }
.hero { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; padding: 20px; border-radius: 28px; background: #111827; color: white; box-shadow: 0 18px 40px rgba(17, 24, 39, 0.18); }
.hero h1 { margin: 4px 0 8px; font-size: 28px; line-height: 1; }
.hero p { margin: 0; color: rgba(255,255,255,.72); }
.hero strong { display: grid; place-items: center; min-width: 64px; height: 64px; border-radius: 22px; background: #2dd4bf; color: #042f2e; font-size: 26px; }
.eyebrow { text-transform: uppercase; letter-spacing: .08em; font-size: 11px; font-weight: 800; }
.entry-form { display: grid; gap: 10px; padding: 14px; border: 1px solid #e1e7ef; border-radius: 24px; background: white; }
input, textarea { width: 100%; border: 1px solid #d7dee8; border-radius: 16px; padding: 13px 14px; background: #fbfcfe; color: #172033; outline: none; }
textarea { min-height: 88px; resize: vertical; }
.entry-form button { border: 0; border-radius: 16px; padding: 14px; background: #2563eb; color: white; font-weight: 800; }
.entry-form button:disabled { opacity: .55; }
.list { display: grid; gap: 10px; }
article { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px; border: 1px solid #e1e7ef; border-radius: 20px; background: white; }
article h2 { margin: 0 0 4px; font-size: 16px; }
article p, .empty { margin: 0; color: #687386; }
article button { border: 1px solid #fecaca; border-radius: 12px; padding: 9px 11px; background: #fff1f2; color: #be123c; font-weight: 800; }
""".strip()
    return validate_mini_app_bundle(
        {
            "id": f"generated.react.{slug}",
            "version": 1,
            "runtime": "react",
            "metadata": {"name": name, "description": f"A React mini app created from: {cleaned}", "category": "Custom"},
            "theme": {"primary": "#2563EB", "secondary": "#14B8A6", "surface": "#F7F8FB"},
            "icon": {"type": "initial", "value": name[:1].upper(), "background": "#2563EB"},
            "dataSchema": {
                "recordType": "entry",
                "fields": [
                    {"name": "title", "type": "text", "required": True},
                    {"name": "note", "type": "text"},
                    {"name": "status", "type": "text"},
                ],
            },
            "screens": [],
            "actions": [],
            "assistantIntents": [
                {"name": "open_app", "utterances": [f"open {name}", f"show {name}"]},
            ],
            "capabilities": ["local_storage", "assistant_actions", "react_runtime", "scoped_storage"],
            "codeBundle": {"entry": "App.jsx", "appJsx": app_jsx, "css": css, "allowedApis": ["records"]},
        }
    )


def fallback_bundle(prompt: str) -> MiniAppBundle:
    cleaned = " ".join(prompt.strip().split())
    name = " ".join(cleaned.split()[:3]).title() or "Mini App"
    slug = re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_") or uuid.uuid4().hex[:8]
    normalized = cleaned.lower()
    if any(word in normalized for word in ("habit", "health", "water", "workout", "wellness")):
        category = "Wellness"
        primary = "#16A34A"
    elif any(word in normalized for word in ("money", "spend", "expense", "budget", "finance")):
        category = "Finance"
        primary = "#0F766E"
    elif any(word in normalized for word in ("focus", "task", "plan", "work", "study")):
        category = "Productivity"
        primary = "#2563EB"
    else:
        category = "Custom"
        primary = "#7C3AED"
    return validate_mini_app_bundle(
        {
            "id": f"generated.{slug}",
            "version": 1,
            "metadata": {"name": name, "description": f"A local {category.lower()} app created from: {cleaned}", "category": category},
            "theme": {"primary": primary, "secondary": "#F59E0B", "surface": "#111827"},
            "icon": {"type": "initial", "value": name[:1].upper(), "background": primary},
            "dataSchema": {
                "recordType": "entry",
                "fields": [
                    {"name": "title", "type": "text", "required": True},
                    {"name": "status", "type": "text", "required": True},
                    {"name": "note", "type": "text"},
                ],
            },
            "actions": [
                {"id": "quick_add", "type": "create_record", "recordType": "entry", "values": {"title": cleaned, "status": "Logged"}},
                {"id": "mark_priority", "type": "create_record", "recordType": "entry", "values": {"title": "Priority", "status": cleaned}},
                {"id": "save_note", "type": "create_record", "recordType": "entry", "values": {"title": "Note", "status": "Captured"}},
            ],
            "assistantIntents": [
                {"name": "quick_add", "utterances": [f"add to {name}", f"log {name}"], "actionId": "quick_add"},
                {"name": "show_dashboard", "utterances": [f"open {name}", f"show {name}"], "screenId": "dashboard"},
            ],
            "screens": [
                {
                    "id": "dashboard",
                    "title": "Dashboard",
                    "components": [
                        {"type": "dashboard_block", "title": "Today", "metric": "today_count"},
                        {"type": "streak_view", "title": "Momentum", "metric": "streak"},
                        {
                            "type": "quick_action_grid",
                            "title": "Actions",
                            "items": [
                                {"label": "Log", "actionId": "quick_add"},
                                {"label": "Priority", "actionId": "mark_priority"},
                                {"label": "Note", "actionId": "save_note"},
                            ],
                        },
                        {"type": "chart", "title": "Last 7 Days", "metric": "weekly_count"},
                        {"type": "timeline", "title": "Activity", "source": "records"},
                        {"type": "slider", "title": "Weekly Pace", "metric": "weekly_count"},
                    ],
                },
                {
                    "id": "details",
                    "title": "Details",
                    "components": [
                        {"type": "form", "title": "Custom Entry", "items": [{"label": "Save entry"}]},
                        {
                            "type": "list",
                            "title": "Shortcuts",
                            "items": [
                                {"label": "Log entry", "actionId": "quick_add", "value": "Capture the default item"},
                                {"label": "Mark priority", "actionId": "mark_priority", "value": "Pin the most important thing"},
                                {"label": "Save note", "actionId": "save_note", "value": "Keep a lightweight note"},
                            ],
                        },
                        {"type": "button", "title": "Log now", "actionId": "quick_add"},
                        {
                            "type": "bottom_sheet",
                            "title": "App note",
                            "items": [
                                {
                                    "label": "This generated app starts with local capture, history, progress, and assistant actions."
                                }
                            ],
                        },
                        {"type": "settings", "title": "App setup"},
                    ],
                },
            ],
            "capabilities": ["local_storage", "assistant_actions"],
        }
    )


def call_builder_llm(data: MiniAppBuildIn, system_prompt: Optional[str] = None) -> str:
    chat = ChatIn(
        message=data.prompt,
        provider=data.provider,
        api_key=data.api_key,
        model=data.model,
    )
    provider = data.provider.lower().strip()
    system = system_prompt or mini_app_builder_system_prompt(runtime=data.runtime)
    if provider == "gemini":
        return call_gemini(chat, system)
    if provider == "openai":
        return call_openai(chat, system)
    if provider == "openrouter":
        return call_openrouter(chat, system)
    raise HTTPException(status_code=400, detail="Unsupported provider")


def call_revision_llm(data: MiniAppRevisionIn, system_prompt: Optional[str] = None) -> str:
    chat = ChatIn(
        message=data.instruction,
        provider=data.provider,
        api_key=data.api_key,
        model=data.model,
    )
    provider = data.provider.lower().strip()
    system = system_prompt or mini_app_revision_system_prompt(data)
    if provider == "gemini":
        return call_gemini(chat, system)
    if provider == "openai":
        return call_openai(chat, system)
    if provider == "openrouter":
        return call_openrouter(chat, system)
    raise HTTPException(status_code=400, detail="Unsupported provider")
