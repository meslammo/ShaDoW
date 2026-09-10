"""Authenticated gateway for the SHADOW runtime."""
from __future__ import annotations
import os
from typing import Any

try:
    from fastapi import FastAPI, Header, HTTPException
    from pydantic import BaseModel, Field
except ImportError:
    FastAPI = None

from shadow.runtime.integration import ShadowService
from shadow.runtime.transport import ShadowRequest, response_from_result

if FastAPI:
    app = FastAPI(title="SHADOW Gateway", version="0.40")
    service = ShadowService()
    token = os.getenv("SHADOW_GATEWAY_TOKEN", "")

    class RequestModel(BaseModel):
        # Canonical gateway form.
        text: str | None = None
        # Mobile/tool protocol form.
        action: str | None = None
        payload: dict[str, Any] = Field(default_factory=dict)
        session_id: str | None = None
        device_id: str | None = None
        context: dict[str, Any] = Field(default_factory=dict)

    @app.get("/health")
    def health():
        return {"service": "SHADOW", "status": "ok", "version": "0.40"}

    @app.post("/v1/request")
    def request(body: RequestModel, authorization: str | None = Header(default=None)):
        if token and authorization != f"Bearer {token}":
            raise HTTPException(status_code=401, detail="unauthorized")
        text = body.text
        if not text and body.action == "run_goal":
            text = str(body.payload.get("goal", "")).strip()
        if not text:
            raise HTTPException(status_code=400, detail="text or run_goal payload.goal is required")
        ctx = dict(body.context)
        if body.action:
            ctx["action"] = body.action
        req = ShadowRequest(text, session_id=body.session_id, device_id=body.device_id, context=ctx)
        result = service.request(text, session_id=body.session_id, device_id=body.device_id, context=ctx)
        return response_from_result(req, result).__dict__
else:
    app = None
