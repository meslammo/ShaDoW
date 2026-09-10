"""Android entry point for the embedded SHADOW Python runtime.

MOD-20.5: make the repository's shadow/ directory importable as the
``shadow`` package inside Chaquopy, while keeping its source root limited
to shadow/ so the Android build does not recurse into app/build.
"""
from __future__ import annotations
import os
import sys
import types
from typing import Any, Dict, Optional

_runtime = None


def _install_shadow_package_alias() -> None:
    """Expose shadow/ as package ``shadow`` inside the Chaquopy VM."""
    if "shadow" in sys.modules:
        return
    package = types.ModuleType("shadow")
    package.__path__ = [os.path.dirname(os.path.abspath(__file__))]
    package.__package__ = "shadow"
    sys.modules["shadow"] = package


def _get_runtime(home: Optional[str] = None):
    global _runtime
    if home:
        os.chdir(home)
    _install_shadow_package_alias()
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
