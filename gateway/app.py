"""Minimal HTTPS-ready gateway shell for the SHADOW runtime."""
from __future__ import annotations
import os
from typing import Any

try:
    from fastapi import FastAPI, Header, HTTPException
    from pydantic import BaseModel
except ImportError:  # runtime package remains usable without gateway extras
    FastAPI = None

from shadow.runtime.integration import ShadowService
from shadow.runtime.transport import ShadowRequest, response_from_result

if FastAPI:
    app = FastAPI(title="SHADOW Gateway", version="0.39")
    service = ShadowService()
    token = os.getenv("SHADOW_GATEWAY_TOKEN", "")

    class RequestModel(BaseModel):
        text: str
        session_id: str | None = None
        device_id: str | None = None
        context: dict[str, Any] = {}

    @app.get("/health")
    def health(): return {"service": "SHADOW", "status": "ok"}

    @app.post("/v1/request")
    def request(body: RequestModel, authorization: str | None = Header(default=None)):
        if token and authorization != f"Bearer {token}":
            raise HTTPException(status_code=401, detail="unauthorized")
        req = ShadowRequest(body.text, session_id=body.session_id, device_id=body.device_id, context=body.context)
        return response_from_result(req, service.request(body.text, session_id=body.session_id, device_id=body.device_id, context=body.context)).__dict__
else:
    app = None
