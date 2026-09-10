"""Transport-neutral envelopes for HTTP/WebSocket/Android/desktop clients."""
from __future__ import annotations
from dataclasses import dataclass, field
from typing import Any, Dict, Optional
from uuid import uuid4

@dataclass
class ShadowRequest:
    text: str
    request_id: str = field(default_factory=lambda: str(uuid4()))
    session_id: Optional[str] = None
    device_id: Optional[str] = None
    context: Dict[str, Any] = field(default_factory=dict)

@dataclass
class ShadowResponse:
    request_id: str
    text: str
    confidence: float
    verified: bool
    actions: list[Dict[str, Any]] = field(default_factory=list)
    requires_confirmation: bool = False


def response_from_result(request: ShadowRequest, result: Any) -> ShadowResponse:
    return ShadowResponse(request_id=request.request_id, text=result.answer,
                          confidence=result.confidence, verified=result.verified,
                          actions=result.actions, requires_confirmation=result.requires_confirmation)
