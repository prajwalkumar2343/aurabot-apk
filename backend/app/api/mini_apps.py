import asyncio
import logging
import uuid
from datetime import datetime, timezone
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException

from app.core.database import get_db
from app.core.security import get_current_user
from app.models.mini_apps import (
    MiniAppBuildIn,
    MiniAppBuildOut,
    MiniAppRecordCreate,
    MiniAppRecordOut,
    MiniAppRecordUpdate,
    MiniAppRevisionIn,
    MiniAppRevisionOut,
    MiniAppWidget,
    MiniAppWidgetBuildIn,
    MiniAppWidgetBuildOut,
)
from app.services.mini_apps import (
    call_builder_llm,
    call_revision_llm,
    compile_mini_app_bundle,
    mini_app_builder_system_prompt,
    mini_app_revision_system_prompt,
    parse_json_object,
    validate_mini_app_bundle,
)
from app.services.mini_app_widgets import (
    call_widget_llm,
    ensure_mini_app_widget,
    validate_mini_app_widget,
    widget_builder_system_prompt,
)

router = APIRouter(prefix="/mini-apps", tags=["Mini Apps"])
logger = logging.getLogger(__name__)
MAX_RECORD_VALUES = 60
MAX_RECORD_VALUE_CHARS = 4000


@router.post("/build", response_model=MiniAppBuildOut)
async def build_mini_app(data: MiniAppBuildIn, user=Depends(get_current_user)):
    trace_id = str(uuid.uuid4())
    if not data.prompt.strip():
        raise HTTPException(status_code=400, detail="Prompt is required")
    logger.info("mini_app_build_started", extra={"trace_id": trace_id, "provider": data.provider, "runtime": data.runtime})
    raw = await asyncio.to_thread(call_builder_llm, data)
    try:
        bundle = validate_mini_app_bundle(parse_json_object(raw))
        _enforce_requested_runtime(data.runtime, bundle.runtime)
    except HTTPException as first_error:
        logger.warning("mini_app_build_repair_started", extra={"trace_id": trace_id, "reason": str(first_error.detail)[:200]})
        repair_prompt = mini_app_builder_system_prompt(str(first_error.detail), raw, runtime=data.runtime)
        repaired = await asyncio.to_thread(call_builder_llm, data, repair_prompt)
        bundle = validate_mini_app_bundle(parse_json_object(repaired))
        _enforce_requested_runtime(data.runtime, bundle.runtime)
    bundle = await asyncio.to_thread(compile_mini_app_bundle, bundle)
    logger.info("mini_app_build_completed", extra={"trace_id": trace_id, "mini_app_id": bundle.id, "version": bundle.version})
    return MiniAppBuildOut(bundle=bundle)


@router.post("/revise", response_model=MiniAppRevisionOut)
async def revise_mini_app(data: MiniAppRevisionIn, user=Depends(get_current_user)):
    trace_id = str(uuid.uuid4())
    if not data.instruction.strip():
        raise HTTPException(status_code=400, detail="Revision instruction is required")
    logger.info("mini_app_revision_started", extra={"trace_id": trace_id, "mini_app_id": data.currentBundle.id})
    raw = await asyncio.to_thread(call_revision_llm, data)
    try:
        bundle, summary, migration_plan = _parse_revision_payload(raw, data)
        _enforce_requested_runtime(data.runtime or data.currentBundle.runtime, bundle.runtime)
    except HTTPException as first_error:
        logger.warning("mini_app_revision_repair_started", extra={"trace_id": trace_id, "reason": str(first_error.detail)[:200]})
        repair_prompt = mini_app_revision_system_prompt(data, str(first_error.detail), raw)
        repaired = await asyncio.to_thread(call_revision_llm, data, repair_prompt)
        bundle, summary, migration_plan = _parse_revision_payload(repaired, data)
        _enforce_requested_runtime(data.runtime or data.currentBundle.runtime, bundle.runtime)
    bundle = await asyncio.to_thread(compile_mini_app_bundle, bundle)
    logger.info("mini_app_revision_completed", extra={"trace_id": trace_id, "mini_app_id": bundle.id, "version": bundle.version})
    return MiniAppRevisionOut(bundle=bundle, summary=summary, migrationPlan=migration_plan)


@router.post("/widgets/build", response_model=MiniAppWidgetBuildOut)
async def build_mini_app_widget(data: MiniAppWidgetBuildIn, user=Depends(get_current_user)):
    trace_id = str(uuid.uuid4())
    data = _validated_widget_build_data(data)
    logger.info("mini_app_widget_build_started", extra={"trace_id": trace_id, "mini_app_id": data.miniApp.id})
    raw = await asyncio.to_thread(call_widget_llm, data)
    try:
        widget = _parse_widget_payload(raw, data)
    except HTTPException as first_error:
        logger.warning("mini_app_widget_repair_started", extra={"trace_id": trace_id, "reason": str(first_error.detail)[:200]})
        repair_prompt = widget_builder_system_prompt(
            data.miniApp,
            data.instruction,
            str(first_error.detail),
            raw,
        )
        repaired = await asyncio.to_thread(call_widget_llm, data, repair_prompt)
        widget = _parse_widget_payload(repaired, data)
    logger.info(
        "mini_app_widget_build_completed",
        extra={"trace_id": trace_id, "mini_app_id": data.miniApp.id, "widget_type": widget.type},
    )
    return MiniAppWidgetBuildOut(widget=widget)


@router.get("/{mini_app_id}/records", response_model=list[MiniAppRecordOut])
async def list_mini_app_records(
    mini_app_id: str,
    recordType: Optional[str] = None,
    user=Depends(get_current_user),
    db=Depends(get_db),
):
    query = {"user_id": user["id"], "mini_app_id": mini_app_id}
    if recordType:
        query["record_type"] = recordType
    cursor = db.mini_app_records.find(query, {"_id": 0}).sort("created_at", -1)
    docs = await cursor.to_list(500)
    return [_record_out(doc) for doc in docs]


@router.post("/{mini_app_id}/records", response_model=MiniAppRecordOut)
async def create_mini_app_record(
    mini_app_id: str,
    data: MiniAppRecordCreate,
    user=Depends(get_current_user),
    db=Depends(get_db),
):
    values = _sanitize_record_values(data.values)
    now = _now()
    doc = {
        "id": str(uuid.uuid4()),
        "user_id": user["id"],
        "mini_app_id": mini_app_id,
        "record_type": data.recordType.strip() or "record",
        "values": values,
        "created_at": now,
        "updated_at": now,
    }
    await db.mini_app_records.insert_one(doc)
    return _record_out(doc)


@router.patch("/{mini_app_id}/records/{record_id}", response_model=MiniAppRecordOut)
async def update_mini_app_record(
    mini_app_id: str,
    record_id: str,
    data: MiniAppRecordUpdate,
    user=Depends(get_current_user),
    db=Depends(get_db),
):
    values = _sanitize_record_values(data.values)
    updated = await db.mini_app_records.find_one_and_update(
        {"id": record_id, "user_id": user["id"], "mini_app_id": mini_app_id},
        {"$set": {"values": values, "updated_at": _now()}},
        return_document=True,
        projection={"_id": 0},
    )
    if not updated:
        raise HTTPException(status_code=404, detail="Mini app record not found")
    return _record_out(updated)


@router.delete("/{mini_app_id}/records/{record_id}")
async def delete_mini_app_record(
    mini_app_id: str,
    record_id: str,
    user=Depends(get_current_user),
    db=Depends(get_db),
):
    result = await db.mini_app_records.delete_one({"id": record_id, "user_id": user["id"], "mini_app_id": mini_app_id})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Mini app record not found")
    return {"ok": True}


def _sanitize_record_values(values: dict[str, object]) -> dict[str, object]:
    if len(values) > MAX_RECORD_VALUES:
        raise HTTPException(status_code=422, detail="Too many record fields")
    clean: dict[str, object] = {}
    for key, value in values.items():
        name = str(key).strip()
        if not name or len(name) > 80:
            raise HTTPException(status_code=422, detail="Invalid record field name")
        if isinstance(value, (str, int, float, bool)) or value is None:
            encoded = str(value) if value is not None else ""
            if len(encoded) > MAX_RECORD_VALUE_CHARS:
                raise HTTPException(status_code=422, detail=f"Record field is too large: {name}")
            clean[name] = value
        else:
            raise HTTPException(status_code=422, detail=f"Unsupported record field value: {name}")
    return clean


def _record_out(doc: dict) -> MiniAppRecordOut:
    return MiniAppRecordOut(
        id=doc["id"],
        miniAppId=doc["mini_app_id"],
        recordType=doc["record_type"],
        values=doc.get("values") or {},
        createdAt=doc["created_at"],
        updatedAt=doc["updated_at"],
    )


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _enforce_requested_runtime(requested: Optional[str], actual: str) -> None:
    if requested and requested != actual:
        raise HTTPException(status_code=422, detail=f"Requested {requested} runtime but LLM returned {actual}")


def _parse_revision_payload(raw: str, data: MiniAppRevisionIn):
    payload = parse_json_object(raw)
    unexpected = set(payload) - {"bundle", "summary", "migrationPlan"}
    if unexpected:
        raise HTTPException(status_code=422, detail=f"Unexpected revision field: {sorted(unexpected)[0]}")
    bundle_payload = payload.get("bundle")
    if not isinstance(bundle_payload, dict):
        raise HTTPException(status_code=422, detail="Revision response requires bundle")
    bundle_payload["id"] = data.currentBundle.id
    bundle_payload["version"] = data.currentBundle.version + 1
    if "widget" not in bundle_payload and data.currentBundle.widget is not None:
        bundle_payload["widget"] = data.currentBundle.widget.model_dump()
    if "metadata" in bundle_payload and isinstance(bundle_payload["metadata"], dict):
        bundle_payload["metadata"]["builtIn"] = False
    bundle = validate_mini_app_bundle(bundle_payload)
    _enforce_revision_compatibility(data.currentBundle, bundle)
    summary = str(payload.get("summary") or "Updated mini app.").strip()[:240]
    migration_plan_raw = payload.get("migrationPlan") or []
    if not isinstance(migration_plan_raw, list):
        raise HTTPException(status_code=422, detail="migrationPlan must be a list")
    migration_plan = [str(item).strip()[:180] for item in migration_plan_raw if str(item).strip()][:8]
    if not migration_plan:
        migration_plan = ["Existing local records stay attached to this mini app id."]
    return bundle, summary, migration_plan


def _enforce_revision_compatibility(previous, next_bundle) -> None:
    next_fields = {field.name: field for field in next_bundle.dataSchema.fields}
    for previous_field in previous.dataSchema.fields:
        next_field = next_fields.get(previous_field.name)
        if next_field is None:
            raise HTTPException(status_code=422, detail=f"Mini app revision cannot remove field: {previous_field.name}")
        if next_field.type != previous_field.type:
            raise HTTPException(status_code=422, detail=f"Mini app revision cannot change field type: {previous_field.name}")


def _parse_widget_payload(raw: str, data: MiniAppWidgetBuildIn) -> MiniAppWidget:
    payload = parse_json_object(raw)
    unexpected = set(payload) - {"widget"}
    if unexpected:
        raise HTTPException(status_code=422, detail=f"Unexpected widget field: {sorted(unexpected)[0]}")
    widget_payload = payload.get("widget")
    if not isinstance(widget_payload, dict):
        raise HTTPException(status_code=422, detail="Widget response requires widget")
    try:
        widget = MiniAppWidget(**widget_payload)
    except Exception as exc:
        raise HTTPException(status_code=422, detail=f"Invalid widget: {str(exc)[:200]}")
    bundle = ensure_mini_app_widget(data.miniApp.model_copy(update={"widget": widget}))
    validate_mini_app_widget(bundle, {action.id for action in bundle.actions})
    if bundle.widget is None:
        raise HTTPException(status_code=422, detail="Widget response requires widget")
    return bundle.widget


def _validated_widget_build_data(data: MiniAppWidgetBuildIn) -> MiniAppWidgetBuildIn:
    source = data.miniApp.model_copy(deep=True)
    if source.codeBundle is not None:
        source.codeBundle.compiledJs = ""
    validated = validate_mini_app_bundle(source.model_dump(exclude_none=True))
    return data.model_copy(update={"miniApp": validated})
