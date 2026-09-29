"""MOD-37.5: device capability model."""
from dataclasses import dataclass, asdict

@dataclass(frozen=True)
class DeviceCapabilities:
    microphone:bool=False; camera:bool=False; storage:bool=False; contacts:bool=False
    phone_calls:bool=False; accessibility:bool=False; ir:bool=False; bluetooth:bool=False
    wifi:bool=False; sensors:bool=False
    def to_dict(self): return asdict(self)

class CapabilityPolicy:
    def allowed(self,capability:str,available:DeviceCapabilities)->bool:
        return bool(getattr(available,capability,False))
