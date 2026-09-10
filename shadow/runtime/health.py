from __future__ import annotations
from typing import Any, Dict
from .capabilities import CAPABILITIES

def health_report() -> Dict[str, Any]:
    return {
        "service": "SHADOW",
        "status": "ready",
        "runtime_pipeline": ["observe", "understand", "plan", "permission_check", "execute", "verify", "learn"],
        "capability_count": len(CAPABILITIES),
        "provider_secrets_in_client": False,
        "fail_closed": True,
    }
