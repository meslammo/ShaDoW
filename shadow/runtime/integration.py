from __future__ import annotations
from typing import Any, Dict, Optional
from .runtime import RuntimeResult, ShadowRuntime

class ShadowService:
    """Long-lived service facade for HTTP, WebSocket, Android and automation adapters."""
    def __init__(self, runtime: Optional[ShadowRuntime] = None):
        self.runtime = runtime or ShadowRuntime()

    def request(self, text: str, *, session_id: Optional[str] = None,
                device_id: Optional[str] = None, context: Optional[Dict[str, Any]] = None) -> RuntimeResult:
        ctx = dict(context or {})
        if session_id: ctx["session_id"] = session_id
        if device_id: ctx["device_id"] = device_id
        return self.runtime.handle(text, context=ctx)
