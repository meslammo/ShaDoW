"""Android entry point for the embedded SHADOW Python runtime.

MOD-24.3: configure online AI in-process while retaining a complete offline fallback.
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


def _seed_owner_memory(runtime) -> None:
    """Give SHADOW useful non-sensitive background without importing private history."""
    if any("shadow-owner-profile-v1" in m.tags for m in runtime.memory.all()):
        return
    facts = [
        "Owner name: محمد.",
        "Owner prefers Egyptian colloquial Arabic, concise direct practical replies, and masculine addressing.",
        "Assistant identity: SHADOW, with a JARVIS-inspired personal-assistant experience.",
        "Primary project context: SHADOW Android assistant with native Android UI and embedded Python runtime.",
        "Related project context: NEXO is an important software/game project for the owner.",
        "Development workflow: owner often works from an Android phone and browser-based development tools.",
        "Product preference: simple uncluttered interfaces with clear visible controls and useful automation.",
    ]
    for idx, text in enumerate(facts, 1):
        runtime.memory.put(text, kind="profile", tags=("shadow-owner-profile-v1", f"profile-{idx}"), source="seed")


def _get_runtime(home: Optional[str] = None):
    global _runtime
    if home:
        os.chdir(home)
    _install_shadow_package_alias()
    if _runtime is None:
        from runtime import ShadowRuntime
        _runtime = ShadowRuntime()
        _seed_owner_memory(_runtime)
    return _runtime


def configure_online(api_key: str, model: str = "gpt-5.6", home: Optional[str] = None) -> Dict[str, Any]:
    """Configure the cloud provider only in the live app process; Android stores the key encrypted."""
    _get_runtime(home)
    key = str(api_key or "").strip()
    selected_model = str(model or "gpt-5.6").strip() or "gpt-5.6"
    if key:
        os.environ["OPENAI_API_KEY"] = key
        os.environ["SHADOW_MODEL_PROVIDER"] = "openai"
        os.environ["SHADOW_MODEL"] = selected_model
        return {"status": "ready", "provider": "openai", "model": selected_model}
    os.environ.pop("OPENAI_API_KEY", None)
    os.environ["SHADOW_MODEL_PROVIDER"] = "offline"
    os.environ.pop("SHADOW_MODEL", None)
    return {"status": "offline", "provider": "offline", "model": "local-safe"}


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
