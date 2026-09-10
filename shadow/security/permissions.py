"""Fail-closed capability permission policy."""
from __future__ import annotations
from dataclasses import dataclass

@dataclass(frozen=True)
class PermissionDecision:
    allowed: bool
    requires_confirmation: bool
    reason: str

class PermissionManager:
    SAFE_READ={"memory.read","device.read","location.read","screen.read","web.search"}
    CONFIRM={"app.launch","file.write","message.send","calendar.write","device.control","home.control","car.control"}
    def decide(self, capability: str, *, confirmed: bool=False, automation_granted: bool=False) -> PermissionDecision:
        if capability in self.SAFE_READ: return PermissionDecision(True,False,"safe read capability")
        if capability in self.CONFIRM:
            if confirmed or automation_granted: return PermissionDecision(True,False,"explicit authorization")
            return PermissionDecision(False,True,"user confirmation required")
        return PermissionDecision(False,True,"unknown capability denied by default")
