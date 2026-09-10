"""Android entry point for the embedded SHADOW Python runtime.

MOD-19.17: the Chaquopy source root is the repository's shadow/ directory,
so runtime packages are imported as top-level modules in the embedded VM.
"""
from __future__ import annotations
import os
from typing import Any, Dict, Optional

_runtime = None


def _get_runtime(home: Optional[str] = None):
    global _runtime
    if home:
        os.chdir(home)
    if _runtime is None:
        from runtime import ShadowRuntime
        _runtime = ShadowRuntime()
    return _runtime


def handle(request: str, home: Optional[str] = None) -> Dict[str, Any]:
    runtime = _get_runtime(home)
    result = runtime.handle(str(request or ""), context={"device_id": "android-local"})
    return {
        "answer": result.answer,
        "confidence": result.confidence,
        "verified": result.verified,
        "requires_confirmation": result.requires_confirmation,
        "plan": list(result.plan),
        "actions": list(result.actions),
        "metadata": dict(result.metadata),
    }


def health(home: Optional[str] = None) -> Dict[str, Any]:
    _get_runtime(home)
    from runtime.health import health_report
    return health_report()
