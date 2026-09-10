"""Authorized multi-device registry for SHADOW."""
from __future__ import annotations
from dataclasses import asdict, dataclass
from typing import Any
import time

@dataclass
class Device:
    id: str
    name: str
    type: str
    owner: str = "local"
    location: str | None = None
    last_location: str | None = None
    battery: int | None = None
    online: bool = False
    last_seen: float = 0.0
    authorized: bool = False
    capabilities: tuple[str,...] = ()

class DeviceRegistry:
    def __init__(self): self._devices: dict[str,Device]={}
    def register(self, device: Device) -> Device: self._devices[device.id]=device; return device
    def heartbeat(self, device_id: str, *, battery: int|None=None, location: str|None=None, online=True) -> Device:
        d=self._devices[device_id]; d.online=online; d.last_seen=time.time()
        if battery is not None: d.battery=max(0,min(100,battery))
        if location is not None: d.last_location=location
        return d
    def authorize(self, device_id: str, allowed: bool=True) -> Device: self._devices[device_id].authorized=allowed; return self._devices[device_id]
    def get(self, device_id: str) -> Device|None: return self._devices.get(device_id)
    def list(self) -> list[Device]: return list(self._devices.values())
    def snapshot(self) -> list[dict[str,Any]]: return [asdict(d) for d in self.list()]
