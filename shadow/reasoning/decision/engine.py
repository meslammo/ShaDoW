from __future__ import annotations
from typing import Any, Dict, Optional

class DecisionEngine:
    """Conservative decision gate: risky actions require explicit confirmation."""
    def decide(self, request: str, *, context: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        low = request.lower()
        risky = any(k in low for k in ("delete", "remove", "send money", "buy", "unlock", "door", "device", "car", "home"))
        return {"action": "respond", "requires_confirmation": risky, "reason": "potentially consequential request" if risky else "informational"}
