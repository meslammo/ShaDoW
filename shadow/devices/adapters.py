"""Provider-neutral device adapters; concrete integrations plug in without changing the brain."""
from __future__ import annotations
from dataclasses import dataclass
from typing import Any, Protocol

class DeviceAdapter(Protocol):
    device_type: str
    def capabilities(self)->list[str]: ...
    def status(self)->dict[str,Any]: ...
    def execute(self, capability:str, payload:dict[str,Any], *, authorized:bool=False)->dict[str,Any]: ...

@dataclass
class SafeAdapter:
    device_type: str
    name: str
    _capabilities: tuple[str,...]
    online: bool=False
    def capabilities(self): return list(self._capabilities)
    def status(self): return {'name':self.name,'type':self.device_type,'online':self.online,'capabilities':self.capabilities()}
    def execute(self,capability,payload,*,authorized=False):
        if capability not in self._capabilities: return {'ok':False,'error':'unsupported capability'}
        if not authorized: return {'ok':False,'error':'authorization required'}
        return {'ok':False,'error':'adapter endpoint not configured'}

class HomeAdapter(SafeAdapter):
    def __init__(self,name='Home'): super().__init__('home',name,('home.read','home.control'))
class CarAdapter(SafeAdapter):
    def __init__(self,name='Car'): super().__init__('car',name,('car.read','car.control'))
class CameraAdapter(SafeAdapter):
    def __init__(self,name='Camera'): super().__init__('camera',name,('camera.read','camera.capture'))
class ComputerAdapter(SafeAdapter):
    def __init__(self,name='Computer'): super().__init__('computer',name,('screen.read','app.launch','file.write'))
