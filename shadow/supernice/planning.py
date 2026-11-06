"""Validated model-produced task plans and safe capability routing for SHADOW."""
from __future__ import annotations

from dataclasses import dataclass
import re
from typing import Any, Mapping

from .catalog import CORE_BY_ID

PLAN_SCHEMA = "shadow.task-plan.v1"
RISK_LEVELS = {"low", "medium", "high", "critical"}
TOOL_NAMES = {
    "calculator", "web_search", "web_fetch", "web_research", "github_read",
    "memory_search", "memory_save", "memory_forget", "file_read", "file_write",
    "device_action",
}
_STEP_ID = re.compile(r"^[a-zA-Z0-9_-]{1,40}$")
_HIGH_RISK = re.compile(
    r"(delete|wipe|format|purchase|buy|pay|transfer|send|publish|deploy|commit|push|merge|"
    r"حذف|امسح|فورمات|شراء|دفع|تحويل|انشر|انشره|ادمج|ارفع)",
    re.I,
)


@dataclass(frozen=True)
class PlanValidation:
    ok: bool
    plan: dict[str, Any] | None
    errors: tuple[str, ...] = ()


def validate_task_plan(payload: Any, request: str = "") -> PlanValidation:
    """Validate untrusted model output; never accept model-asserted completed steps."""
    if not isinstance(payload, Mapping):
        return PlanValidation(False, None, ("plan_not_object",))
    errors: list[str] = []
    if payload.get("schema") != PLAN_SCHEMA:
        errors.append("unsupported_schema")
    goal = str(payload.get("goal") or "").strip()
    if not goal or len(goal) > 1000:
        errors.append("invalid_goal")
    assumptions = payload.get("assumptions", [])
    if not isinstance(assumptions, list) or len(assumptions) > 12 or any(not isinstance(x, str) or len(x) > 500 for x in assumptions):
        errors.append("invalid_assumptions")
        assumptions = []
    raw_steps = payload.get("steps")
    if not isinstance(raw_steps, list) or not 1 <= len(raw_steps) <= 12:
        errors.append("invalid_steps")
        raw_steps = []
    steps: list[dict[str, Any]] = []
    seen: set[str] = set()
    for index, raw in enumerate(raw_steps):
        if not isinstance(raw, Mapping):
            errors.append(f"step_{index}_not_object")
            continue
        step_id = str(raw.get("id") or "")
        title = str(raw.get("title") or "").strip()
        if not _STEP_ID.fullmatch(step_id) or step_id in seen:
            errors.append(f"step_{index}_invalid_id")
        seen.add(step_id)
        if not title or len(title) > 240:
            errors.append(f"step_{index}_invalid_title")
        core_id = raw.get("core_id")
        if core_id is not None and (not isinstance(core_id, str) or core_id not in CORE_BY_ID):
            errors.append(f"step_{index}_unknown_core")
            core_id = None
        tool = raw.get("tool")
        if tool not in (None, "") and tool not in TOOL_NAMES:
            errors.append(f"step_{index}_unknown_tool")
            tool = None
        # The model may propose work, but only runtime evidence may advance status.
        if raw.get("status", "pending") not in {"pending", "blocked"}:
            errors.append(f"step_{index}_forged_status")
        approval = raw.get("requires_approval", False)
        if not isinstance(approval, bool):
            errors.append(f"step_{index}_invalid_approval")
            approval = True
        steps.append({
            "id": step_id, "title": title, "status": "pending",
            "core_id": core_id, "tool": tool, "requires_approval": approval,
        })
    risk = payload.get("risk", "medium")
    if risk not in RISK_LEVELS:
        errors.append("invalid_risk")
        risk = "high"
    approval_required = payload.get("approval_required", False)
    if not isinstance(approval_required, bool):
        errors.append("invalid_approval_required")
        approval_required = True
    if _HIGH_RISK.search(str(request)) or any(x["requires_approval"] for x in steps):
        approval_required = True
        if risk in {"low", "medium"}:
            risk = "high"
    clarification = str(payload.get("clarification_question") or "").strip()
    if len(clarification) > 500:
        errors.append("clarification_too_long")
    recommended_core = payload.get("recommended_core")
    if recommended_core is not None and (not isinstance(recommended_core, str) or recommended_core not in CORE_BY_ID):
        errors.append("unknown_recommended_core")
        recommended_core = None
    if errors:
        return PlanValidation(False, None, tuple(errors))
    return PlanValidation(True, {
        "schema": PLAN_SCHEMA,
        "goal": goal,
        "assumptions": list(assumptions),
        "steps": steps,
        "clarification_question": clarification,
        "risk": risk,
        "approval_required": approval_required,
        "recommended_core": recommended_core,
        "source": "model_validated",
    })


def strip_task_plan_envelope(answer: str) -> str:
    text = str(answer or "")
    opening = re.search(r"<shadow_task_plan>", text, re.I)
    if not opening:
        return text.strip()
    closing = text.lower().find("</shadow_task_plan>", opening.end())
    if closing < 0:
        return text[:opening.start()].strip()
    return (text[:opening.start()] + text[closing + len("</shadow_task_plan>"):]).strip()


def extract_task_plan(answer: str) -> dict[str, Any] | None:
    """Parse one explicitly delimited JSON object, including nested step objects."""
    import json
    text = str(answer or "")
    opening = re.search(r"<shadow_task_plan>", text, re.I)
    if not opening:
        return None
    start = text.find("{", opening.end())
    if start < 0:
        return None
    depth = 0
    in_string = False
    escaped = False
    end = -1
    for index in range(start, len(text)):
        char = text[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\\\":
                escaped = True
            elif char == '"':
                in_string = False
        elif char == '"':
            in_string = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                end = index + 1
                break
    if end < 0 or text.find("</shadow_task_plan>", end) < 0:
        return None
    try:
        value = json.loads(text[start:end])
    except (TypeError, ValueError):
        return None
    return dict(value) if isinstance(value, Mapping) else None


def planning_contract() -> dict[str, Any]:
    return {
        "schema": PLAN_SCHEMA,
        "required_fields": ["schema", "goal", "assumptions", "steps", "clarification_question", "risk", "approval_required"],
        "step_fields": ["id", "title", "status", "core_id", "tool", "requires_approval"],
        "allowed_core_ids": sorted(CORE_BY_ID),
        "allowed_tools": sorted(TOOL_NAMES),
        "rules": [
            "Return the plan inside <shadow_task_plan> JSON delimiters.",
            "Use only registered core_id values and tool names supplied by SHADOW.",
            "Every proposed step starts with status pending; never claim a step completed.",
            "Ask one clarification question when a missing detail blocks safe execution.",
            "Mark destructive, external, financial, publishing, and repository mutations as approval-required.",
            "Do not include hidden reasoning; provide concise user-visible step summaries only.",
        ],
    }
