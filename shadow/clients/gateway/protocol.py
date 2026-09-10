from __future__ import annotations
from dataclasses import dataclass, asdict
import json
from typing import Any, Dict

@dataclass(frozen=True)
class ClientRequest:
    request_id: str
    action: str
    payload: Dict[str, Any]
    context: Dict[str, Any]
    def to_json(self) -> str:
        return json.dumps(asdict(self), ensure_ascii=False, separators=(",", ":"))
