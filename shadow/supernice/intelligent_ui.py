"""Safe structured response blocks inspired by adaptive conversational UI.

This module produces data-only UI contracts. It never emits HTML, JavaScript,
or executable client code. Client rendering remains a separate integration gate.
"""
from __future__ import annotations

import re
from typing import Any

SCHEMA = "shadow.intelligent-ui.v1"
_ACTION_ID = re.compile(r"^[a-zA-Z0-9_.:-]{1,80}$")
_ALLOWED = {"text", "card", "table", "chart", "checklist", "action", "code", "diagram", "image"}

def _clean(value: Any, limit: int = 4000) -> str:
    return str(value or "").strip()[:limit]

def _safe_block(block: Any) -> dict[str, Any] | None:
    if not isinstance(block, dict):
        return None
    kind = _clean(block.get("type"), 24).lower()
    if kind not in _ALLOWED:
        return None
    if kind == "text":
        text = _clean(block.get("text"), 5000)
        return {"type": kind, "text": text} if text else None
    if kind == "card":
        title, body = _clean(block.get("title"), 180), _clean(block.get("body"), 2000)
        if not title and not body:
            return None
        return {"type": kind, "title": title, "body": body}
    if kind == "table":
        columns = [_clean(x, 80) for x in block.get("columns", [])[:8] if _clean(x, 80)]
        rows = block.get("rows", [])
        if not columns or not isinstance(rows, list):
            return None
        safe_rows = [[_clean(cell, 300) for cell in row[:len(columns)]] for row in rows[:20] if isinstance(row, list)]
        return {"type": kind, "columns": columns, "rows": safe_rows}
    if kind == "chart":
        chart_type = _clean(block.get("chart_type"), 16).lower()
        if chart_type not in {"bar", "line", "pie"}:
            return None
        data = block.get("data", [])
        if not isinstance(data, list) or len(data) > 50:
            return None
        safe_data = []
        for point in data:
            if not isinstance(point, dict):
                continue
            label = _clean(point.get("label"), 100)
            value = point.get("value")
            if label and isinstance(value, (int, float)) and not isinstance(value, bool):
                safe_data.append({"label": label, "value": float(value)})
        return {"type": kind, "chart_type": chart_type, "data": safe_data} if safe_data else None
    if kind == "checklist":
        items = [_clean(x, 300) for x in block.get("items", [])[:20] if _clean(x, 300)]
        return {"type": kind, "items": items} if items else None
    if kind == "action":
        action_id = _clean(block.get("action_id"), 80)
        label = _clean(block.get("label"), 120)
        if not _ACTION_ID.fullmatch(action_id) or not label:
            return None
        return {"type": kind, "action_id": action_id, "label": label, "requires_confirmation": True}
    if kind == "code":
        code = _clean(block.get("code"), 12000)
        language = _clean(block.get("language"), 32) or "text"
        return {"type": kind, "language": language, "code": code} if code else None
    if kind == "diagram":
        source = _clean(block.get("source"), 8000)
        if not source or re.search(r"<script|javascript:|<iframe", source, re.I):
            return None
        return {"type": kind, "format": "mermaid", "source": source}
    if kind == "image":
        url = _clean(block.get("url"), 2000)
        if not url.startswith("https://"):
            return None
        return {"type": kind, "url": url, "alt": _clean(block.get("alt"), 300)}
    return None

def build_intelligent_response(
    answer: str,
    components: list[dict[str, Any]] | None = None,
    *,
    mode: str = "auto",
    progress: dict[str, Any] | None = None,
) -> dict[str, Any]:
    blocks = []
    for block in (components or [])[:12]:
        safe = _safe_block(block)
        if safe:
            blocks.append(safe)
    selected_mode = mode if mode in {"auto", "text", "visual", "interactive"} else "auto"
    if not blocks:
        selected_mode = "text"
    return {
        "schema": SCHEMA,
        "mode": selected_mode,
        "answer": _clean(answer, 12000),
        "components": blocks,
        "progress": {
            "state": _clean((progress or {}).get("state"), 24) or "complete",
            "label": _clean((progress or {}).get("label"), 120),
        },
        "execution_policy": {
            "components_are_data_only": True,
            "actions_require_confirmation": True,
            "arbitrary_html_or_javascript": False,
        },
    }
