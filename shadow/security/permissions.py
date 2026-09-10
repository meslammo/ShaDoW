"""Fail-closed capability permission policy.

MOD-17.3: align runtime domain tools with the existing permission matrix.
"""
from __future__ import annotations
from dataclasses import dataclass

@dataclass(frozen=True)
class PermissionDecision:
    allowed: bool
    requires_confirmation: bool
    reason: str

class PermissionManager:
    SAFE_READ={
        "memory.read","memory_search","memory.search",
        "device.read","device_status","device.status",
        "location.read","screen.read","web.search",
        "calculator","calculator.read","time_now","time.now",
        "home_status","car_status",
    }
    CONFIRM={
        "app.launch","file.write","message.send","calendar.write",
        "device.control","home.control","home_control",
        "car.control","car_control","memory.write",
    }

    def decide(self, capability:str, *, confirmed:bool=False, automation_granted:bool=False)->PermissionDecision:
        if capability in self.SAFE_READ:
            return PermissionDecision(True,False,"safe read capability")
        if capability in self.CONFIRM:
            if confirmed or automation_granted:
                return PermissionDecision(True,False,"explicit authorization")
            return PermissionDecision(False,True,"user confirmation required")
        return PermissionDecision(False,True,"unknown capability denied by default")
