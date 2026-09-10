"""Home and vehicle domain boundaries for SHADOW.

MOD-17.1: keep Home/Car in the runtime model now while leaving real endpoint
credentials and device-specific control for a later integration step.
"""
from __future__ import annotations
from dataclasses import dataclass
from typing import Any
from shadow.devices.adapters import HomeAdapter, CarAdapter

@dataclass
class DomainStatus:
    name: str
    adapter_ready: bool
    connected: bool
    endpoint_configured: bool
    capabilities: list[str]

    def as_dict(self) -> dict[str, Any]:
        return {
            "domain": self.name,
            "adapter_ready": self.adapter_ready,
            "connected": self.connected,
            "endpoint_configured": self.endpoint_configured,
            "capabilities": list(self.capabilities),
        }

class DomainRegistry:
    """Provider-neutral Home/Car boundary.

    The registry is intentionally usable before a real gateway/vehicle API
    exists. It reports readiness without pretending that a device is connected.
    """

    def __init__(self) -> None:
        self.home = HomeAdapter()
        self.car = CarAdapter()

    def status(self, domain: str) -> dict[str, Any]:
        adapter = self._adapter(domain)
        raw = adapter.status()
        return DomainStatus(
            name=domain,
            adapter_ready=True,
            connected=bool(raw.get("online", False)),
            endpoint_configured=False,
            capabilities=adapter.capabilities(),
        ).as_dict()

    def execute(self, domain: str, capability: str, payload: dict[str, Any], *, authorized: bool = False) -> dict[str, Any]:
        return self._adapter(domain).execute(capability, payload, authorized=authorized)

    def _adapter(self, domain: str):
        key = str(domain).strip().lower()
        if key == "home":
            return self.home
        if key == "car":
            return self.car
        raise ValueError(f"unknown domain: {domain}")
