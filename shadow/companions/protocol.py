"""MOD-95: provider-neutral SHADOW companion protocol."""
from __future__ import annotations
from dataclasses import asdict, dataclass, field
from time import time
from typing import Any

STATES = ("DISCOVERED", "AUTHENTICATED", "TRUSTED", "ACTIVE", "REVOKED")

@dataclass
class Companion:
    companion_id: str
    kind: str
    state: str = "DISCOVERED"
    capabilities: tuple[str, ...] = ()
    permissions: tuple[str, ...] = ()
    last_seen: float = field(default_factory=time)

class CompanionProtocol:
    def __init__(self):
        self._items: dict[str, Companion] = {}

    def discover(self, companion_id: str, kind: str, capabilities: list[str] | tuple[str, ...] = ()) -> Companion:
        cid = str(companion_id).strip()
        if not cid:
            raise ValueError("companion_id_required")
        c = Companion(cid, str(kind or "generic"), "DISCOVERED", tuple(sorted(set(map(str, capabilities)))), (), time())
        self._items[cid] = c
        return c

    def authenticate(self, companion_id: str) -> Companion:
        c = self._items[companion_id]
        if c.state not in {"DISCOVERED", "AUTHENTICATED"}:
            raise PermissionError("invalid_auth_transition")
        c.state = "AUTHENTICATED"; c.last_seen = time(); return c

    def trust(self, companion_id: str, permissions: list[str] | tuple[str, ...] = ()) -> Companion:
        c = self._items[companion_id]
        if c.state not in {"AUTHENTICATED", "TRUSTED"}:
            raise PermissionError("authentication_required")
        c.state = "TRUSTED"; c.permissions = tuple(sorted(set(map(str, permissions)))); c.last_seen = time(); return c

    def activate(self, companion_id: str) -> Companion:
        c = self._items[companion_id]
        if c.state != "TRUSTED":
            raise PermissionError("trust_required")
        c.state = "ACTIVE"; c.last_seen = time(); return c

    def revoke(self, companion_id: str) -> Companion:
        c = self._items[companion_id]
        c.state = "REVOKED"; c.permissions = (); c.last_seen = time(); return c

    def can(self, companion_id: str, capability: str) -> bool:
        c = self._items.get(companion_id)
        return bool(c and c.state in {"TRUSTED", "ACTIVE"} and capability in c.permissions)

    def snapshot(self) -> list[dict[str, Any]]:
        return [asdict(c) for c in self._items.values()]
