from __future__ import annotations
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

@dataclass
class MemoryContext:
    session_id: str
    user_id: Optional[str] = None
    device_id: Optional[str] = None
    recent: List[Dict[str, Any]] = field(default_factory=list)
    facts: Dict[str, Any] = field(default_factory=dict)
    preferences: Dict[str, Any] = field(default_factory=dict)

    def as_prompt_context(self) -> Dict[str, Any]:
        return {"session_id": self.session_id, "user_id": self.user_id,
                "device_id": self.device_id, "recent": list(self.recent),
                "facts": dict(self.facts), "preferences": dict(self.preferences)}

class MemoryFacade:
    """Stable facade; storage backend can be local, Postgres or encrypted cloud."""
    def __init__(self, store: Any = None): self.store = store

    def load(self, session_id: str, *, user_id: Optional[str] = None, device_id: Optional[str] = None) -> MemoryContext:
        data = self.store.load(session_id) if self.store and hasattr(self.store, "load") else {}
        return MemoryContext(session_id=session_id, user_id=user_id, device_id=device_id,
                             recent=list(data.get("recent", [])), facts=dict(data.get("facts", {})),
                             preferences=dict(data.get("preferences", {})))

    def save(self, context: MemoryContext) -> None:
        if self.store and hasattr(self.store, "save"):
            self.store.save(context.session_id, context.as_prompt_context())
