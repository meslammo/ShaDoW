"""Concrete, permission-gated action execution for SHADOW runtime.

MOD-16.1: turns planned capabilities into executable adapters without bypassing policy.
"""
from __future__ import annotations
from dataclasses import dataclass, field
from typing import Any, Callable, Dict, Optional

from shadow.security.permissions import PermissionManager

@dataclass
class ActionResult:
    action: str
    success: bool
    output: str
    requires_confirmation: bool = False
    metadata: Dict[str, Any] = field(default_factory=dict)

class ActionExecutor:
    def __init__(self, permissions: Optional[PermissionManager] = None):
        self.permissions = permissions or PermissionManager()
        self._handlers: Dict[str, Callable[..., Any]] = {}

    def register(self, action: str, handler: Callable[..., Any]) -> None:
        if not action or not callable(handler):
            raise ValueError("action and callable handler are required")
        self._handlers[action] = handler

    def execute(self, action: str, *, confirmed: bool = False,
                automation_granted: bool = False, **kwargs: Any) -> ActionResult:
        decision = self.permissions.decide(action, confirmed=confirmed, automation_granted=automation_granted)
        if not decision.allowed:
            return ActionResult(action, False, decision.reason, decision.requires_confirmation,
                                {"permission": "denied"})
        handler = self._handlers.get(action)
        if handler is None:
            return ActionResult(action, False, "No execution adapter is registered for this capability.",
                                False, {"permission": "allowed", "adapter": "missing"})
        try:
            value = handler(**kwargs)
            return ActionResult(action, True, str(value) if value is not None else "ok", False,
                                {"permission": "allowed", "adapter": "registered"})
        except Exception as exc:
            return ActionResult(action, False, f"Action failed safely: {exc}", False,
                                {"permission": "allowed", "error": type(exc).__name__})
