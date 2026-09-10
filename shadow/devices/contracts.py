"""Safe, provider-neutral contracts for phone and external device adapters."""
from __future__ import annotations
from dataclasses import dataclass, field
from typing import Any, Dict, Protocol

@dataclass
class DeviceInfo:
    id: str
    name: str
    type: str
    online: bool = False
    battery: int | None = None
    location: str | None = None
    permissions: list[str] = field(default_factory=list)

class DeviceAdapter(Protocol):
    def discover(self) -> list[DeviceInfo]: ...
    def status(self, device_id: str) -> DeviceInfo: ...
    def execute(self, device_id: str, action: str, parameters: Dict[str, Any], *, confirmed: bool = False) -> Dict[str, Any]: ...

class PermissionGate(Protocol):
    def allowed(self, capability: str, *, confirmed: bool = False) -> bool: ...
