from __future__ import annotations
from dataclasses import dataclass, field
from typing import Any, Dict, Optional

@dataclass
class DeviceContext:
    device_id: str
    device_type: str = "android"
    capabilities: list[str] = field(default_factory=list)
    metadata: Dict[str, Any] = field(default_factory=dict)
    location: Optional[Dict[str, Any]] = None

    def as_context(self) -> Dict[str, Any]:
        return {"device_id": self.device_id, "device_type": self.device_type,
                "capabilities": list(self.capabilities), "metadata": dict(self.metadata),
                "location": self.location}
