"""Append-only structured audit events without storing secrets."""
from __future__ import annotations
from dataclasses import asdict, dataclass
import json, time
from pathlib import Path

@dataclass(frozen=True)
class AuditEvent:
    event: str
    actor: str="shadow"
    request_id: str|None=None
    device_id: str|None=None
    outcome: str="ok"
    timestamp: float=0.0
    metadata: dict|None=None

class AuditLog:
    def __init__(self,path: str|Path=".shadow/audit.jsonl"):
        self.path=Path(path); self.path.parent.mkdir(parents=True,exist_ok=True)
    def record(self,event: str, **kwargs):
        data=asdict(AuditEvent(event=event,timestamp=time.time(),metadata=kwargs.pop("metadata",None),**kwargs))
        # Explicitly strip likely credentials from metadata.
        if isinstance(data.get("metadata"),dict): data["metadata"]={k:v for k,v in data["metadata"].items() if "key" not in k.lower() and "token" not in k.lower() and "secret" not in k.lower()}
        with self.path.open("a",encoding="utf-8") as f: f.write(json.dumps(data,ensure_ascii=False)+"\n")
    def read(self):
        if not self.path.exists(): return []
        return [json.loads(x) for x in self.path.read_text(encoding="utf-8").splitlines() if x]
