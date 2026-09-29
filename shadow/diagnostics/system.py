"""MOD-99/100: deterministic self-diagnostics for the SHADOW stack."""
from __future__ import annotations
import importlib
import os
from typing import Any

CORE_MODULES = {
    "identity": "shadow.governance",
    "conversation": "shadow.memory.context",
    "reasoning": "shadow.agent.brain",
    "memory": "shadow.memory.governed",
    "personal": "shadow.memory.context",
    "web": "shadow.core.orchestrator",
    "github_development": "shadow.development.github_authorization",
    "integration": "shadow.integration.engine",
    "companion": "shadow.companions.protocol",
    "device_environment": "shadow.environment.intelligence",
    "spatial": "shadow.spatial.intelligence",
    "action_security": "shadow.security.permissions",
}

def run_diagnostics() -> dict[str, Any]:
    checks = {}
    for core, module in CORE_MODULES.items():
        try:
            importlib.import_module(module)
            checks[core] = {"ok": True, "module": module}
        except Exception as exc:
            checks[core] = {"ok": False, "module": module, "error": type(exc).__name__}
    online_only = not bool(os.getenv("SHADOW_OFFLINE_MODE"))
    return {
        "ok": all(x["ok"] for x in checks.values()) and online_only,
        "online_only": online_only,
        "offline_ai_fallback": False,
        "cores": checks,
    }
