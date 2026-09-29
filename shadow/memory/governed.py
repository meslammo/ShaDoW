"""MOD-90/91: governed long-term memory facade for SHADOW."""
from __future__ import annotations
from dataclasses import dataclass
import re
from typing import Any
from .persistent import MemoryItem, PersistentMemory

_SECRET_RE = re.compile(r"(password|passphrase|api[ _-]?key|access[ _-]?token|private key|secret|كلمة السر|باسورد|توكن|مفتاح سري)", re.I)

@dataclass(frozen=True)
class MemoryDecision:
    allowed: bool
    reason: str

class GovernedMemory:
    def __init__(self, store: PersistentMemory | None = None):
        self.store = store or PersistentMemory()

    def decide_save(self, text: str, *, explicit: bool, sensitive: bool = False) -> MemoryDecision:
        value = str(text or "").strip()
        if not value:
            return MemoryDecision(False, "empty")
        if not explicit:
            return MemoryDecision(False, "explicit_request_required")
        if sensitive or _SECRET_RE.search(value):
            return MemoryDecision(False, "sensitive_or_credential_blocked")
        return MemoryDecision(True, "policy_allow")

    def save(self, text: str, *, explicit: bool = False, kind: str = "fact", tags: tuple[str, ...] = (), source: str = "runtime") -> dict[str, Any]:
        decision = self.decide_save(text, explicit=explicit)
        if not decision.allowed:
            return {"saved": False, "reason": decision.reason}
        item = self.store.put(text, kind=kind, tags=tags, source=source)
        return {"saved": True, "reason": decision.reason, "id": item.id}

    def search(self, query: str, limit: int = 8) -> list[MemoryItem]:
        return self.store.search(query, max(1, min(int(limit), 20)))

    def forget(self, item_id: str, *, explicit: bool = False) -> dict[str, Any]:
        if not explicit:
            return {"forgotten": False, "reason": "explicit_request_required"}
        return {"forgotten": self.store.delete(str(item_id))}

    def status(self) -> dict[str, Any]:
        return {"items": len(self.store.all()), "governed_writes": True, "credential_block": True, "explicit_save_required": True}
